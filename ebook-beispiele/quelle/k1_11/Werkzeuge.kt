// Datei: werkzeuge/Werkzeuge.kt
package de.filmregal.werkzeuge

fun istVideo(dateiname: String): Boolean =
    dateiname.substringAfterLast('.').lowercase() in setOf("mkv", "mp4", "avi")

internal fun nurImModul() = "nur innerhalb dieses Moduls sichtbar"

private fun nurInDieserDatei() = "nur in Werkzeuge.kt sichtbar"
