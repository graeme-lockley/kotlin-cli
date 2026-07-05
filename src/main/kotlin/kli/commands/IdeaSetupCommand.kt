package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import kli.project.ProjectConfig
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.project.TestDependencyDefaults
import java.nio.file.Files
import java.nio.file.Path

class IdeaSetupCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "idea-setup") {
    override fun help(context: Context): String {
        return "Generate IntelliJ/Gradle files for Kotlin IDE support"
    }

    private val force by option(
        "--force",
        help = "Overwrite existing settings.gradle.kts and build.gradle.kts",
    ).flag(default = false)
    private val verbose by option(
        "--verbose",
        "-v",
        help = "Show full stack traces on errors",
    ).flag(default = false)

    override fun run() {
        try {
            val projectRoot = ProjectRootFinder.find(cwd())
                ?: fail("No project.json found in current directory or parents")

            val configResult = ProjectConfigParser.load(projectRoot.resolve("project.json"), strictUnknownFields = false)
            if (!configResult.isValid || configResult.config == null) {
                fail(configResult.errors.joinToString("; "))
            }
            val config = configResult.config

            val settingsFile = projectRoot.resolve("settings.gradle.kts")
            val buildFile = projectRoot.resolve("build.gradle.kts")

            if (!force) {
                val existing = listOf(settingsFile, buildFile).filter { Files.exists(it) }
                if (existing.isNotEmpty()) {
                    fail("Refusing to overwrite existing files: ${existing.joinToString(", ") { it.fileName.toString() }}. Use --force to overwrite.")
                }
            }

            Files.writeString(settingsFile, buildSettingsContent(config))
            Files.writeString(buildFile, buildGradleContent(config))

            echo("Generated IntelliJ/Gradle files in ${projectRoot.toAbsolutePath()}")
            echo("Open this directory in IntelliJ IDEA and import as a Gradle project.")
        } catch (ex: ProgramResult) {
            throw ex
        } catch (ex: Exception) {
            echo("error: ${ex.message ?: "Unknown error"}", err = true)
            if (verbose) {
                ex.printStackTrace(System.err)
            }
            throw ProgramResult(1)
        }
    }

    private fun buildSettingsContent(config: ProjectConfig): String {
        val projectName = escapeKotlinString(config.name ?: cwd().fileName.toString())
        return """
            rootProject.name = "$projectName"
        """.trimIndent()
    }

    private fun buildGradleContent(config: ProjectConfig): String {
        val repositoriesBlock = config.repos.joinToString("\n") { repo ->
            "    maven(\"${escapeKotlinString(repo)}\")"
        }

        val runtimeDeps = config.deps.joinToString("\n") { dep ->
            "    implementation(\"${escapeKotlinString(dep)}\")"
        }

        val effectiveTestDeps = TestDependencyDefaults.withDefaults(config.testDeps)
        val testDeps = effectiveTestDeps.joinToString("\n") { dep ->
            "    testImplementation(\"${escapeKotlinString(dep)}\")"
        }

        val sourceDirs = config.sources.joinToString(", ") { source ->
            "\"${escapeKotlinString(source)}\""
        }

        val dependenciesBlock = buildString {
            appendLine("dependencies {")
            appendLine("    implementation(kotlin(\"stdlib\"))")
            if (runtimeDeps.isNotBlank()) {
                appendLine(runtimeDeps)
            }
            if (testDeps.isNotBlank()) {
                appendLine(testDeps)
            }
            append("}")
        }

        return """
            plugins {
                kotlin("jvm") version "2.0.21"
            }

            repositories {
            $repositoriesBlock
            }

            $dependenciesBlock

            kotlin {
                jvmToolchain(${config.target.toIntOrNull() ?: 21})
            }

            sourceSets {
                named("main") {
                    kotlin.srcDirs($sourceDirs)
                }
            }
        """.trimIndent()
    }

    private fun escapeKotlinString(value: String): String {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
    }

    private fun fail(message: String): Nothing {
        echo("error: $message", err = true)
        throw ProgramResult(1)
    }
}
