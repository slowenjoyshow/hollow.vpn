package app.glassvpn.model

import android.content.Context
import java.util.UUID

/** Постоянный HWID устройства: панели (Remnawave и др.) считают по нему лимит устройств. */
object DeviceId {
    fun get(context: Context): String {
        val prefs = context.getSharedPreferences("glassvpn_device", Context.MODE_PRIVATE)
        return prefs.getString("hwid", null) ?: UUID.randomUUID().toString().replace("-", "").take(16).also {
            prefs.edit().putString("hwid", it).apply()
        }
    }
}
