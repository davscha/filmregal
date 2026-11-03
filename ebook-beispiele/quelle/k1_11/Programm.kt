// Datei: Programm.kt
package de.filmregal

import de.filmregal.werkzeuge.istVideo
import java.io.File
import kotlin.math.roundToInt

fun main() {
    println(istVideo("Vaiana [2026].mkv"))           // true
    println(File(".").absolutePath)                   // aktueller Ordner
    println(System.getProperty("user.home"))          // dein Benutzerordner
    println(7.6.roundToInt())                         // 8
}
