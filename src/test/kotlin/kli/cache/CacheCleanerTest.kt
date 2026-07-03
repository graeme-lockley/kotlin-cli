package kli.cache

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CacheCleanerTest {
    @Test
    fun clean_project_removes_only_project_cache_directory() {
        val home = Files.createTempDirectory("kli-home-clean-project").toString()
        val projectA = Files.createTempDirectory("project-a")
        val projectB = Files.createTempDirectory("project-b")

        val layoutA = ProjectCacheLayouts.forProject(projectA, home)
        val layoutB = ProjectCacheLayouts.forProject(projectB, home)
        ProjectCacheLayouts.ensureDirectories(layoutA)
        ProjectCacheLayouts.ensureDirectories(layoutB)

        val removed = CacheCleaner.cleanProject(projectA, home)

        assertTrue(removed)
        assertFalse(Files.exists(layoutA.projectCacheDir))
        assertTrue(Files.exists(layoutB.projectCacheDir))
    }

    @Test
    fun clean_all_removes_cache_root() {
        val home = Files.createTempDirectory("kli-home-clean-all").toString()
        val project = Files.createTempDirectory("project")
        val layout = ProjectCacheLayouts.forProject(project, home)
        ProjectCacheLayouts.ensureDirectories(layout)

        val removed = CacheCleaner.cleanAll(home)

        assertTrue(removed)
        assertFalse(Files.exists(KliPaths.cacheRoot(home)))
        assertTrue(Files.exists(KliPaths.home(home)))
    }
}
