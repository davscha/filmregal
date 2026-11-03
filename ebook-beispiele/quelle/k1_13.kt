// Datei: Filmliste.kt
data class Film(val titel: String, val jahr: Int, var gesehen: Boolean = false)

fun zeige(filme: List<Film>) {
    filme.forEachIndexed { i, f ->
        val haken = if (f.gesehen) "✓" else " "
        println("%2d. [%s] %s (%d)".format(i + 1, haken, f.titel, f.jahr))
    }
}

fun main() {
    val filme = mutableListOf(Film("Vaiana", 2026), Film("Encanto", 2021), Film("2012", 2009))
    while (true) {
        println()
        zeige(filme.sortedBy { it.titel.lowercase() })
        print("\n[n]eu, [g]esehen, [q]uit: ")
        when (readlnOrNull()?.trim()?.lowercase()) {
            "q", null -> break
            "n" -> {
                print("Titel: ")
                val titel = readln().trim()
                print("Jahr: ")
                val jahr = readln().trim().toIntOrNull()
                if (jahr == null) {
                    println("Bitte eine Zahl eingeben.")
                    continue
                }
                filme.add(Film(titel, jahr))
            }
            "g" -> {
                print("Welcher Titel? ")
                val suche = readln().trim().lowercase()
                filme.filter { it.titel.lowercase() == suche }.forEach { it.gesehen = true }
            }
        }
    }
}
