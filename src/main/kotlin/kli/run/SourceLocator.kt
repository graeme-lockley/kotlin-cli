package kli.run

import kli.project.ProjectConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

object SourceLocator {
    fun discoverRunSources(projectRoot: Path, config: ProjectConfig): List<Path> {
        val sourceRoots = config.sources.ifEmpty { listOf(".") }
        return sourceRoots
            .asSequence()
            .map { projectRoot.resolve(it).normalize() }
            .filter { Files.exists(it) }
            .flatMap { root ->
                Files.walk(root).use { paths ->
                    paths
                        .filter { it.isRegularFile() }
                        .filter { it.extension == "kt" }
                        .filter { !it.name.endsWith("Test.kt") }
                        .toList()
                        .asSequence()
                }
            }
            .distinct()
            .sortedBy { it.toString() }
            .toList()
    }

    fun resolveMainSource(projectRoot: Path, config: ProjectConfig, mainClass: String): Path? {
        val relative = Path.of(mainClass.replace('.', '/') + ".kt")
        val sourceRoots = config.sources.ifEmpty { listOf(".") }

        for (sourceRoot in sourceRoots) {
            val candidate = projectRoot.resolve(sourceRoot).resolve(relative).normalize()
            if (Files.isRegularFile(candidate)) {
                return candidate
            }
        }

        return null
    }
}
