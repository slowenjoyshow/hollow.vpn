package app.glassvpn.model

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.Inflater

/**
 * Импорт из приложения AmneziaVPN: ключ `vpn://...` или файл `.vpn` (тот же ключ или чистый JSON).
 * Ключ = base64url( qCompress(JSON) ), где qCompress = 4 байта длины (big-endian) + zlib.
 * Поддерживаются контейнеры AmneziaWG/WireGuard (-> AwgProfile) и Xray (-> XrayProfile).
 * OpenVPN, Cloak, Shadowsocks Amnezia-ключами пока не импортируются.
 */
object AmneziaImporter {

    /** null = это не формат Amnezia, пусть разбирают остальные парсеры. */
    fun tryParse(input: String): Result<List<Profile>>? {
        val text = input.trim()
        val json = when {
            text.startsWith("vpn://", ignoreCase = true) ->
                decodeKey(text.substring(6)) ?: return Result.failure(
                    IllegalArgumentException("Не удалось расшифровать ключ vpn:// (ключ повреждён или обрезан)")
                )
            text.startsWith("{") && text.contains("\"containers\"") -> text
            else -> return null
        }
        return runCatching { fromJson(JSONObject(json)) }
    }

    private fun decodeKey(body: String): String? {
        val compact = body.filterNot(Char::isWhitespace)
        val bytes = listOf(Base64.URL_SAFE or Base64.NO_PADDING, Base64.DEFAULT)
            .firstNotNullOfOrNull { f -> runCatching { Base64.decode(compact, f) }.getOrNull() }
            ?: return null
        // qCompress: пропускаем 4 байта длины; если не вышло, пробуем без пропуска.
        return inflate(bytes, 4) ?: inflate(bytes, 0) ?: runCatching { String(bytes, Charsets.UTF_8) }
            .getOrNull()?.takeIf { it.trimStart().startsWith("{") }
    }

    private fun inflate(data: ByteArray, offset: Int): String? = runCatching {
        val inf = Inflater()
        inf.setInput(data, offset, data.size - offset)
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!inf.finished()) {
            val n = inf.inflate(buf)
            if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
            out.write(buf, 0, n)
        }
        inf.end()
        String(out.toByteArray(), Charsets.UTF_8).takeIf { it.trimStart().startsWith("{") }
    }.getOrNull()

    private fun fromJson(root: JSONObject): List<Profile> {
        val containers: JSONArray = root.optJSONArray("containers") ?: error("В ключе Amnezia нет контейнеров")
        val dns1 = root.optString("dns1").ifBlank { "1.1.1.1" }
        val dns2 = root.optString("dns2").ifBlank { "1.0.0.1" }
        val title = root.optString("description").ifBlank { root.optString("hostName") }.ifBlank { "Amnezia" }

        val result = mutableListOf<Profile>()
        val skipped = mutableListOf<String>()

        for (i in 0 until containers.length()) {
            val c = containers.optJSONObject(i) ?: continue
            val kind = c.optString("container")
            // Внутри контейнера есть объект с last_config (его ключ зависит от протокола: awg, xray, wireguard...).
            val inner = c.keys().asSequence().mapNotNull { k -> c.optJSONObject(k) }
                .firstOrNull { it.has("last_config") }
            val last = inner?.optString("last_config").orEmpty()
            if (last.isBlank()) { skipped += kind; continue }

            when {
                kind.contains("xray", true) -> {
                    val cfg = last.trim()
                    if (cfg.startsWith("{")) result += XrayProfile(newId(), "$title · Xray", cfg) else skipped += kind
                }
                kind.contains("awg", true) || kind.contains("wireguard", true) -> {
                    val conf = awgConf(JSONObject(last), dns1, dns2)
                    val label = if (kind.contains("awg", true)) "AmneziaWG" else "WireGuard"
                    result += AwgProfile(newId(), "$title · $label", conf)
                }
                else -> skipped += kind
            }
        }

        if (result.isEmpty()) {
            error(
                "В ключе Amnezia нет поддерживаемых протоколов" +
                    (if (skipped.isNotEmpty()) " (найдено: ${skipped.distinct().joinToString()}). " else ". ") +
                    "Поддерживаются AmneziaWG, WireGuard и Xray."
            )
        }
        return result
    }

    /** Берёт готовый wg-quick конфиг из last_config или собирает его из полей. */
    private fun awgConf(cfg: JSONObject, dns1: String, dns2: String): String {
        val ready = cfg.optString("config")
        if (ready.contains("[Interface]")) {
            return ready.replace("\$PRIMARY_DNS", dns1).replace("\$SECONDARY_DNS", dns2)
        }
        fun opt(key: String) = cfg.opt(key)?.toString()?.takeIf { it.isNotBlank() && it != "null" }
        val addr = opt("client_ip") ?: error("В конфиге AmneziaWG нет client_ip")
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = ${opt("client_priv_key") ?: error("В конфиге нет приватного ключа")}")
            appendLine("Address = $addr")
            appendLine("DNS = $dns1, $dns2")
            listOf("Jc", "Jmin", "Jmax", "S1", "S2", "S3", "S4", "H1", "H2", "H3", "H4").forEach { k ->
                opt(k)?.let { appendLine("$k = $it") }
            }
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = ${opt("server_pub_key") ?: error("В конфиге нет публичного ключа сервера")}")
            opt("psk_key")?.let { appendLine("PresharedKey = $it") }
            appendLine("AllowedIPs = 0.0.0.0/0, ::/0")
            appendLine("Endpoint = ${opt("hostName")}:${opt("port") ?: "51820"}")
            appendLine("PersistentKeepalive = 25")
        }
    }

    private fun newId() = UUID.randomUUID().toString()
}
