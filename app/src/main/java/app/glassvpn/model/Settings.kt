package app.glassvpn.model

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { System, Dark, Light }

/** Раздельное туннелирование: Off — всё через VPN, Exclude — выбранные мимо VPN, Only — через VPN только выбранные. */
enum class SplitMode { Off, Exclude, Only }

enum class BackgroundStyle { Solid, Gradient }

enum class Accent { Hollow, Dynamic, Ocean, Violet, Emerald, Sunset, Rose, Mono, Custom }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: Accent = Accent.Dynamic,
    val glassBlur: Float = 24f,
    val glassTint: Float = 0.14f,
    val animateBackground: Boolean = true,
    val motionScale: Float = 1f,
    // --- оформление Material You ---
    val amoled: Boolean = false,
    val bgStyle: BackgroundStyle = BackgroundStyle.Gradient,
    val haptics: Boolean = true,
    val textScale: Float = 1f,
    val autoPing: Boolean = true,
    val sortByPing: Boolean = false,
    val customHue: Float = 220f,       // используется при Accent.Custom
    val cornerScale: Float = 1f,
    // --- подписки ---
    val subUserAgent: SubUserAgent = SubUserAgent.Hollow,
    val customUserAgent: String = "",
    // --- соединение ---
    val bypassLan: Boolean = true,
    val ipv6: Boolean = true,
    val remoteDns: String = "1.1.1.1",
    val tunMtu: Int = 1500,
    // --- раздельное туннелирование ---
    val splitMode: SplitMode = SplitMode.Off,
    val splitApps: Set<String> = emptySet(),
    // --- список серверов: свёрнутые группы (id подписок и "own") ---
    val collapsedGroups: Set<String> = emptySet(),
)

private val Context.settingsDataStore by preferencesDataStore("glassvpn_settings")

private val K_THEME = stringPreferencesKey("theme_mode")
private val K_ACCENT = stringPreferencesKey("accent")
private val K_BLUR = floatPreferencesKey("glass_blur")
private val K_TINT = floatPreferencesKey("glass_tint")
private val K_ANIMATED_BG = booleanPreferencesKey("animated_bg")
private val K_MOTION = floatPreferencesKey("motion_scale")
private val K_AMOLED = booleanPreferencesKey("amoled")
private val K_BG = stringPreferencesKey("bg_style")
private val K_HAPTICS = booleanPreferencesKey("haptics")
private val K_TEXT = floatPreferencesKey("text_scale")
private val K_AUTOPING = booleanPreferencesKey("auto_ping")
private val K_SORTPING = booleanPreferencesKey("sort_ping")
private val K_HUE = floatPreferencesKey("custom_hue")
private val K_CORNER = floatPreferencesKey("corner_scale")
private val K_SUB_UA = stringPreferencesKey("sub_ua")
private val K_CUSTOM_UA = stringPreferencesKey("custom_ua")
private val K_BYPASS_LAN = booleanPreferencesKey("bypass_lan")
private val K_IPV6 = booleanPreferencesKey("ipv6")
private val K_DNS = stringPreferencesKey("remote_dns")
private val K_MTU = androidx.datastore.preferences.core.intPreferencesKey("tun_mtu")
private val K_SPLIT_MODE = stringPreferencesKey("split_mode")
private val K_SPLIT_APPS = stringSetPreferencesKey("split_apps")
private val K_COLLAPSED = stringSetPreferencesKey("collapsed_groups")

private fun Preferences.toSettings() = AppSettings(
    themeMode = this[K_THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
    accent = this[K_ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.Dynamic,
    glassBlur = this[K_BLUR] ?: 24f,
    glassTint = this[K_TINT] ?: 0.14f,
    animateBackground = this[K_ANIMATED_BG] ?: true,
    motionScale = this[K_MOTION] ?: 1f,
    amoled = this[K_AMOLED] ?: false,
    haptics = this[K_HAPTICS] ?: true,
    textScale = this[K_TEXT] ?: 1f,
    autoPing = this[K_AUTOPING] ?: true,
    sortByPing = this[K_SORTPING] ?: false,
    bgStyle = this[K_BG]?.let { runCatching { BackgroundStyle.valueOf(it) }.getOrNull() } ?: BackgroundStyle.Gradient,
    customHue = this[K_HUE] ?: 220f,
    cornerScale = this[K_CORNER] ?: 1f,
    subUserAgent = this[K_SUB_UA]?.let { runCatching { SubUserAgent.valueOf(it) }.getOrNull() } ?: SubUserAgent.Hollow,
    customUserAgent = this[K_CUSTOM_UA] ?: "",
    bypassLan = this[K_BYPASS_LAN] ?: true,
    ipv6 = this[K_IPV6] ?: true,
    remoteDns = this[K_DNS] ?: "1.1.1.1",
    tunMtu = this[K_MTU] ?: 1500,
    splitMode = this[K_SPLIT_MODE]?.let { runCatching { SplitMode.valueOf(it) }.getOrNull() } ?: SplitMode.Off,
    splitApps = this[K_SPLIT_APPS] ?: emptySet(),
    collapsedGroups = this[K_COLLAPSED] ?: emptySet(),
)

private fun MutablePreferences.writeSettings(s: AppSettings) {
    this[K_THEME] = s.themeMode.name
    this[K_ACCENT] = s.accent.name
    this[K_BLUR] = s.glassBlur
    this[K_TINT] = s.glassTint
    this[K_ANIMATED_BG] = s.animateBackground
    this[K_MOTION] = s.motionScale
    this[K_AMOLED] = s.amoled
    this[K_BG] = s.bgStyle.name
    this[K_HAPTICS] = s.haptics
    this[K_TEXT] = s.textScale
    this[K_AUTOPING] = s.autoPing
    this[K_SORTPING] = s.sortByPing
    this[K_HUE] = s.customHue
    this[K_CORNER] = s.cornerScale
    this[K_SUB_UA] = s.subUserAgent.name
    this[K_CUSTOM_UA] = s.customUserAgent
    this[K_BYPASS_LAN] = s.bypassLan
    this[K_IPV6] = s.ipv6
    this[K_DNS] = s.remoteDns
    this[K_MTU] = s.tunMtu
    this[K_SPLIT_MODE] = s.splitMode.name
    this[K_SPLIT_APPS] = s.splitApps
    this[K_COLLAPSED] = s.collapsedGroups
}

class SettingsStore(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toSettings() }.distinctUntilChanged()

    suspend fun update(transform: (AppSettings) -> AppSettings) = context.settingsDataStore.edit { prefs ->
        prefs.writeSettings(transform(prefs.toSettings()))
    }
}
