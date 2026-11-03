// Datei: Zustand.kt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val FILME = listOf("Vaiana", "Encanto", "Lightyear", "2012", "Anaconda", "Chaos Walking", "Bad Moms")

@Composable
fun ZustandBeispiel() {
    var suche by rememberSaveable { mutableStateOf("") }
    var favoriten by remember { mutableStateOf(setOf<String>()) }

    // Abgeleitet: wird bei jeder Änderung von suche automatisch neu berechnet
    val sichtbar = FILME.filter { it.contains(suche, ignoreCase = true) }

    Column(Modifier.padding(16.dp)) {
        OutlinedTextField(
            value = suche,
            onValueChange = { suche = it },
            label = { Text("Filtern") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text("${favoriten.size} Favoriten", modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn {
            items(sichtbar, key = { it }) { titel ->
                FilmZeile(
                    titel = titel,
                    istFavorit = titel in favoriten,
                    onFavorit = { favoriten = if (titel in favoriten) favoriten - titel else favoriten + titel },
                )
            }
        }
    }
}

/** Zustandslos: bekommt Daten von außen und meldet Klicks nach außen. */
@Composable
fun FilmZeile(titel: String, istFavorit: Boolean, onFavorit: () -> Unit) {
    ListItem(
        headlineContent = { Text(titel) },
        trailingContent = {
            IconButton(onClick = onFavorit) {
                Icon(
                    if (istFavorit) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = "Favorit",
                    tint = if (istFavorit) Color(0xFFFFC107) else Color.Gray,
                )
            }
        },
    )
}
