package app.glassvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import app.glassvpn.model.PingResult

/** Цвета пинга: быстро / средне / плохо / нет связи. Подбираются под светлую и тёмную тему. */
@Composable
fun pingColors(result: PingResult?): Pair<Color, Color> {
    val cs = MaterialTheme.colorScheme
    val dark = cs.surface.luminance() < 0.5f
    return when (result) {
        is PingResult.Ok -> when {
            result.ms < 150 -> if (dark) Color(0xFF174A2B) to Color(0xFFB6F2C6) else Color(0xFFB6F2C6) to Color(0xFF00391A)
            result.ms < 350 -> if (dark) Color(0xFF5A4300) to Color(0xFFFFDF9A) else Color(0xFFFFDF9A) to Color(0xFF3F2E00)
            else -> cs.errorContainer to cs.onErrorContainer
        }
        PingResult.Failed -> cs.errorContainer to cs.onErrorContainer
        else -> cs.surfaceContainerHighest to cs.onSurfaceVariant
    }
}

fun pingText(result: PingResult?): String = when (result) {
    null -> "пинг"
    PingResult.Testing -> ""
    is PingResult.Ok -> "${result.ms} мс"
    PingResult.Failed -> "нет связи"
    is PingResult.Unsupported -> result.label
}

/** Маленькая «таблетка» с пингом. Нажатие запускает проверку. */
@Composable
fun PingChip(result: PingResult?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val (container, content) = pingColors(result)
    val base = modifier.clip(CircleShape).background(container)
    Row(
        (if (onClick != null && result !is PingResult.Testing) base.clickable(onClick = onClick) else base)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (result == PingResult.Testing) {
            CircularProgressIndicator(Modifier.size(12.dp), color = content, strokeWidth = 1.8.dp)
        } else {
            Text(pingText(result), style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1)
        }
    }
}

fun fmtBytes(b: Long): String = when {
    b < 1024 -> "$b Б"
    b < 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f КБ", b / 1024.0)
    b < 1024L * 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f МБ", b / 1048576.0)
    else -> String.format(java.util.Locale.US, "%.2f ГБ", b / 1073741824.0)
}
