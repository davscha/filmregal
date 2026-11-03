// Datei: Speichern.kt
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class Einstellungen(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    var adresse: String
        get() = prefs.getString("adresse", "192.168.178.1")!!
        set(wert) = prefs.edit().putString("adresse", wert).apply()

    var dunkel: Boolean
        get() = prefs.getBoolean("dunkel", true)
        set(wert) = prefs.edit().putBoolean("dunkel", wert).apply()
}

@Composable
fun SpeichernBeispiel() {
    val context = LocalContext.current
    val einstellungen = remember { Einstellungen(context) }
    var adresse by remember { mutableStateOf(einstellungen.adresse) }
    var dunkel by remember { mutableStateOf(einstellungen.dunkel) }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value = adresse, onValueChange = { adresse = it }, label = { Text("NAS-Adresse") })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dunkel, onCheckedChange = { dunkel = it })
            Spacer(Modifier.width(12.dp))
            Text("Dunkles Design")
        }
        Button(onClick = {
            einstellungen.adresse = adresse
            einstellungen.dunkel = dunkel
            Toast.makeText(context, "Gespeichert – starte die App neu und schau nach.", Toast.LENGTH_SHORT).show()
        }) { Text("Speichern") }
    }
}
