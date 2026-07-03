package kli.cache

import java.nio.file.Path
import java.security.MessageDigest

object ProjectHash {
    fun fromRoot(projectRoot: Path): String {
        val normalized = projectRoot.toAbsolutePath().normalize().toString()
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { "%02x".format(it) }
    }
}
