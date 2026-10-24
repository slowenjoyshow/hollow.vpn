package app.glassvpn.vpn

import android.os.ParcelFileDescriptor
import app.glassvpn.model.*

/**
 * TUN fd -> локальный SOCKS5 (tun2socks).
 * Используется и для Xray/VLESS (SOCKS на 127.0.0.1:10808), и для внешнего SOCKS5-прокси напрямую.
 *
 * ПОДКЛЮЧИТЬ: hev-socks5-tunnel (heiher/hev-socks5-tunnel) как .aar / JNI.
 * Его API: TProxyStartService(configPath, fd) / TProxyStopService().
 */
object TunBridge {
    private var tun: ParcelFileDescriptor? = null

    private fun yq(v: String) = "'" + v.replace("'", "''") + "'"

    fun start(ctx: EngineContext, socksHost: String, socksPort: Int, user: String = "", pass: String = "") {
        val fd = ctx.establishTun() ?: error("VPN-разрешение не выдано")
        tun = fd
        val yaml = buildString {
            appendLine("tunnel:")
            appendLine("  mtu: ${ctx.settings.tunMtu}")
            appendLine("  ipv4: 172.19.0.1")
            if (ctx.settings.ipv6) appendLine("  ipv6: 'fdfe:dcba:9876::1'")
            appendLine("socks5:")
            appendLine("  port: $socksPort")
            appendLine("  address: ${yq(socksHost)}")
            appendLine("  udp: 'udp'")
            if (user.isNotEmpty()) {
                appendLine("  username: ${yq(user)}")
                appendLine("  password: ${yq(pass)}")
            }
            appendLine("misc:")
            appendLine("  log-level: warn")
        }
        val cfg = ctx.context.filesDir.resolve("tun2socks.yml").apply { writeText(yaml) }
        val ok = try {
            hev.htproxy.TProxyService.TProxyStartService(cfg.absolutePath, fd.fd)
        } catch (e: LinkageError) {
            runCatching { fd.close() }; tun = null
            throw IllegalStateException(
                "Не найден hev-socks5-tunnel.aar. Запусти scripts/fetch-natives.sh и пересобери (${e.message})", e
            )
        }
        if (!ok) {
            runCatching { fd.close() }; tun = null
            error("hev-socks5-tunnel не запустился (см. logcat)")
        }
    }

    fun stop() {
        runCatching { hev.htproxy.TProxyService.TProxyStopService() }
        runCatching { tun?.close() }
        tun = null
    }

    /** (rx, tx) в байтах. */
    fun traffic(): Pair<Long, Long>? = runCatching {
        if (!hev.htproxy.TProxyService.TProxyIsRunning()) return null
        val st = hev.htproxy.TProxyService.TProxyGetStats()   // tx_packets, tx_bytes, rx_packets, rx_bytes
        st[3] to st[1]
    }.getOrNull()
}

/**
 * Xray-core как отдельный процесс (официальный бинарник Xray-android, лежит в jniLibs как libxray.so).
 * Отдельный процесс = нет конфликта Go-рантаймов с AmneziaWG, нет привязки к API gomobile, и сокеты Xray
 * не уходят обратно в туннель: процесс принадлежит тому же приложению, а оно исключено из VPN.
 */
object XrayCore {
    private var proc: Process? = null
    private val log = StringBuffer()

