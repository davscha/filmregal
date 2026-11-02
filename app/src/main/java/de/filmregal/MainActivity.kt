package de.filmregal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

/** Wird bei jeder Rückkehr zur App erhöht, damit Fortschrittsbalken aktuell sind. */
object UiRefresh { val tick = mutableIntStateOf(0) }

/** Merkt sich geladene Ordner, damit "Zurück" sofort geht. */
object ListCache { val map = HashMap<String, List<Entry>>() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = Deps.get(this)
        setContent {
            FilmregalTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { App(d) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        UiRefresh.tick.intValue++
    }
}

@Composable
fun FilmregalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF5AB0FF),
            onPrimary = Color(0xFF002A4D),
            secondaryContainer = Color(0xFF1F4470),
            onSecondaryContainer = Color.White,
            background = Color(0xFF0B1626),
            onBackground = Color(0xFFE6EEF8),
            surface = Color(0xFF102036),
            onSurface = Color(0xFFE6EEF8),
            surfaceVariant = Color(0xFF1A2E4A),
            onSurfaceVariant = Color(0xFFB8C7DA),
        ),
        content = content,
    )
}

fun Context.play(items: List<Item>, index: Int) {
    if (items.isEmpty()) return
    startActivity(
        Intent(this, PlayerActivity::class.java)
            .putStringArrayListExtra("paths", ArrayList(items.map { it.path }))
            .putStringArrayListExtra("titles", ArrayList(items.map { it.title }))
            .putExtra("index", index.coerceIn(0, items.lastIndex))
    )
}

// ---------------------------------------------------------------- Grundgerüst

@Composable
fun App(d: Deps) {
    var nas by remember { mutableStateOf(d.settings.nas) }
    var screen by rememberSaveable { mutableStateOf(if (nas.isComplete) "continue" else "settings") }
    var libs by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var libError by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(nas, reload) {
        if (!nas.isComplete) return@LaunchedEffect
        libError = null
        attempt { Nas.list(nas, nas.root).filter { it.isDir } }
            .onSuccess { libs = it }
            .onFailure { libError = it.message ?: it.javaClass.simpleName }
    }

    Row(Modifier.fillMaxSize()) {
        Sidebar(
            libs = libs,
            error = libError,
            screen = screen,
            onSelect = { screen = it },
            onReload = { ListCache.map.clear(); reload++ },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                screen.startsWith("lib:") -> key(screen) { Browser(d, nas, screen.removePrefix("lib:")) }
                screen == "playlists" -> PlaylistsScreen(d)
                screen == "settings" -> SettingsScreen(d) {
                    nas = d.settings.nas
                    ListCache.map.clear()
                    reload++
                    screen = "continue"
                }
                else -> ContinueScreen(d)
            }
        }
    }
}

