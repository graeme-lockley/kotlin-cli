package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kli.compiler.EmbeddableKotlinCompiler
import kli.packaging.PackageOutcome
import kli.packaging.PackageService
import java.nio.file.Path

class PackageCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "package") {
    override fun help(context: Context): String {
        return "Build a fat jar and install it to the local ~/.kli/m2 repository"
    }

    private val outputPath by option(
        "--output",
        help = "Output jar path (default: ./dist/<name>-<version>.jar)",
    ).path(canBeDir = false, mustExist = false)
    private val showCompilerLogging by option(
        "--show-compiler-logging",
        help = "Show Kotlin compiler diagnostic logging during packaging compilation",
    ).flag(default = false)

    override fun run() {
        val service = PackageService(
            cwd = cwd,
            compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging),
        )
        when (val result = service.build(outputPath)) {
            is PackageOutcome.Success -> {
                echo("Built jar: ${result.outputJar}")
                echo("Installed jar: ${result.installedJar}")
            }

            is PackageOutcome.Failure -> {
                echo("error: ${result.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}
