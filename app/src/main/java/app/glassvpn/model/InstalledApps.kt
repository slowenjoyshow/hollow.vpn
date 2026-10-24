package app.glassvpn.model

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class AppEntry(val pkg: String, val label: String)

/** Приложения с иконкой в лаунчере (видимость обеспечена блоком <queries> в манифесте). */
object InstalledApps {
    suspend fun load(context: Context): List<AppEntry> = withContext(Dispatchers.Default) {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(launcher, 0)
            .asSequence()
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching { AppEntry(pkg, pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()) }.getOrNull()
            }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    suspend fun icon(context: Context, pkg: String): ImageBitmap? = withContext(Dispatchers.IO) {
        runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96).asImageBitmap() }.getOrNull()
    }

    private fun Drawable.toBitmap(size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, size, size)
        draw(c)
        return bmp
    }
}
