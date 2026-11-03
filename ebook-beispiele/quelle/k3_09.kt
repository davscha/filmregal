// Datei: Navigation.kt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class Ziel(val name: String, val symbol: ImageVector)

val ZIELE = listOf(Ziel("Filme", Icons.Filled.Movie), Ziel("Serien", Icons.Filled.Tv), Ziel("Einstellungen", Icons.Filled.Settings))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationBeispiel() {
    var auswahl by remember { mutableIntStateOf(0) }
    var inhalt by remember { mutableStateOf("Filme") }
    var menueOffen by remember { mutableStateOf(false) }
    val schublade = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = schublade,
        drawerContent = {
            ModalDrawerSheet {
                Text("Bibliotheken", fontSize = 18.sp, modifier = Modifier.padding(16.dp))
                listOf("Kino", "Kinder").forEach { name ->
                    NavigationDrawerItem(
                        label = { Text(name) },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        selected = false,
                        onClick = { inhalt = "Bibliothek $name"; scope.launch { schublade.close() } },
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Filmregal") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { schublade.open() } }) { Icon(Icons.Filled.Menu, "Menü") }
                    },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.Filled.Search, "Suchen") }
                        Box {
                            IconButton(onClick = { menueOffen = true }) { Icon(Icons.Filled.MoreVert, "Mehr") }
                            DropdownMenu(expanded = menueOffen, onDismissRequest = { menueOffen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Nach Titel sortieren") },
                                    leadingIcon = { Icon(Icons.Filled.SortByAlpha, null) },
                                    onClick = { inhalt = "Sortiert nach Titel"; menueOffen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Nach Jahr sortieren") },
                                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                                    onClick = { inhalt = "Sortiert nach Jahr"; menueOffen = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Neu durchsuchen") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                    onClick = { inhalt = "Neu durchsucht"; menueOffen = false },
                                )
                            }
                        }
                    },
                )
            },
        ) { innen ->
            Row(Modifier.fillMaxSize().padding(innen)) {
                NavigationRail {
                    ZIELE.forEachIndexed { i, ziel ->
                        NavigationRailItem(
                            selected = auswahl == i,
                            onClick = { auswahl = i; inhalt = ziel.name },
                            icon = { Icon(ziel.symbol, contentDescription = null) },
                            label = { Text(ziel.name) },
                        )
                    }
                }
                VerticalDivider()
                Text(inhalt, fontSize = 30.sp, modifier = Modifier.padding(24.dp))
            }
        }
    }
}
