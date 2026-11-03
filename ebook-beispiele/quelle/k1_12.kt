fun jahrLesen(text: String): Int? {
    val jahr = try {
        text.toInt()
    } catch (e: NumberFormatException) {
        println("'$text' ist keine Zahl")
        return null
    }
    require(jahr in 1900..2100) { "Unplausibles Jahr: $jahr" }
    return jahr
}

fun main() {
    println(jahrLesen("2009"))
    println(jahrLesen("zweitausend"))

    try {
        jahrLesen("1492")
    } catch (e: IllegalArgumentException) {
        println("Fehler: ${e.message}")
    } finally {
        println("Fertig geprüft.")
    }

    // Kurzformen ohne try/catch
    println("2021".toIntOrNull())                     // 2021
    println("abc".toIntOrNull() ?: 0)                 // 0
    val ergebnis = runCatching { "x".toInt() }
    println(ergebnis.isFailure)                       // true
}
