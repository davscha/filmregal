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
    /** Poster wurde von Hand gewählt → hat Vorrang vor Staffelpostern. */
    val customPoster: Boolean = false,
    val genres: List<Int> = emptyList(),
)

data class CastMember(val id: Int, val name: String, val character: String, val profile: String?)
data class Credits(val cast: List<CastMember>, val directors: List<String>)
data class Person(val id: Int, val name: String, val profile: String?, val knownFor: String?)

const val TMDB_PROFILE = "https://image.tmdb.org/t/p/w185"

/** TMDB-Genre-IDs (Filme und Serien) auf Deutsch. */
object Genres {
    val names = mapOf(
        28 to "Action", 12 to "Abenteuer", 16 to "Animation", 35 to "Komödie", 80 to "Krimi",
        99 to "Dokumentarfilm", 18 to "Drama", 10751 to "Familie", 14 to "Fantasy", 36 to "Historie",
        27 to "Horror", 10402 to "Musik", 9648 to "Mystery", 10749 to "Liebesfilm", 878 to "Science Fiction",
        10770 to "TV-Film", 53 to "Thriller", 10752 to "Kriegsfilm", 37 to "Western",
        10759 to "Action & Abenteuer", 10762 to "Kinder", 10763 to "Nachrichten", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Seifenoper", 10767 to "Talk", 10768 to "Krieg & Politik",
    )

    fun of(ids: List<Int>) = ids.mapNotNull { names[it] }
}

/** Ein Suchtreffer im Editor. */
data class Candidate(val title: String, val year: String?, val poster: String?, val overview: String?, val json: JSONObject)

/** Wird erhöht, wenn im Editor etwas geändert wurde → Kacheln und Infoseiten laden neu. */
object MetaVersion { val tick = androidx.compose.runtime.mutableIntStateOf(0) }

