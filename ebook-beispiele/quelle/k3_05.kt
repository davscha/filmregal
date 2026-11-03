// Datei: Layout.kt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Kasten(text: String, farbe: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(farbe)
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White)
    }
}

@Composable
fun LayoutBeispiel() {
    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // Kopfzeile: Spacer mit weight schiebt die Knöpfe nach rechts
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Filmregal", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Filled.Search, "Suchen") }
            IconButton(onClick = {}) { Icon(Icons.Filled.Settings, "Einstellungen") }
        }

        // Nebeneinander: feste Breite und Gewichte
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Kasten("fest 150", Color(0xFF1976D2), Modifier.width(150.dp))
            Kasten("weight 1", Color(0xFF388E3C), Modifier.weight(1f))
            Kasten("weight 2", Color(0xFFF57C00), Modifier.weight(2f))
        }

        // Übereinander: Box mit ausgerichteten Kindern
        Box(
            modifier = Modifier
                .size(width = 300.dp, height = 170.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF303F9F), Color(0xFFAB47BC)))),
        ) {
            Text("Poster", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(16.dp))
            Icon(
                Icons.Filled.CheckCircle, contentDescription = "Gesehen", tint = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
            )
            LinearProgressIndicator(
                progress = { 0.6f },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(5.dp),
            )
        }

        // Reihenfolge der Modifier zählt
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Erst Abstand, dann Farbe", Modifier.padding(16.dp).background(Color(0xFF455A64)))
            Text("Erst Farbe, dann Abstand", Modifier.background(Color(0xFF455A64)).padding(16.dp))
        }
    }
}
