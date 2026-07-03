package kli.packaging

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResourceCollectorTest {
    @Test
    fun collects_files_matching_glob_patterns() {
        val root = Files.createTempDirectory("kli-resources")
        val configDir = Files.createDirectories(root.resolve("config"))
        val templatesDir = Files.createDirectories(root.resolve("templates"))
        val ignoredDir = Files.createDirectories(root.resolve("ignored"))

        Files.writeString(configDir.resolve("app.conf"), "cfg")
        Files.writeString(templatesDir.resolve("index.html"), "<html></html>")
        Files.writeString(ignoredDir.resolve("skip.txt"), "x")

        val resources = ResourceCollector.collect(root, listOf("config/**", "templates/**"))

        assertEquals(2, resources.size)
        assertTrue(resources.containsKey("config/app.conf"))
        assertTrue(resources.containsKey("templates/index.html"))
    }
}
