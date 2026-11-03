// Datei: Katalog.kt
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch

data class Eintrag(val key: String, val titel: String, val jahr: Int, val genre: String, val farben: List<Color>)

val EINTRAEGE = listOf(
    Eintrag("vaiana", "Vaiana", 2026, "Abenteuer", listOf(Color(0xFF0E7490), Color(0xFF38BDF8))),
    Eintrag("encanto", "Encanto", 2021, "Animation", listOf(Color(0xFF9D174D), Color(0xFFF472B6))),
    Eintrag("lightyear", "Lightyear", 2022, "Science Fiction", listOf(Color(0xFF1E3A8A), Color(0xFF818CF8))),
    Eintrag("2012", "2012", 2009, "Action", listOf(Color(0xFF7C2D12), Color(0xFFFB923C))),
    Eintrag("anaconda", "Anaconda", 1997, "Abenteuer", listOf(Color(0xFF14532D), Color(0xFF4ADE80))),
    Eintrag("chaos", "Chaos Walking", 2021, "Science Fiction", listOf(Color(0xFF312E81), Color(0xFFA78BFA))),
    Eintrag("badmoms", "Bad Moms", 2016, "Komödie", listOf(Color(0xFF831843), Color(0xFFFB7185))),
    Eintrag("dates", "50 erste Dates", 2004, "Komödie", listOf(Color(0xFF78350F), Color(0xFFFBBF24))),
)

private val KatalogFarben = darkColorScheme(
    primary = Color(0xFF5AB0FF),
    background = Color(0xFF0B1626),
    surface = Color(0xFF102036),
)

@Composable
fun KatalogApp() {
    val nav = rememberNavController()
    val favoriten = remember { mutableStateListOf<String>() }

    MaterialTheme(colorScheme = KatalogFarben) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavHost(navController = nav, startDestination = "start") {
                composable("start") {
                    Uebersicht(favoriten, onOeffnen = { key -> nav.navigate("film/$key") })
                }
                composable("film/{key}") { eintrag ->
                    val e = EINTRAEGE.first { it.key == eintrag.arguments?.getString("key") }
                    Detail(
                        e = e,
                        favorit = e.key in favoriten,
                        onFavorit = { if (e.key in favoriten) favoriten.remove(e.key) else favoriten.add(e.key) },
                        onZurueck = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
fun Uebersicht(favoriten: List<String>, onOeffnen: (String) -> Unit) {
    var ansicht by rememberSaveable { mutableIntStateOf(0) }
    var suche by rememberSaveable { mutableStateOf("") }
    val quer = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    val sichtbar = EINTRAEGE.filter {
        (ansicht == 0 || it.key in favoriten) && it.titel.contains(suche, ignoreCase = true)
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            Text(
                "Katalog", fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 16.dp),
            )
            NavigationRailItem(
                selected = ansicht == 0, onClick = { ansicht = 0 },
                icon = { Icon(Icons.Filled.Movie, null) }, label = { Text("Alle") },
            )
            NavigationRailItem(
                selected = ansicht == 1, onClick = { ansicht = 1 },
                icon = { Icon(Icons.Filled.Star, null) }, label = { Text("Favoriten") },
            )
        }
        Column(Modifier.weight(1f).padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (ansicht == 0) "Alle Filme" else "Favoriten") + " (${sichtbar.size})",
                    fontSize = 26.sp, color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.weight(1f))
                OutlinedTextField(
                    value = suche, onValueChange = { suche = it },
                    placeholder = { Text("Suchen") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    modifier = Modifier.width(260.dp),
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(if (quer) 5 else 2),
                contentPadding = PaddingValues(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(sichtbar, key = { it.key }) { e ->
                    Poster(e, favorit = e.key in favoriten, onClick = { onOeffnen(e.key) })
                }
            }
        }
    }
}

@Composable
fun Poster(e: Eintrag, favorit: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(e.farben)),
        ) {
            Text(
                e.titel, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White,
                textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(12.dp),
            )
            if (favorit) {
                Icon(
                    Icons.Filled.Star, contentDescription = "Favorit", tint = Color(0xFFFFC107),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
        }
        Text(e.titel, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        Text("${e.jahr} · ${e.genre}", fontSize = 12.sp, color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Detail(e: Eintrag, favorit: Boolean, onFavorit: () -> Unit, onZurueck: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(e.titel) },
                navigationIcon = { IconButton(onClick = onZurueck) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innen ->
        Row(Modifier.padding(innen).padding(40.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Box(
                Modifier
                    .size(width = 220.dp, height = 330.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(e.farben)),
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(e.titel, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text("${e.jahr} · ${e.genre}", fontSize = 18.sp, color = Color.Gray)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {}) {
                        Icon(Icons.Filled.PlayArrow, null)
                        Text("Abspielen")
                    }
                    IconButton(onClick = {
                        onFavorit()
                        scope.launch { snackbar.showSnackbar(if (!favorit) "Zu Favoriten hinzugefügt" else "Entfernt") }
                    }) {
                        Icon(
                            if (favorit) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Favorit", tint = Color(0xFFFFC107), modifier = Modifier.size(32.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}
