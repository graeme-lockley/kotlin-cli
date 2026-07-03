package kli.cache

data class CompilationManifest(
    val sourceHashes: Map<String, String> = emptyMap(),
    val classpathFingerprint: String = "",
    val configFingerprint: String = "",
)
