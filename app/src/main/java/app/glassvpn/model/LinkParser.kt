package app.glassvpn.model

import android.net.Uri
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.UUID

/** Разбор того, что пользователь вставил: ссылки, подписка (base64/plain), JSON Xray, конфиг AmneziaWG. */
object LinkParser {

    fun parse(input: String): Result<Profile> = runCatching {
        val text = input.trim()
        AmneziaImporter.tryParse(text)?.let { return@runCatching it.getOrThrow().first() }
        when {
            text.startsWith("vless://", true) -> parseVless(text)
            text.startsWith("vmess://", true) -> parseVmess(text)
            text.startsWith("socks5://", true) || text.startsWith("socks://", true) -> parseSocks(text)
            text.startsWith("trojan://", true) -> parseTrojan(text)
            text.startsWith("ss://", true) -> parseSs(text)
            text.startsWith("{") -> JSONObject(text).let { o ->
                val (kind, endpoint) = describeXray(o)
                XrayProfile(newId(), o.optString("remarks").ifBlank { "Xray config" }, text, kind, endpoint)
            }
            text.contains("[Interface]") && text.contains("[Peer]") -> parseAwg(text)
            else -> error("Неизвестный формат. Поддерживаются vless://, vmess://, trojan://, ss://, socks5://, JSON Xray, AmneziaWG, ключи AmneziaVPN (vpn://) и подписки")
        }
    }

    /** Результат разбора: профили и протоколы, которые мы пропустили, потому что ядра для них нет. */
    data class ParseReport(val profiles: List<Profile>, val skipped: Map<String, Int> = emptyMap()) {
        val note: String
            get() = if (skipped.isEmpty()) "" else
                "Пропущено, нет ядра: " + skipped.entries.joinToString { "${it.key} ×${it.value}" }
    }

    private val UNSUPPORTED_SCHEMES = setOf(
        "hysteria2", "hy2", "hysteria", "tuic", "wireguard", "wg", "anytls", "juicity", "ssh", "mieru", "naive+https",
    )

    fun parseMany(input: String): Result<List<Profile>> = parseManyReport(input).map { it.profiles }

    /**
     * Парсит сразу несколько профилей: подписку в base64, обычный список ссылок,
     * JSON-массив конфигов Xray (формат Remnawave), несколько конфигов в одном файле и т.п.
     */
    fun parseManyReport(input: String): Result<ParseReport> = runCatching {
        val raw = input.trim()
        require(raw.isNotEmpty()) { "Ссылка или файл пустые" }

        // Ключ или файл AmneziaVPN (vpn://... / .vpn)
        AmneziaImporter.tryParse(raw)?.let { return@runCatching ParseReport(it.getOrThrow()) }

        val expanded = decodeSubscriptionIfNeeded(raw)
        if (expanded.trimStart().startsWith("[")) {
            val list = parseJsonArray(expanded)
            require(list.isNotEmpty()) { "В JSON-подписке нет конфигов" }
            return@runCatching ParseReport(list)
        }
        val chunks = when {
            expanded.trimStart().startsWith("{") -> listOf(expanded)
            expanded.contains("[Interface]") && expanded.contains("[Peer]") -> listOf(expanded)
            else -> expanded.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("//") }
                .toList()
        }
        require(chunks.isNotEmpty()) { "Не найдено ни одного профиля" }

        val profiles = mutableListOf<Profile>()
        val errors = mutableListOf<String>()
        val skipped = linkedMapOf<String, Int>()
        chunks.forEach { chunk ->
            parse(chunk)
                .onSuccess { profiles += it }
                .onFailure { e ->
                    val scheme = chunk.substringBefore("://", "").lowercase()
                    if (scheme in UNSUPPORTED_SCHEMES) skipped.merge(scheme, 1, Int::plus)
                    else errors += (e.message ?: "неизвестная ошибка")
                }
        }

