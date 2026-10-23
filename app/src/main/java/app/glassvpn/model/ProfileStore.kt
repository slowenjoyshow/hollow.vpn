package app.glassvpn.model

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.store by preferencesDataStore("glassvpn")

class ProfileStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    private val listSer = ListSerializer(Profile.serializer())
    private val PROFILES = stringPreferencesKey("profiles")
    private val SELECTED = stringPreferencesKey("selected")

    val profiles: Flow<List<Profile>> = context.store.data.map { p ->
        p[PROFILES]?.let { runCatching { json.decodeFromString(listSer, it) }.getOrNull() } ?: emptyList()
    }
    val selectedId: Flow<String?> = context.store.data.map { it[SELECTED] }

    private val SUBS = stringPreferencesKey("subscriptions")
    private val subSer = ListSerializer(Subscription.serializer())

    val subscriptions: Flow<List<Subscription>> = context.store.data.map { p ->
        p[SUBS]?.let { runCatching { json.decodeFromString(subSer, it) }.getOrNull() } ?: emptyList()
    }

    /**
     * Добавляет подписку или обновляет существующую (по url): старые профили этой подписки заменяются новыми.
     * Выбранный профиль сохраняется, если в новом списке есть сервер с тем же именем.
     */
    suspend fun saveSubscription(sub: Subscription, fresh: List<Profile>) = context.store.edit { p ->
        val subs = p[SUBS]?.let { json.decodeFromString(subSer, it) } ?: emptyList()
        val old = subs.firstOrNull { it.url == sub.url }
        val oldIds = old?.profileIds?.toSet() ?: emptySet()
        val cur = p[PROFILES]?.let { json.decodeFromString(listSer, it) } ?: emptyList()
        val selectedOld = cur.firstOrNull { it.id == p[SELECTED] }

        val kept = cur.filterNot { it.id in oldIds }
        p[PROFILES] = json.encodeToString(listSer, kept + fresh)
        val saved = sub.copy(id = old?.id ?: sub.id, profileIds = fresh.map { it.id })
        p[SUBS] = json.encodeToString(subSer, subs.filterNot { it.url == sub.url } + saved)

        if (p[SELECTED] == null || p[SELECTED] in oldIds) {
            val pick = fresh.firstOrNull { it.name == selectedOld?.name } ?: fresh.firstOrNull()
            if (pick != null) p[SELECTED] = pick.id else p.remove(SELECTED)
        }
    }

    suspend fun removeSubscription(id: String) = context.store.edit { p ->
        val subs = p[SUBS]?.let { json.decodeFromString(subSer, it) } ?: emptyList()
        val sub = subs.firstOrNull { it.id == id } ?: return@edit
        val cur = p[PROFILES]?.let { json.decodeFromString(listSer, it) } ?: emptyList()
        p[PROFILES] = json.encodeToString(listSer, cur.filterNot { it.id in sub.profileIds })
        p[SUBS] = json.encodeToString(subSer, subs.filterNot { it.id == id })
        if (p[SELECTED] in sub.profileIds) p.remove(SELECTED)
    }

    suspend fun add(profile: Profile) = context.store.edit { p ->
        val cur = p[PROFILES]?.let { json.decodeFromString(listSer, it) } ?: emptyList()
        p[PROFILES] = json.encodeToString(listSer, cur + profile)
        if (p[SELECTED] == null) p[SELECTED] = profile.id
    }

    suspend fun remove(id: String) = context.store.edit { p ->
        val cur = p[PROFILES]?.let { json.decodeFromString(listSer, it) } ?: emptyList()
        p[PROFILES] = json.encodeToString(listSer, cur.filterNot { it.id == id })
        if (p[SELECTED] == id) p.remove(SELECTED)
    }

    suspend fun select(id: String) = context.store.edit { it[SELECTED] = id }
}
