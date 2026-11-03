// Datei: Dialoge.kt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogeBeispiel() {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var frageOffen by remember { mutableStateOf(false) }
    var infosOffen by remember { mutableStateOf(false) }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { innen ->
        Column(Modifier.padding(innen).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { frageOffen = true }) { Text("Dialog mit Rückfrage") }
            OutlinedButton(onClick = { infosOffen = true }) { Text("Infos von unten") }
            TextButton(onClick = { scope.launch { snackbar.showSnackbar("Gespeichert!") } }) { Text("Kurze Meldung") }
        }
    }

    if (frageOffen) {
        AlertDialog(
            onDismissRequest = { frageOffen = false },
            title = { Text("Liste löschen?") },
            text = { Text("Die Wiedergabeliste „Filmabend“ wird entfernt.") },
            confirmButton = {
                Button(onClick = {
                    frageOffen = false
                    scope.launch {
                        val ergebnis = snackbar.showSnackbar("Liste gelöscht", actionLabel = "Rückgängig")
                        if (ergebnis == SnackbarResult.ActionPerformed) snackbar.showSnackbar("Wiederhergestellt")
                    }
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { frageOffen = false }) { Text("Abbrechen") } },
        )
    }

    if (infosOffen) {
        ModalBottomSheet(onDismissRequest = { infosOffen = false }) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Vaiana", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Abenteuer · 2026 · 1 h 55 min")
                Button(onClick = { infosOffen = false }) { Text("Schließen") }
            }
        }
    }
}
