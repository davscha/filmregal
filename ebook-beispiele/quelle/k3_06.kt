// Datei: ListenUndRaster.kt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

data class Titel(val name: String, val jahr: Int, val posterUrl: String? = null)

val TITEL = listOf(
    Titel("Vaiana", 2026), Titel("Encanto", 2021), Titel("Lightyear", 2022), Titel("2012", 2009),
    Titel("Anaconda", 1997), Titel("Chaos Walking", 2021), Titel("Bad Moms", 2016),
    Titel("50 erste Dates", 2004), Titel("Hotel for Dogs", 2009),
)

@Composable
fun PosterKachel(t: Titel, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (t.posterUrl != null) {
                AsyncImage(
                    model = t.posterUrl,
                    contentDescription = t.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Filled.Movie, contentDescription = null, modifier = Modifier.size(48.dp))
            }
        }
        Text(t.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${t.jahr}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ListenUndRasterBeispiel() {
    Row(Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.width(280.dp).fillMaxHeight()) {
            items(TITEL) { t ->
                ListItem(
                    headlineContent = { Text(t.name) },
                    supportingContent = { Text("${t.jahr}") },
                    leadingContent = { Icon(Icons.Filled.Movie, contentDescription = null) },
                )
                HorizontalDivider()
            }
        }
        VerticalDivider()
        LazyVerticalGrid(
            columns = GridCells.Fixed(5),                 // 5 Spalten
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(TITEL, key = { it.name }) { t ->
                PosterKachel(t, onClick = { println("Angeklickt: ${t.name}") })
            }
        }
    }
}
