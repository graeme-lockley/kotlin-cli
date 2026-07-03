package kli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.UsageError
import kli.commands.CleanAllCommand
import kli.commands.CleanCommand
import kli.commands.CommandEnvironment
import kli.commands.PackageCommand
import kli.commands.ProjectLintCommand
import kli.commands.RunCommand
import java.nio.file.Path
import kotlin.system.exitProcess

class Kli : CliktCommand(name = "kli") {
    override fun help(context: Context): String {
        return "Minimal Kotlin CLI for run, package, and cache management workflows"
    }

    override fun run() {
        // Root command is a command group; no direct behavior yet.
    }
}

fun buildCli(cwd: () -> Path = CommandEnvironment::cwd): Kli {
    return Kli()
        .subcommands(
            ProjectLintCommand(cwd),
            CleanCommand(cwd),
            CleanAllCommand(),
            RunCommand(cwd),
            PackageCommand(cwd),
        )
}

fun runCli(args: Array<String>, cwd: () -> Path = CommandEnvironment::cwd): Int {
    return try {
        buildCli(cwd).parse(args)
        0
    } catch (ex: UsageError) {
        2
    } catch (ex: ProgramResult) {
        ex.statusCode
    } catch (ex: PrintMessage) {
        ex.statusCode
    }
}

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}
