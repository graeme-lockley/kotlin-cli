package kli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import kli.commands.CleanAllCommand
import kli.commands.CleanCommand
import kli.commands.CommandEnvironment
import kli.commands.ProjectLintCommand
import kli.commands.RunCommand

class Kli : CliktCommand(name = "kli") {
    override fun run() {
        // Root command is a command group; no direct behavior yet.
    }
}

fun main(args: Array<String>) {
    Kli()
        .subcommands(
            ProjectLintCommand(CommandEnvironment::cwd),
            CleanCommand(CommandEnvironment::cwd),
            CleanAllCommand(),
            RunCommand(CommandEnvironment::cwd),
        )
    .main(args)
}
