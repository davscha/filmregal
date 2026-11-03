import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.util.Properties

const val NAS_IP = "192.168.178.1"
const val BENUTZER = "kino"
const val PASSWORT = "geheim"

/** lmCompatibility 3 = modernes NTLMv2, 1 = altes NTLMv1 (ältere FritzBoxen). */
fun kontext(lmCompatibility: Int): CIFSContext {
    val props = Properties().apply {
        setProperty("jcifs.smb.client.minVersion", "SMB1")
        setProperty("jcifs.smb.client.maxVersion", "SMB311")
        setProperty("jcifs.smb.client.responseTimeout", "10000")
        setProperty("jcifs.smb.client.connTimeout", "5000")
        setProperty("jcifs.smb.lmCompatibility", lmCompatibility.toString())
    }
    return BaseContext(PropertyConfiguration(props))
        .withCredentials(NtlmPasswordAuthenticator("", BENUTZER, PASSWORT))
}

fun main() {
    for (verfahren in listOf(3, 1)) {
        val ctx = kontext(verfahren)
        try {
            val server = SmbFile("smb://$NAS_IP/", ctx)
            val freigaben = server.listFiles().map { it.name.trimEnd('/') }.filterNot { it.endsWith("$") }
            println("Verbunden (lmCompatibility $verfahren). Freigaben: $freigaben")

            val ordner = SmbFile("smb://$NAS_IP/${freigaben.first()}/", ctx)
            for (f in ordner.listFiles()) {
                val art = if (f.isDirectory) "Ordner" else "${f.length() / 1_000_000} MB"
                println("%-40s %s".format(f.name, art))
            }

            // Ein Stück aus der Mitte einer Datei lesen – so funktioniert später das Spulen
            val datei = ordner.listFiles().firstOrNull { !it.isDirectory } ?: return
            SmbRandomAccessFile(datei, "r").use { raf ->
                raf.seek(datei.length() / 2)
                val puffer = ByteArray(16)
                raf.read(puffer)
                println("16 Bytes ab der Mitte: ${puffer.joinToString(" ") { "%02x".format(it) }}")
            }
            return
        } catch (e: SmbAuthException) {
            println("Anmeldung mit lmCompatibility $verfahren abgelehnt, versuche nächstes Verfahren …")
        } catch (e: Exception) {
            println("Keine Verbindung zum NAS: ${e.message}")
            return
        }
    }
}
