package kli.cache

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object CacheCleaner {
    fun cleanProject(projectRoot: Path, userHome: String = System.getProperty("user.home")): Boolean {
        val layout = ProjectCacheLayouts.forProject(projectRoot, userHome)
        return deleteIfExists(layout.projectCacheDir)
    }

    fun cleanAll(userHome: String = System.getProperty("user.home")): Boolean {
        val cacheRoot = KliPaths.cacheRoot(userHome)
        return deleteIfExists(cacheRoot)
    }

    private fun deleteIfExists(path: Path): Boolean {
        if (!Files.exists(path)) {
            return false
        }

        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { Files.deleteIfExists(it) }
        return true
    }
}
