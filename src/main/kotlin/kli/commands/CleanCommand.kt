package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.cache.CacheCleaner
import kli.project.ProjectRootFinder
import java.nio.file.Path

class CleanCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "clean") {
    override fun help(context: Context): String {
        return "Remove cache artifacts for the current project only"
    }

    private val verbose by option(
        "--verbose",
        "-v",
        help = "Show full stack traces on errors",
    ).flag(default = false)

    override fun run() {
        try {
            val projectRoot = ProjectRootFinder.find(cwd())
                ?: fail("No project.json found in current directory or parents")

            val removed = CacheCleaner.cleanProject(projectRoot)
            if (removed) {
                echo("Removed project cache")
            } else {
                echo("Project cache already clean")
            }
        } catch (ex: ProgramResult) {
            throw ex
        } catch (ex: Exception) {
            echo("error: ${ex.message ?: "Unknown error"}", err = true)
            if (verbose) {
                ex.printStackTrace(System.err)
            }
            throw ProgramResult(1)
        }
    }

    private fun fail(message: String): Nothing {
        echo("error: $message", err = true)
        throw ProgramResult(1)
    }
}
