package app.glassvpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.glassvpn.model.AppEntry
import app.glassvpn.model.InstalledApps
import app.glassvpn.ui.MainViewModel
import java.util.Locale

/** Выбор приложений для раздельного туннелирования. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelSheet(vm: MainViewModel, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val settings by vm.settings.collectAsState()
    val apps by vm.installedApps.collectAsState()
    var query by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.loadInstalledApps() }

    // Порядок фиксируем при открытии: выбранные сверху, но список не прыгает при нажатии на галочки.
    val initiallySelected = remember { settings.splitApps }
    val ordered = remember(apps) {
        apps?.sortedWith(compareByDescending<AppEntry> { it.pkg in initiallySelected }
            .thenBy { it.label.lowercase(Locale.getDefault()) })
    }
    val filtered = remember(ordered, query) {
        val q = query.trim().lowercase(Locale.getDefault())
        if (q.isEmpty()) ordered else ordered?.filter {
            it.label.lowercase(Locale.getDefault()).contains(q) || it.pkg.lowercase(Locale.US).contains(q)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Приложения", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
                    Text(
                        "Выбрано: ${settings.splitApps.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { vm.updateSettings { it.copy(splitApps = emptySet()) } },
                    enabled = settings.splitApps.isNotEmpty(),
                ) { Text("Сбросить") }
                TextButton(onClick = onDismiss) { Text("Готово") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Поиск") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            val list = filtered
            if (list == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    Text("Ничего не найдено", color = cs.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().height(480.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(list, key = { it.pkg }) { app ->
                        val checked = app.pkg in settings.splitApps
                        val toggle = {
                            vm.updateSettings {
                                it.copy(splitApps = if (checked) it.splitApps - app.pkg else it.splitApps + app.pkg)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1)
                                Text(app.pkg, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1)
                            }
                            Checkbox(checked = checked, onCheckedChange = { toggle() })
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AppIcon(pkg: String) {
    val ctx = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(null, pkg) { value = InstalledApps.icon(ctx, pkg) }
    val bmp = icon
    if (bmp != null) {
        Image(bmp, null, Modifier.size(40.dp))
    } else {
        Box(Modifier.size(40.dp))
    }
}
