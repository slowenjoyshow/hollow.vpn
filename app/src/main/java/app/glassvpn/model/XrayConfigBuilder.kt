package app.glassvpn.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Собирает JSON для Xray-core. Xray поднимает только локальный SOCKS5-inbound,
 * а трафик из TUN в него заворачивает tun2socks (см. vpn/TunBridge).
 */
object XrayConfigBuilder {
    const val LOCAL_SOCKS_PORT = 10808

    private val PRIVATE_CIDRS = listOf(
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16",
        "fc00::/7", "fe80::/10",
    )

    fun build(p: Profile, s: AppSettings = AppSettings()): String = JSONObject().apply {
        put("log", JSONObject().put("loglevel", "warning"))
        put("dns", JSONObject()
            .put("servers", JSONArray().put(s.remoteDns))
            .put("queryStrategy", if (s.ipv6) "UseIP" else "UseIPv4"))
        put("routing", JSONObject().apply {
            put("domainStrategy", "AsIs")   // без лишних DNS-запросов при маршрутизации: быстрее
            put("rules", JSONArray().apply {
                // Без geoip.dat: локальные диапазоны задаём явно, чтобы ядро не падало без базы.
                if (s.bypassLan) put(JSONObject().put("type", "field")
                    .put("ip", JSONArray(PRIVATE_CIDRS)).put("outboundTag", "direct"))
            })
        })
        put("policy", JSONObject().put("levels", JSONObject().put("0", JSONObject()
            .put("handshake", 4).put("connIdle", 300).put("uplinkOnly", 1).put("downlinkOnly", 1))))
        put("inbounds", JSONArray().put(localInbound()))
        put("outbounds", JSONArray()
            .put(
                when (p) {
                    is VlessProfile -> vlessOutbound(p)
                    is VmessProfile -> vmessOutbound(p)
                    else -> error("XrayConfigBuilder: неподдерживаемый профиль")
                }
            )
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
    }.toString()

    /** Для готового JSON из панели: подменяем inbounds на наш локальный SOCKS. */
    fun withLocalInbound(raw: String): String {
        val o = JSONObject(raw)
        o.put("inbounds", JSONArray().put(localInbound()))
        return o.toString()
    }

    private fun localInbound() = JSONObject().apply {
        put("tag", "socks-in")
        put("listen", "127.0.0.1")
        put("port", LOCAL_SOCKS_PORT)
        put("protocol", "socks")
        put("settings", JSONObject().put("udp", true))
        put("sniffing", JSONObject().put("enabled", true)
            .put("destOverride", JSONArray().put("http").put("tls").put("quic")))
    }

    private fun vlessOutbound(p: VlessProfile) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "vless")
        put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
            put("address", p.host)
            put("port", p.port)
            put("users", JSONArray().put(JSONObject().apply {
                put("id", p.uuid)
                put("encryption", p.encryption.ifBlank { "none" })
                if (p.flow.isNotBlank()) put("flow", p.flow)
            }))
        })))
        put("streamSettings", JSONObject().apply {
            put("network", p.network)
            put("security", p.security)
            when (p.security) {
                "tls" -> put("tlsSettings", JSONObject().apply {
                    put("serverName", p.sni.ifBlank { p.host })
                    put("fingerprint", p.fingerprint)
                    if (p.alpn.isNotBlank()) put("alpn", JSONArray(p.alpn.split(',')))
                    if (p.allowInsecure) put("allowInsecure", true)
                })
                "reality" -> put("realitySettings", JSONObject().apply {
                    put("serverName", p.sni)
                    put("fingerprint", p.fingerprint)
                    put("publicKey", p.publicKey)
                    put("shortId", p.shortId)
                    put("spiderX", p.spiderX)
                })
            }
            when (p.network) {
                "ws" -> put("wsSettings", JSONObject().apply {
                    put("path", p.path.ifBlank { "/" })
                    if (p.hostHeader.isNotBlank()) put("headers", JSONObject().put("Host", p.hostHeader))
                })
                "grpc" -> put("grpcSettings", JSONObject().put("serviceName", p.serviceName))
                "httpupgrade" -> put("httpupgradeSettings", JSONObject()
                    .put("path", p.path.ifBlank { "/" }).put("host", p.hostHeader))
                "xhttp" -> put("xhttpSettings", JSONObject().apply {
                    put("path", p.path.ifBlank { "/" })
                    put("host", p.hostHeader)
                    if (p.xhttpMode.isNotBlank()) put("mode", p.xhttpMode)
                    runCatching { JSONObject(p.xhttpExtra) }.getOrNull()?.let { put("extra", it) }
                })
            }
        })
    }

    /** Готовый конфиг из одного outbound (для trojan/ss ссылок): inbound добавит withLocalInbound. */
    fun wrapOutbound(outbound: JSONObject): String = JSONObject().apply {
        put("log", JSONObject().put("loglevel", "warning"))
        put("outbounds", JSONArray()
            .put(outbound)
            .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
            .put(JSONObject().put("tag", "block").put("protocol", "blackhole")))
    }.toString()

    fun trojanOutbound(
        host: String, port: Int, password: String, network: String, security: String, sni: String,
        fingerprint: String, alpn: String, path: String, hostHeader: String, serviceName: String, insecure: Boolean,
    ) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "trojan")
        put("settings", JSONObject().put("servers", JSONArray().put(JSONObject()
            .put("address", host).put("port", port).put("password", password))))
        put("streamSettings", JSONObject().apply {
            put("network", network)
            put("security", security)
            if (security == "tls") put("tlsSettings", JSONObject().apply {
                put("serverName", sni.ifBlank { host })
                put("fingerprint", fingerprint)
                if (alpn.isNotBlank()) put("alpn", JSONArray(alpn.split(',')))
                if (insecure) put("allowInsecure", true)
            })
            when (network) {
                "ws" -> put("wsSettings", JSONObject().apply {
                    put("path", path.ifBlank { "/" })
                    if (hostHeader.isNotBlank()) put("headers", JSONObject().put("Host", hostHeader))
                })
                "grpc" -> put("grpcSettings", JSONObject().put("serviceName", serviceName))
                "httpupgrade" -> put("httpupgradeSettings", JSONObject().put("path", path.ifBlank { "/" }).put("host", hostHeader))
            }
        })
    }

    fun shadowsocksOutbound(host: String, port: Int, method: String, password: String) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "shadowsocks")
        put("settings", JSONObject().put("servers", JSONArray().put(JSONObject()
            .put("address", host).put("port", port).put("method", method).put("password", password))))
    }

    private fun vmessOutbound(p: VmessProfile) = JSONObject().apply {
        put("tag", "proxy")
        put("protocol", "vmess")
        put("settings", JSONObject().put("vnext", JSONArray().put(JSONObject().apply {
            put("address", p.host)
            put("port", p.port)
            put("users", JSONArray().put(JSONObject().apply {
                put("id", p.uuid)
                put("alterId", p.alterId)
                put("security", p.cipher.ifBlank { "auto" })
            }))
        })))
        put("streamSettings", JSONObject().apply {
            put("network", p.network)
            put("security", if (p.tls) "tls" else "none")
            if (p.tls) put("tlsSettings", JSONObject().apply {
                put("serverName", p.sni.ifBlank { p.host })
                put("fingerprint", p.fingerprint)
            })
            when (p.network) {
                "ws" -> put("wsSettings", JSONObject().apply {
                    put("path", p.path.ifBlank { "/" })
                    if (p.hostHeader.isNotBlank()) put("headers", JSONObject().put("Host", p.hostHeader))
                })
                "grpc" -> put("grpcSettings", JSONObject().put("serviceName", p.serviceName))
                "h2" -> put("httpSettings", JSONObject()
                    .put("path", p.path.ifBlank { "/" })
                    .put("host", JSONArray().put(p.hostHeader.ifBlank { p.host })))
            }
        })
    }
}
