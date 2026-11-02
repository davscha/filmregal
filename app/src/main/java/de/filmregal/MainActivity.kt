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
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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

fun Context.play(items: List<Item>, index: Int, fromStart: Boolean = false) {
    if (items.isEmpty()) return
    startActivity(
        Intent(this, PlayerActivity::class.java)
            .putStringArrayListExtra("paths", ArrayList(items.map { it.path }))
            .putStringArrayListExtra("titles", ArrayList(items.map { it.title }))
            .putStringArrayListExtra("backdrops", ArrayList(items.map { it.backdrop.orEmpty() }))
            .putStringArrayListExtra("posters", ArrayList(items.map { it.poster.orEmpty() }))
            .putExtra("index", index.coerceIn(0, items.lastIndex))
            .putExtra("fromStart", fromStart)
    )
}

/** Welche Infoseite gerade offen ist (liegt über allem, wie bei Nova). */
data class DetailTarget(val video: Video, val queue: List<Video>)

object DetailNav {
    var current: DetailTarget? by mutableStateOf(null)
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
            .onSuccess {
                libs = it
                d.settings.smbVariant = Nas.working
            }
            .onFailure { libError = it.message ?: it.javaClass.simpleName }
    }

    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize()) {
        Sidebar(
            libs = libs,
            error = libError,
            screen = screen,
            onSelect = { screen = it },
            onReload = { reload++ },
        )
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                screen.startsWith("lib:") -> key(screen) { LibraryScreen(d, nas, screen.removePrefix("lib:")) }
                screen == "playlists" -> PlaylistsScreen(d)
                screen == "settings" -> SettingsScreen(d) {
                    nas = d.settings.nas
                    reload++
                    screen = "continue"
                }
                else -> ContinueScreen(d)
            }
        }
    }
    DetailNav.current?.let { t -> key(t.video.path) { DetailScreen(d, t) } }
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

// ---------------------------------------------------------------- Bibliothek: Filme | Serien

