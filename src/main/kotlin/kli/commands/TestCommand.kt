package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.compiler.EmbeddableKotlinCompiler
import kli.resolver.MavenDependencyResolver
import kli.test.TestExecutionOutcome
import kli.test.TestExecutor
import kli.test.TestWorkflow
import kli.test.TestWorkflowOutcome
import java.nio.file.Path

class TestCommand(
    private val cwd: () -> Path,
    private val testExecutorFactory: (Boolean, Boolean, (Path, Long) -> Unit) -> TestExecutor = { showCompilerLogging, silent, onCompiledSource ->
        TestExecutor(
            compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging, silent = silent),
            onCompiledSource = onCompiledSource,
        )
    },
) : CliktCommand(name = "test") {
    override fun help(context: Context): String {
        return "Discover and execute Kotlin test files (supports optional file or directory filter)"
    }

    private val pathFilter by argument(
        name = "path",
        help = "Optional file or directory to scope discovery",
    ).optional()
    private val showCompilerLogging by option(
        "--show-compiler-logging",
        help = "Show Kotlin compiler diagnostic logging during test compilation",
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
            val workflow = TestWorkflow(
                cwd = cwd,
                dependencyResolver = MavenDependencyResolver { coordinate, durationMs ->
                    if (!silent) {
                        echo(formatDependencyProgress(coordinate, durationMs))
                    }
                },
            )
            when (val result = workflow.prepare(pathFilter)) {
                is TestWorkflowOutcome.Failure -> {
                    result.errors.forEach { echo("error: $it", err = true) }
                    throw ProgramResult(1)
                }

                is TestWorkflowOutcome.Success -> {
                    val testExecutor = testExecutorFactory(showCompilerLogging, silent) { sourceFile, durationMs ->
                        if (!silent) {
                            echo(formatCompileProgress(result.plan.projectRoot, sourceFile, durationMs))
                        }
                    }
                    when (val execution = testExecutor.execute(result.plan)) {
                        is TestExecutionOutcome.Success -> {
                            // Summary line is printed by the generated test runner.
                        }

                        is TestExecutionOutcome.Failure -> {
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
