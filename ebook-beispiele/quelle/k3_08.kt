// Datei: Bausteine.kt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FilmKarte(
    titel: String,
    jahr: Int,
    modifier: Modifier = Modifier,
    anfangsGesehen: Boolean = false,
) {
    var gesehen by remember { mutableStateOf(anfangsGesehen) }

    Row(
        modifier = modifier
            .width(360.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { gesehen = !gesehen }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(titel, fontWeight = FontWeight.Bold)
            Text("$jahr", fontSize = 12.sp)
        }
        if (gesehen) Icon(Icons.Filled.CheckCircle, contentDescription = "Gesehen", tint = Color(0xFF66BB6A))
    }
}

/** Ein Baustein mit "Slot": Der Inhalt wird von außen hineingereicht. */
@Composable
fun Abschnitt(ueberschrift: String, inhalt: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(ueberschrift, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
        inhalt()
    }
}

@Composable
fun BausteineBeispiel() {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Abschnitt("Neu") {
            FilmKarte("Vaiana", 2026)
            FilmKarte("Lightyear", 2022)
        }
        Abschnitt("Schon gesehen") {
            FilmKarte("Encanto", 2021, anfangsGesehen = true)
        }
    }
}
