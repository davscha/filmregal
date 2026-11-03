// Datei: MiniServer.kt
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import kotlin.concurrent.thread

val DATEI = File("test.bin").apply { writeBytes(ByteArray(1024) { (it % 256).toByte() }) }

fun beantworten(socket: Socket) = socket.use {
    val leser = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1)
    leser.readLine() ?: return@use                      // z. B. "GET / HTTP/1.1"
    var bereich: String? = null
    while (true) {
        val zeile = leser.readLine() ?: break
        if (zeile.isEmpty()) break                      // Leerzeile = Ende der Kopfzeilen
        if (zeile.startsWith("Range:", ignoreCase = true)) bereich = zeile.substringAfter(':').trim()
    }
    val groesse = DATEI.length()
    var start = 0L
    var ende = groesse - 1
    if (bereich != null && bereich.startsWith("bytes=")) {
        val von = bereich.removePrefix("bytes=").substringBefore('-')
        val bis = bereich.substringAfter('-', "")
        start = von.toLongOrNull() ?: 0L
        if (bis.isNotEmpty()) ende = bis.toLong()
    }
    val kopf = buildString {
        append(if (bereich != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
        append("Accept-Ranges: bytes\r\n")
        append("Content-Length: ${ende - start + 1}\r\n")
        if (bereich != null) append("Content-Range: bytes $start-$ende/$groesse\r\n")
        append("Content-Type: application/octet-stream\r\n\r\n")
    }
    val aus = socket.getOutputStream()
    aus.write(kopf.toByteArray(Charsets.ISO_8859_1))
    RandomAccessFile(DATEI, "r").use { raf ->
        raf.seek(start)
        val puffer = ByteArray((ende - start + 1).toInt())
        raf.readFully(puffer)
        aus.write(puffer)
    }
    aus.flush()
}

fun main() {
    val server = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))   // Port 0 = freien Port wählen
    thread(isDaemon = true) {
        while (!server.isClosed) {
            val client = runCatching { server.accept() }.getOrNull() ?: break
            thread(isDaemon = true) { beantworten(client) }
        }
    }
    println("Server läuft auf Port ${server.localPort}")

    val c = URL("http://127.0.0.1:${server.localPort}/").openConnection() as HttpURLConnection
    c.setRequestProperty("Range", "bytes=100-103")
    println("${c.responseCode} ${c.getHeaderField("Content-Range")} ${c.inputStream.readBytes().toList()}")
    server.close()                                     // 206 bytes 100-103/1024 [100, 101, 102, 103]
}
