import java.security.MessageDigest

fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

fun main() {
    val ordner = "Medien/Platte/Filme"
    println(ordner.hashCode())                                  // immer dieselbe Zahl
    println(Integer.toHexString(ordner.hashCode()))             // als kurzer Hex-Text
    println("lib_" + Integer.toHexString(ordner.hashCode()) + ".json")
    println(md5(ordner))                                        // 32 Zeichen, praktisch kollisionsfrei
}
