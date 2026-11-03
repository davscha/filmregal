import java.io.File

fun main() {
    val ordner = File(System.getProperty("user.home"), "Filmregal-Test")
    ordner.mkdirs()

    File(ordner, "Vaiana [2026].mkv").createNewFile()
    val serie = File(ordner, "Serien/Sleepy Hollow").apply { mkdirs() }
    File(serie, "Sleepy Hollow S01E01.mp4").createNewFile()
    File(ordner, "notiz.txt").writeText("Nicht vergessen: Popcorn")

    ordner.listFiles()?.forEach { eintrag ->
        val art = if (eintrag.isDirectory) "Ordner" else "Datei"
        println("$art: ${eintrag.name}")
    }

    ordner.walkTopDown()
        .filter { it.isFile && it.extension.lowercase() in setOf("mkv", "mp4") }
        .forEach { println("${it.nameWithoutExtension} | ${it.extension} | ${it.parentFile.name}") }

    println(File(ordner, "notiz.txt").readText())
    println(System.getenv("USERNAME") ?: System.getenv("USER"))
}
