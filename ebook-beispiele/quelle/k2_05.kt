sealed interface Video {
    val pfad: String
}

data class Film(override val pfad: String, val titel: String, val jahr: String?) : Video

data class Folge(override val pfad: String, val serie: String, val staffel: Int, val nummer: Int) : Video {
    val code: String get() = "S%02dE%02d".format(staffel, nummer)
}

data class Serie(val name: String, val folgen: List<Folge>) {
    val staffeln: Map<Int, List<Folge>>
        get() = folgen.sortedWith(compareBy({ it.staffel }, { it.nummer })).groupBy { it.staffel }
}

/** Sortiert "Folge 2" vor "Folge 10". */
fun natuerlich(a: String, b: String): Int {
    val teile = Regex("""\d+|\D+""")
    val ta = teile.findAll(a.lowercase()).map { it.value }.toList()
    val tb = teile.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(ta.size, tb.size)) {
        val x = ta[i]
        val y = tb[i]
        val c = if (x[0].isDigit() && y[0].isDigit()) x.toBigInteger().compareTo(y.toBigInteger()) else x.compareTo(y)
        if (c != 0) return c
    }
    return ta.size - tb.size
}

fun main() {
    val videos: List<Video> = listOf(
        Folge("b.mp4", "Sleepy Hollow", 1, 2),
        Film("v.mkv", "Vaiana", "2026"),
        Folge("a.mp4", "Sleepy Hollow", 1, 1),
        Folge("c.mp4", "Sleepy Hollow", 2, 1),
    )
    val filme = videos.filterIsInstance<Film>()
    val serie = Serie("Sleepy Hollow", videos.filterIsInstance<Folge>())
    println(filme.map { it.titel })
    serie.staffeln.forEach { (nr, folgen) -> println("Staffel $nr ${folgen.map { it.code }}") }

    val liste = listOf("Folge 10", "Folge 2", "Folge 1")
    println(liste.sorted())                        // [Folge 1, Folge 10, Folge 2] – falsch
    println(liste.sortedWith(::natuerlich))        // [Folge 1, Folge 2, Folge 10] – richtig

    val (pfad, titel) = filme.first()
    println("$titel liegt in $pfad")
}
