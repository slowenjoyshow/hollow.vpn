package app.glassvpn.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Speed
import app.glassvpn.model.PingResult
import app.glassvpn.ui.PingChip
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.glassvpn.model.AwgProfile
import app.glassvpn.model.Profile
import app.glassvpn.model.Socks5Profile
import app.glassvpn.model.VlessProfile
import app.glassvpn.model.VmessProfile
import app.glassvpn.model.XrayProfile
import app.glassvpn.ui.ImportState
import app.glassvpn.ui.MainViewModel
import app.glassvpn.ui.theme.panel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(vm: MainViewModel) {
    val profiles by vm.profiles.collectAsState()
    val selected by vm.selectedId.collectAsState()
    val importState by vm.importState.collectAsState()
    val subs by vm.subscriptions.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val pings by vm.pings.collectAsState()
    val settings by vm.settings.collectAsState()
    LaunchedEffect(settings.autoPing, profiles.size) {
        if (settings.autoPing) {
            val missing = profiles.filter { pings[it.id] == null }
            if (missing.isNotEmpty()) vm.pingMany(missing)
        }
    }
    val cs = MaterialTheme.colorScheme

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importUri(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Серверы", style = MaterialTheme.typography.headlineMedium, color = cs.onSurface)
                    Text(
                        if (profiles.isEmpty()) "Нет сохранённых конфигураций" else "${profiles.size} конфигураций",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                if (profiles.isNotEmpty()) {
                    IconButton(onClick = { vm.pingAll() }) {
                        Icon(Icons.Filled.Speed, "Проверить пинг", tint = cs.primary)
                    }
                }
                if (subs.isNotEmpty()) {
                    if (refreshing) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                    } else {
                        androidx.compose.material3.IconButton(onClick = { vm.refreshSubscriptions() }) {
                            Icon(Icons.Filled.Refresh, "Обновить подписки", tint = cs.primary)
                        }
                    }
                }
            }

            if (profiles.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = settings.sortByPing,
                        onClick = { vm.updateSettings { it.copy(sortByPing = !it.sortByPing) } },
                        label = { Text("По пингу") },
                        leadingIcon = { Icon(Icons.Filled.Speed, null, Modifier.size(18.dp)) },
                    )
                    AssistChip(
                        onClick = { vm.pickBest() },
                        label = { Text("Лучший сервер") },
                        leadingIcon = { Icon(Icons.Filled.Bolt, null, Modifier.size(18.dp)) },
                    )
                }
            }

            if (importState is ImportState.Done || importState is ImportState.Failed) {
                ImportStatus(importState)
                Spacer(Modifier.height(10.dp))
            }

            if (profiles.isEmpty()) {
                EmptyProfiles(Modifier.fillMaxWidth().weight(1f))
            } else {
                val subIds = subs.flatMap { it.profileIds }.toSet()
                val order: (List<Profile>) -> List<Profile> = { list ->
                    if (settings.sortByPing) list.sortedBy { (pings[it.id] as? PingResult.Ok)?.ms ?: Int.MAX_VALUE } else list
                }
                val groups = buildList {
                    subs.forEach { sub ->
                        add(ProfileGroup(sub.id, sub.name, sub.announce, order(profiles.filter { it.id in sub.profileIds }), true))
                    }
                    val own = profiles.filter { it.id !in subIds }
                    if (own.isNotEmpty()) add(ProfileGroup("own", "Мои конфиги", "", order(own), false))
                }.filter { it.items.isNotEmpty() || it.isSub }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    groups.forEach { g ->
                        val showHeader = g.isSub || groups.size > 1
                        val open = g.id !in settings.collapsedGroups
                        if (showHeader) {
                            item(key = "h-${g.id}") {
                                GroupHeader(
                                    title = g.title,
                                    count = g.items.size,
                                    note = g.note,
                                    expanded = open,
                                    onToggle = {
                                        vm.updateSettings {
                                            it.copy(collapsedGroups = if (open) it.collapsedGroups + g.id else it.collapsedGroups - g.id)
                                        }
                                    },
                                    onPing = { vm.pingMany(g.items) },
                                    onDelete = if (g.isSub) ({ vm.removeSubscription(g.id) }) else null,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                        if (open || !showHeader) {
                            items(g.items, key = { it.id }) { p ->
                                ProfileRow(
                                    profile = p,
                                    selected = p.id == selected,
                                    ping = pings[p.id],
                                    onSelect = { vm.select(p.id) },
                                    onPing = { vm.pingProfile(p) },
                                    onDelete = { vm.remove(p.id) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = {
                vm.resetImport()
                sheet = true
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 20.dp),
            containerColor = cs.primaryContainer,
            contentColor = cs.onPrimaryContainer,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("Добавить") },
        )
    }

    if (sheet) {
        ModalBottomSheet(
            onDismissRequest = { sheet = false },
            containerColor = cs.surface.copy(alpha = 0.97f),
        ) {
            AddProfileSheet(
                vm = vm,
                importState = importState,
                onPickFile = { filePicker.launch(arrayOf("*/*")) },
                close = { sheet = false },
            )
        }
    }
}

private data class ProfileGroup(
    val id: String,
    val title: String,
    val note: String,
    val items: List<Profile>,
    val isSub: Boolean,
)

@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    note: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPing: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggle)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            if (expanded) "Свернуть" else "Развернуть",
            tint = cs.primary,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (note.isBlank()) "Серверов: $count" else "Серверов: $count · $note",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface.copy(alpha = 0.55f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onPing) {
            Icon(Icons.Filled.Speed, "Проверить пинг группы", tint = cs.primary)
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, "Удалить подписку", tint = cs.error.copy(alpha = 0.85f))
            }
        }
    }
}

