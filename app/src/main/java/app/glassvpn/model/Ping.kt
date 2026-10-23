package app.glassvpn.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.net.URL

/** Результат проверки сервера. null в карте результатов = ещё не проверяли. */
sealed interface PingResult {
    data object Testing : PingResult
    data class Ok(val ms: Int) : PingResult
    data object Failed : PingResult
    /** Проверить нельзя (например, AmneziaWG работает по UDP). */
    data class Unsupported(val label: String) : PingResult
}

object Pinger {
    data class Target(val host: String, val port: Int)

    fun targetOf(p: Profile): Target? = when (p) {
        is VlessProfile -> Target(p.host, p.port)
        is VmessProfile -> Target(p.host, p.port)
        is Socks5Profile -> Target(p.host, p.port)
        is XrayProfile -> {
            val ep = p.endpoint
            val port = ep.substringAfterLast(':', "").toIntOrNull()
            if (port != null) Target(ep.substringBeforeLast(':').trim('[', ']'), port) else null
        }
        is AwgProfile -> null
    }

    /**
     * TCP-пинг: время установки соединения с host:port сервера (DNS не входит в замер).
     * Приложение исключено из VPN, поэтому замер показывает прямой путь до сервера,
     * в том числе когда туннель включён.
     */
    suspend fun ping(p: Profile, timeoutMs: Int = 3000): PingResult {
        val t = targetOf(p) ?: return PingResult.Unsupported(if (p is AwgProfile) "UDP" else "—")
        return withContext(Dispatchers.IO) {
            runCatching {
                val addr = InetAddress.getByName(t.host)
                var best = Long.MAX_VALUE
                // Два замера, берём меньший: первый часто включает «холодный» маршрут.
                for (attempt in 0 until 2) {
                    val ms = runCatching {
                        Socket().use { s ->
                            val t0 = System.nanoTime()
                            s.connect(InetSocketAddress(addr, t.port), timeoutMs)
                            (System.nanoTime() - t0) / 1_000_000
                        }
                    }
                    if (ms.isSuccess) best = minOf(best, ms.getOrThrow())
                    else if (attempt == 0) error("connect failed")
                }
                PingResult.Ok(best.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()) as PingResult
            }.getOrElse { PingResult.Failed }
        }
    }

    /**
     * Реальная задержка через включённый туннель: HTTPS-запрос через локальный SOCKS Xray.
     * Первый запрос прогревает TLS и соединение, второй измеряется.
     */
    suspend fun throughProxy(): PingResult = withContext(Dispatchers.IO) {
        runCatching {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", XrayConfigBuilder.LOCAL_SOCKS_PORT))
            fun request(): Pair<Int, Int> {
                val c = (URL("https://www.gstatic.com/generate_204").openConnection(proxy) as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    instanceFollowRedirects = false
                    useCaches = false
                }
                val t0 = System.nanoTime()
                val code = c.responseCode
                val ms = ((System.nanoTime() - t0) / 1_000_000).toInt()
                runCatching { c.inputStream.close() }
                return code to ms
            }
            request()
            val (code, ms) = request()
            (if (code in 200..399) PingResult.Ok(ms) else PingResult.Failed) as PingResult
        }.getOrElse { PingResult.Failed }
    }
}
