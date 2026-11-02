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
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.soTimeout", "35000")
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
        }
        BaseContext(PropertyConfiguration(props))
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
