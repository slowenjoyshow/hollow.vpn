package app.glassvpn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glassvpn.model.AwgProfile
import app.glassvpn.model.Profile
import app.glassvpn.model.Socks5Profile
import app.glassvpn.model.VlessProfile
import app.glassvpn.model.VmessProfile
import app.glassvpn.model.XrayProfile
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.theme.panel
import app.glassvpn.vpn.VpnState
import app.glassvpn.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit) {
    val state by VpnStatus.state.collectAsState()
    val error by VpnStatus.error.collectAsState()
    val since by VpnStatus.connectedAt.collectAsState()
    val traffic by VpnStatus.traffic.collectAsState()
    val profile by vm.selected.collectAsState()

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val cs = MaterialTheme.colorScheme
    val accent = when (state) {
        VpnState.Connected -> Color(0xFF4ADE80)
        VpnState.Connecting -> cs.primary
        VpnState.Error -> cs.error
        VpnState.Disconnected -> cs.onSurface.copy(alpha = 0.62f)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "hollow.vpn",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.8).sp,
                    color = cs.onSurface,
                )
                Text(
                    "Приватный туннель",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface.copy(alpha = 0.55f),
                )
            }
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
        }

        Spacer(Modifier.height(20.dp))

        Column(Modifier.fillMaxWidth().panel().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(46.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Shield, null, tint = cs.primary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Активный профиль", style = MaterialTheme.typography.labelMedium, color = cs.onSurface.copy(alpha = 0.6f))
                    Text(
                        profile?.name ?: "Не выбран",
                        style = MaterialTheme.typography.titleLarge,
                        color = cs.onSurface,
                        maxLines = 1,
                    )
                }
                profile?.let {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(cs.primary.copy(alpha = 0.14f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            it.protocolLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.primary,
                        )
                    }
                }
            }
            profile?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    profileDetail(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        PowerButton(
            state = state,
            accent = accent,
            enabled = profile != null,
            onClick = onToggle,
        )

        Spacer(Modifier.height(18.dp))

        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.92f)) togetherWith
                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.96f))
            },
            label = "status",
        ) { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (s) {
                        VpnState.Disconnected -> "Отключено"
                        VpnState.Connecting -> "Подключение…"
                        VpnState.Connected -> "Подключено"
                        VpnState.Error -> "Ошибка"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (s == VpnState.Disconnected) cs.onSurface else accent,
                )
                Text(
                    when (s) {
                        VpnState.Disconnected -> if (profile == null) "Сначала добавь профиль" else "Нажми, чтобы включить защиту"
                        VpnState.Connecting -> "Устанавливаем защищённый канал"
                        VpnState.Connected -> "Трафик зашифрован"
                        VpnState.Error -> "Проверь конфигурацию профиля"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedVisibility(visible = state == VpnState.Connected && since != null) {
            val s = (((now - (since ?: now)) / 1000).coerceAtLeast(0))
            Text(
                String.format(Locale.US, "%02d:%02d:%02d", s / 3600, s / 60 % 60, s % 60),
                modifier = Modifier.padding(top = 10.dp),
                fontFamily = FontFamily.Monospace,
                fontSize = 18.sp,
                letterSpacing = 1.5.sp,
                color = cs.onSurface.copy(alpha = 0.75f),
            )
        }

        AnimatedVisibility(visible = error != null && state == VpnState.Error) {
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cs.error.copy(alpha = 0.12f))
                    .padding(14.dp)
            ) {
                Text(
                    error.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.error,
                )
            }
        }

        Spacer(Modifier.height(26.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard("Загрузка", traffic.first, Icons.Filled.ArrowDownward, cs.primary, Modifier.weight(1f))
            StatCard("Отдача", traffic.second, Icons.Filled.ArrowUpward, cs.tertiary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PowerButton(state: VpnState, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    val connected = state == VpnState.Connected
    val connecting = state == VpnState.Connecting
    val transition = rememberInfiniteTransition(label = "power")
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    val glow by transition.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.62f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(if (connecting) 1300 else 7000, easing = LinearEasing)),
        label = "spin",
    )

    Box(Modifier.size(250.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = if (connected) glow else 0.14f), Color.Transparent),
                    center = c,
                    radius = r,
                ),
                radius = r,
                center = c,
            )

            rotate(spin) {
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.95f),
                            Color.Transparent,
                            accent.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        center = c,
                    ),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(c.x - r * 0.78f, c.y - r * 0.78f),
                    size = Size(r * 1.56f, r * 1.56f),
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round),
                )
            }

            drawCircle(
                color = accent.copy(alpha = 0.16f),
                radius = r * 0.78f,
                center = c,
                style = Stroke(width = 1.4f),
            )
        }
        Box(
            Modifier
                .size(168.dp)
                .graphicsLayer {
                    val s = if (connected) pulse else 1f
                    scaleX = s
                    scaleY = s
                }
                .panel(CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(122.dp),
                    color = accent,
                    strokeWidth = 3.dp,
                    trackColor = accent.copy(alpha = 0.15f),
                )
            }
            Icon(
                Icons.Filled.PowerSettingsNew,
                if (connected) "Отключить" else "Подключить",
                modifier = Modifier.size(72.dp),
                tint = accent,
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    bytes: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.panel(MaterialTheme.shapes.large).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurface.copy(alpha = 0.55f))
            Text(
                fmt(bytes),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
                maxLines = 1,
            )
        }
    }
}

private fun profileDetail(p: Profile): String = when (p) {
    is VlessProfile -> "${p.host}:${p.port} · ${p.network.uppercase()}"
    is VmessProfile -> "${p.host}:${p.port} · ${p.network.uppercase()}"
    is Socks5Profile -> "${p.host}:${p.port}"
    is XrayProfile -> p.endpoint.ifBlank { "Готовый JSON-конфиг Xray" }
    is AwgProfile -> "Конфиг AmneziaWG"
}

private fun fmt(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    else -> String.format(Locale.US, "%.2f GB", b / 1073741824.0)
}
