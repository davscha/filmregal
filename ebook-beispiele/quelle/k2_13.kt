// Datei: Scanner.kt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

val VIDEO_ENDUNGEN = setOf("mkv", "mp4", "avi", "m4v", "webm")
val FOLGEN_MUSTER = Regex("""S(\d{1,2})\s?E(\d{1,3})""", RegexOption.IGNORE_CASE)
val JAHR_MUSTER = Regex("""[\[(]((?:19|20)\d{2})[\])]""")

sealed interface Fund
data class FilmFund(val titel: String, val jahr: String?, val pfad: String) : Fund
data class FolgenFund(val serie: String, val staffel: Int, val nummer: Int, val pfad: String) : Fund

fun erkennen(datei: File): Fund {
    val basis = datei.nameWithoutExtension.replace('.', ' ').replace('_', ' ')
    val m = FOLGEN_MUSTER.find(basis)
    if (m != null) {
        val serie = basis.substring(0, m.range.first).trim(' ', '-').ifEmpty { datei.parentFile.parentFile.name }
        return FolgenFund(serie, m.groupValues[1].toInt(), m.groupValues[2].toInt(), datei.path)
    }
    val jahr = JAHR_MUSTER.find(basis)?.groupValues?.get(1)
    return FilmFund(basis.replace(JAHR_MUSTER, "").trim(), jahr, datei.path)
}

fun scannen(ordner: File): Pair<List<FilmFund>, Map<String, List<FolgenFund>>> {
    val funde = ordner.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in VIDEO_ENDUNGEN }
        .sortedBy { it.path }
        .map(::erkennen)
        .toList()
    val filme = funde.filterIsInstance<FilmFund>()
    val serien = funde.filterIsInstance<FolgenFund>().groupBy { it.serie }
    return filme to serien
}

fun main() {
    val ziel = File(System.getProperty("user.home"), "Filmregal-Test")
    val (filme, serien) = scannen(ziel)
    println("${filme.size} Filme, ${serien.size} Serien")
    filme.forEach { println(" Film: ${it.titel} ${it.jahr}") }
    serien.forEach { (name, folgen) ->
        println(" Serie: $name ${folgen.map { "S%02dE%02d".format(it.staffel, it.nummer) }}")
    }

    val json = JSONObject()
        .put("filme", JSONArray(filme.map { JSONObject().put("titel", it.titel).put("jahr", it.jahr).put("pfad", it.pfad) }))
        .put("serien", JSONObject(serien.mapValues { (_, f) -> f.map { it.pfad } }))
    File("bibliothek.json").writeText(json.toString(2))
}
