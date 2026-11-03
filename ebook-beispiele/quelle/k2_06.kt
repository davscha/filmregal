import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

fun dauer(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sek = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sek) else "%d:%02d".format(m, sek)
}

fun main() {
    val jetzt = LocalDateTime.now()
    println(jetzt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")))   // 03.11.2026 14:30

    val restMs = 97 * 60_000L
    val ende = LocalTime.now().plusSeconds(restMs / 1000)
    println("Film endet um " + ende.format(DateTimeFormatter.ofPattern("HH:mm")))

    val hinzugefuegt = Instant.ofEpochMilli(1_762_171_200_000L).atZone(ZoneId.systemDefault())
    println("${hinzugefuegt.toLocalDate()} – ${ChronoUnit.DAYS.between(hinzugefuegt.toLocalDate(), jetzt.toLocalDate())} Tage her")

    val start = System.currentTimeMillis()
    Thread.sleep(500)
    println("Dauer: ${System.currentTimeMillis() - start} ms")

    println("${dauer(6_045_000)} ${dauer(250_000)}")              // 1:40:45 4:10
}
