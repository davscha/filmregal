import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

const val API_KEY = "DEIN_TMDB_SCHLUESSEL"

fun filmSuchen(titel: String, jahr: Int? = null): JSONObject? {
    val params = buildMap {
        put("api_key", API_KEY)
        put("query", titel)
        put("language", "de-DE")
        if (jahr != null) put("primary_release_year", jahr.toString())
    }
    val query = params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }
    val verbindung = URL("https://api.themoviedb.org/3/search/movie?$query").openConnection() as HttpURLConnection
    verbindung.connectTimeout = 10_000
    verbindung.readTimeout = 10_000
    return try {
        if (verbindung.responseCode != 200) throw IOException("TMDB antwortet ${verbindung.responseCode}")
        val text = verbindung.inputStream.bufferedReader().readText()
        JSONObject(text).getJSONArray("results").optJSONObject(0)
    } catch (e: IOException) {
        println("Keine Antwort von TMDB: ${e.message}")
        null
    } finally {
        verbindung.disconnect()
    }
}

fun main() {
    val film = filmSuchen("Vaiana", 2026)
    if (film != null) {
        println("${film.getString("title")} ${film.optString("release_date")} ${film.optDouble("vote_average")}")
        println("Poster: https://image.tmdb.org/t/p/w342" + film.optString("poster_path"))
    }
}
