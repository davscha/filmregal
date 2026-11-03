package de.filmregal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/** Verbindungsdaten zum NAS. Pfade sind immer relativ zur Freigabe und mit "/" getrennt. */
data class NasConfig(
    val host: String,
    val share: String,
    val root: String,
    val user: String,
    val password: String,
) {
    val isComplete get() = host.isNotBlank()
}

/** Ein Eintrag in einem NAS-Ordner (Unterordner oder Videodatei). */
data class Entry(val name: String, val path: String, val isDir: Boolean, val added: Long = 0)

/** Etwas Abspielbares: Pfad auf dem NAS plus Anzeigename. */
data class Item(
    val path: String,
    val title: String,
    val backdrop: String? = null,
    val poster: String? = null,
)

/** 1:23:45 bzw. 4:10 */
fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

data class Playlist(val name: String, val items: List<Item>)

/** Wie runCatching, schluckt aber keine Coroutine-Abbrüche. */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

fun joinPath(a: String, b: String) = if (a.isEmpty()) b else "$a/$b"

/** Sortiert "Folge 2" vor "Folge 10". */
fun naturalCompare(a: String, b: String): Int {
    val chunk = Regex("\\d+|\\D+")
    val ra = chunk.findAll(a.lowercase()).map { it.value }.toList()
    val rb = chunk.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ra.size, rb.size)) {
        val x = ra[i]
        val y = rb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) {
            x.toBigInteger().compareTo(y.toBigInteger())
        } else {
            x.compareTo(y)
        }
        if (c != 0) return c
    }
    return ra.size - rb.size
}

class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var nas: NasConfig
        get() = NasConfig(
            host = p.getString("host", "")!!,
            share = p.getString("share", "")!!,
            root = p.getString("root", "")!!,
            user = p.getString("user", "")!!,
            password = p.getString("password", "")!!,
        )
        set(v) {
            p.edit()
                .putString("host", v.host.trim())
                .putString("share", v.share.trim().trim('/'))
                .putString("root", v.root.trim().trim('/'))
                .putString("user", v.user.trim())
                .putString("password", v.password)
                .apply()
        }

    var smbVariant: Int
        get() = p.getInt("smbVariant", 0)
        set(v) = p.edit().putInt("smbVariant", v).apply()

    var tmdbKey: String
        get() = p.getString("tmdb", "")!!
        set(v) = p.edit().putString("tmdb", v.trim()).apply()
}

/** Wiedergabestand, "Weiterschauen" und Wiedergabelisten. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("store", Context.MODE_PRIVATE)

    fun position(path: String) = p.getLong("pos:$path", 0L)

    fun progress(path: String): Float {
        val len = p.getLong("len:$path", 0L)
        return if (len > 0) (position(path).toFloat() / len).coerceIn(0f, 1f) else 0f
    }

    fun isWatched(path: String) = p.getBoolean("seen:$path", false)

    fun setWatched(path: String, watched: Boolean) {
        if (watched) {
            p.edit().remove("pos:$path").remove("len:$path").putBoolean("seen:$path", true).apply()
            removeRecent(path)
        } else {
            p.edit().remove("seen:$path").apply()
        }
    }

    fun savePosition(item: Item, pos: Long, len: Long) {
        if (len <= 0) return
        if (pos >= len * 0.92) {
            p.edit()
                .remove("pos:${item.path}")
                .remove("len:${item.path}")
                .putBoolean("seen:${item.path}", true)
                .apply()
            removeRecent(item.path)
        } else if (pos > 30_000) {
            p.edit().putLong("pos:${item.path}", pos).putLong("len:${item.path}", len).apply()
            addRecent(item)
        }
    }

    // --- Weiterschauen ---
    fun recents(): List<Item> = readItems(JSONArray(p.getString("recent", "[]")))

    private fun addRecent(item: Item) {
        val list = recents().filter { it.path != item.path }.toMutableList()
        list.add(0, item)
        p.edit().putString("recent", itemsJson(list.take(40)).toString()).apply()
    }

    fun removeRecent(path: String) {
        p.edit().putString("recent", itemsJson(recents().filter { it.path != path }).toString()).apply()
    }

    // --- Wiedergabelisten ---
    fun playlists(): List<Playlist> {
        val a = JSONArray(p.getString("playlists", "[]"))
        return (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            Playlist(o.getString("name"), readItems(o.getJSONArray("items")))
        }
    }

    private fun savePlaylists(list: List<Playlist>) {
        val a = JSONArray()
        list.forEach { a.put(JSONObject().put("name", it.name).put("items", itemsJson(it.items))) }
        p.edit().putString("playlists", a.toString()).apply()
    }

    private fun update(name: String, change: (List<Item>) -> List<Item>) {
        savePlaylists(playlists().map { if (it.name == name) it.copy(items = change(it.items)) else it })
    }

    fun createPlaylist(name: String) {
        val list = playlists()
        if (list.none { it.name.equals(name, ignoreCase = true) }) savePlaylists(list + Playlist(name, emptyList()))
    }

    /** Fügt hinzu; gibt es die Liste schon (Groß-/Kleinschreibung egal), wird sie ergänzt statt doppelt angelegt. */
    fun addToPlaylist(name: String, items: List<Item>) {
        val list = playlists().toMutableList()
        val i = list.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (i < 0) {
            list.add(Playlist(name, items.distinctBy { it.path }))
        } else {
            val old = list[i].items
            list[i] = list[i].copy(items = old + items.filter { n -> old.none { it.path == n.path } })
        }
        savePlaylists(list)
    }

    fun deletePlaylist(name: String) = savePlaylists(playlists().filter { it.name != name })

    fun removeFromPlaylist(name: String, index: Int) =
        update(name) { items -> items.filterIndexed { i, _ -> i != index } }

    fun moveInPlaylist(name: String, from: Int, to: Int) = update(name) { items ->
        if (from !in items.indices || to !in items.indices) items
        else items.toMutableList().apply { add(to, removeAt(from)) }
    }

    private fun readItems(a: JSONArray): List<Item> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        Item(o.getString("path"), o.getString("title"))
    }

    private fun itemsJson(items: List<Item>) = JSONArray().apply {
        items.forEach { put(JSONObject().put("path", it.path).put("title", it.title)) }
    }
}

/** Gemeinsame Objekte für die ganze App. */
class Deps private constructor(context: Context) {
    val settings = Settings(context)
    val store = Store(context)
    val meta = MetaRepo(context, settings)
    val libraries = LibraryStore(context)

    init {
        Nas.working = settings.smbVariant.coerceIn(0, 3)
    }

    /** Anzeigename für den Player, z. B. "Vaiana" oder "Sleepy Hollow S01E01 – Der kopflose Reiter". */
    fun itemFor(v: Video): Item = when (v) {
        is Movie -> {
            val m = meta.peekMovie(v)
            Item(v.path, m?.title ?: v.title, m?.backdrop, m?.poster)
        }
        is Episode -> {
            val show = meta.peekShow(v.show, v.showYear)
            val season = show?.let { meta.peekSeason(it.id, v.season) }
            val name = season?.episodes?.get(v.number)?.name
            Item(
                v.path,
                "${show?.title ?: v.show} ${v.code}" + (name?.takeIf { it.isNotBlank() }?.let { " – $it" } ?: ""),
                show?.backdrop,
                if (show?.customPoster == true) show.poster else season?.poster ?: show?.poster,
            )
        }
    }

    companion object {
        @Volatile private var instance: Deps? = null
        fun get(context: Context): Deps = instance ?: synchronized(this) {
            instance ?: Deps(context.applicationContext).also { instance = it }
        }
    }
}
