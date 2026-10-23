package app.glassvpn.vpn

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import app.glassvpn.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Плитка «hollow.vpn» в шторке: вкл/выкл одним тапом. */
class VpnTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        // Пока плитка видна в шторке, состояние тянем из общего VpnStatus.
        scope.launch { VpnStatus.state.collect { render(it) } }
    }

    override fun onStartListening() = render(VpnStatus.state.value)

    override fun onClick() {
        when (VpnStatus.state.value) {
            VpnState.Connected, VpnState.Connecting -> AppVpnService.stop(this)
            else -> {
                val prepare = VpnService.prepare(this)
                if (prepare == null) {
                    AppVpnService.start(this)
                } else {
                    // Разрешение VPN ещё не выдано: открываем приложение, оно доведёт подключение.
                    val open = Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(MainActivity.EXTRA_CONNECT, true)
                    if (Build.VERSION.SDK_INT >= 34) {
                        startActivityAndCollapse(
                            PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        startActivityAndCollapse(open)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(state: VpnState) {
        val tile = qsTile ?: return
        tile.state = if (state == VpnState.Connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when (state) {
            VpnState.Connected -> "Подключено"
            VpnState.Connecting -> "Подключение…"
            VpnState.Error -> "Ошибка"
            VpnState.Disconnected -> null
        }
        tile.updateTile()
    }
}
