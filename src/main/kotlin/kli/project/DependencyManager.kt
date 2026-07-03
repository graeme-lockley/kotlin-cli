package kli.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kli.resolver.MavenCentralVersionResolver
import kli.resolver.MavenCoordinate

sealed interface DependencyOutcome {
    data class Success(val message: String) : DependencyOutcome
    data class Failure(val message: String) : DependencyOutcome
}

data class DependencyListResult(
    val runtimeDeps: List<String>,
    val testDeps: List<String>,
)

class DependencyManager(private val projectJsonPath: Path) {
    
    fun list(): Result<DependencyListResult> = runCatching {
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            throw IllegalStateException(configResult.errors.joinToString("; "))
        }
        DependencyListResult(
            runtimeDeps = configResult.config.deps,
            testDeps = configResult.config.testDeps,
        )
    }
    
    fun add(coordinate: String, scope: String = "runtime", latest: Boolean = false, registryUrl: String = "https://repo.maven.apache.org/maven2"): DependencyOutcome {
        if (!coordinate.contains(":")) {
            return DependencyOutcome.Failure("Invalid coordinate: $coordinate. Format: group:artifact (requires --latest) or group:artifact:version")
        }
        
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            return DependencyOutcome.Failure("Unable to parse project.json")
        }
        
        val config = configResult.config
        
        // Resolve version if needed
        val resolvedCoordinate = if (latest) {
            val parts = coordinate.split(":")
            if (parts.size != 2) {
                return DependencyOutcome.Failure("Invalid coordinate for --latest: $coordinate. Format: group:artifact")
            }
            val versionResolver = MavenCentralVersionResolver()
            val versionInfo = versionResolver.resolveVersions(coordinate, registryUrl)
            if (versionInfo.error != null) {
                return DependencyOutcome.Failure("Failed to resolve latest version: ${versionInfo.error}")
            }
            if (versionInfo.latest == null) {
                return DependencyOutcome.Failure("No versions found for $coordinate")
            }
            "$coordinate:${versionInfo.latest}"
        } else {
            if (!coordinate.contains(":") || coordinate.split(":").size != 3) {
                return DependencyOutcome.Failure("Invalid coordinate: $coordinate. Format: group:artifact:version (or use --latest)")
            }
            coordinate
        }
        
        // Check if already exists
        val existingList = if (scope == "test") config.testDeps else config.deps
        if (existingList.contains(resolvedCoordinate)) {
            return DependencyOutcome.Failure("$resolvedCoordinate already exists in $scope dependencies")
        }
        
        // Check if group:artifact exists at different version
        val parts = resolvedCoordinate.split(":")
        val coordinatePrefix = if (parts.size >= 2) "${parts[0]}:${parts[1]}" else resolvedCoordinate
        val conflicting = existingList.find { it.startsWith(coordinatePrefix + ":") }
        if (conflicting != null) {
            return DependencyOutcome.Failure("$coordinatePrefix already exists at version ${conflicting.substringAfterLast(":")}. Use 'kli dependency upgrade' to change versions.")
        }
        
        val updatedDeps = if (scope == "test") {
            config.copy(testDeps = config.testDeps + resolvedCoordinate)
        } else {
            config.copy(deps = config.deps + resolvedCoordinate)
        }
        
        return writeConfig(updatedDeps, resolvedCoordinate, "added to $scope dependencies")
    }
    
    fun remove(coordinate: String, scope: String? = null, force: Boolean = false): DependencyOutcome {
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            return DependencyOutcome.Failure("Unable to parse project.json")
        }
        
        val config = configResult.config
        
        val coordinateParts = coordinate.split(":")
        val isPartialMatch = coordinateParts.size < 3
        
        var updatedConfig = config
        var removed = false
        
        if (scope == null || scope == "runtime") {
            val (newDeps, wasRemoved) = removeMatchingDeps(config.deps, coordinate, isPartialMatch)
            updatedConfig = updatedConfig.copy(deps = newDeps)
            removed = removed || wasRemoved
        }
        
        if (scope == null || scope == "test") {
            val (newTestDeps, wasRemoved) = removeMatchingDeps(config.testDeps, coordinate, isPartialMatch)
            updatedConfig = updatedConfig.copy(testDeps = newTestDeps)
            removed = removed || wasRemoved
        }
        
        if (!removed) {
            return DependencyOutcome.Failure("Dependency not found: $coordinate")
        }
        
        return writeConfig(updatedConfig, coordinate, "removed")
    }
    
    fun upgrade(
        coordinate: String?,
        targetVersion: String?,
        scope: String? = null,
        dryRun: Boolean = false,
        registryUrl: String = "https://repo.maven.apache.org/maven2",
    ): DependencyOutcome {
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            return DependencyOutcome.Failure("Unable to parse project.json")
        }

        val config = configResult.config
        val versionResolver = MavenCentralVersionResolver()

        if (coordinate == null) {
            // Upgrade all dependencies to latest
            return upgradeAllToLatest(config, versionResolver, registryUrl, scope, dryRun)
        }

        if (targetVersion == null) {
            // Coordinate provided but no version - resolve to latest
            return upgradeToLatest(config, coordinate, versionResolver, registryUrl, scope, dryRun)
        }

        // Explicit version provided
        return upgradeToVersion(config, coordinate, targetVersion, scope, dryRun)
    }

    private fun upgradeAllToLatest(
        config: ProjectConfig,
        versionResolver: MavenCentralVersionResolver,
        registryUrl: String,
        scope: String?,
        dryRun: Boolean,
    ): DependencyOutcome {
        var updatedConfig = config
        val upgrades = mutableListOf<Pair<String, String>>()  // old -> new
        var failed = false

        if (scope == null || scope == "runtime") {
            val (newDeps, upgradedPairs, hadFailure) = upgradeListToLatest(config.deps, versionResolver, registryUrl)
            updatedConfig = updatedConfig.copy(deps = newDeps)
            upgrades.addAll(upgradedPairs)
            failed = failed || hadFailure
        }

        if (scope == null || scope == "test") {
            val (newTestDeps, upgradedPairs, hadFailure) = upgradeListToLatest(config.testDeps, versionResolver, registryUrl)
            updatedConfig = updatedConfig.copy(testDeps = newTestDeps)
            upgrades.addAll(upgradedPairs)
            failed = failed || hadFailure
        }

        if (upgrades.isEmpty()) {
            return DependencyOutcome.Success("All dependencies already at latest versions")
        }

        val upgradeMessages = upgrades.joinToString("\n  ") { (oldDep, newDep) ->
            val oldVersion = oldDep.substringAfterLast(":")
            val newVersion = newDep.substringAfterLast(":")
            "${oldDep.substringBeforeLast(":")}  $oldVersion -> $newVersion"
        }

        if (dryRun) {
            return DependencyOutcome.Success("Dry run: would upgrade:\n  $upgradeMessages")
        }

        if (failed) {
            return DependencyOutcome.Failure("Some dependencies could not be resolved; no updates applied")
        }

        return writeConfig(updatedConfig, "Multiple dependencies", "upgraded")
    }

    private fun upgradeListToLatest(
        deps: List<String>,
        versionResolver: MavenCentralVersionResolver,
        registryUrl: String,
    ): Triple<List<String>, List<Pair<String, String>>, Boolean> {
        val newDeps = mutableListOf<String>()
        val upgradedPairs = mutableListOf<Pair<String, String>>()
        var hadFailure = false

        for (dep in deps) {
            try {
                val coord = MavenCoordinate.parse(dep)
                val versionInfo = versionResolver.resolveVersions(dep, registryUrl)

                if (versionInfo.error != null || versionInfo.latest == null) {
                    hadFailure = true
                    newDeps.add(dep)  // Keep original on failure
                } else if (versionInfo.latest != coord.version) {
                    val newDep = "${coord.group}:${coord.artifact}:${versionInfo.latest}"
                    newDeps.add(newDep)
                    upgradedPairs.add(dep to newDep)
                } else {
                    newDeps.add(dep)
                }
            } catch (ex: Exception) {
                hadFailure = true
                newDeps.add(dep)
            }
        }

        return Triple(newDeps, upgradedPairs, hadFailure)
    }

    private fun upgradeToLatest(
        config: ProjectConfig,
        coordinate: String,
        versionResolver: MavenCentralVersionResolver,
        registryUrl: String,
        scope: String?,
        dryRun: Boolean,
    ): DependencyOutcome {
        val coordinateParts = coordinate.split(":")
        if (coordinateParts.size < 2) {
            return DependencyOutcome.Failure("Invalid coordinate: $coordinate")
        }

        val searchPrefix = "${coordinateParts[0]}:${coordinateParts[1]}:"

        // Find the existing dependency
        var existingDep: String? = null

        if (scope == null || scope == "runtime") {
            existingDep = config.deps.find { it.startsWith(searchPrefix) }
        }

        if (existingDep == null && (scope == null || scope == "test")) {
            existingDep = config.testDeps.find { it.startsWith(searchPrefix) }
        }

        if (existingDep == null) {
            return DependencyOutcome.Failure("Dependency not found: $coordinate")
        }

        // Resolve to latest version
        val versionInfo = versionResolver.resolveVersions(existingDep, registryUrl)
        if (versionInfo.error != null || versionInfo.latest == null) {
            return DependencyOutcome.Failure("Could not resolve latest version: ${versionInfo.error ?: "Unknown error"}")
        }

        val coord = MavenCoordinate.parse(existingDep)
        if (versionInfo.latest == coord.version) {
            return DependencyOutcome.Success("$coordinate already at latest version (${versionInfo.latest})")
        }

        return upgradeToVersion(config, coordinate, versionInfo.latest, scope, dryRun)
    }

    private fun upgradeToVersion(
        config: ProjectConfig,
        coordinate: String,
        targetVersion: String,
        scope: String?,
        dryRun: Boolean,
    ): DependencyOutcome {
        val coordinateParts = coordinate.split(":")
        if (coordinateParts.size < 2) {
            return DependencyOutcome.Failure("Invalid coordinate: $coordinate")
        }

        val searchPrefix = "${coordinateParts[0]}:${coordinateParts[1]}:"

        var updatedConfig = config
        var upgraded = false
        var oldDep: String? = null

        if (scope == null || scope == "runtime") {
            val (newDeps, wasUpgraded, foundDep) = upgradeInList(config.deps, searchPrefix, targetVersion)
            if (wasUpgraded) {
                updatedConfig = updatedConfig.copy(deps = newDeps)
                upgraded = true
                oldDep = foundDep
            }
        }

        if (!upgraded && (scope == null || scope == "test")) {
            val (newTestDeps, wasUpgraded, foundDep) = upgradeInList(config.testDeps, searchPrefix, targetVersion)
            if (wasUpgraded) {
                updatedConfig = updatedConfig.copy(testDeps = newTestDeps)
                upgraded = true
                oldDep = foundDep
            }
        }

        if (!upgraded) {
            return DependencyOutcome.Failure("Dependency not found: $coordinate")
        }

        val oldVersion = oldDep?.substringAfterLast(":") ?: "unknown"

        if (dryRun) {
            return DependencyOutcome.Success("Dry run: would upgrade from $oldVersion to $targetVersion")
        }

        return writeConfig(updatedConfig, coordinate, "upgraded from $oldVersion -> $targetVersion")
    }
    
    private fun removeMatchingDeps(deps: List<String>, coordinate: String, isPartialMatch: Boolean): Pair<List<String>, Boolean> {
        val newDeps = mutableListOf<String>()
        var removed = false
        
        for (dep in deps) {
            if (isPartialMatch) {
                // Partial match: group:artifact matches group:artifact:version
                if (dep.startsWith("$coordinate:") || dep == coordinate) {
                    removed = true
                } else {
                    newDeps.add(dep)
                }
            } else {
                // Exact match
                if (dep == coordinate) {
                    removed = true
                } else {
                    newDeps.add(dep)
                }
            }
        }
        
        return Pair(newDeps, removed)
    }
    
    private fun upgradeInList(
        deps: List<String>,
        searchPrefix: String,
        targetVersion: String?,
    ): Triple<List<String>, Boolean, String?> {
        val newDeps = mutableListOf<String>()
        var upgraded = false
        var oldDep: String? = null
        
        for (dep in deps) {
            if (dep.startsWith(searchPrefix)) {
                val newDep = if (targetVersion != null) {
                    "$searchPrefix$targetVersion"
                } else {
                    dep // No change if no target version specified
                }
                newDeps.add(newDep)
                upgraded = true
                oldDep = dep
            } else {
                newDeps.add(dep)
            }
        }
        
        return Triple(newDeps, upgraded, oldDep)
    }
    
    private fun writeConfig(updatedConfig: ProjectConfig, coordinate: String, action: String): DependencyOutcome {
        return try {
            val json = serializeProjectConfig(updatedConfig)
            Files.writeString(projectJsonPath, json)
            DependencyOutcome.Success("$coordinate $action")
        } catch (ex: Exception) {
            DependencyOutcome.Failure("Failed to update project.json: ${ex.message}")
        }
    }
    
    private fun serializeProjectConfig(config: ProjectConfig): String {
        val sb = StringBuilder()
        sb.append("{\n")
        
        if (config.name != null) sb.append("  \"name\": \"${config.name}\",\n")
        sb.append("  \"version\": \"${config.version}\",\n")
        
        sb.append("  \"deps\": [")
        sb.append(config.deps.joinToString(", ") { "\"$it\"" })
        sb.append("],\n")
        
        sb.append("  \"testDeps\": [")
        sb.append(config.testDeps.joinToString(", ") { "\"$it\"" })
        sb.append("],\n")
        
        sb.append("  \"target\": \"${config.target}\"")
        
        if (config.sources.isNotEmpty() && config.sources != listOf(".")) {
            sb.append(",\n  \"sources\": [")
            sb.append(config.sources.joinToString(", ") { "\"$it\"" })
            sb.append("]")
        }
        
        if (config.resources.isNotEmpty()) {
            sb.append(",\n  \"resources\": [")
            sb.append(config.resources.joinToString(", ") { "\"$it\"" })
            sb.append("]")
        }
        
        if (config.jvmArgs.isNotEmpty()) {
            sb.append(",\n  \"jvmArgs\": [")
            sb.append(config.jvmArgs.joinToString(", ") { "\"$it\"" })
            sb.append("]")
        }
        
        if (config.repos.isNotEmpty()) {
            sb.append(",\n  \"repos\": [")
            sb.append(config.repos.joinToString(", ") { "\"$it\"" })
            sb.append("]")
        }
        
        sb.append("\n}\n")
        return sb.toString()
    }
}
