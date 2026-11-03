val JAHR = Regex("""[\[(]((?:19|20)\d{2})[\])]""")
val FOLGE = Regex("""S(\d{1,2})\s?E(\d{1,3})""", RegexOption.IGNORE_CASE)

fun main() {
    val namen = listOf("Vaiana [2026].mp4", "2012 (2009).mkv", "Sleepy Hollow S01E01.mp4", "Lost s2e5.mkv", "Encanto.mkv")
    for (name in namen) {
        val basis = name.substringBeforeLast('.')
        val folge = FOLGE.find(basis)
        if (folge != null) {
            val serie = basis.substring(0, folge.range.first).trim()
            val (staffel, nummer) = folge.destructured
            println("Folge:  $serie – Staffel ${staffel.toInt()}, Folge ${nummer.toInt()}")
            continue
        }
        val jahr = JAHR.find(basis)?.groupValues?.get(1)
        val titel = basis.replace(JAHR, "").trim()
        println("Film:   $titel – Jahr ${jahr ?: "unbekannt"}")
    }

    println(Regex("""\d+""").findAll("Staffel 2, Folge 14").map { it.value }.toList())   // [2, 14]
    println("Toy.Story.5.German.2160p".replace(Regex("[._]+"), " "))                     // Toy Story 5 German 2160p
    println(FOLGE.containsMatchIn("serie.s03e07.mkv"))                                   // true
}
