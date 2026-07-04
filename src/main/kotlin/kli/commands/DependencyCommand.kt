package kli.commands

import com.google.gson.GsonBuilder
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.cache.CacheCleaner
import kli.project.DependencyManager
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.resolver.DependencyTreeNode
import kli.resolver.MavenCentralVersionResolver
import kli.resolver.MavenCoordinate
import kli.resolver.MavenDependencyResolver
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
    private val tree by option("--tree", help = "Show dependency tree including transitives").flag(default = false)
    private val scope by option("--scope", help = "Scope: runtime, test, or all")
    private val format by option("--format", help = "Output format: text or json")

    override fun help(context: Context): String {
        return "List all dependencies"
    }

    override fun run() {
        val selectedScope = scope ?: "all"
        if (selectedScope !in setOf("runtime", "test", "all")) {
            echo("error: Invalid --scope value '$selectedScope'. Use runtime, test, or all.", err = true)
            throw ProgramResult(2)
        }

        val selectedFormat = format ?: "text"
        if (selectedFormat !in setOf("text", "json")) {
            echo("error: Invalid --format value '$selectedFormat'. Use text or json.", err = true)
            throw ProgramResult(2)
        }

        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(1)
            }

        if (tree) {
            val configResult = ProjectConfigParser.load(projectRoot.resolve("project.json"), strictUnknownFields = false)
            if (!configResult.isValid || configResult.config == null) {
                echo("error: ${configResult.errors.joinToString("; ")}", err = true)
                throw ProgramResult(1)
            }

            val trees = try {
                MavenDependencyResolver().resolveTrees(configResult.config)
            } catch (ex: Exception) {
                echo("error: Dependency tree resolution failed: ${ex.message}", err = true)
                throw ProgramResult(1)
            }

            val showRuntime = selectedScope == "runtime" || selectedScope == "all"
            val showTest = selectedScope == "test" || selectedScope == "all"

            if (selectedFormat == "json") {
                val runtimeTrees = if (showRuntime) trees.runtime else emptyList()
                val testTrees = if (showTest) trees.test else emptyList()
                echo(buildTreeJson(selectedScope, runtimeTrees, testTrees))
                return
            }

            var printed = false
            if (showRuntime) {
                if (trees.runtime.isNotEmpty()) {
                    echo("Runtime dependency tree:")
                    renderForest(trees.runtime).forEach { echo(it) }
                    printed = true
                } else if (selectedScope == "runtime") {
                    echo("(no runtime dependencies)")
                    return
                }
            }

            if (showTest) {
                if (trees.test.isNotEmpty()) {
                    if (printed) echo("")
                    echo("Test dependency tree:")
                    renderForest(trees.test).forEach { echo(it) }
                    printed = true
                } else if (selectedScope == "test") {
                    echo("(no test dependencies)")
                    return
                }
            }

            if (!printed) {
                echo("(no dependencies)")
            }
            return
        }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val result = manager.list()

        result.onSuccess { deps ->
            val showRuntime = selectedScope == "runtime" || selectedScope == "all"
            val showTest = selectedScope == "test" || selectedScope == "all"

            val runtimeDeps = if (showRuntime) deps.runtimeDeps else emptyList()
            val testDeps = if (showTest) deps.testDeps else emptyList()

            if (selectedFormat == "json") {
                echo(buildListJson(selectedScope, runtimeDeps, testDeps))
                return@onSuccess
            }

            if (deps.runtimeDeps.isEmpty() && deps.testDeps.isEmpty()) {
                echo("(no dependencies)")
                return@onSuccess
            }

            var printed = false
            if (showRuntime && deps.runtimeDeps.isNotEmpty()) {
                echo("Runtime dependencies:")
                deps.runtimeDeps.forEach { echo("  $it") }
                printed = true
            }

            if (showTest && deps.testDeps.isNotEmpty()) {
                if (printed) echo("")
                echo("Test dependencies:")
                deps.testDeps.forEach { echo("  $it") }
                printed = true
            }

            if (!printed) {
                when (selectedScope) {
                    "runtime" -> echo("(no runtime dependencies)")
                    "test" -> echo("(no test dependencies)")
                    else -> echo("(no dependencies)")
                }
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

            val registryUrl = registry ?: "https://repo.maven.apache.org/maven2"
            echo("Checking ${allDeps.size} dependencies against Maven Central...")
            if (offline) {
                echo("(offline mode - versions from local cache only)")
            }
            echo("")

            val versionResolver = MavenCentralVersionResolver()
            var hasUpdates = false
            var hasFailed = false

            // Check runtime dependencies
            if (deps.runtimeDeps.isNotEmpty()) {
                echo("Runtime dependencies:")
                deps.runtimeDeps.forEach { depString ->
                    try {
                        val coord = MavenCoordinate.parse(depString)
                        val versionInfo = if (offline) {
                            // In offline mode, we can't check, so mark as unknown
                            null
                        } else {
                            versionResolver.resolveVersions(depString, registryUrl)
                        }

                        if (versionInfo?.error != null) {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ✗ ${versionInfo.error}")
                            hasFailed = true
                        } else if (versionInfo?.latest == null && offline) {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ? (offline)")
                        } else if (versionInfo?.latest != null && versionInfo.latest != coord.version) {
                            echo("  ${coord.group}:${coord.artifact}  ${coord.version}  -> ${versionInfo.latest}  (latest)")
                            hasUpdates = true
                        } else {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ✓ up to date")
                        }
                    } catch (ex: Exception) {
                        echo("  $depString  ✗ Invalid coordinate")
                        hasFailed = true
                    }
                }
            }

            // Check test dependencies
            if (deps.testDeps.isNotEmpty()) {
                if (deps.runtimeDeps.isNotEmpty()) echo("")
                echo("Test dependencies:")
                deps.testDeps.forEach { depString ->
                    try {
                        val coord = MavenCoordinate.parse(depString)
                        val versionInfo = if (offline) {
                            null
                        } else {
                            versionResolver.resolveVersions(depString, registryUrl)
                        }

                        if (versionInfo?.error != null) {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ✗ ${versionInfo.error}")
                            hasFailed = true
                        } else if (versionInfo?.latest == null && offline) {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ? (offline)")
                        } else if (versionInfo?.latest != null && versionInfo.latest != coord.version) {
                            echo("  ${coord.group}:${coord.artifact}  ${coord.version}  -> ${versionInfo.latest}  (latest)")
                            hasUpdates = true
                        } else {
                            echo("  ${coord.group}:${coord.artifact}:${coord.version}  ✓ up to date")
                        }
                    } catch (ex: Exception) {
                        echo("  $depString  ✗ Invalid coordinate")
                        hasFailed = true
                    }
                }
            }

            // Exit code 2 if updates available or resolution failed
            if (hasUpdates || hasFailed) {
                throw ProgramResult(2)
            }
        }.onFailure { ex ->
            echo("error: ${ex.message}", err = true)
            throw ProgramResult(1)
        }
    }
}

