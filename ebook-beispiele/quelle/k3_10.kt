// Datei: Seiten.kt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

val KATALOG = mapOf("vaiana" to ("Vaiana" to 2026), "encanto" to ("Encanto" to 2021), "lightyear" to ("Lightyear" to 2022))

@Composable
fun SeitenBeispiel() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = "start") {
        composable("start") {
            StartSeite(onFilm = { key -> nav.navigate("film/$key") })
        }
        composable("film/{key}") { eintrag ->
            val key = eintrag.arguments?.getString("key") ?: ""
            FilmSeite(key, onZurueck = { nav.popBackStack() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSeite(onFilm: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Alle Filme") }) }) { innen ->
        LazyColumn(Modifier.padding(innen)) {
            items(KATALOG.entries.toList()) { (key, film) ->
                ListItem(
                    headlineContent = { Text(film.first) },
                    supportingContent = { Text("${film.second}") },
                    modifier = Modifier.clickable { onFilm(key) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilmSeite(key: String, onZurueck: () -> Unit) {
    val (titel, jahr) = KATALOG[key] ?: ("Unbekannt" to 0)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titel) },
                navigationIcon = {
                    IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") }
                },
            )
        },
    ) { innen ->
        Column(Modifier.padding(innen).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(titel, fontSize = 36.sp, fontWeight = FontWeight.Bold)
            Text("Erschienen $jahr")
            Button(onClick = {}) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Abspielen")
            }
        }
    }
}