@Composable
private fun EmptyProfiles(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier.padding(bottom = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(104.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Layers, null, Modifier.size(46.dp), tint = cs.primary)
        }
        Spacer(Modifier.height(20.dp))
        Text("Пока пусто", style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
        Spacer(Modifier.height(6.dp))
        Text(
            "Добавь профиль из ссылки, подписки\nили файла с конфигом",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    selected: Boolean,
    ping: PingResult?,
    onSelect: () -> Unit,
    onPing: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    val tint = protocolColor(profile)
    val container by animateColorAsState(
        if (selected) cs.secondaryContainer else cs.surfaceContainer,
        label = "rowBg",
    )
    val onContainer = if (selected) cs.onSecondaryContainer else cs.onSurface

    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(container)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(protocolIcon(profile), null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                profile.name,
                style = MaterialTheme.typography.titleMedium,
                color = onContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${profile.protocolLabel} · ${profileDetail(profile)}",
                style = MaterialTheme.typography.bodySmall,
                color = onContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        PingChip(ping, onClick = onPing)
        if (selected) {
            Icon(
                Icons.Filled.CheckCircle, "Выбран", tint = cs.primary,
                modifier = Modifier.padding(start = 6.dp).size(22.dp),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, "Удалить", tint = cs.error.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun AddProfileSheet(
    vm: MainViewModel,
    importState: ImportState,
    onPickFile: () -> Unit,
    close: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
    ) {
        Text(
            "Новый профиль",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
        ) {
            Tab(tab == 0, { tab = 0 }, text = { Text("Ссылка") })
            Tab(tab == 1, { tab = 1 }, text = { Text("Файл") })
            Tab(tab == 2, { tab = 2 }, text = { Text("SOCKS5") })
        }
        Spacer(Modifier.height(16.dp))
        when (tab) {
            0 -> PasteTab(vm, importState)
            1 -> FileTab(importState, onPickFile)
            else -> SocksTab(vm, close)
        }
    }
}

@Composable
private fun PasteTab(vm: MainViewModel, importState: ImportState) {
    var text by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme
    val isUrl = text.trim().startsWith("http://", true) || text.trim().startsWith("https://", true)
    val loading = importState is ImportState.Loading

    Text(
        if (isUrl) "Ссылка на подписку" else "Ссылка, подписка или конфиг",
        style = MaterialTheme.typography.titleSmall,
        color = cs.onSurface,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "vless://, vmess://, socks5://, base64-подписка, JSON Xray, AmneziaWG или https:// на подписку",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurface.copy(alpha = 0.55f),
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        maxLines = 9,
        placeholder = { Text("Вставь сюда ссылку или конфиг") },
        shape = RoundedCornerShape(18.dp),
    )
    ImportStatus(importState)
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
            onClick = { clipboard.getText()?.text?.let { text = it } },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Link, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Вставить")
        }
        Button(
            onClick = { vm.importText(text) },
            modifier = Modifier.weight(1f),
            enabled = text.isNotBlank() && !loading,
            shape = RoundedCornerShape(14.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = cs.onPrimary)
            } else {
                Text(if (isUrl) "Загрузить" else "Добавить")
            }
        }
    }
}

@Composable
private fun FileTab(importState: ImportState, onPickFile: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(cs.primary.copy(alpha = 0.08f))
                .border(1.5.dp, cs.primary.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                .clickable(onClick = onPickFile),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(60.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.UploadFile, null, Modifier.size(30.dp), tint = cs.primary)
                }
                Spacer(Modifier.height(12.dp))
                Text("Выбрать файл", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                Text(
                    "Конфиг, ссылки или подписка",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = 0.55f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Поддерживаются .conf, .json, .txt и любые текстовые файлы с vless/vmess/socks ссылками",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface.copy(alpha = 0.55f),
            textAlign = TextAlign.Center,
        )
        ImportStatus(importState)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onPickFile, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Filled.UploadFile, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Открыть файл")
        }
    }
}

@Composable
private fun ImportStatus(state: ImportState) {
    val cs = MaterialTheme.colorScheme
    val visible = state is ImportState.Loading || state is ImportState.Done || state is ImportState.Failed
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(initialScale = 0.96f),
        exit = fadeOut() + scaleOut(targetScale = 0.96f),
    ) {
        val bg = when (state) {
            is ImportState.Done -> Color(0xFF4ADE80).copy(alpha = 0.12f)
            is ImportState.Failed -> cs.error.copy(alpha = 0.12f)
            else -> cs.primary.copy(alpha = 0.12f)
        }
        Row(
            Modifier
                .padding(top = 10.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is ImportState.Loading -> CircularProgressIndicator(
                    Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = cs.primary,
                )
                is ImportState.Done -> Icon(Icons.Filled.CheckCircle, null, Modifier.size(20.dp), tint = Color(0xFF4ADE80))
                is ImportState.Failed -> Icon(Icons.Filled.ErrorOutline, null, Modifier.size(20.dp), tint = cs.error)
                else -> Unit
            }
            Spacer(Modifier.width(10.dp))
            Text(
                when (state) {
                    is ImportState.Loading -> "Загрузка…"
                    is ImportState.Done -> (if (state.source.startsWith("обновление")) "Подписки обновлены" else "Добавлено профилей: ${state.added} (${state.source})") +
                        (if (state.note.isNotBlank()) "\n${state.note}" else "")
                    is ImportState.Failed -> state.message
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state is ImportState.Failed) cs.error else cs.onSurface,
            )
        }
    }
}

