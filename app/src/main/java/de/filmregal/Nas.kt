package de.filmregal

import android.net.Uri
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg")

object Nas {
    /**
     * jcifs-ng spricht SMB1 bis SMB3. SMB1 ist nötig für ältere FritzBoxen
     * (z. B. 7362 SL mit FRITZ!OS 6), die kein SMB2 können.
     */
    private val base: CIFSContext by lazy {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB1")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.responseTimeout", "15000")
            setProperty("jcifs.smb.client.soTimeout", "20000")
            setProperty("jcifs.smb.client.sessionTimeout", "20000")
            setProperty("jcifs.netbios.retryTimeout", "3000")
            setProperty("jcifs.resolveOrder", "DNS")
        }
        BaseContext(PropertyConfiguration(props))
    }

    /** Prüft Schritt für Schritt: Namensauflösung, Ports, Anmeldung, Ordnerliste. */
    suspend fun diagnose(cfg: NasConfig, report: (String) -> Unit) = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        fun say(s: String) { lines += s; report(lines.joinToString("\n")) }

        val ip = try {
            java.net.InetAddress.getByName(cfg.host).hostAddress.also { say("✓ ${cfg.host} → $it") }
        } catch (e: Exception) {
            say("✗ Adresse ${cfg.host} nicht gefunden – probier 192.168.178.1"); return@withContext
        }
        for (port in listOf(445, 139)) {
            try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 4000) }
                say("✓ Port $port offen")
            } catch (e: Exception) {
                say("✗ Port $port geschlossen (${e.javaClass.simpleName})")
            }
        }
        say("… Anmeldung als ${cfg.user.ifBlank { "Gast" }} und Ordner lesen")
        val start = System.currentTimeMillis()
        try {
            val list = list(cfg, cfg.root)
            val dirs = list.filter { it.isDir }.map { it.name }
            say("✓ Verbunden nach ${(System.currentTimeMillis() - start) / 1000} s. ${dirs.size} Ordner: ${dirs.joinToString(", ")}")
        } catch (e: Exception) {
            var t: Throwable? = e
            val chain = mutableListOf<String>()
            while (t != null && chain.size < 4) { chain += "${t.javaClass.simpleName}: ${t.message}"; t = t.cause }
            say("✗ Fehler nach ${(System.currentTimeMillis() - start) / 1000} s:\n" + chain.joinToString("\n"))
        }
    }

    private fun context(cfg: NasConfig): CIFSContext =
        if (cfg.user.isBlank()) base.withGuestCrendentials()
        else base.withCredentials(NtlmPasswordAuthenticator("", cfg.user, cfg.password))

    private fun smbUrl(cfg: NasConfig, path: String): String {
        val p = listOf(cfg.share, path).filter { it.isNotEmpty() }.joinToString("/")
        return "smb://${cfg.host}/$p/"
    }

    /** Listet Unterordner und Videodateien eines NAS-Ordners. */
    suspend fun list(cfg: NasConfig, path: String): List<Entry> = withContext(Dispatchers.IO) {
        SmbFile(smbUrl(cfg, path), context(cfg)).use { dir ->
            dir.listFiles().mapNotNull { f ->
                val isDir = f.name.endsWith("/")
                val name = f.name.trimEnd('/')
                f.close()
                if (name.startsWith(".") || name.startsWith("@") || name.endsWith("$")) return@mapNotNull null
                if (!isDir && name.substringAfterLast('.', "").lowercase() !in VIDEO_EXTENSIONS) return@mapNotNull null
                Entry(name, joinPath(path, name), isDir)
            }.sortedWith { a, b ->
                if (a.isDir != b.isDir) (if (a.isDir) -1 else 1) else naturalCompare(a.name, b.name)
            }
        }
    }

    /** smb://-Adresse für den Player (libVLC streamt direkt vom NAS). */
    fun url(cfg: NasConfig, path: String): String {
        val credentials = if (cfg.user.isBlank()) "" else Uri.encode(cfg.user) + ":" + Uri.encode(cfg.password) + "@"
        val segments = (listOf(cfg.share) + path.split('/')).joinToString("/") { Uri.encode(it) }
        return "smb://$credentials${cfg.host}/$segments"
    }
}
