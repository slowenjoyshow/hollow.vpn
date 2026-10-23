package app.glassvpn.model

import android.os.Build
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/** Какой User-Agent отправлять панели подписки. Многие панели отвечают «app not supported» на незнакомые клиенты. */
enum class SubUserAgent(val label: String, val value: String) {
    V2rayNG("v2rayNG", "v2rayNG/1.10.16"),
    Happ("Happ", "Happ/2.7.0"),
    V2rayTun("v2RayTun", "v2raytun/ios"),
    Custom("Свой", ""),
}

/** Сохранённая подписка: по ссылке её можно обновлять. */
@Serializable
data class Subscription(
    val id: String,
    val url: String,
    val name: String,
    val profileIds: List<String> = emptyList(),
    val updatedAt: Long = 0L,
    val intervalHours: Int = 12,
    val announce: String = "",
)

data class FetchResult(
    val body: String,
    val title: String?,
    val intervalHours: Int?,
    val announce: String?,
)

/** Загрузка подписок по https:// без лишних зависимостей. */
object SubscriptionFetcher {

    /**
     * Пробует User-Agent по очереди (сначала выбранный пользователем), пока ответ не пройдёт [validate].
     * Так «app not supported» обходится автоматически. Заглушки панели («Happ включит HWID…») отсеиваются.
     */
    suspend fun fetch(
        url: String,
        hwid: String,
        preferred: SubUserAgent = SubUserAgent.V2rayNG,
        customUa: String = "",
        validate: (String) -> Result<*> = { Result.success(Unit) },
    ): Result<FetchResult> = withContext(Dispatchers.IO) {
        runCatching {
            require(url.startsWith("https://", ignoreCase = true)) {
                "Подписка должна быть по https:// (http-ссылки отключены из соображений безопасности)"
            }
            val agents = buildList {
                if (preferred == SubUserAgent.Custom && customUa.isNotBlank()) add(customUa.trim())
                else if (preferred != SubUserAgent.Custom) add(preferred.value)
                SubUserAgent.entries.filter { it != SubUserAgent.Custom }.forEach { add(it.value) }
            }.distinct()

            var lastError: Throwable? = null
            for (ua in agents) {
                val attempt = runCatching { request(url, ua, hwid) }
                val res = attempt.getOrNull()
                if (res != null) {
                    val check = validate(res.body)
                    if (check.isSuccess) return@runCatching res
                    lastError = check.exceptionOrNull()
                } else {
                    lastError = attempt.exceptionOrNull()
                }
            }
            error(lastError?.message ?: "Сервер отклонил запрос")
        }
    }

    private fun request(url: String, ua: String, hwid: String): FetchResult {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 25_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            // Remnawave/Marzban-панели с лимитом устройств ждут эти заголовки.
            setRequestProperty("x-hwid", hwid)
            setRequestProperty("x-device-os", "Android")
            setRequestProperty("x-ver-os", Build.VERSION.RELEASE ?: "")
            setRequestProperty("x-device-model", Build.MODEL ?: "")
        }
        try {
            val code = c.responseCode
            if (code !in 200..399) error("HTTP $code")
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return FetchResult(
                body = body,
                title = c.getHeaderField("profile-title")?.let(::decodeHeader),
                intervalHours = c.getHeaderField("profile-update-interval")?.trim()?.toIntOrNull(),
                announce = c.getHeaderField("announce")?.let(::decodeHeader),
            )
        } finally {
            c.disconnect()
        }
    }

    /** Заголовки панелей бывают вида `base64:...`. */
    private fun decodeHeader(v: String): String {
        val t = v.trim()
        return if (t.startsWith("base64:", ignoreCase = true)) {
            runCatching { String(Base64.decode(t.substring(7), Base64.DEFAULT), Charsets.UTF_8) }.getOrDefault(t)
        } else t
    }
}

/** Профиль-заглушка: панель подсовывает его вместо серверов, когда что-то не так (HWID, лимит, истёк срок). */
fun Profile.isPlaceholder(): Boolean = when (this) {
    is VlessProfile -> host == "0.0.0.0" || host == "127.0.0.1" || port <= 1
    is VmessProfile -> host == "0.0.0.0" || host == "127.0.0.1" || port <= 1
    is Socks5Profile -> host == "0.0.0.0" || port <= 1
    else -> false
}

/** Разбор подписки без заглушек. Если остались только заглушки, ошибка содержит их текст (это сообщение панели). */
fun parseSubscriptionReport(text: String): Result<LinkParser.ParseReport> = runCatching {
    val report = LinkParser.parseManyReport(text).getOrThrow()
    val real = report.profiles.filterNot { it.isPlaceholder() }
    if (real.isEmpty()) {
        error("Панель ответила сообщением вместо серверов: " + report.profiles.joinToString(" / ") { it.name }.take(240))
    }
    report.copy(profiles = real)
}

fun parseSubscription(text: String): Result<List<Profile>> = parseSubscriptionReport(text).map { it.profiles }
