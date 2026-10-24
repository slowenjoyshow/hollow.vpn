package app.glassvpn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glassvpn.model.AwgProfile
import app.glassvpn.model.PingResult
import app.glassvpn.model.Pinger
import app.glassvpn.model.Profile
import app.glassvpn.model.Socks5Profile
import app.glassvpn.model.VlessProfile
import app.glassvpn.model.VmessProfile
import app.glassvpn.model.XrayProfile
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.PingChip
import app.glassvpn.ui.fmtBytes
import app.glassvpn.vpn.VpnState
import app.glassvpn.vpn.VpnStatus
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel, onToggle: () -> Unit, onOpenProfiles: () -> Unit = {}) {
    val state by VpnStatus.state.collectAsState()
    val error by VpnStatus.error.collectAsState()
    val since by VpnStatus.connectedAt.collectAsState()
    val traffic by VpnStatus.traffic.collectAsState()
    val profile by vm.selected.collectAsState()
    val pings by vm.pings.collectAsState()
    val settings by vm.settings.collectAsState()
    val haptic = LocalHapticFeedback.current
    val cs = MaterialTheme.colorScheme

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(since) {
        while (since != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    // Скорость: раз в секунду считаем прирост суммарного трафика.
    val downHistory = remember { mutableStateListOf<Float>() }
    val upHistory = remember { mutableStateListOf<Float>() }
    var downSpeed by remember { mutableFloatStateOf(0f) }
    var upSpeed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        downHistory.clear()
        upHistory.clear()
        downSpeed = 0f
        upSpeed = 0f
        if (state == VpnState.Connected) {
            var last = VpnStatus.traffic.value
            while (true) {
                delay(1000)
                val cur = VpnStatus.traffic.value
                downSpeed = (cur.first - last.first).coerceAtLeast(0L).toFloat()
                upSpeed = (cur.second - last.second).coerceAtLeast(0L).toFloat()
                last = cur
                downHistory.add(downSpeed)
                upHistory.add(upSpeed)
                if (downHistory.size > 60) {
                    downHistory.removeAt(0)
                    upHistory.removeAt(0)
                }
            }
        }
    }

    // Реальная задержка через туннель (только для ядер на базе Xray).
    var tunnelPing by remember { mutableStateOf<PingResult?>(null) }
    LaunchedEffect(state, profile?.id) {
        tunnelPing = null
        val p = profile
        if (state == VpnState.Connected && (p is VlessProfile || p is VmessProfile || p is XrayProfile)) {
            delay(1200)
            while (true) {
                tunnelPing = PingResult.Testing
                tunnelPing = Pinger.throughProxy()
                delay(15_000)
            }
        }
    }

    val heroBg by animateColorAsState(
        when (state) {
            VpnState.Connected -> cs.primaryContainer
            VpnState.Connecting -> cs.secondaryContainer
            VpnState.Error -> cs.errorContainer
            VpnState.Disconnected -> cs.surfaceContainerHigh
        },
        tween(450), label = "heroBg",
    )
    val heroFg by animateColorAsState(
        when (state) {
            VpnState.Connected -> cs.onPrimaryContainer
            VpnState.Connecting -> cs.onSecondaryContainer
            VpnState.Error -> cs.onErrorContainer
            VpnState.Disconnected -> cs.onSurface
        },
        tween(450), label = "heroFg",
    )

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("hollow.vpn", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
                Text("Приватный туннель", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            }
            StatusPill(state)
        }

        // ---------- главная карточка ----------
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge,
            color = heroBg,
        ) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConnectButton(
                    state = state,
                    enabled = profile != null,
                    onClick = {
                        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    },
                )
                Spacer(Modifier.height(12.dp))
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
                            style = MaterialTheme.typography.headlineMedium,
                            color = heroFg,
                        )
                        Text(
                            when (s) {
                                VpnState.Disconnected -> if (profile == null) "Сначала добавь профиль" else "Нажми, чтобы включить защиту"
                                VpnState.Connecting -> "Устанавливаем защищённый канал"
                                VpnState.Connected -> "Трафик зашифрован"
                                VpnState.Error -> "Проверь конфигурацию профиля"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = heroFg.copy(alpha = 0.75f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                AnimatedVisibility(visible = state == VpnState.Connected && since != null) {
                    val sec = (((now - (since ?: now)) / 1000).coerceAtLeast(0))
                    Text(
                        String.format(Locale.US, "%02d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60),
                        modifier = Modifier.padding(top = 10.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        color = heroFg,
                    )
                }
                AnimatedVisibility(visible = error != null && state == VpnState.Error) {
                    Text(
                        error.orEmpty(),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = heroFg,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ---------- сервер ----------
        val current = profile
        Row(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainer)
                .clickable(onClick = onOpenProfiles)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(cs.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Shield, null, tint = cs.onSecondaryContainer, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Активный сервер", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Text(
                    current?.name ?: "Не выбран",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Text(
                        "${current.protocolLabel} · ${profileDetail(current)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (current != null) {
                PingChip(pings[current.id], onClick = { vm.pingProfile(current) })
                Spacer(Modifier.width(4.dp))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = cs.onSurfaceVariant)
        }

        AnimatedVisibility(visible = tunnelPing != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(cs.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Задержка через туннель", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    Text("Реальный запрос в интернет через сервер", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                PingChip(tunnelPing)
            }
        }

        // ---------- скорость ----------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SpeedCard("Загрузка", downSpeed, traffic.first, Icons.Filled.ArrowDownward, cs.primary, cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f))
            SpeedCard("Отдача", upSpeed, traffic.second, Icons.Filled.ArrowUpward, cs.tertiary, cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f))
        }

        AnimatedVisibility(visible = state == VpnState.Connected) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(cs.surfaceContainer)
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Скорость за минуту", style = MaterialTheme.typography.titleSmall, color = cs.onSurface, modifier = Modifier.weight(1f))
                    val peak = maxOf(downHistory.maxOrNull() ?: 0f, upHistory.maxOrNull() ?: 0f)
                    Text("пик ${fmtBytes(peak.toLong())}/с", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.height(10.dp))
                SpeedGraph(downHistory, upHistory, Modifier.fillMaxWidth().height(110.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Legend(cs.primary, "загрузка")
                    Legend(cs.tertiary, "отдача")
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: VpnState) {
    val cs = MaterialTheme.colorScheme
    val dot by animateColorAsState(
        when (state) {
            VpnState.Connected -> Color(0xFF4ADE80)
            VpnState.Connecting -> cs.primary
            VpnState.Error -> cs.error
            VpnState.Disconnected -> cs.outline
        },
        label = "dot",
    )
    Row(
        Modifier.clip(CircleShape).background(cs.surfaceContainerHighest).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            when (state) {
                VpnState.Connected -> "онлайн"
                VpnState.Connecting -> "соединяем"
                VpnState.Error -> "ошибка"
                VpnState.Disconnected -> "офлайн"
            },
            style = MaterialTheme.typography.labelLarge,
            color = cs.onSurface,
        )
    }
}

/** Большая кнопка: форма «перетекает» из круга в скруглённый квадрат, вокруг расходятся кольца. */
/** Расходящиеся кольца. Бесконечная анимация живёт только пока VPN подключается или работает, в покое кадры не тратятся. */
@Composable
private fun PulseRings(connecting: Boolean, ringColor: Color) {
    val infinite = rememberInfiniteTransition(label = "rings")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (connecting) 1400 else 3400, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(Modifier.fillMaxSize()) {
        val inner = size.minDimension * 0.33f
        val outer = size.minDimension * 0.5f
        for (i in 0 until 3) {
            val p = (phase + i / 3f) % 1f
            drawCircle(
                color = ringColor.copy(alpha = (1f - p) * (if (connecting) 0.45f else 0.30f)),
                radius = inner + (outer - inner) * p,
                center = center,
                style = Stroke(width = 3f),
            )
        }
    }
}

@Composable
private fun ConnectButton(state: VpnState, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val connected = state == VpnState.Connected
    val connecting = state == VpnState.Connecting

    val corner by animateIntAsState(
        when (state) {
            VpnState.Connected -> 30
            VpnState.Connecting -> 40
            VpnState.Error -> 36
            VpnState.Disconnected -> 50
        },
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "corner",
    )
    val container by animateColorAsState(
        when (state) {
            VpnState.Connected -> cs.primary
            VpnState.Connecting -> cs.primaryContainer
            VpnState.Error -> cs.errorContainer
            VpnState.Disconnected -> cs.surfaceContainerHighest
        },
        tween(350), label = "btnBg",
    )
    val content by animateColorAsState(
        when (state) {
            VpnState.Connected -> cs.onPrimary
            VpnState.Connecting -> cs.onPrimaryContainer
            VpnState.Error -> cs.onErrorContainer
            VpnState.Disconnected -> cs.primary
        },
        tween(350), label = "btnFg",
    )
    val scale by animateFloatAsState(
        if (connected) 1.04f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale",
    )
    val ringColor = cs.primary

    Box(Modifier.size(236.dp), contentAlignment = Alignment.Center) {
        if (connected || connecting) PulseRings(connecting, ringColor)
        Box(
            Modifier
                .size(152.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(corner))
                .background(container)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            if (connecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(114.dp),
                    color = content,
                    strokeWidth = 3.dp,
                    trackColor = content.copy(alpha = 0.15f),
                )
            }
            Icon(
                Icons.Filled.PowerSettingsNew,
                if (connected) "Отключить" else "Подключить",
                modifier = Modifier.size(64.dp),
                tint = content,
            )
        }
    }
}

@Composable
private fun SpeedCard(
    label: String,
    speed: Float,
    total: Long,
    icon: ImageVector,
    tint: Color,
    container: Color,
    onContainer: Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.clip(MaterialTheme.shapes.large).background(cs.surfaceContainer).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(container),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = onContainer, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${fmtBytes(speed.toLong())}/с",
            style = MaterialTheme.typography.titleLarge,
            color = tint,
            maxLines = 1,
        )
        Text("всего ${fmtBytes(total)}", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SpeedGraph(down: List<Float>, up: List<Float>, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val c1 = cs.primary
    val c2 = cs.tertiary
    val grid = cs.outlineVariant
    Canvas(modifier) {
        val maxV = maxOf(1f, down.maxOrNull() ?: 0f, up.maxOrNull() ?: 0f) * 1.15f
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(grid.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        drawSeries(down, maxV, c1)
        drawSeries(up, maxV, c2)
    }
}

private fun DrawScope.drawSeries(values: List<Float>, maxV: Float, color: Color) {
    if (values.size < 2) return
    val step = size.width / 59f
    val startX = size.width - step * (values.size - 1)
    val line = Path()
    val fill = Path()
    values.forEachIndexed { i, v ->
        val x = startX + step * i
        val y = size.height - (v / maxV) * size.height
        if (i == 0) {
            line.moveTo(x, y)
            fill.moveTo(x, size.height)
            fill.lineTo(x, y)
        } else {
            line.lineTo(x, y)
            fill.lineTo(x, y)
        }
    }
    fill.lineTo(size.width, size.height)
    fill.close()
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
    drawPath(line, color, style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun profileDetail(p: Profile): String = when (p) {
    is VlessProfile -> "${p.host}:${p.port} · ${p.network.uppercase()}"
    is VmessProfile -> "${p.host}:${p.port} · ${p.network.uppercase()}"
    is Socks5Profile -> "${p.host}:${p.port}"
    is XrayProfile -> p.endpoint.ifBlank { "Готовый JSON-конфиг Xray" }
    is AwgProfile -> "Конфиг AmneziaWG"
}
