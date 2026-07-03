package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import kli.cache.CacheCleaner
import kli.project.ProjectRootFinder
import java.nio.file.Path

class CleanCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "clean") {
    override fun help(context: Context): String {
        return "Remove cache artifacts for the current project only"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: fail("No project.json found in current directory or parents")

        val removed = CacheCleaner.cleanProject(projectRoot)
        if (removed) {
            echo("Removed project cache")
        } else {
            echo("Project cache already clean")
        }
    }

    private fun fail(message: String): Nothing {
        echo("error: $message", err = true)
        throw ProgramResult(1)
    }
}