    fun start(ctx: EngineContext, configJson: String) {
        val bin = java.io.File(ctx.context.applicationInfo.nativeLibraryDir, "libxray.so")
        if (!bin.exists()) {
            error("Не найден бинарник Xray (libxray.so). Запусти scripts/fetch-natives.sh и пересобери. Поддерживаются arm64-v8a и x86_64.")
        }
        val dir = ctx.context.filesDir
        copyGeoAssets(ctx)
        val cfg = dir.resolve("xray.json").apply { writeText(configJson) }

        log.setLength(0)
        val pb = ProcessBuilder(bin.absolutePath, "run", "-c", cfg.absolutePath)
            .directory(dir)
            .redirectErrorStream(true)
        pb.environment()["XRAY_LOCATION_ASSET"] = dir.absolutePath
        val p = pb.start()
        proc = p
        Thread {
            runCatching {
                p.inputStream.bufferedReader().forEachLine { line ->
                    log.append(line).append('\n')
                    if (log.length > 4000) log.delete(0, log.length - 3000)
                }
            }
        }.apply { isDaemon = true }.start()

        // Ждём, пока локальный SOCKS-порт начнёт слушать, либо процесс упадёт.
        val deadline = System.currentTimeMillis() + 6000
        while (System.currentTimeMillis() < deadline) {
            if (!p.isAlive) {
                val out = log.toString().trim().lines().takeLast(6).joinToString("\n")
                stop()
                error("Xray завершился с кодом ${runCatching { p.exitValue() }.getOrNull()}:\n$out")
            }
            if (portOpen(XrayConfigBuilder.LOCAL_SOCKS_PORT)) return
            Thread.sleep(100)
        }
        val out = log.toString().trim().lines().takeLast(6).joinToString("\n")
        stop()
        error("Xray не поднялся за 6 с.\n$out")
    }

    fun stop() {
        proc?.let { p ->
            p.destroy()
            runCatching { if (!p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS)) p.destroyForcibly() }
        }
        proc = null
    }

    private fun portOpen(port: Int) = runCatching {
        java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 200) }
        true
    }.getOrDefault(false)

    /** geoip.dat / geosite.dat нужны только готовым JSON-конфигам из панелей, если те ссылаются на geo-правила. */
    private fun copyGeoAssets(ctx: EngineContext) {
        for (name in listOf("geoip.dat", "geosite.dat")) {
            val dst = ctx.context.filesDir.resolve(name)
            runCatching {
                ctx.context.assets.open("xray/$name").use { input ->
                    if (!dst.exists()) dst.outputStream().use { input.copyTo(it) }
                }
            }
        }
    }
}

/** VLESS / VMess / Xray JSON: Xray-core (отдельный процесс) + tun2socks. */
class XrayEngine : VpnEngine {
    override suspend fun start(profile: Profile, ctx: EngineContext) {
        val json = when (profile) {
            is VlessProfile, is VmessProfile -> XrayConfigBuilder.build(profile, ctx.settings)
            is XrayProfile -> XrayConfigBuilder.withLocalInbound(profile.rawJson)
            else -> error("XrayEngine: неподдерживаемый профиль")
        }
        XrayCore.start(ctx, json)
        try {
            TunBridge.start(ctx, "127.0.0.1", XrayConfigBuilder.LOCAL_SOCKS_PORT)
        } catch (t: Throwable) {
            XrayCore.stop()
            throw t
        }
    }

    override fun traffic() = TunBridge.traffic()

    override suspend fun stop() {
        TunBridge.stop()
        XrayCore.stop()
    }
}

/** SOCKS5-прокси: весь трафик телефона через указанный прокси (TCP + UDP, если сервер умеет UDP ASSOCIATE). */
class Socks5Engine : VpnEngine {
    override suspend fun start(profile: Profile, ctx: EngineContext) {
        val p = profile as? Socks5Profile ?: error("Socks5Engine: неподдерживаемый профиль")
        TunBridge.start(ctx, p.host, p.port, p.username, p.password)
    }

    override fun traffic() = TunBridge.traffic()

    override suspend fun stop() = TunBridge.stop()
}

/**
 * AmneziaWG: библиотека amneziawg-android поднимает собственный VpnService (GoBackend),
 * поэтому establishTun здесь НЕ вызывается.
 */
class AwgEngine : VpnEngine {
    private var backend: AwgBackend? = null

    override suspend fun start(profile: Profile, ctx: EngineContext) {
        val p = profile as? AwgProfile ?: error("AwgEngine: неподдерживаемый профиль")
        val b = AwgBackend(ctx.context).also { backend = it }
        b.up(SplitTunnel.patchAwgConf(p.rawConf, ctx.settings))
    }

    override suspend fun stop() {
        runCatching { backend?.down() }
        backend = null
    }
}

fun engineFor(profile: Profile): VpnEngine = when (profile) {
    is VlessProfile, is VmessProfile, is XrayProfile -> XrayEngine()
    is Socks5Profile -> Socks5Engine()
    is AwgProfile -> AwgEngine()
}
