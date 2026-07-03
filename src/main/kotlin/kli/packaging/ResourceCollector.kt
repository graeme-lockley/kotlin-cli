package kli.packaging

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

object ResourceCollector {
    fun collect(projectRoot: Path, patterns: List<String>): Map<String, Path> {
        if (patterns.isEmpty()) {
            return emptyMap()
        }

        val matchers = patterns.map { pattern ->
            FileSystems.getDefault().getPathMatcher("glob:$pattern")
        }

        val resources = linkedMapOf<String, Path>()
        Files.walk(projectRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) }.forEach { file ->
                val relativePath = projectRoot.relativize(file).toString().replace('\\', '/')
                val relative = Path.of(relativePath)
                if (matchers.any { it.matches(relative) }) {
                    resources.putIfAbsent(relativePath, file)
                }
            }
        }

        return resources
    }
}