@Composable
fun LibraryScreen(d: Deps, nas: NasConfig, root: String) {
    var data by remember { mutableStateOf(d.libraries.load(root)) }
    var scanTick by remember { mutableIntStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var scanInfo by remember { mutableStateOf<String?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var mode by rememberSaveable { mutableStateOf("") }
    var openShow by rememberSaveable { mutableStateOf<String?>(null) }
    var openSeason by rememberSaveable { mutableStateOf<Int?>(null) }
    var addTarget by remember { mutableStateOf<Pair<String, List<Video>>?>(null) }

    // Beim ersten Öffnen oder wenn der letzte Scan älter als 6 Stunden ist: im Hintergrund neu durchsuchen
    LaunchedEffect(scanTick) {
        val current = data
        val stale = current == null || System.currentTimeMillis() - current.scannedAt > 6 * 3_600_000L
        if (scanTick == 0 && !stale) return@LaunchedEffect
        scanning = true
        scanError = null
        scanInfo = null
        attempt { d.libraries.scan(nas, root) { f, v -> scanInfo = "$f Ordner durchsucht, $v Videos gefunden" } }
            .onSuccess { data = it }
            .onFailure { scanError = it.message ?: it.javaClass.simpleName }
        scanning = false
    }

    BackHandler(enabled = openShow != null) {
        if (openSeason != null) openSeason = null else openShow = null
    }

    val lib = data
    val effectiveMode = mode.ifEmpty {
        if (lib != null && lib.movies.isEmpty() && lib.shows.isNotEmpty()) "serien" else "filme"
    }
    val show = lib?.shows?.firstOrNull { it.name == openShow }
    val season = openSeason

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (show != null) {
                IconButton(onClick = { if (openSeason != null) openSeason = null else openShow = null }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                }
            }
            val title = when {
                show != null && season != null -> "${show.name}  ›  Staffel $season"
                show != null -> show.name
                else -> root.substringAfterLast('/').replaceFirstChar { it.uppercase() }
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (show == null && lib != null) {
                ModeButton("Filme (${lib.movies.size})", effectiveMode == "filme") { mode = "filme" }
                Spacer(Modifier.width(8.dp))
                ModeButton("Serien (${lib.shows.size})", effectiveMode == "serien") { mode = "serien" }
                Spacer(Modifier.width(8.dp))
            }
            IconButton(onClick = { scanTick++ }, enabled = !scanning) {
                Icon(Icons.Filled.Refresh, "Neu durchsuchen")
            }
        }
        if (scanning) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                scanInfo ?: "Durchsuche Bibliothek …",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        scanError?.let {
            Text(
                "Durchsuchen fehlgeschlagen: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        when {
            lib == null && scanning -> CenterMessage(
                "Die Bibliothek wird zum ersten Mal durchsucht.\nBei vielen Ordnern kann das eine Minute dauern."
            )
            lib == null -> CenterMessage("Noch nicht durchsucht.") {
                Button(onClick = { scanTick++ }) { Text("Jetzt durchsuchen") }
            }
            show != null && season != null -> EpisodeList(d, show, season) { e ->
                addTarget = "Folge ${e.code}" to listOf(e)
            }
            show != null -> SeasonGrid(
                d, show,
                onOpen = { openSeason = it },
                onLong = { n -> addTarget = "Staffel $n" to show.seasons[n].orEmpty() },
            )
            effectiveMode == "serien" ->
                if (lib.shows.isEmpty()) {
                    CenterMessage("Keine Serien gefunden.\nFolgen werden an S01E01 im Dateinamen erkannt.")
                } else {
                    ShowGrid(
                        d, lib.shows,
                        onOpen = { openShow = it.name; openSeason = null },
                        onLong = { s -> addTarget = "Ganze Serie „${s.name}“" to s.episodes },
                    )
                }
            else ->
                if (lib.movies.isEmpty()) CenterMessage("Keine Filme gefunden.")
                else MovieGrid(d, lib.movies) { m -> addTarget = "Film" to listOf(m) }
        }
    }

    addTarget?.let { (label, videos) -> AddToPlaylistDialog(d, label, videos) { addTarget = null } }
}

@Composable
fun ModeButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
fun PosterGrid(content: LazyGridScope.() -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        content = content,
    )
}

@Composable
fun progressOf(d: Deps, path: String): Pair<Float, Boolean> {
    val tick = UiRefresh.tick.intValue
    return remember(tick, path) { d.store.progress(path) to d.store.isWatched(path) }
}

@Composable
fun MovieGrid(d: Deps, movies: List<Movie>, onLong: (Movie) -> Unit) {
    val ctx = LocalContext.current
    PosterGrid {
        items(movies, key = { it.path }) { m ->
            MovieTile(d, m, onClick = { DetailNav.current = DetailTarget(m, listOf(m)) }, onLong = { onLong(m) })
        }
    }
}

@Composable
fun MovieTile(d: Deps, m: Movie, onClick: () -> Unit, onLong: () -> Unit) {
    val version = MetaVersion.tick.intValue
    val meta by produceState(d.meta.peekMovie(m) ?: Meta(m.title, null, null, m.year), m.path, version) {
        value = d.meta.movie(m)
    }
    val (progress, watched) = progressOf(d, m.path)
    PosterTile(meta.title, meta.year, meta.poster?.let { TMDB_IMG + it }, Icons.Filled.Movie, progress, watched, onClick, onLong)
}

@Composable
fun showMetaState(d: Deps, name: String, year: String?): androidx.compose.runtime.State<Meta> {
    val version = MetaVersion.tick.intValue
    return produceState(d.meta.peekShow(name, year) ?: Meta(name, null, null, year), name, version) {
        value = d.meta.show(name, year)
    }
}

@Composable
fun ShowGrid(d: Deps, shows: List<Show>, onOpen: (Show) -> Unit, onLong: (Show) -> Unit) {
    PosterGrid {
        items(shows, key = { it.name }) { s ->
            val meta by showMetaState(d, s.name, s.year)
            val n = s.seasons.size
            PosterTile(
                meta.title,
                "$n ${if (n == 1) "Staffel" else "Staffeln"} · ${s.episodes.size} Folgen",
                meta.poster?.let { TMDB_IMG + it },
                Icons.Filled.Tv, 0f, false,
                onClick = { onOpen(s) },
                onLong = { onLong(s) },
            )
        }
    }
}

@Composable
fun SeasonGrid(d: Deps, s: Show, onOpen: (Int) -> Unit, onLong: (Int) -> Unit) {
    val showMeta by showMetaState(d, s.name, s.year)
    val tick = UiRefresh.tick.intValue
    Column(Modifier.fillMaxSize()) {
        showMeta.overview?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp).widthIn(max = 900.dp),
            )
        }
        PosterGrid {
            items(s.seasons.entries.toList(), key = { it.key }) { (n, eps) ->
                val seasonMeta by produceState(d.meta.peekSeason(showMeta.id, n), showMeta.id, n) {
                    value = d.meta.season(showMeta.id, n)
                }
                val watched = remember(tick, eps) { eps.all { d.store.isWatched(it.path) } }
                PosterTile(
                    "Staffel $n",
                    "${eps.size} ${if (eps.size == 1) "Folge" else "Folgen"}",
                    (seasonMeta?.poster ?: showMeta.poster)?.let { TMDB_IMG + it },
                    Icons.Filled.Tv, 0f, watched,
                    onClick = { onOpen(n) },
                    onLong = { onLong(n) },
                )
            }
        }
    }
}

