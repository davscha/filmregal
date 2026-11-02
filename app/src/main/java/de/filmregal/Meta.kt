package de.filmregal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

data class Meta(
    val title: String,
    val poster: String?,
    val overview: String?,
    val year: String?,
    val id: Int = 0,
    val backdrop: String? = null,
    val rating: Double? = null,
)

data class EpisodeMeta(val name: String, val still: String?, val overview: String?)
data class SeasonMeta(val poster: String?, val episodes: Map<Int, EpisodeMeta>)

const val TMDB_IMG = "https://image.tmdb.org/t/p/w342"
const val TMDB_STILL = "https://image.tmdb.org/t/p/w300"
const val TMDB_BACKDROP = "https://image.tmdb.org/t/p/w1280"

/** Macht aus Dateinamen wie "Toy.Story.5.[2026].German.2160p.AV1.mkv" einen suchbaren Titel. */
object Titles {
    private val tags = Regex(
        "(?i)\\b(2160p|1080p|720p|576p|480p|uhd|x264|x265|h ?26[45]|hevc|av1|bluray|blu-ray|bdrip|brrip|" +
            "web-?dl|webrip|remux|german|deutsch|ac3|eac3|dts|truehd|atmos)\\b.*"
    )
    private val year = Regex("[\\[(]?((?:19|20)\\d{2})[\\])]?")

    /** S01E01, s1e2, S01.E05, S01 E05 oder 1x02 – führende Nullen sind egal. */
    val episode = Regex("(?i)(?:^|[^a-z0-9])S(\\d{1,2})[ ._-]?E(\\d{1,3})(?![0-9])|(?:^|[^0-9])(\\d{1,2})x(\\d{2,3})(?![0-9])")
    val seasonDir = Regex("(?i)^(staffel|season|s)\\s*\\d+$")

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

/** Holt Poster, Beschreibungen und Episodentitel von TMDB und merkt sie sich dauerhaft. */
class MetaRepo(context: Context, private val settings: Settings) {
    private val file = File(context.filesDir, "meta3.json")
    private val cache: JSONObject = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    private val writeLock = Mutex()

    private fun cached(k: String): JSONObject? = synchronized(cache) { cache.optJSONObject(k) }

