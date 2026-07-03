package kli.cache

import kli.project.ProjectConfig
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

object IncrementalCompilation {
    fun classpathFingerprint(classpath: List<Path>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val joined = classpath
            .map { it.toAbsolutePath().normalize().toString() }
            .sorted()
            .joinToString(separator = "|")
        val hash = digest.digest(joined.toByteArray(Charsets.UTF_8))
        return hash.joinToString(separator = "") { "%02x".format(it) }
    }

    fun computeSourceHashes(projectRoot: Path, sourceFiles: List<Path>): Map<String, String> {
        return sourceFiles.associate { file ->
            val key = projectRoot.toAbsolutePath().normalize().relativize(file.toAbsolutePath().normalize()).toString()
            key to SourceHasher.sha256(file)
        }
    }

    fun configFingerprint(config: ProjectConfig): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = buildString {
            append(config.target)
            append('|')
            append(config.sources.sorted().joinToString(","))
            append('|')
            append(config.deps.sorted().joinToString(","))
            append('|')
            append(config.repos.sorted().joinToString(","))
            append('|')
            append(config.jvmArgs.joinToString(","))
        }
        val hash = digest.digest(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString(separator = "") { "%02x".format(it) }
    }

    fun isUpToDate(
        manifest: CompilationManifest?,
        sourceHashes: Map<String, String>,
        classpathFingerprint: String,
        configFingerprint: String,
        classesDir: Path,
    ): Boolean {
        if (manifest == null) {
            return false
        }
        if (!Files.isDirectory(classesDir)) {
            return false
        }
        val hasClassFiles = Files.walk(classesDir).use { stream ->
            stream.anyMatch { path -> Files.isRegularFile(path) && path.toString().endsWith(".class") }
        }
        if (!hasClassFiles) {
            return false
        }

        return manifest.classpathFingerprint == classpathFingerprint &&
            manifest.configFingerprint == configFingerprint &&
            manifest.sourceHashes == sourceHashes
    }
}
