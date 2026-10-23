package app.glassvpn.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface Profile {
    val id: String
    val name: String
    val protocolLabel: String
}

@Serializable
@SerialName("vless")
data class VlessProfile(
    override val id: String,
    override val name: String,
    val host: String,
    val port: Int,
    val uuid: String,
    val flow: String = "",
    val network: String = "tcp",        // tcp | ws | grpc | httpupgrade | xhttp
    val security: String = "none",      // none | tls | reality
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",         // reality pbk
    val shortId: String = "",           // reality sid
    val spiderX: String = "",
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
    val alpn: String = "",
    val encryption: String = "none",     // VLESS Encryption (mlkem...) из параметра encryption
    val allowInsecure: Boolean = false,
    val xhttpMode: String = "",          // auto | packet-up | stream-up | stream-one
    val xhttpExtra: String = "",         // JSON из параметра extra
) : Profile {
    override val protocolLabel get() = if (security == "reality") "VLESS · Reality" else "VLESS"
}

/** VMess (в т.ч. из подписок, ссылка vmess://base64). */
@Serializable
@SerialName("vmess")
data class VmessProfile(
    override val id: String,
    override val name: String,
    val host: String,
    val port: Int,
    val uuid: String,
    val alterId: Int = 0,
    val cipher: String = "auto",
    val network: String = "tcp",        // tcp | ws | grpc | h2
    val tls: Boolean = false,
    val sni: String = "",
    val fingerprint: String = "chrome",
    val path: String = "",
    val hostHeader: String = "",
    val serviceName: String = "",
) : Profile {
    override val protocolLabel get() = if (tls) "VMess · TLS" else "VMess"
}

/** Произвольный JSON-конфиг Xray (если у тебя готовый конфиг из панели). */
@Serializable
@SerialName("xray")
data class XrayProfile(
    override val id: String,
    override val name: String,
    val rawJson: String,
    val kind: String = "",       // подпись протокола для списка: Trojan, Shadowsocks, VLESS...
    val endpoint: String = "",   // host:port для списка
) : Profile {
    override val protocolLabel get() = kind.ifBlank { "Xray" }
}

/** Конфиг AmneziaWG в формате wg-quick (+ Jc, Jmin, Jmax, S1, S2, H1..H4). */
@Serializable
@SerialName("awg")
data class AwgProfile(
    override val id: String,
    override val name: String,
    val rawConf: String,
) : Profile {
    override val protocolLabel get() = "AmneziaWG"
}

@Serializable
@SerialName("socks5")
data class Socks5Profile(
    override val id: String,
    override val name: String,
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
) : Profile {
    override val protocolLabel get() = "SOCKS5"
}
