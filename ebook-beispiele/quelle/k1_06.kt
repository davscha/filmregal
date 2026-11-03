fun main() {
    val titel = "Vaiana"          // String  – Text
    val jahr = 2026               // Int     – ganze Zahl
    val bewertung = 7.5           // Double  – Kommazahl
    var gesehen = false           // Boolean – wahr oder falsch
    val dauerMs = 6_900_000L      // Long    – große ganze Zahl
    var poster: String? = null    // String? – Text oder "nichts"

    gesehen = true                // var darf sich ändern, val nicht
    println(titel.uppercase())                       // VAIANA
    println(titel.length)                            // 6
    println("$titel ($jahr) – ${"%.1f".format(bewertung)} Sterne, gesehen: $gesehen")

    val alter = 2026 - 2016
    val jahrAlsText = jahr.toString()
    val zahlAusText = "2009".toInt()
    println("$alter ${jahrAlsText + "!"} ${zahlAusText + 1} ${dauerMs / 60_000} min")

    println(poster?.length)                          // null – kein Absturz
    println(poster ?: "kein Poster")                 // Ersatzwert
    poster = "/abc.jpg"
    if (poster != null) println(poster.length)       // hier weiß Kotlin: nicht null
}
