package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.option
import kli.packaging.PublishOutcome
import kli.packaging.PublishService
import java.nio.file.Path

class PublishCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "publish") {
    override fun help(context: Context): String {
        return "Publish a built package to a Maven registry"
    }

    private val registry by option(
        "--registry",
        help = "Registry URL (default: from project.json or Maven Central)",
    )

    override fun run() {
        val service = PublishService(cwd)
        when (val result = service.publish(registry)) {
            is PublishOutcome.Success -> {
                echo("Published ${result.artifact} to ${result.registry}")
            }

            is PublishOutcome.Failure -> {
                echo("error: ${result.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}
