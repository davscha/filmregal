// Datei: PlayerActivity.kt (Android)
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerActivity : ComponentActivity() {
    private lateinit var libVlc: LibVLC
    private lateinit var player: MediaPlayer
    private lateinit var bild: VLCVideoLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        libVlc = LibVLC(this, arrayListOf("--network-caching=2000"))
        player = MediaPlayer(libVlc)
        bild = VLCVideoLayout(this)
        setContentView(bild)

        player.setEventListener { ereignis ->
            when (ereignis.type) {
                MediaPlayer.Event.Playing -> println("Läuft")
                MediaPlayer.Event.TimeChanged -> println("Position: ${ereignis.timeChanged} ms")
                MediaPlayer.Event.EndReached -> finish()
                MediaPlayer.Event.EncounteredError -> println("Fehler beim Abspielen")
            }
        }
    }

    override fun onStart() {
        super.onStart()
        player.attachViews(bild, null, true, false)
        val media = Media(libVlc, Uri.parse("http://127.0.0.1:8080/Vaiana%20%5B2026%5D.mkv"))
        media.setHWDecoderEnabled(true, false)      // Hardware-Decoder, z. B. für AV1
        media.addOption(":start-time=120")          // bei 2:00 beginnen
        player.setMedia(media)
        media.release()
        player.play()
    }

    override fun onStop() {
        super.onStop()
        player.pause()
        player.detachViews()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        libVlc.release()
    }
}
