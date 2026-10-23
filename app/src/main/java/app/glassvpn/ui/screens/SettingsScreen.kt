package app.glassvpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.glassvpn.model.Accent
import app.glassvpn.model.BackgroundStyle
import app.glassvpn.model.SubUserAgent
import app.glassvpn.model.ThemeMode
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.theme.panel
import app.glassvpn.ui.theme.swatch
import java.util.Locale

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val cs = MaterialTheme.colorScheme

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 18.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
        Text(
            "Сделай интерфейс под себя — изменения применяются сразу",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface.copy(alpha = 0.55f),
        )

        SectionCard("Тема", "Светлая, тёмная или как в системе", Icons.Filled.Contrast) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                        label = {
                            Text(
                                when (mode) {
                                    ThemeMode.System -> "Авто"
                                    ThemeMode.Dark -> "Тёмная"
                                    ThemeMode.Light -> "Светлая"
                                }
                            )
                        },
                    )
                }
            }
        }

        SectionCard("Акцент", "Основной цвет интерфейса", Icons.Filled.Palette) {
            Accent.entries.chunked(5).forEach { rowItems ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rowItems.forEach { accent ->
                        AccentSwatch(
                            accent = accent,
                            selected = settings.accent == accent,
                            onClick = { vm.updateSettings { it.copy(accent = accent) } },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                accentName(settings.accent),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.65f),
            )
        }

        if (settings.accent == Accent.Custom) {
            SectionCard("Свой оттенок", "Material You из одного цвета", Icons.Filled.Palette) {
                SettingSlider(
                    label = "Оттенок",
                    valueLabel = "${settings.customHue.toInt()}°",
                    value = settings.customHue,
                    range = 0f..360f,
                ) { v -> vm.updateSettings { it.copy(customHue = v) } }
            }
        }

        SectionCard("Фон", "Однотонный или с мягким свечением", Icons.Filled.Palette) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BackgroundStyle.entries.forEach { st ->
                    FilterChip(
                        selected = settings.bgStyle == st,
                        onClick = { vm.updateSettings { it.copy(bgStyle = st) } },
                        label = { Text(if (st == BackgroundStyle.Solid) "Однотонный" else "Свечение") },
                    )
                }
            }
        }

        SectionCard("Оформление", "AMOLED и скругления", Icons.Filled.Contrast) {
            ToggleRow("Чёрный AMOLED", "Чистый чёрный фон в тёмной теме", settings.amoled) { on ->
                vm.updateSettings { it.copy(amoled = on) }
            }
            Spacer(Modifier.height(6.dp))
            SettingSlider(
                label = "Скругление углов",
                valueLabel = String.format(Locale.US, "%.1f×", settings.cornerScale),
                value = settings.cornerScale,
                range = 0.4f..1.6f,
            ) { v -> vm.updateSettings { it.copy(cornerScale = v) } }
        }

        SectionCard("Подписки", "Если сервер пишет «app not supported»", Icons.Filled.Link) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubUserAgent.entries.forEach { ua ->
                    FilterChip(
                        selected = settings.subUserAgent == ua,
                        onClick = { vm.updateSettings { it.copy(subUserAgent = ua) } },
                        label = { Text(ua.label) },
                    )
                }
            }
            if (settings.subUserAgent == SubUserAgent.Custom) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = settings.customUserAgent,
                    onValueChange = { v -> vm.updateSettings { it.copy(customUserAgent = v) } },
                    label = { Text("User-Agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "HWID устройства: ${vm.hwid}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Остальные клиенты пробуются автоматически, если выбранный отклонён.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.55f),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                "hollow.vpn 0.5.0",
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurface.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = 0.55f))
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SectionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .panel(MaterialTheme.shapes.large)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(19.dp), tint = cs.primary)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurface.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface.copy(alpha = 0.75f))
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = cs.primary,
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
        )
    }
}

@Composable
private fun AccentSwatch(accent: Accent, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = if (accent == Accent.Dynamic || accent == Accent.Custom) {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF6B6B),
                Color(0xFFFFD93D),
                Color(0xFF6BCB77),
                Color(0xFF4D96FF),
                Color(0xFF9B5DE5),
                Color(0xFFFF6B6B),
            )
        )
    } else {
        Brush.linearGradient(listOf(accent.swatch, accent.swatch))
    }
    val ring = if (selected) cs.onSurface else Color.Transparent

    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(brush)
            .border(2.5.dp, ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            Icon(
                Icons.Filled.Check,
                accentName(accent),
                Modifier.size(18.dp),
                tint = if (accent == Accent.Dynamic || accent == Accent.Custom) Color.White else Color(0xFF08101F),
            )
        }
    }
}

private fun accentName(accent: Accent): String = when (accent) {
    Accent.Dynamic -> "Динамический (Material You)"
    Accent.Ocean -> "Океан"
    Accent.Violet -> "Фиалка"
    Accent.Emerald -> "Изумруд"
    Accent.Sunset -> "Закат"
    Accent.Rose -> "Роза"
    Accent.Mono -> "Графит"
    Accent.Custom -> "Свой оттенок"
    Accent.Hollow -> "Hollow"
}
