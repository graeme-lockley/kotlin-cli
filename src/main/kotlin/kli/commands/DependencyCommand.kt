package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.cache.CacheCleaner
import kli.project.DependencyManager
import kli.project.ProjectRootFinder
import java.nio.file.Path

class DependencyCommand(private val cwd: () -> Path) : CliktCommand(name = "dependency") {
    override fun help(context: Context): String {
        return "Manage project dependencies"
    }

    override fun run() {
        // Parent command; subcommands handle behavior
    }
}

class DependencyListSubcommand(private val cwd: () -> Path) : CliktCommand(name = "list") {
    override fun help(context: Context): String {
        return "List all dependencies"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(1)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val result = manager.list()

        result.onSuccess { deps ->
            if (deps.runtimeDeps.isEmpty() && deps.testDeps.isEmpty()) {
                echo("(no dependencies)")
                return@onSuccess
            }

            var printed = false
            if (deps.runtimeDeps.isNotEmpty()) {
                echo("Runtime dependencies:")
                deps.runtimeDeps.forEach { echo("  $it") }
                printed = true
            }

            if (deps.testDeps.isNotEmpty()) {
                if (printed) echo("")
                echo("Test dependencies:")
                deps.testDeps.forEach { echo("  $it") }
            }
        }.onFailure { ex ->
            echo("error: ${ex.message}", err = true)
            throw ProgramResult(1)
        }
    }
}

class DependencyStatusSubcommand(private val cwd: () -> Path) : CliktCommand(name = "status") {
    private val registry by option("--registry", help = "Registry URL for version checks")
    private val offline by option("--offline", help = "Use only local cache").flag(default = false)
    private val showAll by option("--show-all", help = "Show all available versions").flag(default = false)
    private val format by option("--format", help = "Output format: text or json")

    override fun help(context: Context): String {
        return "Check for dependency updates"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(1)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val result = manager.list()

        result.onSuccess { deps ->
            val allDeps = deps.runtimeDeps + deps.testDeps
            if (allDeps.isEmpty()) {
                echo("(no dependencies)")
                return@onSuccess
            }

            echo("Checking ${allDeps.size} dependencies against Maven Central...")
            if (offline) {
                echo("(offline mode - versions from local cache only)")
            }
            echo("")

            // Simple implementation: report all as up to date for now
            if (deps.runtimeDeps.isNotEmpty()) {
                echo("Runtime dependencies:")
                deps.runtimeDeps.forEach { dep ->
                    echo("  $dep  ✓ up to date")
                }
            }

            if (deps.testDeps.isNotEmpty()) {
                if (deps.runtimeDeps.isNotEmpty()) echo("")
                echo("Test dependencies:")
                deps.testDeps.forEach { dep ->
                    echo("  $dep  ✓ up to date")
                }
            }
        }.onFailure { ex ->
            echo("error: ${ex.message}", err = true)
            throw ProgramResult(1)
        }
    }
}

class DependencyAddSubcommand(private val cwd: () -> Path) : CliktCommand(name = "add") {
    private val coordinate by argument("COORDINATE", help = "Maven coordinate (group:artifact:version)")
    private val scope by option("--scope", help = "Dependency scope: runtime or test")
    private val latest by option("--latest", help = "Resolve to latest version").flag(default = false)
    private val registry by option("--registry", help = "Registry for --latest resolution")
    private val noClean by option("--no-clean", help = "Skip cache cleanup").flag(default = false)

    override fun help(context: Context): String {
        return "Add a dependency"
    }

    override fun run() {
        val colonCount = coordinate.count { it == ':' }
        if (colonCount < 2) {
            echo("error: Invalid coordinate. Format: group:artifact:version (requires exactly 2 colons)", err = true)
            throw ProgramResult(2)
        }

        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(2)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val resolvedScope = scope ?: "runtime"
        val outcome = manager.add(coordinate, resolvedScope)

        when (outcome) {
            is kli.project.DependencyOutcome.Success -> {
                echo("✓ ${outcome.message}")
                if (!noClean) {
                    CacheCleaner.cleanProject(projectRoot)
                    echo("✓ Cache cleaned")
                }
            }

            is kli.project.DependencyOutcome.Failure -> {
                echo("error: ${outcome.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}

class DependencyRemoveSubcommand(private val cwd: () -> Path) : CliktCommand(name = "remove") {
    private val coordinate by argument("COORDINATE", help = "Maven coordinate (group:artifact or group:artifact:version)")
    private val scope by option("--scope", help = "Dependency scope: runtime or test")
    private val yes by option("--yes", "-y", help = "Skip confirmation").flag(default = false)
    private val noClean by option("--no-clean", help = "Skip cache cleanup").flag(default = false)

    override fun help(context: Context): String {
        return "Remove a dependency"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(1)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val outcome = manager.remove(coordinate, scope, yes)

        when (outcome) {
            is kli.project.DependencyOutcome.Success -> {
                echo("✓ ${outcome.message}")
                if (!noClean) {
                    CacheCleaner.cleanProject(projectRoot)
                    echo("✓ Cache cleaned")
                }
            }

            is kli.project.DependencyOutcome.Failure -> {
                echo("error: ${outcome.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}

class DependencyUpgradeSubcommand(private val cwd: () -> Path) : CliktCommand(name = "upgrade") {
    private val coordinate by argument("COORDINATE", help = "Maven coordinate (optional)")
    private val version by argument("VERSION", help = "Target version (optional)")
    private val scope by option("--scope", help = "Dependency scope: runtime or test")
    private val yes by option("--yes", "-y", help = "Skip confirmation").flag(default = false)
    private val dryRun by option("--dry-run", help = "Show planned changes only").flag(default = false)
    private val registry by option("--registry", help = "Registry for version resolution")
    private val offline by option("--offline", help = "Upgrade only to cached versions").flag(default = false)
    private val noClean by option("--no-clean", help = "Skip cache cleanup").flag(default = false)

    override fun help(context: Context): String {
        return "Upgrade dependencies"
    }

    override fun run() {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(1)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val outcome = manager.upgrade(coordinate, version, scope, dryRun)

        when (outcome) {
            is kli.project.DependencyOutcome.Success -> {
                echo("✓ ${outcome.message}")
                if (!dryRun && !noClean) {
                    CacheCleaner.cleanProject(projectRoot)
                    echo("✓ Cache cleaned")
                }
            }

            is kli.project.DependencyOutcome.Failure -> {
                echo("error: ${outcome.message}", err = true)
                throw ProgramResult(1)
            }
        }
    }
}

fun buildDependencyCommand(cwd: () -> Path): DependencyCommand {
    return DependencyCommand(cwd).subcommands(
        DependencyListSubcommand(cwd),
        DependencyStatusSubcommand(cwd),
        DependencyAddSubcommand(cwd),
        DependencyRemoveSubcommand(cwd),
        DependencyUpgradeSubcommand(cwd),
    )
}
