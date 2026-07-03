package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import java.nio.file.Path
import kotlin.io.path.name

class ProjectLintCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "project-lint") {
    override fun help(context: Context): String {
        return "Validate project.json strictly and report schema/type errors"
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

            val projectFile = projectRoot.resolve("project.json")
            val result = ProjectConfigParser.load(projectFile, strictUnknownFields = true)

            if (!result.isValid) {
                result.errors.forEach { echo("error: $it", err = true) }
                throw ProgramResult(1)
            }

            echo("${projectFile.name} is valid")
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
