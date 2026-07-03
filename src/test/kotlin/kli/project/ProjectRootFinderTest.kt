package kli.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProjectRootFinderTest {
    @Test
    fun finds_nearest_parent_with_project_json() {
        val root = Files.createTempDirectory("kli-root-test")
        val nested = Files.createDirectories(root.resolve("src/tools"))
        Files.writeString(root.resolve("project.json"), "{}")

        val found = ProjectRootFinder.find(nested)

        assertEquals(root.toAbsolutePath().normalize(), found)
    }

    @Test
    fun returns_null_when_project_json_absent() {
        val start = Files.createTempDirectory("kli-root-missing")

        val found = ProjectRootFinder.find(start)

        assertNull(found)
    }
}
