package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.compiler.EmbeddableKotlinCompiler
import kli.test.TestExecutionOutcome
import kli.test.TestExecutor
import kli.test.TestWorkflow
import kli.test.TestWorkflowOutcome
import java.nio.file.Path

class TestCommand(
    private val cwd: () -> Path,
    private val testExecutorFactory: (Boolean) -> TestExecutor = { showCompilerLogging ->
        TestExecutor(compiler = EmbeddableKotlinCompiler(verboseLogging = showCompilerLogging))
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

    override fun run() {
        val workflow = TestWorkflow(cwd)
        val testExecutor = testExecutorFactory(showCompilerLogging)
        when (val result = workflow.prepare(pathFilter)) {
            is TestWorkflowOutcome.Failure -> {
                result.errors.forEach { echo("error: $it", err = true) }
                throw ProgramResult(1)
            }

            is TestWorkflowOutcome.Success -> {
                when (val execution = testExecutor.execute(result.plan)) {
                    is TestExecutionOutcome.Success -> {
                        echo("Executed ${execution.discoveredTests} test file(s)")
                    }

                    is TestExecutionOutcome.Failure -> {
                        echo("error: ${execution.message}", err = true)
                        throw ProgramResult(1)
                    }
                }
            }
        }
    }
}
