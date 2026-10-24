package app.glassvpn.vpn

import android.net.VpnService
import app.glassvpn.model.AppSettings
import app.glassvpn.model.SplitMode

/** Раздельное туннелирование по приложениям. Для TUN-движков и для AmneziaWG применяется по-разному. */
object SplitTunnel {
    const val EMPTY_ONLY_ERROR = "Раздельное туннелирование: выбери хотя бы одно приложение или отключи режим «Только выбранные»"

    fun validate(s: AppSettings) {
        if (s.splitMode == SplitMode.Only && s.splitApps.isEmpty()) error(EMPTY_ONLY_ERROR)
    }

    /** Xray / SOCKS5: правила для нашего собственного VpnService.Builder. */
    fun configure(b: VpnService.Builder, s: AppSettings, ownPackage: String) {
        when (s.splitMode) {
            SplitMode.Only ->   // сам клиент в список не попадает и ходит напрямую
                s.splitApps.filter { it != ownPackage }.forEach { runCatching { b.addAllowedApplication(it) } }
            SplitMode.Exclude -> {
                runCatching { b.addDisallowedApplication(ownPackage) }
                s.splitApps.filter { it != ownPackage }.forEach { runCatching { b.addDisallowedApplication(it) } }
            }
            SplitMode.Off -> runCatching { b.addDisallowedApplication(ownPackage) }
        }
    }

    /**
     * AmneziaWG: VpnService внутри библиотеки, поэтому правила пишутся в [Interface] конфига
     * (ExcludedApplications / IncludedApplications). Прежние строки с такими ключами убираем.
     */
    fun patchAwgConf(conf: String, s: AppSettings): String {
        if (s.splitMode == SplitMode.Off || s.splitApps.isEmpty()) return conf
        val key = if (s.splitMode == SplitMode.Only) "IncludedApplications" else "ExcludedApplications"
        val clean = conf.lines().filterNot { line ->
            val k = line.substringBefore('=').trim().lowercase()
            line.contains('=') && (k == "includedapplications" || k == "excludedapplications")
        }
        val out = ArrayList<String>(clean.size + 1)
        var inserted = false
        for (line in clean) {
            out += line
            if (!inserted && line.trim().equals("[Interface]", ignoreCase = true)) {
                out += "$key = ${s.splitApps.sorted().joinToString(", ")}"
                inserted = true
            }
        }
        if (!inserted) error("В конфиге AmneziaWG нет секции [Interface]")
        return out.joinToString("\n")
    }
}