        if (profiles.isEmpty()) {
            if (skipped.isNotEmpty()) error("Есть только неподдерживаемые протоколы: " + ParseReport(emptyList(), skipped).note.removePrefix("Пропущено, нет ядра: "))
            error(errors.firstOrNull() ?: "Не удалось распознать профили")
        }
        ParseReport(profiles, skipped)
    }

    private fun parseJsonArray(text: String): List<Profile> {
        val arr = JSONArray(text)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val (kind, endpoint) = describeXray(o)
            XrayProfile(newId(), o.optString("remarks").ifBlank { "Xray ${i + 1}" }, o.toString(), kind, endpoint)
        }
    }

    /** Подпись протокола и host:port из готового JSON, чтобы список выглядел как у обычных ссылок. */
    private fun describeXray(o: JSONObject): Pair<String, String> = runCatching {
        val outs = o.optJSONArray("outbounds") ?: return@runCatching "" to ""
        val ob = (0 until outs.length()).mapNotNull { outs.optJSONObject(it) }
            .firstOrNull { it.optString("protocol") !in setOf("freedom", "blackhole", "dns", "loopback") }
            ?: return@runCatching "" to ""
        val protocol = ob.optString("protocol")
        val reality = ob.optJSONObject("streamSettings")?.optString("security") == "reality"
        val settings = ob.optJSONObject("settings")
        val first = settings?.optJSONArray("vnext")?.optJSONObject(0) ?: settings?.optJSONArray("servers")?.optJSONObject(0)
        val endpoint = first?.let { "${it.optString("address")}:${it.optInt("port")}" }.orEmpty()
        val label = when (protocol.lowercase()) {
            "vless" -> if (reality) "VLESS · Reality" else "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "shadowsocks" -> "Shadowsocks"
            else -> protocol.uppercase()
        }
        label to endpoint
    }.getOrDefault("" to "")

    private fun parseTrojan(link: String): XrayProfile {
        val uri = Uri.parse(link)
        val q = { k: String -> uri.getQueryParameter(k).orEmpty() }
        val host = uri.host ?: error("В trojan-ссылке нет хоста")
        val port = uri.port.takeIf { it > 0 } ?: 443
        val password = uri.userInfo ?: error("В trojan-ссылке нет пароля")
        val outbound = XrayConfigBuilder.trojanOutbound(
            host = host, port = port, password = password,
            network = q("type").ifBlank { "tcp" },
            security = q("security").ifBlank { "tls" },
            sni = q("sni").ifBlank { q("peer") },
            fingerprint = q("fp").ifBlank { "chrome" },
            alpn = q("alpn"), path = q("path"), hostHeader = q("host"), serviceName = q("serviceName"),
            insecure = q("allowInsecure") == "1" || q("insecure") == "1",
        )
        return XrayProfile(
            newId(), uri.fragment?.takeIf { it.isNotBlank() } ?: host,
            XrayConfigBuilder.wrapOutbound(outbound), "Trojan", "$host:$port",
        )
    }

    /** ss://BASE64(method:pass)@host:port#name (SIP002) или старый ss://BASE64(method:pass@host:port)#name. */
    private fun parseSs(link: String): XrayProfile {
        val name = link.substringAfter('#', "").let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
        val body = link.substring(5).substringBefore('#').substringBefore('?').trimEnd('/')   // плагины не поддерживаются
        val method: String
        val password: String
        val hostPort: String
        if (body.contains('@')) {
            val userPart = body.substringBeforeLast('@')
            val decoded = if (userPart.contains(':')) Uri.decode(userPart)
            else decodeBase64(userPart) ?: error("Не удалось декодировать ss-ссылку")
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':')
            hostPort = body.substringAfterLast('@')
        } else {
            val decoded = decodeBase64(body) ?: error("Не удалось декодировать ss-ссылку")
            method = decoded.substringBefore(':')
            password = decoded.substringAfter(':').substringBeforeLast('@')
            hostPort = decoded.substringAfterLast('@')
        }
        val host = hostPort.substringBeforeLast(':').trim('[', ']')
        val port = hostPort.substringAfterLast(':').toIntOrNull() ?: error("В ss-ссылке нет порта")
        require(host.isNotBlank() && method.isNotBlank()) { "Некорректная ss-ссылка" }
        return XrayProfile(
            newId(), name.ifBlank { host },
            XrayConfigBuilder.wrapOutbound(XrayConfigBuilder.shadowsocksOutbound(host, port, method, password)),
            "Shadowsocks", "$host:$port",
        )
    }

    private fun decodeSubscriptionIfNeeded(text: String): String {
        val looksLikeConfig = text.contains("://") ||
            text.contains("[Interface]") ||
            text.trimStart().startsWith("{")
        if (looksLikeConfig) return text

        val compact = text.filterNot(Char::isWhitespace)
        if (compact.length < 16) return text
        val decoded = decodeBase64(compact) ?: return text
        val ok = decoded.contains("://") ||
            decoded.contains("[Interface]") ||
            decoded.trimStart().startsWith("{")
        return if (ok) decoded else text
    }

    private fun parseVless(link: String): VlessProfile {
        val uri = Uri.parse(link)
        val q = { k: String -> uri.getQueryParameter(k).orEmpty() }
        val host = uri.host ?: error("В ссылке нет хоста")
        return VlessProfile(
            id = newId(),
            name = uri.fragment?.takeIf { it.isNotBlank() } ?: host,
            host = host,
            port = uri.port.takeIf { it > 0 } ?: 443,
            uuid = uri.userInfo ?: error("В ссылке нет UUID"),
            flow = q("flow"),
            network = q("type").ifBlank { "tcp" },
            security = q("security").ifBlank { "none" },
            sni = q("sni"),
            fingerprint = q("fp").ifBlank { "chrome" },
            publicKey = q("pbk"),
            shortId = q("sid"),
            spiderX = q("spx"),
            path = q("path"),
            hostHeader = q("host"),
            serviceName = q("serviceName"),
            alpn = q("alpn"),
            encryption = q("encryption").ifBlank { "none" },
            allowInsecure = q("allowInsecure") == "1" || q("insecure") == "1",
            xhttpMode = q("mode"),
            xhttpExtra = q("extra"),
        )
    }

    private fun parseVmess(link: String): VmessProfile {
        val payload = link.removePrefix("vmess://").substringBefore('#').trim()
        val json = decodeBase64(payload) ?: error("Не удалось декодировать vmess-ссылку")
        val o = runCatching { JSONObject(json) }.getOrElse { error("Некорректный vmess JSON") }

        val host = o.optString("add").ifBlank { error("В vmess-ссылке нет хоста") }
        val uuid = o.optString("id").ifBlank { error("В vmess-ссылке нет UUID") }
        val tlsValue = o.optString("tls")
        return VmessProfile(
            id = newId(),
            name = o.optString("ps").ifBlank { host },
            host = host,
            port = o.optString("port").toIntOrNull()?.takeIf { it > 0 } ?: 443,
            uuid = uuid,
            alterId = o.optString("aid").toIntOrNull() ?: 0,
            cipher = o.optString("scy").ifBlank { "auto" },
            network = o.optString("net").ifBlank { "tcp" },
            tls = tlsValue.equals("tls", true) || tlsValue.equals("reality", true),
            sni = o.optString("sni"),
            fingerprint = o.optString("fp").ifBlank { "chrome" },
            path = o.optString("path"),
            hostHeader = o.optString("host"),
            serviceName = o.optString("path").takeIf { o.optString("net") == "grpc" }.orEmpty(),
        )
    }

    private fun parseSocks(link: String): Socks5Profile {
        val uri = Uri.parse(link.replaceFirst("socks://", "socks5://", true))
        val host = uri.host ?: error("В ссылке нет хоста")
        val (user, pass) = (uri.userInfo ?: "").let {
            val i = it.indexOf(':')
            if (i < 0) it to "" else it.substring(0, i) to it.substring(i + 1)
        }
        return Socks5Profile(
            id = newId(),
            name = uri.fragment?.takeIf { it.isNotBlank() } ?: host,
            host = host,
            port = uri.port.takeIf { it > 0 } ?: 1080,
            username = user,
            password = pass,
        )
    }

    private fun parseAwg(conf: String): AwgProfile {
        val endpoint = Regex("(?im)^\\s*Endpoint\\s*=\\s*(\\S+)").find(conf)?.groupValues?.get(1)
        return AwgProfile(newId(), endpoint?.substringBeforeLast(':') ?: "AmneziaWG", conf)
    }

    private val base64Flags = intArrayOf(
        Base64.DEFAULT,
        Base64.NO_PADDING,
        Base64.URL_SAFE,
        Base64.URL_SAFE or Base64.NO_PADDING,
    )

    private fun decodeBase64(input: String): String? {
        val compact = input.filterNot(Char::isWhitespace)
        for (flag in base64Flags) {
            val bytes = runCatching { Base64.decode(compact, flag) }.getOrNull() ?: continue
            return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
        }
        return null
    }

    private fun newId() = UUID.randomUUID().toString()
}
