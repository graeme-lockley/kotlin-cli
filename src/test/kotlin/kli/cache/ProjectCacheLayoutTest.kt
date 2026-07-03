package kli.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class ProjectCacheLayoutTest {
    @Test
    fun creates_expected_project_cache_directories() {
        val home = Files.createTempDirectory("kli-home").toString()
        val root = Files.createTempDirectory("kli-project")

        val layout = ProjectCacheLayouts.forProject(root, home)
        ProjectCacheLayouts.ensureDirectories(layout)

        assertTrue(Files.isDirectory(layout.projectCacheDir))
        assertTrue(Files.isDirectory(layout.classesDir))
        assertTrue(Files.isDirectory(layout.resourcesDir))
        assertTrue(Files.isDirectory(layout.generatedDir))
    }
}
