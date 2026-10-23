package app.glassvpn.vpn

import android.content.Context
import android.net.VpnService
import android.os.ParcelFileDescriptor
import app.glassvpn.model.AppSettings
import app.glassvpn.model.Profile

/** Что движку нужно от сервиса. */
class EngineContext(
    val context: Context,
    val service: VpnService,
    /** Защищает сокет ядра от маршрутизации обратно в туннель. */
    val protect: (Int) -> Boolean,
    /** Создаёт TUN-интерфейс. Не вызывай для AmneziaWG: у него свой VpnService внутри библиотеки. */
    val establishTun: () -> ParcelFileDescriptor?,
    val settings: AppSettings = AppSettings(),
)

interface VpnEngine {
    suspend fun start(profile: Profile, ctx: EngineContext)
    suspend fun stop()
    /** Суммарные байты (rx, tx) или null, если ядро не отдаёт статистику. */
    fun traffic(): Pair<Long, Long>? = null
}
