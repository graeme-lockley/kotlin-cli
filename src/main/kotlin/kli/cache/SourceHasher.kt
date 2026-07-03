package kli.cache

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

object SourceHasher {
    fun sha256(file: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(file.readBytes())
        return hash.joinToString(separator = "") { "%02x".format(it) }
    }
}
