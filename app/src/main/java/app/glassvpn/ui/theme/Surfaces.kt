package app.glassvpn.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import app.glassvpn.model.AppSettings
import app.glassvpn.model.BackgroundStyle

val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

/** Фон приложения: однотонный или с мягким свечением акцентного цвета сверху. Без размытия и анимаций. */
@Composable
fun AppBackground(content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val style = LocalAppSettings.current.bgStyle
    Box(Modifier.fillMaxSize().background(cs.background)) {
        if (style == BackgroundStyle.Gradient) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(cs.primary.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(size.width * 0.5f, 0f),
                        radius = size.width * 1.15f,
                    )
                )
            }
        }
        content()
    }
}

/** Обычная карточка Material 3: тональная заливка и тонкая рамка. Скругление берётся из темы. */
@Composable
fun Modifier.panel(shape: Shape = MaterialTheme.shapes.extraLarge): Modifier {
    val cs = MaterialTheme.colorScheme
    return this
        .clip(shape)
        .background(cs.surfaceVariant.copy(alpha = 0.55f), shape)
        .border(1.dp, cs.outline.copy(alpha = 0.35f), shape)
}
