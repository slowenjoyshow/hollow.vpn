package app.glassvpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glassvpn.model.Accent
import app.glassvpn.model.AppSettings
import app.glassvpn.model.ThemeMode

private val BaseTypography = Typography()
private val GlassTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
)

/** Цвет акцента для кружков в настройках. */
val Accent.swatch: Color
    get() = when (this) {
        Accent.Hollow -> Color(0xFFFF3B5C)
        Accent.Dynamic -> Color(0xFF7FB0FF)
        Accent.Ocean -> Color(0xFF4F8BFF)
        Accent.Violet -> Color(0xFFB28CFF)
        Accent.Emerald -> Color(0xFF5BE7A9)
        Accent.Sunset -> Color(0xFFFF9A6B)
        Accent.Rose -> Color(0xFFFF8FBE)
        Accent.Mono -> Color(0xFFB9C3D3)
        Accent.Custom -> Color(0xFF9FA8FF)
    }

private fun darkScheme(accent: Accent) = darkColorScheme(
    primary = accent.swatch,
    secondary = when (accent) {
        Accent.Emerald -> Color(0xFFA7F3D0)
        Accent.Sunset -> Color(0xFFFFCFA3)
        Accent.Rose -> Color(0xFFFFC2DC)
        Accent.Violet -> Color(0xFFD8C4FF)
        Accent.Mono -> Color(0xFFCBD5E1)
        Accent.Hollow -> Color(0xFFFFB3C0)
        else -> Color(0xFFA9C7FF)
    },
    tertiary = when (accent) {
        Accent.Sunset -> Color(0xFFFF8FB1)
        Accent.Rose -> Color(0xFFC4B5FD)
        Accent.Emerald -> Color(0xFF7DD3FC)
        Accent.Violet -> Color(0xFFF0A8E8)
        Accent.Mono -> Color(0xFF94A3B8)
        Accent.Hollow -> Color(0xFFD9D2D3)
        else -> Color(0xFF6FE7E0)
    },
    background = if (accent == Accent.Hollow) Color(0xFF080506) else Color(0xFF070B16),
    surface = if (accent == Accent.Hollow) Color(0xFF0F0A0B) else Color(0xFF0B1020),
    surfaceVariant = if (accent == Accent.Hollow) Color(0xFF1C1214) else Color(0xFF141B2E),
    onBackground = if (accent == Accent.Hollow) Color(0xFFF2E9EA) else Color(0xFFE8EEF9),
    onSurface = if (accent == Accent.Hollow) Color(0xFFF2E9EA) else Color(0xFFE8EEF9),
    onSurfaceVariant = if (accent == Accent.Hollow) Color(0xFFC7B3B6) else Color(0xFFAEB9D0),
    onPrimary = if (accent == Accent.Hollow) Color(0xFF1A0408) else Color(0xFF061020),
    outline = if (accent == Accent.Hollow) Color(0xFF45282D) else Color(0xFF2A3450),
)

private fun lightScheme(accent: Accent) = lightColorScheme(
    primary = when (accent) {
        Accent.Hollow -> Color(0xFFB4122F)
        Accent.Dynamic, Accent.Ocean -> Color(0xFF1D4ED8)
        Accent.Violet -> Color(0xFF6D28D9)
        Accent.Emerald -> Color(0xFF047857)
        Accent.Sunset -> Color(0xFFC2410C)
        Accent.Rose -> Color(0xFFBE185D)
        Accent.Mono -> Color(0xFF334155)
        Accent.Custom -> Color(0xFF3F51B5)
    },
    secondary = when (accent) {
        Accent.Violet -> Color(0xFF7C3AED)
        Accent.Emerald -> Color(0xFF0D9488)
        Accent.Sunset -> Color(0xFFEA580C)
        Accent.Rose -> Color(0xFFDB2777)
        Accent.Mono -> Color(0xFF475569)
        else -> Color(0xFF2563EB)
    },
    tertiary = when (accent) {
        Accent.Rose -> Color(0xFF7C3AED)
        Accent.Sunset -> Color(0xFFBE123C)
        Accent.Emerald -> Color(0xFF0369A1)
        Accent.Violet -> Color(0xFFDB2777)
        Accent.Mono -> Color(0xFF64748B)
        else -> Color(0xFF0E7490)
    },
    background = Color(0xFFEEF3FF),
    surface = Color(0xFFF7F9FF),
    surfaceVariant = Color(0xFFDFE6FA),
    onBackground = Color(0xFF131A2A),
    onSurface = Color(0xFF131A2A),
    onSurfaceVariant = Color(0xFF43506B),
    onPrimary = Color.White,
    outline = Color(0xFFB9C6E4),
)

/** Схема из одного оттенка (hue 0..360): primary/secondary/tertiary — соседние тона. */
private fun hueScheme(hue: Float, dark: Boolean): ColorScheme {
    fun c(h: Float, s: Float, v: Float) = Color.hsv(((h % 360f) + 360f) % 360f, s, v)
    return if (dark) darkColorScheme(
        primary = c(hue, 0.45f, 1.00f), secondary = c(hue + 30f, 0.30f, 0.95f), tertiary = c(hue + 120f, 0.40f, 0.95f),
        background = c(hue, 0.35f, 0.07f), surface = c(hue, 0.30f, 0.09f), surfaceVariant = c(hue, 0.25f, 0.17f),
        onBackground = c(hue, 0.05f, 0.95f), onSurface = c(hue, 0.05f, 0.95f), onSurfaceVariant = c(hue, 0.10f, 0.78f),
        onPrimary = c(hue, 0.80f, 0.15f), outline = c(hue, 0.20f, 0.32f),
    ) else lightColorScheme(
        primary = c(hue, 0.80f, 0.60f), secondary = c(hue + 30f, 0.70f, 0.55f), tertiary = c(hue + 120f, 0.75f, 0.50f),
        background = c(hue, 0.05f, 0.99f), surface = c(hue, 0.04f, 0.98f), surfaceVariant = c(hue, 0.12f, 0.92f),
        onBackground = c(hue, 0.40f, 0.12f), onSurface = c(hue, 0.40f, 0.12f), onSurfaceVariant = c(hue, 0.25f, 0.35f),
        onPrimary = Color.White, outline = c(hue, 0.15f, 0.78f),
    )
}

private fun ColorScheme.asAmoled() = copy(
    background = Color.Black, surface = Color.Black,
    surfaceVariant = surfaceVariant.copy(alpha = 1f).let { Color(it.red * 0.55f, it.green * 0.55f, it.blue * 0.55f) },
)

private fun shapesFor(scale: Float): Shapes {
    fun r(dp: Int) = RoundedCornerShape((dp * scale).dp)
    return Shapes(extraSmall = r(6), small = r(10), medium = r(16), large = r(24), extraLarge = r(32))
}

@Composable
fun HollowTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val useDynamic = settings.accent == Accent.Dynamic && Build.VERSION.SDK_INT >= 31
    var scheme = when {
        useDynamic -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        settings.accent == Accent.Custom -> hueScheme(settings.customHue, dark)
        dark -> darkScheme(settings.accent)
        else -> lightScheme(settings.accent)
    }
    if (dark && settings.amoled) scheme = scheme.asAmoled()
    MaterialTheme(colorScheme = scheme, typography = GlassTypography, shapes = shapesFor(settings.cornerScale), content = content)
}
