package kli.cache

import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ProjectHashTest {
    @Test
    fun hash_is_deterministic_for_same_path() {
        val path = Path.of("/tmp/example")

        val first = ProjectHash.fromRoot(path)
        val second = ProjectHash.fromRoot(path)

        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun hash_changes_for_different_paths() {
        val first = ProjectHash.fromRoot(Path.of("/tmp/example-a"))
        val second = ProjectHash.fromRoot(Path.of("/tmp/example-b"))

        assertNotEquals(first, second)
    }
}
