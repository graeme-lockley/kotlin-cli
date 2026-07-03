package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.compiler.EmbeddableKotlinCompiler
import kli.resolver.MavenDependencyResolver
import kli.run.RunExecutionOutcome
import kli.run.RunExecutor
import kli.run.RunWorkflow
import kli.run.RunWorkflowOutcome
import java.nio.file.Path

class RunCommand(
    private val cwd: () -> Path,
    private val runExecutorFactory: (Boolean, (Path, Long) -> Unit) -> RunExecutor = { showCompilerLogging, onCompiledSource ->
        RunExecutor(
            compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging),
            onCompiledSource = onCompiledSource,
        )
    },
) : CliktCommand(name = "run") {
    override fun help(context: Context): String {
        return "Compile stale sources and run a top-level main from a qualified source name"
    }

    private val mainClass by argument(
        name = "qualified-name",
        help = "Qualified source name, for example tools.Server",
    )
    private val programArgs by argument(
        name = "args",
        help = "Arguments forwarded to the target program",
    ).multiple()
    private val showCompilerLogging by option(
        "--show-compiler-logging",
        help = "Show Kotlin compiler diagnostic logging during compilation",
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
            val workflow = RunWorkflow(
                cwd = cwd,
                dependencyResolver = MavenDependencyResolver { coordinate, durationMs ->
                    if (!silent) {
                        echo(formatDependencyProgress(coordinate, durationMs))
                    }
                },
            )
            when (val result = workflow.prepare(mainClass, programArgs)) {
                is RunWorkflowOutcome.Failure -> {
                    result.errors.forEach { echo("error: $it", err = true) }
                    throw ProgramResult(1)
                }

                is RunWorkflowOutcome.Success -> {
                    val runExecutor = runExecutorFactory(showCompilerLogging) { sourceFile, durationMs ->
                        if (!silent) {
                            echo(formatCompileProgress(result.plan.projectRoot, sourceFile, durationMs))
                        }
                    }
                    when (val execution = runExecutor.execute(result.plan)) {
                        is RunExecutionOutcome.Success -> {
                            if (execution.exitCode != 0) {
                                throw ProgramResult(execution.exitCode)
                            }
                        }

                        is RunExecutionOutcome.Failure -> {
                            echo("error: ${execution.message}", err = true)
                            throw ProgramResult(1)
                        }
                    }
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
