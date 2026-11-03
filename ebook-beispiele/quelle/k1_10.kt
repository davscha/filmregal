class Zaehler(start: Int = 0) {
    var wert = start
        private set                        // von außen nur lesbar

    fun plus() {
        wert++
    }
}

data class Film(
    val titel: String,
    val jahr: Int? = null,
    val genres: List<String> = emptyList(),
    val gesehen: Boolean = false,
) {
    fun anzeige(): String = if (jahr != null) "$titel ($jahr)" else titel
}

sealed interface Video
data class Spielfilm(val titel: String) : Video
data class Folge(val serie: String, val staffel: Int, val nummer: Int) : Video

object Einstellungen {
    var sprache = "de-DE"
}

fun beschreibe(v: Video): String = when (v) {
    is Spielfilm -> "Film: ${v.titel}"
    is Folge -> "Folge: ${v.serie} S${v.staffel}E${v.nummer}"
}

fun main() {
    val z = Zaehler()
    z.plus()
    z.plus()
    println(z.wert)                                         // 2

    val vaiana = Film("Vaiana", 2026, listOf("Abenteuer"))
    val encanto = Film("Encanto", 2021)
    println(vaiana)                                         // Film(titel=Vaiana, jahr=2026, ...)
    println(encanto.anzeige())                              // Encanto (2021)
    println(vaiana == Film("Vaiana", 2026, listOf("Abenteuer")))   // true
    println(vaiana.copy(gesehen = true))                    // Kopie mit einer Änderung

    println(beschreibe(Folge("Sleepy Hollow", 1, 2)))
    println(Einstellungen.sprache)
}
