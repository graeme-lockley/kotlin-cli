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
import kli.resolver.MavenDependencyResolver
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
    private val silent by option(
        "--silent",
        help = "Hide compile and dependency progress output",
    ).flag(default = false)
    private val verbose by option(
        "--verbose",
        "-v",
        help = "Show full stack traces on errors",
    ).flag(default = false)

    override fun run() {
        try {
            val service = PackageService(
                cwd = cwd,
                dependencyResolver = MavenDependencyResolver { coordinate, durationMs ->
                    if (!silent) {
                        echo(formatDependencyProgress(coordinate, durationMs))
                    }
                },
                compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging, silent = silent),
                onCompiledSource = { sourceFile, durationMs ->
                    if (!silent) {
                        val root = cwd().toAbsolutePath().normalize()
                        echo(formatCompileProgress(root, sourceFile, durationMs))
                    }
                },
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
        } catch (ex: ProgramResult) {
            throw ex
        } catch (ex: Exception) {
            echo("error: ${ex.message}", err = true)
            if (verbose) {
                ex.printStackTrace(System.err)
            }
            throw ProgramResult(1)
        }
    }
}