class DependencyAddSubcommand(private val cwd: () -> Path) : CliktCommand(name = "add") {
    private val coordinate by argument("COORDINATE", help = "Maven coordinate (group:artifact or group:artifact:version)")
    private val scope by option("--scope", help = "Dependency scope: runtime or test")
    private val latest by option("--latest", help = "Resolve to latest version").flag(default = false)
    private val registry by option("--registry", help = "Registry for --latest resolution")
    private val noClean by option("--no-clean", help = "Skip cache cleanup").flag(default = false)

    override fun help(context: Context): String {
        return "Add a dependency"
    }

    override fun run() {
        val colonCount = coordinate.count { it == ':' }
        if (latest) {
            // With --latest, we need group:artifact (1 colon)
            if (colonCount != 1) {
                echo("error: With --latest, coordinate must be group:artifact (exactly 1 colon)", err = true)
                throw ProgramResult(2)
            }
        } else {
            // Without --latest, we need group:artifact:version (2 colons)
            if (colonCount != 2) {
                echo("error: Invalid coordinate. Format: group:artifact:version (or use --latest for automatic version resolution)", err = true)
                throw ProgramResult(2)
            }
        }

        val projectRoot = ProjectRootFinder.find(cwd())
            ?: run {
                echo("error: No project.json found", err = true)
                throw ProgramResult(2)
            }

        val manager = DependencyManager(projectRoot.resolve("project.json"))
        val resolvedScope = scope ?: "runtime"
        val registryUrl = registry ?: "https://repo.maven.apache.org/maven2"
        val outcome = manager.add(coordinate, resolvedScope, latest, registryUrl)

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
    private val coordinate by argument("COORDINATE", help = "Maven coordinate (optional)").optional()
    private val version by argument("VERSION", help = "Target version (optional)").optional()
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
        val registryUrl = registry ?: "https://repo.maven.apache.org/maven2"
        val outcome = manager.upgrade(coordinate, version, scope, dryRun, registryUrl)

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

private const val ANSI_LIGHT_GRAY = "\u001B[90m"
private const val ANSI_RESET = "\u001B[0m"
private const val BASE_TREE_INDENT = "    "
private const val TREE_SEGMENT_WITH_PIPE = "|       "
private const val TREE_SEGMENT_BLANK = "        "

internal fun renderForest(roots: List<DependencyTreeNode>, useColor: Boolean = true): List<String> {
    if (roots.isEmpty()) {
        return emptyList()
    }

    val lines = mutableListOf<String>()
    roots.forEach { root ->
        lines += renderLine(prefix = "- ", coordinate = root.coordinate, useColor = useColor)
        renderChildren(root.children, prefixSegments = "", out = lines, useColor = useColor)
    }
    return lines
}

private fun renderChildren(
    children: List<DependencyTreeNode>,
    prefixSegments: String,
    out: MutableList<String>,
    useColor: Boolean = true,
) {
    children.forEachIndexed { index, child ->
        val isLast = index == children.lastIndex
        val branch = "+-- "
        val linePrefix = BASE_TREE_INDENT + prefixSegments + branch
        out += renderLine(prefix = linePrefix, coordinate = child.coordinate, useColor = useColor)
        val nextSegments = prefixSegments + if (isLast) TREE_SEGMENT_BLANK else TREE_SEGMENT_WITH_PIPE
        renderChildren(child.children, nextSegments, out, useColor)
    }
}

private fun renderLine(prefix: String, coordinate: String, useColor: Boolean): String {
    if (!useColor) {
        return "$prefix$coordinate"
    }

    val treePart = colorizeLightGray(prefix)
    val coordinatePart = colorizeCoordinateWithGrayColons(coordinate)
    return "$treePart$coordinatePart"
}

private fun colorizeCoordinateWithGrayColons(coordinate: String): String {
    val parts = coordinate.split(":")
    if (parts.size < 2) {
        return coordinate
    }
    return parts.joinToString(separator = colorizeLightGray(":"))
}

private fun colorizeLightGray(text: String): String {
    return "$ANSI_LIGHT_GRAY$text$ANSI_RESET"
}

internal fun buildListJson(scope: String, runtimeDeps: List<String>, testDeps: List<String>): String {
    val payload = mapOf(
        "scope" to scope,
        "runtimeDeps" to runtimeDeps,
        "testDeps" to testDeps,
    )
    return GsonBuilder().setPrettyPrinting().create().toJson(payload)
}

internal fun buildTreeJson(scope: String, runtimeTrees: List<DependencyTreeNode>, testTrees: List<DependencyTreeNode>): String {
    val payload = mapOf(
        "scope" to scope,
        "runtime" to runtimeTrees,
        "test" to testTrees,
    )
    return GsonBuilder().setPrettyPrinting().create().toJson(payload)
}
