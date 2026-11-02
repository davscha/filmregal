package de.filmregal

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class PlayerActivity : ComponentActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private lateinit var audio: AudioManager
    private var items: List<Item> = emptyList()
    private val d by lazy { Deps.get(this) }
    private var started = false
    private var skipResumeOnce = false

    private var index by mutableIntStateOf(0)
    private var playing by mutableStateOf(false)
    private var time by mutableLongStateOf(0L)
    private var length by mutableLongStateOf(0L)
    private var buffering by mutableFloatStateOf(100f)
    private var hasVideo by mutableStateOf(false)
    private var fill by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayListExtra("paths").orEmpty()
        val titles = intent.getStringArrayListExtra("titles").orEmpty()
        val backdrops = intent.getStringArrayListExtra("backdrops").orEmpty()
        val posters = intent.getStringArrayListExtra("posters").orEmpty()
        items = paths.mapIndexed { i, p ->
            Item(
                p,
                titles.getOrElse(i) { p.substringAfterLast('/') },
                backdrops.getOrNull(i)?.ifEmpty { null },
                posters.getOrNull(i)?.ifEmpty { null },
            )
        }
        if (items.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0)).coerceIn(0, items.lastIndex)
        skipResumeOnce = savedInstanceState == null && intent.getBooleanExtra("fromStart", false)
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        libVlc = LibVLC(this, arrayListOf("--network-caching=2000", "--audio-time-stretch"))
        player = MediaPlayer(libVlc)
        player.setEventListener { ev ->
            when (ev.type) {
                MediaPlayer.Event.Playing -> {
                    playing = true
                    error = null
                }
                MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.TimeChanged -> {
                    time = ev.timeChanged
                    if (time > 500) hasVideo = true
                }
                MediaPlayer.Event.LengthChanged -> length = ev.lengthChanged
                MediaPlayer.Event.Buffering -> buffering = ev.buffering
                MediaPlayer.Event.Vout -> if (ev.voutCount > 0) hasVideo = true
                MediaPlayer.Event.EndReached -> videoLayout.post { onEnded() }
                MediaPlayer.Event.EncounteredError -> error = "Die Datei konnte nicht abgespielt werden."
            }
        }

        videoLayout = VLCVideoLayout(this)
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        root.addView(videoLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(
            ComposeView(this).apply {
                setContent {
                    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF5AB0FF))) { PlayerUi() }
                }
            },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
        setContentView(root)

        // Alle 10 Sekunden Position sichern
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                if (playing) savePosition()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("index", index)
    }

    override fun onStart() {
        super.onStart()
        if (!::player.isInitialized) return
        player.attachViews(videoLayout, null, true, false)
        if (!started) {
            started = true
            load(index)
        }
    }

    override fun onStop() {
        super.onStop()
        if (!::player.isInitialized) return
        savePosition()
        player.pause()
        player.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!::player.isInitialized) return
        player.release()
        libVlc.release()
    }

    private fun load(i: Int) {
        index = i
        time = 0
        length = 0
        error = null
        hasVideo = false
        buffering = 0f
        val item = items[i]
        val media = Media(libVlc, Uri.parse(StreamServer.url(d.settings.nas, item.path)))
        media.setHWDecoderEnabled(true, false) // AV1 über den Snapdragon-Decoder
        val start = if (skipResumeOnce) 0L else d.store.position(item.path)
        skipResumeOnce = false
        if (start > 30_000) media.addOption(":start-time=${start / 1000}")
        player.setMedia(media)
        media.release()
        player.play()
        attachSubtitle(item.path)
    }

    /** Liegt "Film.srt" neben "Film.mp4", wird sie automatisch eingeblendet. */
    private fun attachSubtitle(videoPath: String) {
        val srtPath = videoPath.substringBeforeLast('.') + ".srt"
        val cfg = d.settings.nas
        lifecycleScope.launch {
            val exists = withContext(Dispatchers.IO) {
                try {
                    Nas.openFile(cfg, srtPath).first.close()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (exists && ::player.isInitialized) {
                player.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(StreamServer.url(cfg, srtPath)), true)
            }
        }
    }

    private fun savePosition() {
        if (::player.isInitialized && player.length > 0) {
            d.store.savePosition(items[index], player.time, player.length)
        }
    }

    private fun onEnded() {
        val len = maxOf(length, 1L)
        d.store.savePosition(items[index], len, len)
        if (index < items.lastIndex) load(index + 1) else finish()
    }

    private fun seekBy(ms: Long) {
        val t = (player.time + ms).coerceIn(0L, maxOf(0L, player.length - 1000))
        player.setTime(t)
        time = t
    }

    private fun toggleFill() {
        fill = !fill
        player.setVideoScale(if (fill) MediaPlayer.ScaleType.SURFACE_FILL else MediaPlayer.ScaleType.SURFACE_BEST_FIT)
    }

    // ---------- Wischgesten: links Helligkeit, rechts Lautstärke

    private var volumeLevel = -1f
    private var brightnessLevel = -1f

    private fun adjustVolume(delta: Float): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (volumeLevel < 0) volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * max).roundToInt(), 0)
        return (volumeLevel * 100).roundToInt()
    }

    private fun adjustBrightness(delta: Float): Int {
        if (brightnessLevel < 0) brightnessLevel = window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
        brightnessLevel = (brightnessLevel + delta).coerceIn(0.02f, 1f)
        window.attributes = window.attributes.apply { screenBrightness = brightnessLevel }
        return (brightnessLevel * 100).roundToInt()
    }

    private fun audioTracks(): List<Pair<Int, String>> =
        player.audioTracks?.map { it.id to it.name }.orEmpty()

    private fun subtitleTracks(): List<Pair<Int, String>> =
        player.spuTracks?.map { it.id to it.name }.orEmpty()

    // ---------- Oberfläche

    @Composable
    private fun PlayerUi() {
        var visible by remember { mutableStateOf(true) }
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        var menu by remember { mutableStateOf<String?>(null) }
        var hint by remember { mutableStateOf<String?>(null) }
        var hintTick by remember { mutableIntStateOf(0) }

        LaunchedEffect(visible, playing, dragging, menu) {
            if (visible && playing && !dragging && menu == null) {
                delay(4000)
                visible = false
            }
        }
        LaunchedEffect(hintTick) {
            if (hint != null) {
                delay(900)
                hint = null
            }
        }
        fun showHint(text: String) {
            hint = text
            hintTick++
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { visible = !visible },
                        onDoubleTap = { o ->
                            if (o.x < size.width / 2) {
                                seekBy(-10_000)
                                showHint("« 10 s")
                            } else {
                                seekBy(10_000)
                                showHint("10 s »")
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    var leftSide = false
                    detectVerticalDragGestures(
                        onDragStart = { o ->
                            leftSide = o.x < size.width / 2
                            volumeLevel = -1f
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            val delta = -dy / size.height * 1.3f
                            if (leftSide) showHint("Helligkeit ${adjustBrightness(delta)} %")
                            else showHint("Lautstärke ${adjustVolume(delta)} %")
                        },
                    )
                }
        ) {
            if (!hasVideo) LoadingScreen(items[index])
            else if (buffering < 100f && playing) {
                CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Color.White)
            }

            AnimatedVisibility(visible && hasVideo, enter = fadeIn(), exit = fadeOut()) {
                Box(Modifier.fillMaxSize()) {
                    TopBar(menu) { menu = it }
                    CenterButtons(Modifier.align(Alignment.Center))
                    BottomBar(
                        Modifier.align(Alignment.BottomCenter),
                        dragging, dragValue,
                        onDrag = {
                            dragging = true
                            dragValue = it
                        },
                        onDragEnd = {
                            player.setTime(dragValue.toLong())
                            time = dragValue.toLong()
                            dragging = false
                        },
                    )
                }
            }

            hint?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 96.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
    }

    @Composable
    private fun LoadingScreen(item: Item) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            item.backdrop?.let {
                AsyncImage(
                    model = TMDB_BACKDROP + it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.55f,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Black.copy(alpha = 0.9f)))
                )
            )
            Row(
                Modifier.align(Alignment.Center).padding(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item.poster?.let {
                    AsyncImage(
                        model = TMDB_IMG + it,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(190.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.width(36.dp))
                }
                Column {
                    Text(
                        item.title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (error == null) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 3.dp)
                            Spacer(Modifier.width(14.dp))
                        }
                        Text(
                            error ?: if (buffering > 0f) "Wird geladen … ${buffering.roundToInt()} %" else "Verbinde mit dem NAS …",
                            color = if (error != null) Color(0xFFFF8A80) else Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
            IconButton(onClick = { finish() }, modifier = Modifier.align(Alignment.TopStart).padding(16.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
            }
        }
    }

    @Composable
    private fun TopBar(menu: String?, setMenu: (String?) -> Unit) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 40.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { finish() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text(
                        items[index].title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (items.size > 1) {
                        Text(
                            "Titel ${index + 1} von ${items.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TrackMenu("audio", Icons.Filled.Audiotrack, "Tonspur", menu, setMenu,
                    ::audioTracks, { player.audioTrack }, { player.setAudioTrack(it) })
                TrackMenu("spu", Icons.Filled.Subtitles, "Untertitel", menu, setMenu,
                    ::subtitleTracks, { player.spuTrack }, { player.setSpuTrack(it) })
                IconButton(onClick = { toggleFill() }) {
                    Icon(Icons.Filled.AspectRatio, if (fill) "Ganzes Bild" else "Bild füllen", tint = if (fill) MaterialTheme.colorScheme.primary else Color.White)
                }
            }
        }
    }

    @Composable
    private fun CenterButtons(modifier: Modifier) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { seekBy(-10_000) }, Modifier.size(72.dp)) {
                Icon(Icons.Filled.Replay10, "10 Sekunden zurück", Modifier.size(44.dp), tint = Color.White)
            }
            Box(
                Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, Modifier.size(96.dp)) {
                    Icon(
                        if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        if (playing) "Pause" else "Abspielen",
                        Modifier.size(64.dp),
                        tint = Color.White,
                    )
                }
            }
            IconButton(onClick = { seekBy(10_000) }, Modifier.size(72.dp)) {
                Icon(Icons.Filled.Forward10, "10 Sekunden vor", Modifier.size(44.dp), tint = Color.White)
            }
        }
    }

    @Composable
    private fun BottomBar(
        modifier: Modifier,
        dragging: Boolean,
        dragValue: Float,
        onDrag: (Float) -> Unit,
        onDragEnd: () -> Unit,
    ) {
        val max = length.toFloat().coerceAtLeast(1f)
        val shown = if (dragging) dragValue.toLong() else time
        val remaining = (length - shown).coerceAtLeast(0)
        val end = LocalTime.now().plusSeconds(remaining / 1000).format(DateTimeFormatter.ofPattern("HH:mm"))

        Column(
            modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                .padding(start = 32.dp, end = 32.dp, top = 48.dp, bottom = 16.dp)
        ) {
            Slider(
                value = shown.toFloat().coerceIn(0f, max),
                onValueChange = onDrag,
                onValueChangeFinished = onDragEnd,
                valueRange = 0f..max,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(shown), color = Color.White, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                if (length > 0) {
                    Text(
                        "−${fmtTime(remaining)}   ·   endet um $end",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                if (index < items.lastIndex) {
                    Spacer(Modifier.width(16.dp))
                    TextButton(onClick = {
                        savePosition()
                        load(index + 1)
                    }) {
                        Text("Nächster Titel", color = Color.White)
                        Spacer(Modifier.size(6.dp))
                        Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                    }
                }
            }
        }
    }

    @Composable
    private fun TrackMenu(
        id: String,
        icon: ImageVector,
        label: String,
        openMenu: String?,
        setMenu: (String?) -> Unit,
        tracks: () -> List<Pair<Int, String>>,
        selected: () -> Int,
        select: (Int) -> Unit,
    ) {
        Box {
            IconButton(onClick = { setMenu(id) }) { Icon(icon, label, tint = Color.White) }
            DropdownMenu(expanded = openMenu == id, onDismissRequest = { setMenu(null) }) {
                val list = tracks()
                if (list.isEmpty()) {
                    DropdownMenuItem(text = { Text("Keine $label verfügbar") }, onClick = { setMenu(null) })
                }
                val current = selected()
                list.forEach { (trackId, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        leadingIcon = {
                            if (trackId == current) Icon(Icons.Filled.Check, null) else Spacer(Modifier.size(24.dp))
                        },
                        onClick = {
                            select(trackId)
                            setMenu(null)
                        },
                    )
                }
            }
        }
    }
}
