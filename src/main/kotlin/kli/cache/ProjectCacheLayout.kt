package kli.cache

import java.nio.file.Files
import java.nio.file.Path

data class ProjectCacheLayout(
    val projectRoot: Path,
    val projectHash: String,
    val projectCacheDir: Path,
    val classesDir: Path,
    val resourcesDir: Path,
    val generatedDir: Path,
    val manifestFile: Path,
)

object ProjectCacheLayouts {
    fun forProject(projectRoot: Path, userHome: String = System.getProperty("user.home")): ProjectCacheLayout {
        val hash = ProjectHash.fromRoot(projectRoot)
        val root = KliPaths.cacheRoot(userHome).resolve(hash)
        return ProjectCacheLayout(
            projectRoot = projectRoot.toAbsolutePath().normalize(),
            projectHash = hash,
            projectCacheDir = root,
            classesDir = root.resolve("classes"),
            resourcesDir = root.resolve("resources"),
            generatedDir = root.resolve("gen"),
            manifestFile = root.resolve("manifest.json"),
        )
    }

    fun ensureDirectories(layout: ProjectCacheLayout) {
        Files.createDirectories(layout.classesDir)
        Files.createDirectories(layout.resourcesDir)
        Files.createDirectories(layout.generatedDir)
    }
}
