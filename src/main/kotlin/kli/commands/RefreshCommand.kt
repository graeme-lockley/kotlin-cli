package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import kli.cache.ProjectCacheLayouts
import kli.project.ProjectRootFinder
import java.nio.file.Files
import java.nio.file.Path

class RefreshCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "refresh") {
    override fun help(context: Context): String {
        return "Re-resolve all dependencies (including SNAPSHOTs) and recompile all sources"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: fail("No project.json found in current directory or parents")

        val cacheLayout = ProjectCacheLayouts.forProject(projectRoot, System.getenv("HOME") ?: System.getProperty("user.home"))

        // Clear the manifest to invalidate incremental compilation
        val manifestFile = cacheLayout.manifestFile
        val manifestCleared = if (Files.exists(manifestFile)) {
            try {
                Files.delete(manifestFile)
                true
            } catch (e: Exception) {
                fail("Failed to clear compilation manifest: ${e.message}")
            }
        } else {
            false
        }

        if (manifestCleared) {
            echo("Cleared compilation cache")
        } else {
            echo("Compilation cache already clear")
        }

        echo("Dependencies will be re-resolved on next build/test/run")
    }

    private fun fail(message: String): Nothing {
        echo("error: $message", err = true)
        throw ProgramResult(1)
    }
}
