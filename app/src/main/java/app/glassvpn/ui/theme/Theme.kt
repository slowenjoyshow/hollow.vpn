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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glassvpn.model.Accent
import app.glassvpn.model.AppSettings
import app.glassvpn.model.ThemeMode
import kotlin.math.abs

private val BaseTypography = Typography()
private val AppTypography = BaseTypography.copy(
    displaySmall = BaseTypography.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = BaseTypography.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Исходный цвет палитры: из него строится вся схема Material 3. */
val Accent.seed: Color
    get() = when (this) {
        Accent.Hollow -> Color(0xFFE5304F)
        Accent.Dynamic -> Color(0xFF6750A4)   // запасной вариант для Android 11 и старше
        Accent.Ocean -> Color(0xFF2F6BFF)
        Accent.Violet -> Color(0xFF7C4DFF)
        Accent.Emerald -> Color(0xFF1FA67A)
        Accent.Sunset -> Color(0xFFFF7A3D)
        Accent.Rose -> Color(0xFFE8467C)
        Accent.Mono -> Color(0xFF6B7280)
        Accent.Custom -> Color(0xFF6750A4)
    }

/** Цвет кружка в выборе палитры. */
val Accent.swatch: Color get() = seed

/** Исходный цвет из оттенка 0..360 для режима «свой цвет». */
fun hueSeed(hue: Float): Color = Color.hsv(((hue % 360f) + 360f) % 360f, 0.72f, 0.95f)

// ---------- тональные палитры ----------

private fun tone(hue: Float, sat: Float, tone: Int): Color {
    val l = tone / 100f
    val edge = abs(2f * l - 1f)   // 0 в середине шкалы, 1 на краях
    // у самых тёмных и светлых тонов насыщенность падает, как в палитрах Material
    val s = (sat * (1f - 0.55f * edge * edge)).coerceIn(0f, 1f)
    return Color.hsl(((hue % 360f) + 360f) % 360f, s, l)
}

/** Схема Material 3 из одного цвета: primary/secondary/tertiary + нейтральные тона и все surfaceContainer*. */
fun tonalScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    val h = hsv[0]
    val mono = hsv[1] < 0.15f
    val sp = if (mono) 0.05f else hsv[1].coerceIn(0.45f, 0.95f)
    val ss = sp * 0.35f
    val st = sp * 0.65f
    val sn = if (mono) 0.03f else 0.07f
    val snv = if (mono) 0.05f else 0.14f
    val th = h + 60f
    fun p(t: Int) = tone(h, sp, t)
    fun s(t: Int) = tone(h, ss, t)
    fun t3(t: Int) = tone(th, st, t)
    fun n(t: Int) = tone(h, sn, t)
    fun nv(t: Int) = tone(h, snv, t)

    return if (dark) darkColorScheme(
        primary = p(80), onPrimary = p(20), primaryContainer = p(30), onPrimaryContainer = p(90),
        inversePrimary = p(40),
        secondary = s(80), onSecondary = s(20), secondaryContainer = s(30), onSecondaryContainer = s(90),
        tertiary = t3(80), onTertiary = t3(20), tertiaryContainer = t3(30), onTertiaryContainer = t3(90),
        background = n(6), onBackground = n(90),
        surface = n(6), onSurface = n(90),
        surfaceVariant = nv(30), onSurfaceVariant = nv(80),
        surfaceTint = p(80),
        inverseSurface = n(90), inverseOnSurface = n(20),
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        outline = nv(60), outlineVariant = nv(30), scrim = Color.Black,
        surfaceBright = n(24), surfaceDim = n(6),
        surfaceContainerLowest = n(4), surfaceContainerLow = n(10), surfaceContainer = n(12),
        surfaceContainerHigh = n(17), surfaceContainerHighest = n(22),
    ) else lightColorScheme(
        primary = p(40), onPrimary = p(100), primaryContainer = p(90), onPrimaryContainer = p(10),
        inversePrimary = p(80),
        secondary = s(40), onSecondary = s(100), secondaryContainer = s(90), onSecondaryContainer = s(10),
        tertiary = t3(40), onTertiary = t3(100), tertiaryContainer = t3(90), onTertiaryContainer = t3(10),
        background = n(98), onBackground = n(10),
        surface = n(98), onSurface = n(10),
        surfaceVariant = nv(90), onSurfaceVariant = nv(30),
        surfaceTint = p(40),
        inverseSurface = n(20), inverseOnSurface = n(95),
        error = Color(0xFFBA1A1A), onError = Color.White,
        errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
        outline = nv(50), outlineVariant = nv(80), scrim = Color.Black,
        surfaceBright = n(98), surfaceDim = n(87),
        surfaceContainerLowest = n(100), surfaceContainerLow = n(96), surfaceContainer = n(94),
        surfaceContainerHigh = n(92), surfaceContainerHighest = n(90),
    )
}

/** Чёрный AMOLED: фон чисто чёрный, контейнеры затемнены, но сохраняют оттенок палитры. */
private fun ColorScheme.asAmoled(): ColorScheme {
    fun d(c: Color, f: Float) = lerp(c, Color.Black, f)
    return copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = d(surfaceContainerLow, 0.6f),
        surfaceContainer = d(surfaceContainer, 0.55f),
        surfaceContainerHigh = d(surfaceContainerHigh, 0.5f),
        surfaceContainerHighest = d(surfaceContainerHighest, 0.45f),
        surfaceBright = d(surfaceBright, 0.4f),
    )
}

/** Скругления в духе Material 3 Expressive: крупные, масштабируются ползунком в настройках. */
private fun shapesFor(scale: Float): Shapes {
    fun r(dp: Int) = RoundedCornerShape((dp * scale).dp)
    return Shapes(extraSmall = r(8), small = r(12), medium = r(18), large = r(26), extraLarge = r(36))
}

@Composable
fun HollowTheme(settings: AppSettings = AppSettings(), content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    val ctx = LocalContext.current
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    var scheme = when {
        settings.accent == Accent.Dynamic && dynamicAvailable ->
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        settings.accent == Accent.Custom -> tonalScheme(hueSeed(settings.customHue), dark)
        else -> tonalScheme(settings.accent.seed, dark)
    }
    if (dark && settings.amoled) scheme = scheme.asAmoled()

    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * settings.textScale),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = shapesFor(settings.cornerScale),
            content = content,
        )
    }
}
