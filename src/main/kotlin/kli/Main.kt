package kli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.PrintHelpMessage
import com.github.ajalt.clikt.core.PrintMessage
import com.github.ajalt.clikt.core.parse
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.versionOption
import kli.commands.CleanAllCommand
import kli.commands.CleanCommand
import kli.commands.CommandEnvironment
import kli.commands.BuildCommand
import kli.commands.buildDependencyCommand
import kli.commands.CompletionCommand
import kli.commands.InitCommand
import kli.commands.PackageCommand
import kli.commands.ProjectLintCommand
import kli.commands.PublishCommand
import kli.commands.RefreshCommand
import kli.commands.RunCommand
import kli.commands.TestCommand
import java.nio.file.Path
import kotlin.system.exitProcess

private val VERSION = loadVersion()

private fun loadVersion(): String {
    return try {
        val resource = object {}.javaClass.classLoader.getResourceAsStream("version.txt")
        if (resource != null) {
            resource.bufferedReader().use { it.readText().trim() }
        } else {
            "0.1.0"
        }
    } catch (ex: Exception) {
        "0.1.0"
    }
}

class Kli : CliktCommand(name = "kli") {
    override fun help(context: Context): String {
        return "Minimal Kotlin CLI for run, test, package, and cache management workflows"
    }

    init {
        versionOption(version = VERSION)
    }

    override fun run() {
        // Root command is a command group; no direct behavior yet.
    }
}

fun buildCli(cwd: () -> Path = CommandEnvironment::cwd): Kli {
    return Kli()
        .subcommands(
            InitCommand(cwd),
            ProjectLintCommand(cwd),
            CleanCommand(cwd),
            CleanAllCommand(),
            RefreshCommand(cwd),
            RunCommand(cwd),
            TestCommand(cwd),
            BuildCommand(cwd),
            PackageCommand(cwd),
            PublishCommand(cwd),
            buildDependencyCommand(cwd),
            CompletionCommand(cwd),
        )
}

fun runCli(args: Array<String>, cwd: () -> Path = CommandEnvironment::cwd): Int {
    val effectiveArgs = if (args.isEmpty()) arrayOf("--help") else args

    return try {
        buildCli(cwd).parse(effectiveArgs)
        0
    } catch (ex: PrintHelpMessage) {
        ex.context?.command?.echoFormattedHelp(ex)
            ?: ex.message?.let { println(it) }
        ex.statusCode
    } catch (ex: UsageError) {
        ex.message?.let { System.err.println(it) }
        2
    } catch (ex: PrintMessage) {
        ex.message?.let {
            if (ex.printError) System.err.println(it) else println(it)
        }
        ex.statusCode
    } catch (ex: CliktError) {
        ex.message?.let { System.err.println(it) }
        ex.statusCode
    }
}

fun main(args: Array<String>) {
    exitProcess(runCli(args))
}
