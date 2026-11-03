fun dauerText(minuten: Int): String {
    val stunden = minuten / 60
    val rest = minuten % 60
    return if (stunden == 0) "$rest min" else "$stunden h $rest min"
}

fun begruessung(name: String, sprache: String = "de"): String =
    if (sprache == "en") "Hello $name" else "Hallo $name"

/** Erweiterungsfunktion: "Vaiana [2026].mkv".istVideo() */
fun String.istVideo(): Boolean = substringAfterLast('.').lowercase() in setOf("mkv", "mp4", "avi")

fun main() {
    println(dauerText(135))                         // 2 h 15 min
    println(dauerText(45))                          // 45 min
    println(begruessung("Anna"))                   // Hallo Anna
    println(begruessung("Anna", sprache = "en"))   // Hello Anna
    println("Vaiana [2026].MKV".istVideo())         // true

    val quadrat = { x: Int -> x * x }               // Lambda in einer Variablen
    println(quadrat(7))                             // 49
    println(listOf("Encanto", "2012", "Anaconda").sortedBy { it.length })
}
