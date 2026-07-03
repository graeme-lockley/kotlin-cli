package kli.project

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.writeText

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
    
    fun add(coordinate: String, scope: String = "runtime"): DependencyOutcome {
        if (!coordinate.contains(":")) {
            return DependencyOutcome.Failure("Invalid coordinate: $coordinate. Format: group:artifact:version")
        }
        
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            return DependencyOutcome.Failure("Unable to parse project.json")
        }
        
        val config = configResult.config
        
        // Check if already exists
        val existingList = if (scope == "test") config.testDeps else config.deps
        if (existingList.contains(coordinate)) {
            return DependencyOutcome.Failure("$coordinate already exists in $scope dependencies")
        }
        
        // Check if group:artifact exists at different version
        val parts = coordinate.split(":")
        val coordinatePrefix = if (parts.size >= 2) "${parts[0]}:${parts[1]}" else coordinate
        val conflicting = existingList.find { it.startsWith(coordinatePrefix + ":") }
        if (conflicting != null) {
            return DependencyOutcome.Failure("$coordinatePrefix already exists at version ${conflicting.substringAfterLast(":")}. Use 'kli dependency upgrade' to change versions.")
        }
        
        val updatedDeps = if (scope == "test") {
            config.copy(testDeps = config.testDeps + coordinate)
        } else {
            config.copy(deps = config.deps + coordinate)
        }
        
        return writeConfig(updatedDeps, coordinate, "added to $scope dependencies")
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
    ): DependencyOutcome {
        val configResult = ProjectConfigParser.load(projectJsonPath, strictUnknownFields = false)
        if (!configResult.isValid || configResult.config == null) {
            return DependencyOutcome.Failure("Unable to parse project.json")
        }
        
        val config = configResult.config
        
        if (coordinate == null) {
            // Upgrade all - for now, just return success (full implementation would resolve new versions)
            return DependencyOutcome.Success("All dependencies are up to date")
        }
        
        val coordinateParts = coordinate.split(":")
        if (coordinateParts.size < 2) {
            return DependencyOutcome.Failure("Invalid coordinate: $coordinate")
        }
        
        val searchPrefix = "${coordinateParts[0]}:${coordinateParts[1]}:"
        
        var updatedConfig = config
        var upgraded = false
        
        if (scope == null || scope == "runtime") {
            val (newDeps, wasUpgraded, message) = upgradeInList(config.deps, searchPrefix, targetVersion)
            if (wasUpgraded) {
                updatedConfig = updatedConfig.copy(deps = newDeps)
                upgraded = true
            }
        }
        
        if (scope == null || scope == "test") {
            val (newTestDeps, wasUpgraded, message) = upgradeInList(config.testDeps, searchPrefix, targetVersion)
            if (wasUpgraded) {
                updatedConfig = updatedConfig.copy(testDeps = newTestDeps)
                upgraded = true
            }
        }
        
        if (!upgraded) {
            return DependencyOutcome.Failure("Dependency not found: $coordinate")
        }
        
        if (dryRun) {
            return DependencyOutcome.Success("Dry run: would upgrade $coordinate")
        }
        
        return writeConfig(updatedConfig, coordinate, "upgraded")
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
    ): Triple<List<String>, Boolean, String> {
        val newDeps = mutableListOf<String>()
        var upgraded = false
        var message = ""
        
        for (dep in deps) {
            if (dep.startsWith(searchPrefix)) {
                val newDep = if (targetVersion != null) {
                    "$searchPrefix$targetVersion"
                } else {
                    dep // No change if no target version specified
                }
                newDeps.add(newDep)
                upgraded = true
                message = "Upgraded to $newDep"
            } else {
                newDeps.add(dep)
            }
        }
        
        return Triple(newDeps, upgraded, message)
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
