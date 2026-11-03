fun main() {
    // Liste: geordnet – listOf ist unveränderlich, mutableListOf veränderbar
    val filme = mutableListOf("Vaiana", "Encanto")
    filme.add("Lightyear")
    println("${filme[0]} ${filme.last()} ${filme.size}")   // Vaiana Lightyear 3
    println(filme.drop(1))                                 // [Encanto, Lightyear]

    // Map: Schlüssel → Wert
    val film = mutableMapOf<String, Any>("titel" to "Vaiana", "jahr" to 2026)
    film["bewertung"] = 7.5
    println("${film["titel"]} ${film.getOrDefault("regie", "unbekannt")}")

    // Pair: zwei zusammengehörige Werte
    val aufloesung = 1920 to 1080
    val (breite, hoehe) = aufloesung
    println("$breite x $hoehe")

    // Set: ohne Doppelte
    val endungen = setOf("mkv", "mp4", "avi", "mp4")
    println("$endungen ${"mkv" in endungen}")
}
