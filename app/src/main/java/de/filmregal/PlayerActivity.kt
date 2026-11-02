package de.filmregal

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerActivity : ComponentActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout
    private var items: List<Item> = emptyList()
    private val d by lazy { Deps.get(this) }
    private var started = false

    private var index by mutableIntStateOf(0)
    private var playing by mutableStateOf(false)
    private var time by mutableLongStateOf(0L)
    private var length by mutableLongStateOf(0L)
    private var error by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val paths = intent.getStringArrayListExtra("paths").orEmpty()
        val titles = intent.getStringArrayListExtra("titles").orEmpty()
        items = paths.mapIndexed { i, p -> Item(p, titles.getOrElse(i) { p.substringAfterLast('/') }) }
        if (items.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt("index") ?: intent.getIntExtra("index", 0)).coerceIn(0, items.lastIndex)

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
                MediaPlayer.Event.TimeChanged -> time = ev.timeChanged
                MediaPlayer.Event.LengthChanged -> length = ev.lengthChanged
                MediaPlayer.Event.EndReached -> videoLayout.post { onEnded() }
                MediaPlayer.Event.EncounteredError -> error = "Datei konnte nicht abgespielt werden."
            }
        }

        videoLayout = VLCVideoLayout(this)
        val root = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        root.addView(videoLayout, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        root.addView(
            ComposeView(this).apply {
                setContent { MaterialTheme(colorScheme = darkColorScheme()) { Controls() } }
            },
            FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT),
        )
        setContentView(root)

        // Alle 10 Sekunden Position sichern, falls die App abstürzt oder beendet wird
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
        val item = items[i]
        val media = Media(libVlc, Uri.parse(StreamServer.url(d.settings.nas, item.path)))
        media.setHWDecoderEnabled(true, false) // AV1 über den Snapdragon-Decoder
        val start = d.store.position(item.path)
        if (start > 30_000) media.addOption(":start-time=${start / 1000}")
        player.setMedia(media)
        media.release()
        player.play()
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

    private fun audioTracks(): List<Pair<Int, String>> =
        player.audioTracks?.map { it.id to it.name }.orEmpty()

    private fun subtitleTracks(): List<Pair<Int, String>> =
        player.spuTracks?.map { it.id to it.name }.orEmpty()

    @Composable
    private fun Controls() {
        var visible by remember { mutableStateOf(true) }
        var dragging by remember { mutableStateOf(false) }
        var dragValue by remember { mutableFloatStateOf(0f) }
        var menu by remember { mutableStateOf<String?>(null) }

        // Bedienelemente nach 4 Sekunden ausblenden
        LaunchedEffect(visible, playing, dragging, menu) {
            if (visible && playing && !dragging && menu == null) {
                delay(4000)
                visible = false
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    visible = !visible
                }
        ) {
            if (visible) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))

                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                    }
                    Text(
                        items[index].title + if (items.size > 1) "   (${index + 1}/${items.size})" else "",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TrackMenu("audio", Icons.Filled.Audiotrack, "Tonspur", menu, { menu = it },
                        ::audioTracks, { player.audioTrack }, { player.setAudioTrack(it) })
                    TrackMenu("spu", Icons.Filled.Subtitles, "Untertitel", menu, { menu = it },
                        ::subtitleTracks, { player.spuTrack }, { player.setSpuTrack(it) })
                }

                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { seekBy(-10_000) }, Modifier.size(72.dp)) {
                        Icon(Icons.Filled.Replay10, "10 Sekunden zurück", Modifier.size(48.dp), tint = Color.White)
                    }
                    IconButton(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        Modifier.size(96.dp),
                    ) {
                        Icon(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playing) "Pause" else "Abspielen",
                            Modifier.size(80.dp),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = { seekBy(30_000) }, Modifier.size(72.dp)) {
                        Icon(Icons.Filled.Forward30, "30 Sekunden vor", Modifier.size(48.dp), tint = Color.White)
                    }
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    val max = length.toFloat().coerceAtLeast(1f)
                    Slider(
                        value = if (dragging) dragValue else time.toFloat().coerceIn(0f, max),
                        onValueChange = {
                            dragging = true
                            dragValue = it
                        },
                        onValueChangeFinished = {
                            player.setTime(dragValue.toLong())
                            time = dragValue.toLong()
                            dragging = false
                        },
                        valueRange = 0f..max,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${fmt(if (dragging) dragValue.toLong() else time)} / ${fmt(length)}",
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                        )
                        if (index < items.lastIndex) {
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

            error?.let {
                Text(
                    it,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .background(Color(0xCC8B0000))
                        .padding(16.dp),
                )
            }
        }
    }

    @Composable
    private fun TrackMenu(
        id: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector,
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

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
