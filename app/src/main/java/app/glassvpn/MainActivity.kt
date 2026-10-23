package app.glassvpn

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.glassvpn.model.ThemeMode
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.screens.HomeScreen
import app.glassvpn.ui.screens.ProfilesScreen
import app.glassvpn.ui.screens.SettingsScreen
import app.glassvpn.ui.theme.AppBackground
import app.glassvpn.ui.theme.HollowTheme
import app.glassvpn.ui.theme.LocalAppSettings
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
                ThemeMode.System -> systemDark
                ThemeMode.Dark -> true
                ThemeMode.Light -> false
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
                val haptic = LocalHapticFeedback.current
                val snackbar = remember { SnackbarHostState() }
                val notice by vm.notice.collectAsState()

                LaunchedEffect(notice) {
                    notice?.let {
                        snackbar.showSnackbar(it)
                        vm.clearNotice()
                    }
                }
                BackHandler(enabled = tab != 0) { tab = 0 }

                val vpnPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    if (it.resultCode == Activity.RESULT_OK) AppVpnService.start(this)
                }
                val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
                LaunchedEffect(Unit) {
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

                val items = listOf(
                    Triple("Главная", Icons.Filled.Shield, Icons.Outlined.Shield),
                    Triple("Серверы", Icons.Filled.Layers, Icons.Outlined.Layers),
                    Triple("Настройки", Icons.Filled.Tune, Icons.Outlined.Tune),
                )

                CompositionLocalProvider(LocalAppSettings provides settings) {
                    AppBackground {
                        Scaffold(
                            containerColor = Color.Transparent,
                            snackbarHost = { SnackbarHost(snackbar) },
                            bottomBar = {
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                                    items.forEachIndexed { index, (label, filled, outlined) ->
                                        NavigationBarItem(
                                            selected = tab == index,
                                            onClick = {
                                                if (tab != index && settings.haptics) {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                }
                                                tab = index
                                            },
                                            icon = { Icon(if (tab == index) filled else outlined, label) },
                                            label = { Text(label) },
                                        )
                                    }
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
                                    0 -> HomeScreen(vm, onToggle, onOpenProfiles = { tab = 1 })
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
