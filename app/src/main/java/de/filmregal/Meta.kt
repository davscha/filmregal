package de.filmregal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

data class Meta(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String?,
    val year: String?,
)

/** Macht aus Dateinamen wie "Toy.Story.5.2026.German.DL.2160p.AV1.mkv" einen suchbaren Titel. */
object Titles {
    private val tags = Regex(
        "(?i)\\b(2160p|1080p|720p|576p|480p|uhd|x264|x265|h ?26[45]|hevc|av1|bluray|blu-ray|bdrip|brrip|" +
            "web-?dl|webrip|remux|german|deutsch|ac3|eac3|dts|truehd|atmos)\\b.*"
    )
    private val year = Regex("[\\[(]?((?:19|20)\\d{2})[\\])]?")
    val episode = Regex("(?i)S\\d{1,2}\\s?E\\d{1,3}|\\b\\d{1,2}x\\d{2}\\b")
    val season = Regex("(?i)^(staffel|season|s)\\s*\\d+$")

    fun clean(raw: String, isDir: Boolean): Pair<String, String?> {
        var s = if (isDir) raw else raw.substringBeforeLast('.')
        s = s.replace('.', ' ').replace('_', ' ')
        var y: String? = null
        val m = year.findAll(s).lastOrNull { it.range.first > 0 }
        if (m != null) {
            y = m.groupValues[1]
            s = s.substring(0, m.range.first)
        }
        s = tags.replace(s, "")
        s = s.replace(Regex("\\s+"), " ").trim(' ', '-', '(', '[')
        return s.ifEmpty { raw } to y
    }
}

/** Holt Poster und Beschreibungen von TMDB und merkt sie sich dauerhaft. */
class MetaRepo(context: Context, private val settings: Settings) {
    private val file = File(context.filesDir, "meta.json")
    private val cache: JSONObject = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    private val writeLock = Mutex()

    fun fallback(e: Entry): Meta {
        val (t, y) = Titles.clean(e.name, e.isDir)
        return Meta(t, null, null, null, y)
    }

    private fun keyFor(e: Entry): String? {
        if (Titles.episode.containsMatchIn(e.name)) return null
        val (t, y) = Titles.clean(e.name, e.isDir)
        if (Titles.season.matches(t)) return null
        return "$t|${y.orEmpty()}"
    }

    /** Sofort verfügbares Ergebnis aus dem Cache, sonst null. */
    fun peek(e: Entry): Meta? {
        val k = keyFor(e) ?: return null
        val o = synchronized(cache) { cache.optJSONObject(k) } ?: return null
        return if (o.length() == 0) fallback(e) else parse(o)
    }

    suspend fun lookup(e: Entry): Meta {
        peek(e)?.let { return it }
        val k = keyFor(e) ?: return fallback(e)
        val apiKey = settings.tmdbKey
        if (apiKey.isBlank()) return fallback(e)
        val (t, y) = Titles.clean(e.name, e.isDir)
        val result = try {
            withContext(Dispatchers.IO) { search(apiKey, t, y, e.isDir) }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            return fallback(e) // Netzwerkfehler: nicht cachen, später erneut versuchen
        }
        synchronized(cache) { cache.put(k, result) }
        persist()
        return if (result.length() == 0) fallback(e) else parse(result)
    }

    fun clear() {
        synchronized(cache) { cache.keys().asSequence().toList().forEach { cache.remove(it) } }
        file.delete()
    }

    private suspend fun persist() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            val text = synchronized(cache) { cache.toString() }
            file.writeText(text)
        }
    }

    private fun parse(o: JSONObject) = Meta(
        title = o.optString("title"),
        poster = o.optString("poster").ifEmpty { null },
        backdrop = o.optString("backdrop").ifEmpty { null },
        overview = o.optString("overview").ifEmpty { null },
        year = o.optString("year").ifEmpty { null },
    )

    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

    private fun search(apiKey: String, title: String, year: String?, isDir: Boolean): JSONObject {
        var hit: JSONObject? = null
        if (!isDir) {
            val params = mutableMapOf("query" to title)
            if (year != null) params["year"] = year
            hit = get(apiKey, "search/movie", params).optJSONArray("results")?.optJSONObject(0)
        }
        if (hit == null) {
            val results = get(apiKey, "search/multi", mapOf("query" to title)).optJSONArray("results")
            if (results != null) {
                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    if (r.optString("media_type") == "movie" || r.optString("media_type") == "tv") {
                        hit = r
                        break
                    }
                }
            }
        }
        val h = hit ?: return JSONObject()
        val date = h.str("release_date").ifEmpty { h.str("first_air_date") }
        return JSONObject()
            .put("title", h.str("title").ifEmpty { h.str("name") }.ifEmpty { title })
            .put("poster", h.str("poster_path"))
            .put("backdrop", h.str("backdrop_path"))
            .put("overview", h.str("overview"))
            .put("year", date.take(4))
    }

    private fun get(apiKey: String, path: String, params: Map<String, String>): JSONObject {
        // Kurzer v3-Schlüssel als Parameter, langer v4-Token als Bearer
        val bearer = apiKey.length > 40
        val query = (params + ("language" to "de-DE") + (if (bearer) emptyMap() else mapOf("api_key" to apiKey)))
            .entries.joinToString("&") { it.key + "=" + URLEncoder.encode(it.value, "UTF-8") }
        val c = URL("https://api.themoviedb.org/3/$path?$query").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        c.setRequestProperty("Accept", "application/json")
        if (bearer) c.setRequestProperty("Authorization", "Bearer $apiKey")
        try {
            if (c.responseCode != 200) throw IOException("TMDB antwortet ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally {
            c.disconnect()
        }
    }
}

const val TMDB_IMG = "https://image.tmdb.org/t/p/w342"
