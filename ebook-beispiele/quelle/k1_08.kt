fun main() {
    val dateien = listOf("Vaiana [2026].mkv", "cover.jpg", "Encanto [2021].mp4", "notizen.txt")
    val videos = setOf("mkv", "mp4")

    for (datei in dateien) {
        val endung = datei.substringAfterLast('.')
        if (endung in videos) {
            println("Video: $datei")
        } else if (endung == "jpg") {
            println("Bild: $datei")
        } else {
            println("Übersprungen: $datei")
        }
    }

    // when: das bessere switch – und ein Ausdruck mit Ergebnis
    for (datei in dateien) {
        val art = when (datei.substringAfterLast('.')) {
            "mkv", "mp4" -> "Video"
            "jpg", "png" -> "Bild"
            else -> "Sonstiges"
        }
        println("$art: $datei")
    }

    for (nummer in 1..3) println("Folge $nummer")          // 1, 2, 3
    for (nummer in 10 downTo 8) println("Countdown $nummer")

    var versuche = 0
    while (versuche < 3) versuche++
    println("Versuche: $versuche")

    // filter und map: Listen in einer Zeile umbauen
    val nurVideos = dateien.filter { it.substringAfterLast('.') in videos }
    val ohneEndung = nurVideos.map { it.substringBeforeLast('.') }
    println(nurVideos)
    println(ohneEndung)
}
