package de.filmregal

import android.net.Uri
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.security.bc.BCSecurityProvider
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

val VIDEO_EXTENSIONS = setOf("mkv", "mp4", "m4v", "avi", "mov", "webm", "ts", "m2ts", "wmv", "mpg", "mpeg")

object Nas {
    private val client by lazy {
        SMBClient(
            SmbConfig.builder()
                .withSecurityProvider(BCSecurityProvider())
                .withTimeout(30, TimeUnit.SECONDS)
                .build()
        )
    }

    /** Listet Unterordner und Videodateien eines NAS-Ordners. */
    suspend fun list(cfg: NasConfig, path: String): List<Entry> = withContext(Dispatchers.IO) {
        client.connect(cfg.host).use { connection ->
            val auth = if (cfg.user.isBlank()) {
                AuthenticationContext.guest()
            } else {
                AuthenticationContext(cfg.user, cfg.password.toCharArray(), "")
            }
            val session = connection.authenticate(auth)
            (session.connectShare(cfg.share) as DiskShare).use { share ->
                share.list(path.replace('/', '\\')).mapNotNull { info ->
                    val name = info.fileName
                    if (name == "." || name == ".." || name.startsWith(".") ||
                        name.startsWith("@") || name.startsWith("$")
                    ) return@mapNotNull null
                    val attrs = info.fileAttributes
                    if (EnumWithValue.EnumUtils.isSet(attrs, FileAttributes.FILE_ATTRIBUTE_HIDDEN)) return@mapNotNull null
                    val isDir = EnumWithValue.EnumUtils.isSet(attrs, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                    if (!isDir && name.substringAfterLast('.', "").lowercase() !in VIDEO_EXTENSIONS) return@mapNotNull null
                    Entry(name, joinPath(path, name), isDir)
                }.sortedWith { a, b ->
                    if (a.isDir != b.isDir) (if (a.isDir) -1 else 1) else naturalCompare(a.name, b.name)
                }
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
