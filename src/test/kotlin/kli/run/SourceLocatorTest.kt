package kli.run

import kli.project.ProjectConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SourceLocatorTest {
    @Test
    fun discovers_non_test_kotlin_sources() {
        val root = Files.createTempDirectory("kli-source-discovery")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        Files.writeString(toolsDir.resolve("Server.kt"), "fun main() {}")
        Files.writeString(toolsDir.resolve("ServerTest.kt"), "fun test() {}")

        val sources = SourceLocator.discoverRunSources(root, ProjectConfig())

        assertEquals(1, sources.size)
        assertTrue(sources.first().endsWith("Server.kt"))
    }

    @Test
    fun resolves_main_source_from_qualified_name() {
        val root = Files.createTempDirectory("kli-main-source")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        val source = toolsDir.resolve("Server.kt")
        Files.writeString(source, "fun main() {}")

        val resolved = SourceLocator.resolveMainSource(root, ProjectConfig(), "tools.Server")

        assertNotNull(resolved)
        assertEquals(source.toAbsolutePath().normalize(), resolved.toAbsolutePath().normalize())
    }

    @Test
    fun returns_null_for_missing_main_source() {
        val root = Files.createTempDirectory("kli-main-missing")

        val resolved = SourceLocator.resolveMainSource(root, ProjectConfig(), "tools.Server")

        assertNull(resolved)
    }
}