@Composable
private fun SocksTab(vm: MainViewModel, close: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("1080") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text("Название") }, singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                host, { host = it }, Modifier.weight(2f),
                label = { Text("Хост") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            )
            OutlinedTextField(
                port, { port = it.filter(Char::isDigit) }, Modifier.weight(1f),
                label = { Text("Порт") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        OutlinedTextField(
            user, { user = it }, Modifier.fillMaxWidth(),
            label = { Text("Логин (необязательно)") }, singleLine = true, shape = RoundedCornerShape(16.dp),
        )
        OutlinedTextField(
            pass, { pass = it }, Modifier.fillMaxWidth(),
            label = { Text("Пароль") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(
            onClick = {
                vm.addSocks(name, host.trim(), port.toIntOrNull() ?: 1080, user, pass)
                close()
            },
            Modifier.fillMaxWidth().padding(top = 4.dp),
            enabled = host.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
        ) { Text("Добавить") }
    }
}

private fun protocolColor(p: Profile): Color = when (p) {
    is VlessProfile -> Color(0xFF7FB0FF)
    is VmessProfile -> Color(0xFFB28CFF)
    is Socks5Profile -> Color(0xFF5BE7A9)
    is XrayProfile -> Color(0xFFFBBF24)
    is AwgProfile -> Color(0xFF4ADE80)
}

private fun protocolIcon(p: Profile): ImageVector = when (p) {
    is VlessProfile -> Icons.Filled.Shield
    is VmessProfile -> Icons.Filled.Bolt
    is Socks5Profile -> Icons.Filled.Language
    is XrayProfile -> Icons.Filled.Code
    is AwgProfile -> Icons.Filled.VpnKey
}

private fun profileDetail(p: Profile): String = when (p) {
    is VlessProfile -> "${p.host}:${p.port}"
    is VmessProfile -> "${p.host}:${p.port}"
    is Socks5Profile -> "${p.host}:${p.port}"
    is XrayProfile -> p.endpoint.ifBlank { "JSON" }
    is AwgProfile -> "AmneziaWG"
}
