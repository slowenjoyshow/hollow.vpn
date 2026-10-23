package app.glassvpn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.screens.HomeScreen
import app.glassvpn.ui.screens.ProfilesScreen
import app.glassvpn.ui.screens.SettingsScreen
import app.glassvpn.ui.theme.AppBackground
import app.glassvpn.ui.theme.HollowTheme
import app.glassvpn.ui.theme.LocalAppSettings
import app.glassvpn.ui.theme.panel
import app.glassvpn.vpn.AppVpnService
import app.glassvpn.vpn.VpnState
import app.glassvpn.vpn.VpnStatus

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    companion object {
        /** Открывающая активность должна сразу подключить VPN (например, плитка шторки). */
        const val EXTRA_CONNECT = "app.glassvpn.CONNECT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (settings.themeMode) {
                app.glassvpn.model.ThemeMode.System -> systemDark
                app.glassvpn.model.ThemeMode.Dark -> true
                app.glassvpn.model.ThemeMode.Light -> false
            }
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
            HollowTheme(settings) {
                var tab by remember { mutableIntStateOf(0) }
                val state by VpnStatus.state.collectAsState()

                val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (it.resultCode == Activity.RESULT_OK) AppVpnService.start(this)
                }
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // Открыто из плитки шторки: довести подключение до конца.
                    if (intent?.getBooleanExtra(EXTRA_CONNECT, false) == true &&
                        state == VpnState.Disconnected
                    ) {
                        val i: Intent? = VpnService.prepare(this@MainActivity)
                        if (i != null) vpnPermission.launch(i) else AppVpnService.start(this@MainActivity)
                    }
                }

                val onToggle: () -> Unit = {
                    if (state == VpnState.Connected || state == VpnState.Connecting) AppVpnService.stop(this)
                    else {
                        val i: Intent? = VpnService.prepare(this)
                        if (i != null) vpnPermission.launch(i) else AppVpnService.start(this)
                    }
                }

                CompositionLocalProvider(
                    LocalAppSettings provides settings,
                ) {
                    AppBackground {
                        Scaffold(
                            containerColor = Color.Transparent,
                            bottomBar = {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 24.dp, vertical = 12.dp)
                                ) {
                                    BottomNav(tab) { tab = it }
                                }
                            },
                        ) { pad ->
                            AnimatedContent(
                                targetState = tab,
                                modifier = Modifier.fillMaxSize().padding(pad),
                                transitionSpec = {
                                    val forward = targetState > initialState
                                    val slide = if (forward) 1 else -1
                                    (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { slide * it / 10 }) togetherWith
                                        (fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -slide * it / 10 })
                                },
                                label = "screen",
                            ) { screen ->
                                when (screen) {
                                    0 -> HomeScreen(vm, onToggle)
                                    1 -> ProfilesScreen(vm)
                                    else -> SettingsScreen(vm)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        Triple("Главная", Icons.Filled.Shield, 0),
        Triple("Профили", Icons.Filled.Layers, 1),
        Triple("Настройки", Icons.Filled.Tune, 2),
    )
    Row(
        Modifier.fillMaxWidth().panel(CircleShape).padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, icon, index) ->
            val active = index == selected
            val bg by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f) else Color.Transparent,
                label = "navBg",
            )
            val fg by animateColorAsState(
                if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f),
                label = "navFg",
            )
            Row(
                Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(index) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, label, tint = fg, modifier = Modifier.size(18.dp))
                AnimatedVisibility(
                    visible = active,
                    enter = fadeIn(tween(180)) + expandHorizontally(tween(180)),
                    exit = fadeOut(tween(120)) + shrinkHorizontally(tween(120)),
                ) {
                    Row {
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
