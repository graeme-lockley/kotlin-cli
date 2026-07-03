package kli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import kli.commands.CommandEnvironment
import kli.commands.ProjectLintCommand

class Kli : CliktCommand(name = "kli") {
    override fun run() {
        // Root command is a command group; no direct behavior yet.
    }
}

fun main(args: Array<String>) {
    Kli()
        .subcommands(ProjectLintCommand(CommandEnvironment::cwd))
    .main(args)
}