@Composable
fun Sidebar(
    libs: List<Entry>,
    error: String?,
    screen: String,
    onSelect: (String) -> Unit,
    onReload: () -> Unit,
) {
    Column(
        Modifier
            .width(250.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Text(
            "Filmregal",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        NavItem("Weiterschauen", Icons.Filled.History, screen == "continue") { onSelect("continue") }

        SectionLabel("Bibliotheken")
        libs.forEach { lib ->
            val id = "lib:${lib.path}"
            NavItem(lib.name.replaceFirstChar { it.uppercase() }, Icons.Filled.Folder, screen == id) { onSelect(id) }
        }
        if (error != null) {
            Text(
                "Keine Verbindung zum NAS:\n$error",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
        TextButton(onClick = onReload, modifier = Modifier.padding(start = 12.dp)) {
            Icon(Icons.Filled.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Neu laden")
        }

        SectionLabel("Mehr")
        NavItem("Wiedergabelisten", Icons.AutoMirrored.Filled.PlaylistPlay, screen == "playlists") { onSelect("playlists") }
        NavItem("Einstellungen", Icons.Filled.Settings, screen == "settings") { onSelect("settings") }
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun NavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

@Composable
fun Header(title: String, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

@Composable
fun CenterMessage(text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

// ---------------------------------------------------------------- Ordner durchblättern

@Composable
fun Browser(d: Deps, nas: NasConfig, libPath: String) {
    val ctx = LocalContext.current
    var path by rememberSaveable { mutableStateOf(libPath) }
    var entries by remember(path) { mutableStateOf(ListCache.map[path]) }
    var error by remember(path) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var addTarget by remember { mutableStateOf<Entry?>(null) }

    fun goUp() { path = path.substringBeforeLast('/', libPath) }
    BackHandler(enabled = path != libPath) { goUp() }

    LaunchedEffect(path, retry) {
        attempt { Nas.list(nas, path) }
            .onSuccess { entries = it; ListCache.map[path] = it; error = null }
            .onFailure { if (entries == null) error = it.message ?: it.javaClass.simpleName }
    }

    val list = entries
    val videos = list.orEmpty().filter { !it.isDir }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (path != libPath) {
                IconButton(onClick = { goUp() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            }
            val crumbs = path.removePrefix(nas.root).trim('/').split('/')
                .joinToString("  ›  ") { it.replaceFirstChar { c -> c.uppercase() } }
            Text(
                crumbs,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (videos.isNotEmpty()) {
                FilledTonalButton(onClick = { ctx.play(videos.map { d.itemFor(it) }, 0) }) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Alle abspielen")
                }
            }
        }
        when {
            error != null -> CenterMessage("Ordner konnte nicht geladen werden:\n$error") {
                Button(onClick = { error = null; retry++ }) { Text("Nochmal versuchen") }
            }
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> CenterMessage("Hier liegen keine Videos.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(list, key = { it.path }) { e ->
                    Tile(
                        d, e,
                        onClick = {
                            if (e.isDir) path = e.path
                            else ctx.play(videos.map { d.itemFor(it) }, videos.indexOf(e))
                        },
                        onLongClick = { addTarget = e },
                    )
                }
            }
        }
    }

    addTarget?.let { t -> AddToPlaylistDialog(d, nas, t) { addTarget = null } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(d: Deps, e: Entry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val meta by produceState(initialValue = d.meta.peek(e) ?: d.meta.fallback(e), e.path) {
        value = d.meta.lookup(e)
    }
    val tick = UiRefresh.tick.intValue
    val progress = remember(tick, e.path) { if (e.isDir) 0f else d.store.progress(e.path) }
    val watched = remember(tick, e.path) { !e.isDir && d.store.isWatched(e.path) }

    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val poster = meta.poster
            if (poster != null) {
                AsyncImage(
                    model = TMDB_IMG + poster,
                    contentDescription = meta.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        if (e.isDir) Icons.Filled.Folder else Icons.Filled.Movie,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(meta.title, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (e.isDir && poster != null) {
                Badge(Icons.Filled.Folder, Modifier.align(Alignment.TopStart))
            }
            if (watched) {
                Badge(Icons.Filled.Check, Modifier.align(Alignment.TopEnd))
            }
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
                )
            }
        }
        Text(
            meta.title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        meta.year?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
    }
}

@Composable
fun Badge(icon: ImageVector, modifier: Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
    }
}

@Composable
fun AddToPlaylistDialog(d: Deps, nas: NasConfig, target: Entry, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lists = remember { d.store.playlists() }
    var newName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun add(name: String) {
        if (busy || name.isBlank()) return
        busy = true
        scope.launch {
            val items = if (target.isDir) {
                attempt { Nas.list(nas, target.path) }.getOrDefault(emptyList())
                    .filter { !it.isDir }.map { d.itemFor(it) }
            } else {
                listOf(d.itemFor(target))
            }
            d.store.addToPlaylist(name, items)
            Toast.makeText(ctx, "${items.size} Titel zu „$name“ hinzugefügt", Toast.LENGTH_SHORT).show()
            onDone()
        }
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (target.isDir) "Ganzen Ordner zur Wiedergabeliste" else "Zur Wiedergabeliste") },
        text = {
            Column {
                if (lists.isNotEmpty()) {
                    Text("Bestehende Liste wählen:", style = MaterialTheme.typography.labelLarge)
                    lists.forEach { pl ->
                        TextButton(onClick = { add(pl.name) }, enabled = !busy) {
                            Text("${pl.name}  (${pl.items.size})")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Oder neue Liste") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { add(newName.trim()) }, enabled = newName.isNotBlank() && !busy) {
                Text("Neue Liste anlegen")
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Weiterschauen

@Composable
fun ContinueScreen(d: Deps) {
    val ctx = LocalContext.current
    val tick = UiRefresh.tick.intValue
    var changed by remember { mutableIntStateOf(0) }
    val recents = remember(tick, changed) { d.store.recents() }

    Column(Modifier.fillMaxSize()) {
        Header("Weiterschauen")
        if (recents.isEmpty()) {
            CenterMessage("Noch nichts angefangen.\nWähl links eine Bibliothek aus.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                items(recents, key = { it.path }) { item ->
                    Tile(
                        d, Entry(item.path.substringAfterLast('/'), item.path, false),
                        onClick = { ctx.play(listOf(item), 0) },
                        onLongClick = {
                            d.store.removeRecent(item.path)
                            changed++
                            Toast.makeText(ctx, "Aus „Weiterschauen“ entfernt", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Wiedergabelisten

@Composable
fun PlaylistsScreen(d: Deps) {
    val ctx = LocalContext.current
    var lists by remember { mutableStateOf(d.store.playlists()) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val current = lists.firstOrNull { it.name == open }

    BackHandler(enabled = current != null) { open = null }

    if (current == null) {
        Column(Modifier.fillMaxSize()) {
            Header("Wiedergabelisten") {
                FilledTonalButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Neue Liste")
                }
            }
            if (lists.isEmpty()) {
                CenterMessage("Noch keine Listen.\nTipp in einer Bibliothek lange auf einen Film oder Ordner.")
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lists, key = { it.name }) { pl ->
                    Card(Modifier.fillMaxWidth().widthIn(max = 800.dp).clickable { open = pl.name }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(pl.name, style = MaterialTheme.typography.titleMedium)
                                Text("${pl.items.size} Titel", style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { ctx.play(pl.items, 0) }, enabled = pl.items.isNotEmpty()) {
                                Icon(Icons.Filled.PlayArrow, "Abspielen")
                            }
                            IconButton(onClick = { d.store.deletePlaylist(pl.name); lists = d.store.playlists() }) {
                                Icon(Icons.Filled.Delete, "Löschen")
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { open = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                Text(
                    current.name,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                FilledTonalButton(onClick = { ctx.play(current.items, 0) }, enabled = current.items.isNotEmpty()) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Alle abspielen")
                }
            }
            if (current.items.isEmpty()) {
                CenterMessage("Die Liste ist leer.\nTipp in einer Bibliothek lange auf einen Film, um ihn hinzuzufügen.")
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 24.dp)) {
                itemsIndexed(current.items) { i, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { ctx.play(current.items, i) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${i + 1}.", Modifier.width(40.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(item.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(
                            onClick = { d.store.moveInPlaylist(current.name, i, i - 1); lists = d.store.playlists() },
                            enabled = i > 0,
                        ) { Icon(Icons.Filled.KeyboardArrowUp, "Nach oben") }
                        IconButton(onClick = { d.store.removeFromPlaylist(current.name, i); lists = d.store.playlists() }) {
                            Icon(Icons.Filled.Close, "Entfernen")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Neue Wiedergabeliste") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(
                    onClick = { d.store.createPlaylist(name.trim()); lists = d.store.playlists(); creating = false },
                    enabled = name.isNotBlank(),
                ) { Text("Anlegen") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Abbrechen") } },
        )
    }
}

// ---------------------------------------------------------------- Einstellungen

@Composable
fun SettingsScreen(d: Deps, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val saved = remember { d.settings.nas }
    var host by remember { mutableStateOf(saved.host.ifEmpty { "fritz.box" }) }
    var share by remember { mutableStateOf(saved.share.ifEmpty { "FRITZ.NAS" }) }
    var root by remember { mutableStateOf(saved.root) }
    var user by remember { mutableStateOf(saved.user) }
    var password by remember { mutableStateOf(saved.password) }
    var showPassword by remember { mutableStateOf(false) }
    var tmdb by remember { mutableStateOf(d.settings.tmdbKey) }
    var status by remember { mutableStateOf<String?>(null) }

    fun current() = NasConfig(host.trim(), share.trim().trim('/'), root.trim().trim('/'), user.trim(), password)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Einstellungen", style = MaterialTheme.typography.headlineSmall)

            Text("NAS (FritzBox)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                host, { host = it }, Modifier.fillMaxWidth(),
                label = { Text("Adresse") },
                supportingText = { Text("z. B. fritz.box oder 192.168.178.1") },
                singleLine = true,
            )
            OutlinedTextField(
                share, { share = it }, Modifier.fillMaxWidth(),
                label = { Text("Freigabe") },
                supportingText = { Text("Bei der FritzBox meist FRITZ.NAS") },
                singleLine = true,
            )
            OutlinedTextField(
                root, { root = it }, Modifier.fillMaxWidth(),
                label = { Text("Startordner") },
                supportingText = { Text("Ordner, in dem disney, pixar, vault … liegen, z. B. Medien/Filme. Leer = oberste Ebene.") },
                singleLine = true,
            )
            OutlinedTextField(
                user, { user = it }, Modifier.fillMaxWidth(),
                label = { Text("Benutzername") },
                singleLine = true,
            )
            OutlinedTextField(
                password, { password = it }, Modifier.fillMaxWidth(),
                label = { Text("Passwort") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            if (showPassword) "Passwort verbergen" else "Passwort anzeigen",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )

            Text("Poster & Beschreibungen", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                tmdb, { tmdb = it }, Modifier.fillMaxWidth(),
                label = { Text("TMDB-API-Schlüssel") },
                supportingText = { Text("Kostenlos auf themoviedb.org → Einstellungen → API. Ohne Schlüssel zeigt die App nur Dateinamen.") },
                singleLine = true,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = {
                    d.settings.nas = current()
                    d.settings.tmdbKey = tmdb
                    onSaved()
                }) { Text("Speichern") }
                OutlinedButton(onClick = {
                    status = "Verbinde …"
                    val cfg = current()
                    scope.launch {
                        Nas.diagnose(cfg) { text -> scope.launch { status = text } }
                    }
                }) { Text("Verbindung testen") }
                TextButton(onClick = { d.meta.clear(); status = "Poster werden beim nächsten Öffnen neu geladen." }) {
                    Text("Poster neu laden")
                }
            }
            status?.let { Text(it) }
            val ctx = LocalContext.current
            val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
