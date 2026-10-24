package app.glassvpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import app.glassvpn.MainActivity
import app.glassvpn.model.Profile
import app.glassvpn.model.AppSettings
import app.glassvpn.model.ProfileStore
import app.glassvpn.model.SettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import java.util.Locale

enum class VpnState { Disconnected, Connecting, Connected, Error }

/** Общее состояние для UI (сервис и Activity живут в одном процессе). */
object VpnStatus {
    val state = MutableStateFlow(VpnState.Disconnected)
    val error = MutableStateFlow<String?>(null)
    val connectedAt = MutableStateFlow<Long?>(null)
    val traffic = MutableStateFlow(0L to 0L)
}

class AppVpnService : VpnService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: VpnEngine? = null
    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> disconnect()
            else -> connect()
        }
        return START_NOT_STICKY
    }

    private fun connect() {
        if (job?.isActive == true) return
        ensureChannel()
        startForeground(NOTIF_ID, notification(VpnState.Connecting))
        VpnStatus.state.value = VpnState.Connecting
        VpnStatus.error.value = null
        job = scope.launch {
            try {
                val store = ProfileStore(applicationContext)
                val id = store.selectedId.first()
                val profile: Profile = store.profiles.first().firstOrNull { it.id == id }
                    ?: error("Профиль не выбран")
                val settings = SettingsStore(applicationContext).settings.first()
                SplitTunnel.validate(settings)
                val e = engineFor(profile).also { engine = it }
                e.start(
                    profile,
                    EngineContext(this@AppVpnService, this@AppVpnService, ::protect, { establish(settings) }, settings),
                )
                VpnStatus.connectedAt.value = System.currentTimeMillis()
                VpnStatus.state.value = VpnState.Connected
                notificationManager.notify(NOTIF_ID, notification(VpnState.Connected))
                while (isActive) {          // опрос статистики + обновление уведомления
                    e.traffic()?.let {
                        VpnStatus.traffic.value = it
                        notificationManager.notify(NOTIF_ID, notification(VpnState.Connected))
                    }
                    delay(1000)
                }
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                VpnStatus.error.value = t.message ?: t.toString()
                VpnStatus.state.value = VpnState.Error
                teardown()
            }
        }
    }

    private fun establish(s: AppSettings): ParcelFileDescriptor? = Builder().apply {
        setSession("hollow.vpn")
        setMtu(s.tunMtu)
        addAddress("172.19.0.1", 30)
        addRoute("0.0.0.0", 0)
        if (s.ipv6) {
            addAddress("fdfe:dcba:9876::1", 126)
            addRoute("::", 0)
        }
        addDnsServer(s.remoteDns)
        addDnsServer("8.8.8.8")
        setBlocking(false)
        SplitTunnel.configure(this, s, packageName)   // сам клиент ходит напрямую + раздельное туннелирование
    }.establish()

    private fun disconnect() {
        job?.cancel()
        scope.launch { teardown(); VpnStatus.state.value = VpnState.Disconnected }
    }

    private suspend fun teardown() {
        runCatching { engine?.stop() }
        engine = null
        VpnStatus.connectedAt.value = null
        VpnStatus.traffic.value = 0L to 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() = disconnect()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val notificationManager get() = getSystemService(NotificationManager::class.java)

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Статус VPN", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Трафик, время работы и управление подключением"
                setShowBadge(false)
            }
        )
    }

    /** Живое уведомление: трафик + время работы (тикает через chronometer) + кнопка отключения. */
    private fun notification(state: VpnState): Notification {
        val (down, up) = VpnStatus.traffic.value
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(app.glassvpn.R.drawable.ic_notification)
            .setContentTitle("hollow.vpn")
            .setContentText(
                when (state) {
                    VpnState.Connected -> "↓ ${fmt(down)}    ↑ ${fmt(up)}"
                    VpnState.Connecting -> "Подключение…"
                    else -> ""
                }
            )
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, app.glassvpn.R.drawable.ic_power),
                    "Отключить",
                    PendingIntent.getService(
                        this, 1,
                        Intent(this, AppVpnService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).build()
            )
            .setOnlyAlertOnce(true)
            .setOngoing(true)
        if (state == VpnState.Connected) {
            VpnStatus.connectedAt.value?.let {
                builder.setWhen(it)
                builder.setUsesChronometer(true)   // время работы в поле времени
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun fmt(b: Long): String = when {
        b < 1024 -> "$b B"
        b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
        b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
        else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
    }

    companion object {
        const val ACTION_STOP = "app.glassvpn.STOP"
        private const val CHANNEL_ID = "vpn"
        private const val NOTIF_ID = 1
        fun start(c: Context) = c.startForegroundService(Intent(c, AppVpnService::class.java))
        fun stop(c: Context) = c.startService(Intent(c, AppVpnService::class.java).setAction(ACTION_STOP))
    }
}
