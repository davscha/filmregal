package de.filmregal

import android.net.Uri
import jcifs.smb.SmbRandomAccessFile
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Kleiner HTTP-Server auf dem Tablet: libVLC holt das Video per http://127.0.0.1,
 * wir lesen es mit derselben SMB-Anmeldung wie die Ordnerliste vom NAS.
 * So funktioniert die Wiedergabe auch bei alten FritzBoxen, mit denen
 * libVLCs eigener SMB-Zugriff nicht klarkommt. Spulen geht über HTTP-Range.
 */
object StreamServer {
    @Volatile private var server: ServerSocket? = null
    @Volatile private var cfg: NasConfig? = null

    @Synchronized
    fun url(config: NasConfig, path: String): String {
        cfg = config
        val s = server ?: ServerSocket(0, 16, InetAddress.getByName("127.0.0.1")).also { srv ->
            server = srv
            thread(isDaemon = true, name = "filmregal-stream") {
                while (!srv.isClosed) {
                    val client = try { srv.accept() } catch (e: Exception) { break }
                    thread(isDaemon = true) { handle(client) }
                }
            }
        }
        val encoded = path.split('/').joinToString("/") { Uri.encode(it) }
        return "http://127.0.0.1:${s.localPort}/$encoded"
    }

    private fun handle(socket: Socket) = socket.use {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.ISO_8859_1))
            val requestLine = reader.readLine() ?: return@use
            var range: String? = null
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
                if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrNull(0) ?: "GET"
            val path = Uri.decode(parts.getOrNull(1)?.removePrefix("/")?.substringBefore('?') ?: "")
            val out = socket.getOutputStream()
            val config = cfg ?: return@use respond(out, "503 Service Unavailable")

            val (file, length) = try {
                Nas.openFile(config, path)
            } catch (e: Exception) {
                return@use respond(out, "404 Not Found")
            }

            var start = 0L
            var end = length - 1
            if (range != null && range.startsWith("bytes=")) {
                val spec = range.removePrefix("bytes=").substringBefore(',')
                val a = spec.substringBefore('-').trim()
                val b = spec.substringAfter('-', "").trim()
                if (a.isEmpty() && b.isNotEmpty()) {
                    start = (length - b.toLong()).coerceAtLeast(0)
                } else {
                    start = a.toLongOrNull() ?: 0L
                    if (b.isNotEmpty()) end = minOf(b.toLong(), length - 1)
                }
            }
            if (start >= length && length > 0) {
                return@use respond(out, "416 Range Not Satisfiable", "Content-Range: bytes */$length\r\n")
            }
            val count = end - start + 1
            val header = StringBuilder()
                .append(if (range != null) "HTTP/1.1 206 Partial Content\r\n" else "HTTP/1.1 200 OK\r\n")
                .append("Content-Type: ").append(mimeFor(path)).append("\r\n")
                .append("Accept-Ranges: bytes\r\n")
                .append("Content-Length: ").append(count).append("\r\n")
                .apply { if (range != null) append("Content-Range: bytes $start-$end/$length\r\n") }
                .append("Connection: close\r\n\r\n")
            out.write(header.toString().toByteArray(Charsets.ISO_8859_1))
            if (method == "HEAD") {
                out.flush()
                file.close()
                return@use
            }

            SmbRandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buf = ByteArray(512 * 1024)
                var left = count
                while (left > 0) {
                    val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    left -= n
                }
            }
            out.flush()
            file.close()
        } catch (e: Exception) {
            // Player hat die Verbindung beendet (z. B. beim Spulen) – normal
        }
    }

    private fun respond(out: OutputStream, status: String, extra: String = "") {
        out.write("HTTP/1.1 $status\r\n${extra}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
        out.flush()
    }

    private fun mimeFor(path: String) = when (path.substringAfterLast('.').lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts", "m2ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }
}
