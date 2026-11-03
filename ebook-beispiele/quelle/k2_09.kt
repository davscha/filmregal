import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

suspend fun posterLaden(titel: String): String {
    delay(1000)                          // tut so, als würde es aus dem Internet laden
    return "$titel.jpg"
}

suspend fun dateiLesen(): String = withContext(Dispatchers.IO) {
    Thread.sleep(300)                    // blockierende Arbeit gehört auf Dispatchers.IO
    "Inhalt"
}

fun main() = runBlocking {
    // 1) Nebenher etwas starten
    val job = launch {
        println("Lade im Hintergrund …")
        println("Fertig: ${posterLaden("Vaiana")}")
    }
    println("Das Hauptprogramm läuft weiter!")
    job.join()

    // 2) Viele Aufgaben parallel
    val titel = listOf("Vaiana", "Encanto", "Lightyear", "2012")
    val zeit = measureTimeMillis {
        val ergebnisse = titel.map { t -> async { posterLaden(t) } }.awaitAll()
        println(ergebnisse)
    }
    println("in ${zeit / 1000.0} s statt ${titel.size} s")

    // 3) Blockierendes auf einen anderen Thread schieben
    println(dateiLesen())

    // 4) Gemeinsame Daten schützen
    var zaehler = 0
    val schloss = Mutex()
    List(4) {
        launch(Dispatchers.Default) {
            repeat(10_000) { schloss.withLock { zaehler++ } }
        }
    }.forEach { it.join() }
    println("Zähler: $zaehler")          // immer 40000
}
