// Datei: Controls.kt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ControlsBeispiel() {
    var ausgabe by remember { mutableStateOf("Noch nichts gewählt") }
    var titel by remember { mutableStateOf("") }
    var passwort by remember { mutableStateOf("") }
    var zeigen by remember { mutableStateOf(false) }
    var genreOffen by remember { mutableStateOf(false) }
    var genre by remember { mutableStateOf("Alle Genres") }
    var nurUngesehen by remember { mutableStateOf(false) }
    var dunkel by remember { mutableStateOf(true) }
    var sterne by remember { mutableFloatStateOf(7f) }
    var sortierung by remember { mutableStateOf("Titel") }

    Column(
        modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Die wichtigsten Controls", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Text mit Symbol", fontSize = 18.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { ausgabe = "Button" }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Gefüllt")
            }
            FilledTonalButton(onClick = { ausgabe = "FilledTonalButton" }) { Text("Getönt") }
            OutlinedButton(onClick = { ausgabe = "OutlinedButton" }) { Text("Umrandet") }
            TextButton(onClick = { ausgabe = "TextButton" }) { Text("Nur Text") }
            IconButton(onClick = { ausgabe = "Herz" }) { Icon(Icons.Filled.Favorite, "Favorit", tint = Color(0xFFE57373)) }
        }

        OutlinedTextField(
            value = titel,
            onValueChange = { titel = it },
            label = { Text("Filmtitel") },
            placeholder = { Text("z. B. Vaiana") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { ausgabe = "Gesucht: $titel" }),
        )
        OutlinedTextField(
            value = passwort,
            onValueChange = { passwort = it },
            label = { Text("Passwort") },
            singleLine = true,
            visualTransformation = if (zeigen) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { zeigen = !zeigen }) {
                    Icon(if (zeigen) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Passwort zeigen")
                }
            },
        )

        Box {
            OutlinedButton(onClick = { genreOffen = true }) {
                Text(genre)
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = genreOffen, onDismissRequest = { genreOffen = false }) {
                listOf("Alle Genres", "Animation", "Komödie", "Action").forEach { g ->
                    DropdownMenuItem(text = { Text(g) }, onClick = { genre = g; genreOffen = false; ausgabe = "Genre: $g" })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = nurUngesehen, onCheckedChange = { nurUngesehen = it })
            Text("Nur ungesehene")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = dunkel, onCheckedChange = { dunkel = it })
            Spacer(Modifier.width(12.dp))
            Text("Dunkles Design")
        }

        Text("Mindestens ${sterne.toInt()} Sterne")
        Slider(
            value = sterne,
            onValueChange = { sterne = it },
            valueRange = 0f..10f,
            steps = 9,
            modifier = Modifier.width(320.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            listOf("Titel", "Jahr").forEach { option ->
                RadioButton(selected = sortierung == option, onClick = { sortierung = option })
                Text(option, modifier = Modifier.padding(end = 16.dp))
            }
        }

        LinearProgressIndicator(progress = { 0.4f }, modifier = Modifier.width(320.dp))
        Text(ausgabe, fontStyle = FontStyle.Italic)
    }
}
