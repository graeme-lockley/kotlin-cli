package kli.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ManifestStoreTest {
    @Test
    fun load_returns_null_when_missing() {
        val file = Files.createTempDirectory("kli-manifest").resolve("manifest.json")
        val store = ManifestStore()

        val manifest = store.load(file)

        assertNull(manifest)
    }

    @Test
    fun save_and_load_round_trip() {
        val file = Files.createTempDirectory("kli-manifest").resolve("manifest.json")
        val store = ManifestStore()
        val expected = CompilationManifest(
            sourceHashes = mapOf("tools/Server.kt" to "abc123"),
            classpathFingerprint = "cp123",
        )

        store.save(file, expected)
        val loaded = store.load(file)

        assertNotNull(loaded)
        assertEquals(expected.sourceHashes, loaded.sourceHashes)
        assertEquals(expected.classpathFingerprint, loaded.classpathFingerprint)
    }
}
