package app.glassvpn.ui.screens

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.glassvpn.model.Accent
import app.glassvpn.model.BackgroundStyle
import app.glassvpn.model.SplitMode
import app.glassvpn.model.SubUserAgent
import app.glassvpn.model.ThemeMode
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.theme.hueSeed
import app.glassvpn.ui.theme.swatch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val settings by vm.settings.collectAsState()
    val cs = MaterialTheme.colorScheme
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicOn = settings.accent == Accent.Dynamic && dynamicAvailable

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
            Text(
                "Всё применяется сразу",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }

        // ---------- превью палитры ----------
        PalettePreview()

        Section("Тема") {
            val modes = ThemeMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.themeMode == mode,
                        onClick = { vm.updateSettings { it.copy(themeMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.System -> "Авто"
                                ThemeMode.Dark -> "Тёмная"
                                ThemeMode.Light -> "Светлая"
                            }
                        )
                    }
                }
            }
        }

        Section("Цвета") {
            if (dynamicAvailable) {
                SwitchRow(
                    title = "Цвета из обоев",
                    subtitle = "Material You: палитра подстраивается под обои телефона",
                    checked = dynamicOn,
                ) { on -> vm.updateSettings { it.copy(accent = if (on) Accent.Dynamic else Accent.Hollow) } }
            }
            AnimatedVisibility(visible = !dynamicOn) {
                Column {
                    if (dynamicAvailable) Spacer(Modifier.height(14.dp))
                    Text("Палитра", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    val choices = Accent.entries.filter { it != Accent.Dynamic }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        choices.take(4).forEach { a ->
                            AccentSwatch(a, settings.accent == a, settings.customHue) { vm.updateSettings { s -> s.copy(accent = a) } }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        choices.drop(4).forEach { a ->
                            AccentSwatch(a, settings.accent == a, settings.customHue) { vm.updateSettings { s -> s.copy(accent = a) } }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(accentName(settings.accent), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            AnimatedVisibility(visible = !dynamicOn && settings.accent == Accent.Custom) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    SettingSlider(
                        label = "Оттенок",
                        valueLabel = "${settings.customHue.toInt()}°",
                        value = settings.customHue,
                        range = 0f..360f,
                    ) { v -> vm.updateSettings { it.copy(customHue = v) } }
                }
            }
        }

        Section("Оформление") {
            SwitchRow("Чёрный AMOLED", "Чистый чёрный фон в тёмной теме", settings.amoled) { on ->
                vm.updateSettings { it.copy(amoled = on) }
            }
            Spacer(Modifier.height(14.dp))
            Text("Фон", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val styles = BackgroundStyle.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                styles.forEachIndexed { index, st ->
                    SegmentedButton(
                        selected = settings.bgStyle == st,
                        onClick = { vm.updateSettings { it.copy(bgStyle = st) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = styles.size),
                    ) { Text(if (st == BackgroundStyle.Solid) "Однотонный" else "Свечение") }
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingSlider(
                label = "Скругление углов",
                valueLabel = String.format(Locale.US, "%.1f×", settings.cornerScale),
                value = settings.cornerScale,
                range = 0.4f..1.6f,
            ) { v -> vm.updateSettings { it.copy(cornerScale = v) } }
            SettingSlider(
                label = "Размер текста",
                valueLabel = String.format(Locale.US, "%d%%", (settings.textScale * 100).toInt()),
                value = settings.textScale,
                range = 0.85f..1.3f,
            ) { v -> vm.updateSettings { it.copy(textScale = v) } }
            Spacer(Modifier.height(4.dp))
            SwitchRow("Вибрация", "Отклик при подключении и переключении вкладок", settings.haptics) { on ->
                vm.updateSettings { it.copy(haptics = on) }
            }
        }

        Section("Раздельное туннелирование") {
            var pickApps by remember { mutableStateOf(false) }
            val modes = SplitMode.entries
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = settings.splitMode == mode,
                        onClick = { vm.updateSettings { it.copy(splitMode = mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size),
                    ) {
                        Text(
                            when (mode) {
                                SplitMode.Off -> "Выкл"
                                SplitMode.Exclude -> "Обход"
                                SplitMode.Only -> "Только"
                            },
                            maxLines = 1,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when (settings.splitMode) {
                    SplitMode.Off -> "Весь трафик телефона идёт через VPN."
                    SplitMode.Exclude -> "Выбранные приложения ходят напрямую, мимо VPN. Остальные работают через него."
                    SplitMode.Only -> "Через VPN ходят только выбранные приложения. Остальные работают напрямую."
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (settings.splitMode != SplitMode.Off) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { pickApps = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (settings.splitApps.isEmpty()) "Выбрать приложения" else "Приложения: ${settings.splitApps.size}")
                }
                if (settings.splitMode == SplitMode.Only && settings.splitApps.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выбери хотя бы одно приложение, иначе подключиться не получится.",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Изменения применяются при следующем подключении.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            if (pickApps) SplitTunnelSheet(vm) { pickApps = false }
        }

        Section("Серверы") {
            SwitchRow(
                "Проверять пинг при открытии",
                "TCP-пинг до каждого сервера, чтобы сразу видеть лучший",
                settings.autoPing,
            ) { on -> vm.updateSettings { it.copy(autoPing = on) } }
            Spacer(Modifier.height(10.dp))
            Text(
                "Пинг показывает время установки соединения с сервером. Для AmneziaWG (UDP) он недоступен. " +
                    "При включённом VPN в карточке на главной дополнительно измеряется реальная задержка через туннель.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        Section("Подписки") {
            Text(
                "По умолчанию приложение представляется панели как hollow.vpn/${vm.appVersion}. Если сервер пишет «app not supported», выбери Happ, v2rayNG или v2RayTun, остальные пробуются автоматически.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),   // 5 чипов не помещаются в ширину экрана
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
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
            Spacer(Modifier.height(10.dp))
            Text(
                "HWID устройства: ${vm.hwid}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface,
            )
        }

        Text(
            "hollow.vpn 0.6.0",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Полоска с основными цветами текущей схемы: сразу видно, что получилось. */
@Composable
private fun PalettePreview() {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(cs.surfaceContainerHigh)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            cs.primary, cs.primaryContainer, cs.secondary, cs.secondaryContainer, cs.tertiary, cs.tertiaryContainer,
        ).forEach { c ->
            Box(Modifier.weight(1f).height(44.dp).clip(MaterialTheme.shapes.medium).background(c))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column {
        Text(
            title,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            style = MaterialTheme.typography.titleSmall,
            color = cs.primary,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .background(cs.surfaceContainer)
                .padding(18.dp),
            content = content,
        )
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) {
                { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
            } else null,
        )
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
            Text(label, style = MaterialTheme.typography.bodyMedium, color = cs.onSurface)
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
private fun AccentSwatch(accent: Accent, selected: Boolean, customHue: Float, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = if (accent == Accent.Custom) {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF6B6B), Color(0xFFFFD93D), Color(0xFF6BCB77),
                Color(0xFF4D96FF), Color(0xFF9B5DE5), Color(0xFFFF6B6B),
            )
        )
    } else {
        Brush.linearGradient(listOf(accent.swatch, accent.swatch))
    }
    val checkTint = if (accent == Accent.Custom) hueSeed(customHue) else Color.White

    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(brush)
            .border(if (selected) 3.dp else 0.dp, if (selected) cs.onSurface else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut() + scaleOut(targetScale = 0.6f),
        ) {
            Icon(Icons.Filled.Check, accentName(accent), Modifier.size(22.dp), tint = if (accent == Accent.Custom) Color.White else checkTint)
        }
    }
}

private fun accentName(accent: Accent): String = when (accent) {
    Accent.Dynamic -> "Из обоев (Material You)"
    Accent.Ocean -> "Океан"
    Accent.Violet -> "Фиалка"
    Accent.Emerald -> "Изумруд"
    Accent.Sunset -> "Закат"
    Accent.Rose -> "Роза"
    Accent.Mono -> "Графит"
    Accent.Custom -> "Свой оттенок"
    Accent.Hollow -> "Hollow"
}
