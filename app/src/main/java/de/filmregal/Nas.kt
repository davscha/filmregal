package de.filmregal

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/** Ordner, die Windows bzw. die FritzBox selbst anlegen. */
private val SYSTEM_FOLDERS = setOf("system volume information", "fritz", "lost+found", "recycler", "found.000")

val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg")

object Nas {
    /**
     * Ältere FritzBoxen können nur SMB1 und oft nur das alte
     * NTLMv1-Anmeldeverfahren. Deshalb probieren wir mehrere Varianten durch
     * und merken uns die erste, die klappt.
     */
    private class Variant(val label: String, val props: Map<String, String>)

    private val variants = listOf(
        Variant("SMB1–3, NTLMv2", mapOf("jcifs.smb.lmCompatibility" to "3")),
        Variant("SMB1–3, NTLMv1", mapOf("jcifs.smb.lmCompatibility" to "1")),
        Variant(
            "SMB1, NTLMv1",
            mapOf(
                "jcifs.smb.client.maxVersion" to "SMB1",
                "jcifs.smb.lmCompatibility" to "1",
            ),
        ),
        Variant(
            "SMB1, LM/NTLM ohne Extended Security",
            mapOf(
                "jcifs.smb.client.maxVersion" to "SMB1",
                "jcifs.smb.lmCompatibility" to "0",
                "jcifs.smb.client.useExtendedSecurity" to "false",
            ),
        ),
    )

