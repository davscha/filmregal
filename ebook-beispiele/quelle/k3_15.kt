// Datei: Bildschirm.kt
import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BildschirmBeispiel() {
    val konfig = LocalConfiguration.current
    val quer = konfig.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spalten = if (quer) 5 else 2

    Column(Modifier.padding(24.dp)) {
        Text(if (quer) "Querformat" else "Hochformat", fontSize = 28.sp)
        Text("${konfig.screenWidthDp} × ${konfig.screenHeightDp} dp")
        Text("Kleinste Seite: ${konfig.smallestScreenWidthDp} dp – ${if (konfig.smallestScreenWidthDp >= 600) "Tablet" else "Handy"}")
        Text("Poster pro Zeile: $spalten")
    }
}