@Composable
fun EpisodeList(d: Deps, s: Show, season: Int, onLong: (Episode) -> Unit) {
    val ctx = LocalContext.current
    val eps = s.seasons[season].orEmpty()
    val showMeta by showMetaState(d, s.name, s.year)
    val seasonMeta by produceState(d.meta.peekSeason(showMeta.id, season), showMeta.id, season) {
        value = d.meta.season(showMeta.id, season)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
            FilledTonalButton(onClick = { ctx.play(eps.map { d.itemFor(it) }, 0) }) {
                Icon(Icons.Filled.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text("Staffel abspielen")
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(eps, key = { _, e -> e.path }) { i, e ->
                val (progress, watched) = progressOf(d, e.path)
                EpisodeRow(
                    e, seasonMeta?.episodes?.get(e.number), progress, watched,
                    onClick = { DetailNav.current = DetailTarget(e, eps) },
                    onLong = { onLong(e) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeRow(
    e: Episode,
    em: EpisodeMeta?,
    progress: Float,
    watched: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .width(200.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val still = em?.still
            if (still != null) {
                AsyncImage(
                    model = TMDB_STILL + still,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.PlayArrow, null,
                    Modifier.align(Alignment.Center).size(40.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (watched) Badge(Icons.Filled.Check, Modifier.align(Alignment.TopEnd))
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            val name = em?.name?.takeIf { it.isNotBlank() } ?: "Folge ${e.number}"
            Text("${e.number} – $name", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(e.code, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            em?.overview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterTile(
    title: String,
    subtitle: String?,
    posterUrl: String?,
    placeholder: ImageVector,
    progress: Float,
    watched: Boolean,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (posterUrl != null) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(
                    Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(placeholder, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text(title, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (watched) Badge(Icons.Filled.Check, Modifier.align(Alignment.TopEnd))
            if (progress > 0f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        subtitle?.let {
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
fun AddToPlaylistDialog(d: Deps, label: String, videos: List<Video>, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lists = remember { d.store.playlists() }
    var newName by remember { mutableStateOf("") }

    fun add(name: String) {
        if (name.isBlank()) return
        val items = videos.map { d.itemFor(it) }
        d.store.addToPlaylist(name, items)
        Toast.makeText(ctx, "${items.size} Titel zu „$name“ hinzugefügt", Toast.LENGTH_SHORT).show()
        onDone()
    }

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("$label zur Wiedergabeliste") },
        text = {
            Column {
                if (lists.isNotEmpty()) {
                    Text("Bestehende Liste wählen:", style = MaterialTheme.typography.labelLarge)
                    lists.forEach { pl ->
                        TextButton(onClick = { add(pl.name) }) { Text("${pl.name}  (${pl.items.size})") }
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
            TextButton(onClick = { add(newName.trim()) }, enabled = newName.isNotBlank()) { Text("Neue Liste anlegen") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Abbrechen") } },
    )
}

// ---------------------------------------------------------------- Infoseite

data class DetailInfo(
    val title: String,
    val subtitle: String?,
    val overview: String?,
    val poster: String?,
    val backdrop: String?,
    val still: String? = null,
    val hasInfo: Boolean = true,
)

private fun ratingText(r: Double?) = r?.let { "★ " + "%.1f".format(it) }

fun peekDetail(d: Deps, v: Video): DetailInfo = when (v) {
    is Movie -> {
        val m = d.meta.peekMovie(v) ?: Meta(v.title, null, null, v.year)
        DetailInfo(m.title, listOfNotNull(m.year, ratingText(m.rating)).joinToString("  ·  "), m.overview, m.poster, m.backdrop, hasInfo = m.id != 0)
    }
    is Episode -> {
        val s = d.meta.peekShow(v.show, v.showYear) ?: Meta(v.show, null, null, v.showYear)
        val season = d.meta.peekSeason(s.id, v.season)
        episodeInfo(v, s, season)
    }
}

suspend fun loadDetail(d: Deps, v: Video): DetailInfo = when (v) {
    is Movie -> {
        val m = d.meta.movie(v)
        DetailInfo(m.title, listOfNotNull(m.year, ratingText(m.rating)).joinToString("  ·  "), m.overview, m.poster, m.backdrop, hasInfo = m.id != 0)
    }
    is Episode -> {
        val s = d.meta.show(v.show, v.showYear)
        episodeInfo(v, s, d.meta.season(s.id, v.season))
    }
}

private fun episodeInfo(v: Episode, s: Meta, season: SeasonMeta?): DetailInfo {
    val em = season?.episodes?.get(v.number)
    val name = em?.name?.takeIf { it.isNotBlank() }
    return DetailInfo(
        title = s.title,
        subtitle = listOfNotNull(v.code, name).joinToString("  ·  "),
        overview = em?.overview ?: s.overview,
        poster = if (s.customPoster) s.poster else season?.poster ?: s.poster,
        backdrop = s.backdrop,
        still = em?.still,
        hasInfo = s.id != 0,
    )
}

@Composable
fun DetailScreen(d: Deps, target: DetailTarget) {
    val ctx = LocalContext.current
    val v = target.video
    val version = MetaVersion.tick.intValue
    val info by produceState(peekDetail(d, v), v.path, version) { value = loadDetail(d, v) }
    var editor by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val tick = UiRefresh.tick.intValue
    var changed by remember { mutableIntStateOf(0) }
    val position = remember(tick, changed) { d.store.position(v.path) }
    val (progress, watched) = remember(tick, changed) { d.store.progress(v.path) to d.store.isWatched(v.path) }
    val index = target.queue.indexOfFirst { it.path == v.path }.coerceAtLeast(0)
    val next = target.queue.getOrNull(index + 1)

    BackHandler(enabled = editor == null) { DetailNav.current = null }

    fun start(fromStart: Boolean) {
        ctx.play(target.queue.map { d.itemFor(it) }, index, fromStart)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
    ) {
        info.backdrop?.let {
            AsyncImage(
                model = TMDB_BACKDROP + it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0x660B1626), Color(0xF20B1626)))
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xE60B1626), Color(0x330B1626)))
            )
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 56.dp, end = 56.dp, top = 72.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Box(
                Modifier
                    .width(240.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val poster = info.poster
                if (poster != null) {
                    AsyncImage(
                        model = TMDB_IMG + poster,
                        contentDescription = info.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        if (v is Episode) Icons.Filled.Tv else Icons.Filled.Movie, null,
                        Modifier.align(Alignment.Center).size(64.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp),
                    )
                }
            }

            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                info.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(28.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (position > 30_000) {
                        Button(onClick = { start(fromStart = false) }) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Fortsetzen ab ${fmtTime(position)}")
                        }
                        OutlinedButton(onClick = { start(fromStart = true) }) {
                            Icon(Icons.Filled.Replay, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Von Anfang")
                        }
                    } else {
                        Button(onClick = { start(fromStart = true) }) {
                            Icon(Icons.Filled.PlayArrow, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Abspielen")
                        }
                    }
                    if (next != null) {
                        OutlinedButton(onClick = { DetailNav.current = DetailTarget(next, target.queue) }) {
                            Text("Nächste Folge")
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Filled.SkipNext, null)
                        }
                    }
                }

                if (!info.hasInfo) {
                    FilledTonalButton(onClick = { editor = "search" }, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(Icons.Filled.Search, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Info suchen")
                    }
                }

                val still = info.still
                if (still != null) {
                    AsyncImage(
                        model = TMDB_STILL + still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(top = 24.dp)
                            .width(360.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                }

                info.overview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 24.dp).widthIn(max = 900.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { d.store.setWatched(v.path, !watched); changed++ }) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (watched) "Als ungesehen markieren" else "Als gesehen markieren")
                }
                Text(
                    v.path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    v.path.substringBeforeLast('/'),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.4f),
                )
            }
        }

        IconButton(
            onClick = { DetailNav.current = null },
            modifier = Modifier.align(Alignment.TopStart).padding(16.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
        }

        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, "Mehr", tint = Color.White)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val what = if (v is Episode) "Serie" else "Film"
                DropdownMenuItem(text = { Text("Info suchen / $what zuordnen …") }, onClick = { menuOpen = false; editor = "search" })
                if (info.hasInfo) {
                    DropdownMenuItem(text = { Text("Anderes Poster auswählen …") }, onClick = { menuOpen = false; editor = "poster" })
                    DropdownMenuItem(text = { Text("Anderes Hintergrundbild …") }, onClick = { menuOpen = false; editor = "backdrop" })
                    DropdownMenuItem(text = { Text("Info entfernen") }, onClick = { menuOpen = false; d.meta.removeInfo(v) })
                }
                if (d.meta.hasOverride(v)) {
                    DropdownMenuItem(text = { Text("Automatische Erkennung wiederherstellen") }, onClick = { menuOpen = false; d.meta.resetOverride(v) })
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (watched) "Als ungesehen markieren" else "Als gesehen markieren") },
                    onClick = { menuOpen = false; d.store.setWatched(v.path, !watched); changed++ },
                )
            }
        }

        when (editor) {
            "search" -> MetaSearchScreen(d, v) { editor = null }
            "poster", "backdrop" -> ImagePickerScreen(d, v, editor!!) { editor = null }
        }
    }
}

// ---------------------------------------------------------------- Editor

private fun kindOf(v: Video) = if (v is Episode) "tv" else "movie"

private suspend fun currentMeta(d: Deps, v: Video): Meta = when (v) {
    is Movie -> d.meta.movie(v)
    is Episode -> d.meta.show(v.show, v.showYear)
}

@Composable
fun EditorFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    BackHandler { onClose() }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickable(enabled = false) {}
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(start = 8.dp))
        }
        content()
    }
}

@Composable
fun MetaSearchScreen(d: Deps, v: Video, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val kind = kindOf(v)
    var query by remember {
        mutableStateOf(
            when (v) {
                is Movie -> d.meta.peekMovie(v)?.takeIf { it.id != 0 }?.title ?: v.title
                is Episode -> v.show
            }
        )
    }
    var year by remember {
        mutableStateOf(
            when (v) {
                is Movie -> v.year.orEmpty()
                is Episode -> v.showYear.orEmpty()
            }
        )
    }
    var results by remember { mutableStateOf<List<Candidate>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search() {
        loading = true
        error = null
        scope.launch {
            attempt { d.meta.search(kind, query.trim(), year.trim()) }
                .onSuccess { results = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }
    LaunchedEffect(Unit) { search() }

    EditorFrame(if (kind == "tv") "Serie zuordnen" else "Film zuordnen", onClose) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Titel") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = year,
                onValueChange = { year = it.filter(Char::isDigit).take(4) },
                label = { Text("Jahr") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.width(110.dp),
            )
            Button(onClick = { search() }) { Text("Suchen") }
        }
        Text(
            "Datei: " + v.path.substringAfterLast('/'),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        val list = results
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> CenterMessage("Suche fehlgeschlagen: $error")
            list == null -> {}
            list.isEmpty() -> CenterMessage("Nichts gefunden.\nProbier einen kürzeren Titel oder lass das Jahr weg.")
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list) { c ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                d.meta.assign(v, c)
                                onClose()
                            }
                            .padding(10.dp),
                    ) {
                        Box(
                            Modifier
                                .width(80.dp)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            c.poster?.let {
                                AsyncImage(
                                    model = TMDB_IMG + it,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp)) {
                            Text(c.title, style = MaterialTheme.typography.titleMedium)
                            c.year?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            c.overview?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ImagePickerScreen(d: Deps, v: Video, which: String, onClose: () -> Unit) {
    val poster = which == "poster"
    var meta by remember { mutableStateOf<Meta?>(null) }
    var images by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        attempt {
            val m = currentMeta(d, v)
            meta = m
            d.meta.images(kindOf(v), m.id, if (poster) "posters" else "backdrops")
        }.onSuccess { images = it }.onFailure { error = it.message ?: it.javaClass.simpleName }
    }

    EditorFrame(if (poster) "Poster auswählen" else "Hintergrundbild auswählen", onClose) {
        val list = images
        when {
            error != null -> CenterMessage("Bilder konnten nicht geladen werden: $error")
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> CenterMessage("Keine Bilder gefunden.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(if (poster) 150.dp else 320.dp),
                contentPadding = PaddingValues(24.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(list) { path ->
                    AsyncImage(
                        model = (if (poster) TMDB_IMG else TMDB_STILL) + path,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (poster) 2f / 3f else 16f / 9f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                meta?.let { d.meta.setImage(v, it, which, path) }
                                onClose()
                            },
                    )
                }
            }
        }
    }
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
            PosterGrid {
                items(recents, key = { it.path }) { item ->
                    val remove = {
                        d.store.removeRecent(item.path)
                        changed++
                        Toast.makeText(ctx, "Aus „Weiterschauen“ entfernt", Toast.LENGTH_SHORT).show()
                    }
                    when (val v = classify(item.path)) {
                        is Movie -> MovieTile(d, v, onClick = { DetailNav.current = DetailTarget(v, listOf(v)) }, onLong = remove)
                        is Episode -> {
                            val meta by showMetaState(d, v.show, v.showYear)
                            val (progress, watched) = progressOf(d, v.path)
                            PosterTile(
                                item.title, null, meta.poster?.let { TMDB_IMG + it },
                                Icons.Filled.Tv, progress, watched,
                                onClick = { DetailNav.current = DetailTarget(v, d.libraries.episodesOf(v)) },
                                onLong = remove,
                            )
                        }
                    }
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
    var share by remember { mutableStateOf(saved.share) }
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
                supportingText = { Text("Leer lassen, wenn der Startordner mit dem Freigabenamen beginnt (z. B. Freigabe/USB-Platte/…)") },
                singleLine = true,
            )
            OutlinedTextField(
                root, { root = it }, Modifier.fillMaxWidth(),
                label = { Text("Startordner") },
                supportingText = { Text("Ordner, in dem deine Bibliotheken liegen, z. B. Medien. Leer = oberste Ebene.") },
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
                    d.settings.smbVariant = Nas.working
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
                TextButton(onClick = {
                    d.meta.clear()
                    d.libraries.clear()
                    status = "Poster und Bibliotheken werden beim nächsten Öffnen neu geladen."
                }) {
                    Text("Alles neu laden")
                }
            }
            status?.let { Text(it) }
            val ctx = LocalContext.current
            val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            Text("Version $version", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