    private val bases: List<CIFSContext> by lazy {
        variants.map { v ->
            val props = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", "SMB1")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.connTimeout", "10000")
                setProperty("jcifs.smb.client.responseTimeout", "15000")
                setProperty("jcifs.smb.client.soTimeout", "20000")
                setProperty("jcifs.smb.client.sessionTimeout", "20000")
                setProperty("jcifs.netbios.retryTimeout", "3000")
                setProperty("jcifs.resolveOrder", "DNS")
                v.props.forEach { (k, value) -> setProperty(k, value) }
            }
            BaseContext(PropertyConfiguration(props))
        }
    }

    /** Index des funktionierenden Anmeldeverfahrens; wird in den Einstellungen gemerkt. */
    @Volatile var working = 0
    val workingLabel get() = variants[working].label

    /** Angemeldeter Kontext wird wiederverwendet, damit nicht jeder Ordner neu anmeldet. */
    private val contexts = HashMap<Pair<NasConfig, Int>, CIFSContext>()

    private fun context(cfg: NasConfig, i: Int): CIFSContext = synchronized(contexts) {
        contexts.getOrPut(cfg to i) {
            if (cfg.user.isBlank()) bases[i].withGuestCrendentials()
            else bases[i].withCredentials(NtlmPasswordAuthenticator("", cfg.user, cfg.password))
        }
    }

    /** Führt [block] mit dem zuletzt funktionierenden Verfahren aus, bei Anmeldefehlern mit den anderen. */
    private fun <T> withAuth(cfg: NasConfig, block: (CIFSContext) -> T): T {
        var last: Exception? = null
        val order = listOf(working) + variants.indices.filter { it != working }
        for (i in order) {
            try {
                val result = block(context(cfg, i))
                working = i
                return result
            } catch (e: SmbAuthException) {
                last = e
            }
        }
        throw last ?: IllegalStateException("Anmeldung fehlgeschlagen")
    }

    private fun smbUrl(cfg: NasConfig, path: String, dir: Boolean): String {
        val p = listOf(cfg.share, path).filter { it.isNotEmpty() }.joinToString("/")
        if (p.isEmpty()) return "smb://${cfg.host}/"
        return "smb://${cfg.host}/$p" + if (dir) "/" else ""
    }

    /** Listet Unterordner und Videodateien eines NAS-Ordners. */
    suspend fun list(cfg: NasConfig, path: String): List<Entry> = withContext(Dispatchers.IO) {
        withAuth(cfg) { ctx ->
            SmbFile(smbUrl(cfg, path, dir = true), ctx).use { dir ->
                dir.listFiles().mapNotNull { f ->
                    val isDir = f.name.endsWith("/")
                    val name = f.name.trimEnd('/')
                    // Zeitstempel kommen mit der Ordnerliste, kosten also keine extra Anfrage
                    val added = if (isDir) 0L else runCatching {
                        f.createTime().takeIf { it > 0 } ?: f.lastModified()
                    }.getOrDefault(0L)
                    f.close()
                    if (name.startsWith(".") || name.startsWith("@") || name.startsWith("$") || name.endsWith("$")) return@mapNotNull null
                    if (isDir && name.lowercase() in SYSTEM_FOLDERS) return@mapNotNull null
                    if (!isDir && name.substringAfterLast('.', "").lowercase() !in VIDEO_EXTENSIONS) return@mapNotNull null
                    Entry(name, joinPath(path, name), isDir, added)
                }
            }
        }.sortedWith { a, b ->
            if (a.isDir != b.isDir) (if (a.isDir) -1 else 1) else naturalCompare(a.name, b.name)
        }
    }

    /** Öffnet eine Videodatei zum Streamen (blockierend, nicht im Main-Thread aufrufen). */
    fun openFile(cfg: NasConfig, path: String): Pair<SmbFile, Long> = withAuth(cfg) { ctx ->
        val f = SmbFile(smbUrl(cfg, path, dir = false), ctx)
        f to f.length()
    }

    /** Listet die Freigaben des Servers (z. B. FRITZ.NAS oder den Namen der USB-Platte). */
    fun shares(cfg: NasConfig): List<String> = withAuth(cfg) { ctx ->
        SmbFile("smb://${cfg.host}/", ctx).use { root ->
            root.listFiles().map { it.name.trimEnd('/') }.filter { !it.endsWith("$") }
        }
    }

    /** Prüft Schritt für Schritt: Namensauflösung, Ports, Anmeldung, Ordnerliste. */
    suspend fun diagnose(cfg: NasConfig, report: (String) -> Unit) = withContext(Dispatchers.IO) {
        val lines = mutableListOf<String>()
        fun say(s: String) {
            lines += s
            report(lines.joinToString("\n"))
        }

        val ip = try {
            java.net.InetAddress.getByName(cfg.host).hostAddress
        } catch (e: Exception) {
            say("✗ Adresse ${cfg.host} nicht gefunden – probier 192.168.178.1")
            return@withContext
        }
        say("✓ ${cfg.host} → $ip")
        if (!ip.startsWith("192.168.") && !ip.startsWith("10.") && !ip.startsWith("172.")) {
            say("⚠ Das ist keine Heimnetz-Adresse – trag lieber die IP der FritzBox ein (meist 192.168.178.1)")
        }
        for (port in listOf(445, 139)) {
            try {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress(ip, port), 4000) }
                say("✓ Port $port offen")
            } catch (e: Exception) {
                say("✗ Port $port geschlossen")
            }
        }
        say("… Anmeldung als ${cfg.user.ifBlank { "Gast" }}")
        val start = System.currentTimeMillis()
        try {
            val dirs = list(cfg, cfg.root).filter { it.isDir }.map { it.name }
            say("✓ Verbunden ($workingLabel) nach ${(System.currentTimeMillis() - start) / 1000} s")
            say("${dirs.size} Ordner: ${dirs.joinToString(", ")}")
        } catch (e: Exception) {
            var t: Throwable? = e
            val chain = mutableListOf<String>()
            while (t != null && chain.size < 4) {
                chain += "${t.javaClass.simpleName}: ${t.message}"
                t = t.cause
            }
            say("✗ Fehler nach ${(System.currentTimeMillis() - start) / 1000} s:\n" + chain.joinToString("\n"))
            try {
                val names = shares(cfg)
                say("Vorhandene Freigaben: ${names.joinToString(", ").ifEmpty { "keine" }}")
                if (names.isNotEmpty() && names.none { it.equals(cfg.share, ignoreCase = true) }) {
                    say("→ Trag bei „Freigabe“ einen dieser Namen ein")
                }
            } catch (e2: Exception) {
                say("Freigaben konnten nicht gelistet werden: ${e2.message}")
            }
        }
    }
}
