import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Einstellungen(
    val adresse: String = "192.168.178.1",
    val startordner: String = "",
    val benutzer: String = "",
)

fun Einstellungen.toJson(): JSONObject = JSONObject()
    .put("adresse", adresse)
    .put("startordner", startordner)
    .put("benutzer", benutzer)

fun einstellungenAus(o: JSONObject) = Einstellungen(
    adresse = o.optString("adresse", "192.168.178.1"),
    startordner = o.optString("startordner"),
    benutzer = o.optString("benutzer"),
)

fun main() {
    val datei = File("einstellungen.json")

    // Speichern
    val e = Einstellungen(startordner = "Medien/Filme", benutzer = "kino")
    datei.writeText(e.toJson().toString(2))

    // Laden – mit Rückfall auf Standardwerte
    val geladen = try {
        einstellungenAus(JSONObject(datei.readText()))
    } catch (ex: Exception) {
        Einstellungen()
    }
    println(geladen)

    // JSON-Text <-> Objekte
    val film = JSONObject("""{"titel": "Encanto", "jahr": 2021, "genres": ["Animation", "Familie"]}""")
    val genres: JSONArray = film.getJSONArray("genres")
    println("${film.getString("titel")} ${film.getInt("jahr")} ${genres.getString(0)}")
    println(film.optString("regie", "unbekannt"))
    println(JSONArray(listOf("Vaiana", "Encanto")).toString())
}
