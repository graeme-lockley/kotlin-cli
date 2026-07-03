package kli.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IncrementalCompilationTest {
    @Test
    fun is_not_up_to_date_without_manifest() {
        val classesDir = Files.createTempDirectory("kli-classes")
        val result = IncrementalCompilation.isUpToDate(
            manifest = null,
            sourceHashes = mapOf("tools/Server.kt" to "hash"),
            classpathFingerprint = "cp",
            classesDir = classesDir,
        )

        assertFalse(result)
    }

    @Test
    fun is_up_to_date_when_manifest_hashes_and_classpath_match_and_classes_exist() {
        val classesDir = Files.createTempDirectory("kli-classes")
        Files.writeString(classesDir.resolve("ServerKt.class"), "bytecode")
        val manifest = CompilationManifest(
            sourceHashes = mapOf("tools/Server.kt" to "hash"),
            classpathFingerprint = "cp",
        )

        val result = IncrementalCompilation.isUpToDate(
            manifest = manifest,
            sourceHashes = mapOf("tools/Server.kt" to "hash"),
            classpathFingerprint = "cp",
            classesDir = classesDir,
        )

        assertTrue(result)
    }

    @Test
    fun is_not_up_to_date_when_classpath_changes() {
        val classesDir = Files.createTempDirectory("kli-classes")
        Files.writeString(classesDir.resolve("ServerKt.class"), "bytecode")
        val manifest = CompilationManifest(
            sourceHashes = mapOf("tools/Server.kt" to "hash"),
            classpathFingerprint = "old",
        )

        val result = IncrementalCompilation.isUpToDate(
            manifest = manifest,
            sourceHashes = mapOf("tools/Server.kt" to "hash"),
            classpathFingerprint = "new",
            classesDir = classesDir,
        )

        assertFalse(result)
    }
}
