package de.filmregal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

sealed interface Video { val path: String }

data class Movie(override val path: String, val title: String, val year: String?) : Video

data class Episode(
    override val path: String,
    val show: String,
    val showYear: String?,
    val season: Int,
    val number: Int,
) : Video {
    val code get() = "S%02dE%02d".format(season, number)
}

data class Show(val name: String, val year: String?, val episodes: List<Episode>) {
    val seasons: Map<Int, List<Episode>> get() = episodes.groupBy { it.season }.toSortedMap()
}

data class LibraryData(val videos: List<Video>, val scannedAt: Long) {
    val movies: List<Movie> by lazy {
        videos.filterIsInstance<Movie>().sortedWith { a, b -> naturalCompare(a.title, b.title) }
    }
    val shows: List<Show> by lazy {
        videos.filterIsInstance<Episode>()
            .groupBy { it.show.lowercase() }
            .map { (_, eps) ->
                val sorted = eps.sortedWith(compareBy<Episode>({ it.season }, { it.number }))
                Show(sorted.first().show, sorted.firstNotNullOfOrNull { it.showYear }, sorted)
            }
            .sortedWith { a, b -> naturalCompare(a.name, b.name) }
    }
}

/** Entscheidet anhand des Dateinamens: Folge (S01E01, 1x02 …) oder Film. */
fun classify(path: String): Video {
    val file = path.substringAfterLast('/')
    val base = file.substringBeforeLast('.')
    val m = Titles.episode.find(base)
    if (m != null) {
        val season = m.groupValues[1].ifEmpty { m.groupValues[3] }.toInt()
        val number = m.groupValues[2].ifEmpty { m.groupValues[4] }.toInt()
        val prefix = base.substring(0, m.range.first)
        var (show, year) = Titles.clean(prefix, isDir = true)
        if (prefix.trim(' ', '.', '-', '_').isEmpty()) {
            // Dateiname ist nur "S01E01.mkv" → Serienname aus dem Ordner (Staffel-Ordner überspringen)
            val folder = path.split('/').dropLast(1).reversed()
                .firstOrNull { !Titles.seasonDir.matches(it.trim()) } ?: "Unbekannt"
            val c = Titles.clean(folder, isDir = true)
            show = c.first
            year = c.second
        }
        return Episode(path, show, year, season, number)
    }
    val (title, year) = Titles.clean(file, isDir = false)
    return Movie(path, title, year)
}

/** Durchsucht eine Bibliothek inklusive aller Unterordner und speichert das Ergebnis. */
class LibraryStore(context: Context) {
    private val dir = File(context.filesDir, "libraries").apply { mkdirs() }
    private val memory = HashMap<String, LibraryData>()

    private fun fileFor(root: String) = File(dir, "lib_" + Integer.toHexString(root.hashCode()) + ".json")

    fun load(root: String): LibraryData? = memory[root] ?: try {
        val o = JSONObject(fileFor(root).readText())
        val a = o.getJSONArray("paths")
        LibraryData((0 until a.length()).map { classify(a.getString(it)) }, o.getLong("scannedAt"))
            .also { memory[root] = it }
    } catch (e: Exception) {
        null
    }

    private fun save(root: String, data: LibraryData) {
        memory[root] = data
        val a = JSONArray()
        data.videos.forEach { a.put(it.path) }
        fileFor(root).writeText(JSONObject().put("scannedAt", data.scannedAt).put("paths", a).toString())
    }

    fun clear() {
        memory.clear()
        dir.listFiles()?.forEach { it.delete() }
    }

    /** Scannt bis zu 4 Ordner gleichzeitig. [progress] bekommt (Ordner, Videos). */
    suspend fun scan(cfg: NasConfig, root: String, progress: (Int, Int) -> Unit): LibraryData {
        val videos = mutableListOf<Video>()
        var pending = listOf(root)
        var folders = 0
        while (pending.isNotEmpty()) {
            val next = mutableListOf<String>()
            for (batch in pending.chunked(4)) {
                val lists = coroutineScope {
                    batch.map { dirPath ->
                        async {
                            try {
                                Nas.list(cfg, dirPath)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (dirPath == root) throw e else emptyList()
                            }
                        }
                    }.awaitAll()
                }
                lists.flatten().forEach { e -> if (e.isDir) next += e.path else videos += classify(e.path) }
                folders += batch.size
                progress(folders, videos.size)
            }
            pending = next
        }
        val data = LibraryData(videos, System.currentTimeMillis())
        withContext(Dispatchers.IO) { save(root, data) }
        return data
    }
}
