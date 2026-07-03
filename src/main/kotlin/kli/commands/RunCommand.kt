package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import kli.run.RunExecutionOutcome
import kli.run.RunExecutor
import kli.run.RunWorkflow
import kli.run.RunWorkflowOutcome
import java.nio.file.Path

class RunCommand(
    private val cwd: () -> Path,
    private val runExecutor: RunExecutor = RunExecutor(),
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

    override fun run() {
        val workflow = RunWorkflow(cwd)
        when (val result = workflow.prepare(mainClass, programArgs)) {
            is RunWorkflowOutcome.Failure -> {
                result.errors.forEach { echo("error: $it", err = true) }
                throw ProgramResult(1)
            }

            is RunWorkflowOutcome.Success -> {
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
    }
}
