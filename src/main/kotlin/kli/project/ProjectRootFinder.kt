package kli.project

import java.nio.file.Files
import java.nio.file.Path

object ProjectRootFinder {
    private const val PROJECT_FILE = "project.json"

    fun find(startPath: Path): Path? {
        var current: Path? = startPath.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isRegularFile(current.resolve(PROJECT_FILE))) {
                return current
            }
            current = current.parent
        }
        return null
    }
}
