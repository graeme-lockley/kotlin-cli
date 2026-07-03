package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import kli.run.RunWorkflow
import kli.run.RunWorkflowOutcome
import java.nio.file.Path

class RunCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "run") {
    private val mainClass by argument(name = "qualified-name")
    private val programArgs by argument(name = "args").multiple()

    override fun run() {
        val workflow = RunWorkflow(cwd)
        when (val result = workflow.prepare(mainClass, programArgs)) {
            is RunWorkflowOutcome.Failure -> {
                result.errors.forEach { echo("error: $it", err = true) }
                throw ProgramResult(1)
            }

            is RunWorkflowOutcome.Success -> {
                val plan = result.plan
                val depCount = plan.dependencies.runtimeClasspath.size
                echo("Prepared run for ${plan.mainClass} (${depCount} runtime dependencies)")
                echo("Run execution is not implemented yet")
                throw ProgramResult(1)
            }
        }
    }
}