data class EpisodeMeta(val name: String, val still: String?, val overview: String?, val minutes: Int? = null)
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
    private val file = File(context.filesDir, "meta4.json")
    private val cache: JSONObject = try { JSONObject(file.readText()) } catch (e: Exception) { JSONObject() }
    private val writeLock = Mutex()

    /** Von Hand gesetzte Zuordnungen – bleiben auch bei "Alles neu laden" erhalten. */
    private val overrideFile = File(context.filesDir, "overrides.json")
    private val overrides: JSONObject = try { JSONObject(overrideFile.readText()) } catch (e: Exception) { JSONObject() }

    fun overrideKey(v: Video) = when (v) {
        is Movie -> "m|${v.path}"
        is Episode -> "t|${v.show.lowercase()}"
    }

    private fun override(k: String): JSONObject? = synchronized(overrides) { overrides.optJSONObject(k) }

    fun hasOverride(v: Video) = override(overrideKey(v)) != null

    private fun saveOverride(k: String, o: JSONObject?) {
        synchronized(overrides) {
            if (o == null) overrides.remove(k) else overrides.put(k, o)
            overrideFile.writeText(overrides.toString())
        }
        MetaVersion.tick.intValue++
    }

    /** Treffer aus der Suche übernehmen. */
    fun assign(v: Video, c: Candidate) = saveOverride(overrideKey(v), JSONObject(c.json.toString()))

    /** Keine Infos anzeigen, nur den Dateinamen. */
    fun removeInfo(v: Video) = saveOverride(overrideKey(v), JSONObject().put("none", true))

    /** Zurück zur automatischen Erkennung. */
    fun resetOverride(v: Video) = saveOverride(overrideKey(v), null)

    /** Anderes Poster oder Hintergrundbild wählen ([kind] = "poster" oder "backdrop"). */
    fun setImage(v: Video, current: Meta, kind: String, path: String) {
        val k = overrideKey(v)
        val base = override(k)?.let { JSONObject(it.toString()) } ?: metaJson(current)
        base.put(kind, path)
        if (kind == "poster") base.put("custom", true)
        saveOverride(k, base)
    }

    private fun metaJson(m: Meta) = JSONObject()
        .put("id", m.id)
        .put("title", m.title)
        .put("poster", m.poster.orEmpty())
        .put("backdrop", m.backdrop.orEmpty())
        .put("overview", m.overview.orEmpty())
        .put("year", m.year.orEmpty())
        .put("rating", m.rating ?: 0.0)
        .put("genres", JSONArray(m.genres))

    private fun overrideMeta(k: String, fallbackTitle: String, fallbackYear: String?): Meta? {
        val o = override(k) ?: return null
        if (o.optBoolean("none")) return Meta(fallbackTitle, null, null, fallbackYear)
        return toMeta(o, fallbackTitle, fallbackYear)
    }

    fun peekMovie(m: Movie): Meta? =
        overrideMeta("m|${m.path}", m.title, m.year) ?: peekMovie(m.title, m.year)

    suspend fun movie(m: Movie): Meta =
        overrideMeta("m|${m.path}", m.title, m.year) ?: movie(m.title, m.year)

    suspend fun search(kind: String, query: String, year: String?): List<Candidate> {
        val apiKey = settings.tmdbKey
        if (apiKey.isBlank() || query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            val path = if (kind == "tv") "search/tv" else "search/movie"
            val yearParam = if (kind == "tv") "first_air_date_year" else "year"
            var list = if (!year.isNullOrBlank()) {
                results(get(apiKey, path, mapOf("query" to query, yearParam to year)))
            } else emptyList()
            if (list.isEmpty()) list = results(get(apiKey, path, mapOf("query" to query)))
            list.take(25).map { r ->
                val c = compact(r, query)
                Candidate(
                    c.optString("title"),
                    c.optString("year").ifEmpty { null },
                    c.optString("poster").ifEmpty { null },
                    c.optString("overview").ifEmpty { null },
                    c,
                )
            }
        }
    }

    /** Besetzung und Regie. [kind] = "movie" oder "tv". */
    suspend fun credits(kind: String, id: Int): Credits? {
        if (id == 0) return null
        val o = fetch("c|$kind|$id") { key ->
            val src = get(key, if (kind == "tv") "tv/$id/aggregate_credits" else "movie/$id/credits", emptyMap())
            val cast = JSONArray()
            val a = src.optJSONArray("cast") ?: JSONArray()
            for (i in 0 until minOf(a.length(), 30)) {
                val c = a.getJSONObject(i)
                val role = c.str("character").ifEmpty {
                    c.optJSONArray("roles")?.optJSONObject(0)?.let { r -> if (r.isNull("character")) "" else r.optString("character") }.orEmpty()
                }
                cast.put(
                    JSONObject()
                        .put("id", c.optInt("id"))
                        .put("name", c.str("name"))
                        .put("character", role)
                        .put("profile", c.str("profile_path"))
                )
            }
            val directors = JSONArray()
            val crew = src.optJSONArray("crew") ?: JSONArray()
            for (i in 0 until crew.length()) {
                val c = crew.getJSONObject(i)
                val job = c.str("job").ifEmpty {
                    c.optJSONArray("jobs")?.optJSONObject(0)?.let { j -> if (j.isNull("job")) "" else j.optString("job") }.orEmpty()
                }
                if (job == "Director" && kind == "movie") directors.put(c.str("name"))
            }
            JSONObject().put("cast", cast).put("directors", directors)
        } ?: return null
        val cast = o.optJSONArray("cast") ?: JSONArray()
        val dirs = o.optJSONArray("directors") ?: JSONArray()
        return Credits(
            (0 until cast.length()).map { i ->
                val c = cast.getJSONObject(i)
                CastMember(c.optInt("id"), c.optString("name"), c.optString("character"), c.optString("profile").ifEmpty { null })
            },
            (0 until dirs.length()).map { dirs.optString(it) }.distinct(),
        )
    }

    /** Schauspieler suchen (nicht gecacht, Ergebnisse ändern sich). */
    suspend fun searchPeople(query: String): List<Person> {
        val apiKey = settings.tmdbKey
        if (apiKey.isBlank() || query.isBlank()) return emptyList()
        return withContext(Dispatchers.IO) {
            results(get(apiKey, "search/person", mapOf("query" to query)))
                .filter { it.str("known_for_department").let { d -> d.isEmpty() || d == "Acting" } }
                .take(15)
                .map { r ->
                    val known = r.optJSONArray("known_for")?.let { a ->
                        (0 until a.length()).map { a.getJSONObject(it) }
                            .map { it.str("title").ifEmpty { it.str("name") } }
                            .filter { it.isNotBlank() }.take(2).joinToString(", ")
                    }
                    Person(r.optInt("id"), r.str("name"), r.str("profile_path").ifEmpty { null }, known?.ifEmpty { null })
                }
        }
    }

    /** Alle Filme und Serien einer Person als "movie:123" / "tv:456". */
    suspend fun personCredits(personId: Int): Set<String> {
        val o = fetch("p|$personId") { key ->
            val src = get(key, "person/$personId/combined_credits", emptyMap())
            val ids = JSONArray()
            val cast = src.optJSONArray("cast") ?: JSONArray()
            for (i in 0 until cast.length()) {
                val c = cast.getJSONObject(i)
                ids.put(c.str("media_type") + ":" + c.optInt("id"))
            }
            JSONObject().put("ids", ids)
        } ?: return emptySet()
        val a = o.optJSONArray("ids") ?: JSONArray()
        return (0 until a.length()).map { a.optString(it) }.toSet()
    }

    /** Alle Poster bzw. Hintergrundbilder eines Films oder einer Serie. */
    suspend fun images(kind: String, id: Int, which: String): List<String> {
        val apiKey = settings.tmdbKey
        if (apiKey.isBlank() || id == 0) return emptyList()
        return withContext(Dispatchers.IO) {
            val o = get(apiKey, "$kind/$id/images", mapOf("include_image_language" to "de,en,null"))
            val a = o.optJSONArray(which) ?: JSONArray()
            (0 until a.length()).mapNotNull { a.getJSONObject(it).optString("file_path").ifEmpty { null } }
        }
    }

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
        overrideMeta("t|${name.lowercase()}", name, year)
            ?: cached(showKey(name, year))?.let { toMeta(it, name, year) }

    suspend fun show(name: String, year: String?): Meta =
        overrideMeta("t|${name.lowercase()}", name, year)
            ?: fetch(showKey(name, year)) { key -> searchShow(key, name, year) }?.let { toMeta(it, name, year) }
            ?: Meta(name, null, null, year)

    fun peekSeason(showId: Int, season: Int): SeasonMeta? =
        if (showId == 0) null else cached("s|$showId|$season")?.let { toSeason(it) }

    suspend fun season(showId: Int, season: Int): SeasonMeta? {
        if (showId == 0) return null
        val k = "s|$showId|$season"
        // Ältere Zwischenspeicher-Einträge kennen die Laufzeit noch nicht → einmal neu laden
        cached(k)?.let { c ->
            if (c.has("episodes") && !c.has("v2")) synchronized(cache) { cache.remove(k) }
        }
        return fetch(k) { key -> loadSeason(key, showId, season) }?.let { toSeason(it) }
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
                customPoster = o.optBoolean("custom"),
                genres = o.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optInt(it) } }.orEmpty(),
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
                e.optInt("rt").takeIf { it > 0 },
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
            .put("genres", r.optJSONArray("genre_ids") ?: JSONArray())
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
                    .put("rt", e.optInt("runtime", 0))
            )
        }
        return JSONObject().put("poster", o.str("poster_path")).put("episodes", eps).put("v2", true)
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