    /** Holt aus dem Cache oder per [block] aus dem Netz. Netzwerkfehler werden nicht gecacht. */
    private suspend fun fetch(k: String, block: (String) -> JSONObject): JSONObject? {
        cached(k)?.let { return it }
        val apiKey = settings.tmdbKey
        if (apiKey.isBlank()) return null
        val result = try {
            withContext(Dispatchers.IO) { block(apiKey) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
        synchronized(cache) { cache.put(k, result) }
        persist()
        return result
    }

    // ---------- Filme

    private fun movieKey(title: String, year: String?) = "m|${title.lowercase()}|${year.orEmpty()}"

    fun peekMovie(title: String, year: String?): Meta? =
        cached(movieKey(title, year))?.let { toMeta(it, title, year) }

    suspend fun movie(title: String, year: String?): Meta =
        fetch(movieKey(title, year)) { key -> searchMovie(key, title, year) }?.let { toMeta(it, title, year) }
            ?: Meta(title, null, null, year)

    // ---------- Serien

    private fun showKey(name: String, year: String?) = "t|${name.lowercase()}|${year.orEmpty()}"

    fun peekShow(name: String, year: String?): Meta? =
        cached(showKey(name, year))?.let { toMeta(it, name, year) }

    suspend fun show(name: String, year: String?): Meta =
        fetch(showKey(name, year)) { key -> searchShow(key, name, year) }?.let { toMeta(it, name, year) }
            ?: Meta(name, null, null, year)

    fun peekSeason(showId: Int, season: Int): SeasonMeta? =
        if (showId == 0) null else cached("s|$showId|$season")?.let { toSeason(it) }

    suspend fun season(showId: Int, season: Int): SeasonMeta? {
        if (showId == 0) return null
        return fetch("s|$showId|$season") { key -> loadSeason(key, showId, season) }?.let { toSeason(it) }
    }

    fun clear() {
        synchronized(cache) { cache.keys().asSequence().toList().forEach { cache.remove(it) } }
        file.delete()
    }

    // ---------- intern

    private suspend fun persist() = writeLock.withLock {
        withContext(Dispatchers.IO) {
            val text = synchronized(cache) { cache.toString() }
            file.writeText(text)
        }
    }

    private fun toMeta(o: JSONObject, fallbackTitle: String, fallbackYear: String?): Meta =
        if (o.length() == 0) {
            Meta(fallbackTitle, null, null, fallbackYear)
        } else {
            Meta(
                title = o.optString("title").ifEmpty { fallbackTitle },
                poster = o.optString("poster").ifEmpty { null },
                overview = o.optString("overview").ifEmpty { null },
                year = o.optString("year").ifEmpty { fallbackYear },
                id = o.optInt("id"),
                backdrop = o.optString("backdrop").ifEmpty { null },
                rating = o.optDouble("rating").takeIf { !it.isNaN() && it > 0 },
            )
        }

    private fun toSeason(o: JSONObject): SeasonMeta {
        val eps = o.optJSONArray("episodes") ?: JSONArray()
        val map = (0 until eps.length()).associate { i ->
            val e = eps.getJSONObject(i)
            e.optInt("n") to EpisodeMeta(
                e.optString("name"),
                e.optString("still").ifEmpty { null },
                e.optString("overview").ifEmpty { null },
            )
        }
        return SeasonMeta(o.optString("poster").ifEmpty { null }, map)
    }

    private fun JSONObject.str(k: String) = if (isNull(k)) "" else optString(k)

    private fun results(o: JSONObject): List<JSONObject> {
        val a = o.optJSONArray("results") ?: return emptyList()
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun compact(r: JSONObject, fallbackTitle: String): JSONObject {
        val date = r.str("release_date").ifEmpty { r.str("first_air_date") }
        return JSONObject()
            .put("id", r.optInt("id"))
            .put("title", r.str("title").ifEmpty { r.str("name") }.ifEmpty { fallbackTitle })
            .put("poster", r.str("poster_path"))
            .put("backdrop", r.str("backdrop_path"))
            .put("rating", r.optDouble("vote_average", 0.0))
            .put("overview", r.str("overview"))
            .put("year", date.take(4))
    }

    private fun yearOf(r: JSONObject) =
        r.str("release_date").ifEmpty { r.str("first_air_date") }.take(4).toIntOrNull()

    /** Mit Jahr suchen; ohne Treffer nur Ergebnisse mit höchstens 1 Jahr Abweichung akzeptieren. */
    private fun searchMovie(key: String, title: String, year: String?): JSONObject {
        val y = year?.toIntOrNull()
        var hit: JSONObject? = null
        if (y != null) {
            hit = results(get(key, "search/movie", mapOf("query" to title, "primary_release_year" to "$y"))).firstOrNull()
                ?: results(get(key, "search/movie", mapOf("query" to title, "year" to "$y"))).firstOrNull()
        }
        if (hit == null) {
            val all = results(get(key, "search/movie", mapOf("query" to title)))
            hit = if (y == null) all.firstOrNull()
            else all.firstOrNull { r -> yearOf(r)?.let { abs(it - y) <= 1 } == true }
        }
        return hit?.let { compact(it, title) } ?: JSONObject()
    }

    private fun searchShow(key: String, name: String, year: String?): JSONObject {
        var hit: JSONObject? = null
        if (year != null) {
            hit = results(get(key, "search/tv", mapOf("query" to name, "first_air_date_year" to year))).firstOrNull()
        }
        if (hit == null) hit = results(get(key, "search/tv", mapOf("query" to name))).firstOrNull()
        return hit?.let { compact(it, name) } ?: JSONObject()
    }

    private fun loadSeason(key: String, showId: Int, season: Int): JSONObject {
        val o = get(key, "tv/$showId/season/$season", emptyMap())
        val eps = JSONArray()
        val src = o.optJSONArray("episodes") ?: JSONArray()
        for (i in 0 until src.length()) {
            val e = src.getJSONObject(i)
            eps.put(
                JSONObject()
                    .put("n", e.optInt("episode_number"))
                    .put("name", e.str("name"))
                    .put("still", e.str("still_path"))
                    .put("overview", e.str("overview"))
            )
        }
        return JSONObject().put("poster", o.str("poster_path")).put("episodes", eps)
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
