// Datei: Theme.kt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Dunkel = darkColorScheme(
    primary = Color(0xFF5AB0FF),
    onPrimary = Color(0xFF002A4D),
    background = Color(0xFF0B1626),
    surface = Color(0xFF102036),
    onSurface = Color(0xFFE6EEF8),
)
private val Hell = lightColorScheme(primary = Color(0xFF1E6FD9))

private val Schriften = Typography(
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun FilmregalTheme(dunkel: Boolean = true, inhalt: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dunkel) Dunkel else Hell, typography = Schriften, content = inhalt)
}

@Composable
fun ThemeBeispiel() {
    var dunkel by remember { mutableStateOf(true) }
    FilmregalTheme(dunkel) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = !dunkel, onCheckedChange = { dunkel = !it })
                    Spacer(Modifier.width(12.dp))
                    Text("Helles Design")
                }

                Column(
                    Modifier
                        .width(420.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF1E6FD9), Color(0xFF7B2FF7))))
                        .padding(24.dp),
                ) {
                    Text("Vaiana", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text("2026 · ★ 7.5 · Abenteuer", color = Color.White.copy(alpha = 0.8f))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {},
                        shape = RoundedCornerShape(30.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text("Abspielen", fontSize = 18.sp)
                    }
                    OutlinedButton(
                        onClick = {},
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    ) { Text("Von Anfang") }
                }
                Text("Primärfarbe aus dem Theme", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
