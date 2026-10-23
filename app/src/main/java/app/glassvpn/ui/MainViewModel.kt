package app.glassvpn.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.glassvpn.model.AppSettings
import app.glassvpn.model.DeviceId
import app.glassvpn.model.Subscription
import app.glassvpn.model.parseSubscription
import app.glassvpn.model.parseSubscriptionReport
import app.glassvpn.model.LinkParser
import app.glassvpn.model.Profile
import app.glassvpn.model.ProfileStore
import app.glassvpn.model.SettingsStore
import app.glassvpn.model.Socks5Profile
import app.glassvpn.model.SubscriptionFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface ImportState {
    data object Idle : ImportState
    data object Loading : ImportState
    data class Done(val added: Int, val source: String, val note: String = "") : ImportState
    data class Failed(val message: String) : ImportState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = ProfileStore(app)
    private val settingsStore = SettingsStore(app)
    private val appContext = app.applicationContext

    val profiles = store.profiles.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val selectedId = store.selectedId.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val selected: StateFlow<Profile?> = combine(profiles, selectedId) { l, id -> l.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val importState = MutableStateFlow<ImportState>(ImportState.Idle)

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settingsStore.update(transform) }
    }

    /** Ссылка, список ссылок, base64-подписка, JSON или конфиг AmneziaWG. */
    fun importText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            importState.value = ImportState.Failed("Вставь ссылку или конфиг")
            return
        }
        viewModelScope.launch { addParsed(trimmed, "текст") }
    }

    fun importUri(uri: Uri) {
        viewModelScope.launch {
            importState.value = ImportState.Loading
            val text = runCatching {
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Не удалось открыть файл")
            }.getOrElse {
                importState.value = ImportState.Failed(it.message ?: "Не удалось прочитать файл")
                return@launch
            }
            addParsed(text, "файл")
        }
    }

    private suspend fun addParsed(text: String, source: String) {
        val trimmed = text.trim()
        val isUrl = (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) &&
            trimmed.none(Char::isWhitespace)

        if (isUrl) {
            importState.value = ImportState.Loading
            fetchSubscription(trimmed)
                .onSuccess { importState.value = ImportState.Done(it.first, "подписка", it.second) }
                .onFailure { importState.value = ImportState.Failed(it.message ?: "Не удалось загрузить подписку") }
            return
        }

        addProfiles(trimmed, source)
    }

    val subscriptions = store.subscriptions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val refreshing = MutableStateFlow(false)
    val hwid: String = DeviceId.get(app)

    /** Скачивает подписку и сохраняет (или обновляет) её вместе с профилями. Возвращает число серверов. */
    private suspend fun fetchSubscription(url: String): Result<Pair<Int, String>> {
        val s = settings.value
        return SubscriptionFetcher.fetch(url, hwid, s.subUserAgent, s.customUserAgent, ::parseSubscription)
            .mapCatching { res ->
                val report = parseSubscriptionReport(res.body).getOrThrow()
                val fresh = report.profiles
                val sub = Subscription(
                    id = UUID.randomUUID().toString(),
                    url = url,
                    name = res.title ?: runCatching { java.net.URI(url).host }.getOrNull() ?: "Подписка",
                    updatedAt = System.currentTimeMillis(),
                    intervalHours = res.intervalHours ?: 12,
                    announce = res.announce ?: "",
                )
                store.saveSubscription(sub, fresh)
                fresh.size to report.note
            }
    }

    /** Обновить все подписки. [onlyStale] = только те, чей интервал обновления истёк. */
    fun refreshSubscriptions(onlyStale: Boolean = false) {
        if (refreshing.value) return
        viewModelScope.launch {
            refreshing.value = true
            val now = System.currentTimeMillis()
            val due = subscriptions.value.filter {
                !onlyStale || now - it.updatedAt > it.intervalHours * 3_600_000L
            }
            var failed: String? = null
            for (sub in due) {
                fetchSubscription(sub.url).onFailure { failed = it.message }
            }
            importState.value = when {
                due.isEmpty() -> ImportState.Idle
                failed != null -> ImportState.Failed(failed!!)
                else -> ImportState.Done(due.size, "обновление подписок")
            }
            refreshing.value = false
        }
    }

    fun removeSubscription(id: String) = viewModelScope.launch { store.removeSubscription(id) }

    init {
        // Автообновление при запуске приложения.
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)   // дать DataStore прогрузиться
            refreshSubscriptions(onlyStale = true)
        }
    }

    private suspend fun addProfiles(text: String, source: String) {
        LinkParser.parseManyReport(text)
            .onSuccess { report ->
                report.profiles.forEach { store.add(it) }
                importState.value = ImportState.Done(report.profiles.size, source, report.note)
            }
            .onFailure { importState.value = ImportState.Failed(it.message ?: "Не удалось распознать профиль") }
    }

    fun resetImport() {
        importState.value = ImportState.Idle
    }

    fun addSocks(name: String, host: String, port: Int, user: String, pass: String) = viewModelScope.launch {
        store.add(Socks5Profile(UUID.randomUUID().toString(), name.ifBlank { host }, host, port, user, pass))
        importState.value = ImportState.Done(1, "SOCKS5")
    }

    fun select(id: String) = viewModelScope.launch { store.select(id) }
    fun remove(id: String) = viewModelScope.launch { store.remove(id) }
}
