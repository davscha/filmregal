// Datei: LangeAufgaben.kt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Tut so, als würde ein NAS-Ordner gelesen – blockierend, deshalb auf Dispatchers.IO. */
suspend fun ordnerLesen(nummer: Int): String = withContext(Dispatchers.IO) {
    Thread.sleep(300)
    "Ordner $nummer"
}

@Composable
fun LangeAufgabenBeispiel() {
    val scope = rememberCoroutineScope()
    var fortschritt by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Bereit") }
    var laeuft by remember { mutableStateOf(false) }
    var suche by remember { mutableStateOf("") }
    var suchErgebnis by remember { mutableStateOf("") }

    // 1) Beim ersten Anzeigen automatisch etwas laden
    LaunchedEffect(Unit) {
        delay(1000)
        status = "Startdaten geladen"
    }

    // 2) Bei jeder Änderung von "suche" neu starten – der alte Durchlauf wird abgebrochen
    LaunchedEffect(suche) {
        delay(400)                                  // erst suchen, wenn 400 ms nicht getippt wurde
        suchErgebnis = if (suche.isBlank()) "" else "Suche nach „$suche“ ausgeführt"
    }

    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 3) Auf Knopfdruck eine Coroutine starten
            Button(
                enabled = !laeuft,
                onClick = {
                    scope.launch {
                        laeuft = true
                        for (i in 1..10) {
                            status = ordnerLesen(i) + " durchsucht"
                            fortschritt = i / 10f
                        }
                        status = "Fertig"
                        laeuft = false
                    }
                },
            ) { Text("Scannen") }
        }
        LinearProgressIndicator(progress = { fortschritt }, modifier = Modifier.width(400.dp))
        Text(status)
        OutlinedTextField(value = suche, onValueChange = { suche = it }, label = { Text("Tippen geht weiterhin") })
        Text(suchErgebnis)
    }
}
