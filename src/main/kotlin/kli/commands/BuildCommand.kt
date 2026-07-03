package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import kli.compiler.EmbeddableKotlinCompiler
import kli.packaging.BuildOutcome
import kli.packaging.BuildService
import kli.resolver.MavenDependencyResolver
import java.nio.file.Path

class BuildCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "build") {
    override fun help(context: Context): String {
        return "Build a fat jar with a dispatcher entrypoint"
    }

    private val outputPath by option(
        "--output",
        help = "Output jar path (default: ./dist/<name>-<version>.jar)",
    ).path(canBeDir = false, mustExist = false)
    private val showCompilerLogging by option(
        "--show-compiler-logging",
        help = "Show Kotlin compiler diagnostic logging during build compilation",
    ).flag(default = false)
    private val silent by option(
        "--silent",
        help = "Hide compile and dependency progress output",
    ).flag(default = false)

    override fun run() {
        val service = BuildService(
            cwd = cwd,
            dependencyResolver = MavenDependencyResolver { coordinate, durationMs ->
                if (!silent) {
                    echo(formatDependencyProgress(coordinate, durationMs))
                }
            },
            compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging),
            onCompiledSource = { sourceFile, durationMs ->
                if (!silent) {
                    val root = cwd().toAbsolutePath().normalize()
                    echo(formatCompileProgress(root, sourceFile, durationMs))
                }
            },
        )
        when (val result = service.build(outputPath)) {
            is BuildOutcome.Success -> {
                echo("Built jar: ${result.outputJar}")
            }

            is BuildOutcome.Failure -> {
                echo("error: ${result.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}
