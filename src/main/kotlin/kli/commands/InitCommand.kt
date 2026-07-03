package kli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import java.nio.file.Files
import java.nio.file.Path

class InitCommand(
    private val cwd: () -> Path,
) : CliktCommand(name = "init") {
    override fun help(context: Context): String {
        return "Scaffold a new Kotlin project"
    }

    private val projectName by argument(
        name = "name",
        help = "Project name and directory (defaults to current directory name)",
    ).optional()

    override fun run() {
        val projectDir = if (projectName != null) {
            val dir = cwd().resolve(projectName!!)
            Files.createDirectories(dir)
            dir
        } else {
            cwd()
        }

        val name = projectName ?: projectDir.fileName.toString()
        val projectJsonContent = """
            {
              "name": "$name",
              "version": "0.1.0",
              "deps": [],
              "target": "21"
            }
        """.trimIndent()

        val projectJsonPath = projectDir.resolve("project.json")
        try {
            // Create project.json if it doesn't exist, or overwrite if it does
            Files.writeString(projectJsonPath, projectJsonContent)
        } catch (e: Exception) {
            fail("Failed to create project.json: ${e.message}")
        }

        // Create tools directory and CLI.kt
        val toolsDir = projectDir.resolve("tools")
        try {
            Files.createDirectories(toolsDir)
        } catch (e: Exception) {
            fail("Failed to create tools directory: ${e.message}")
        }

        val cliKtContent = """
            fun main() {
                println("Hello from $name!")
            }
        """.trimIndent()

        val cliKtPath = toolsDir.resolve("CLI.kt")
        try {
            Files.writeString(cliKtPath, cliKtContent)
        } catch (e: Exception) {
            fail("Failed to create tools/CLI.kt: ${e.message}")
        }

        // Create .gitignore
        val gitignoreContent = """
            # Kotlin CLI build artifacts
            .kotlin/
            build/
            dist/
            *.jar
            *.class
        """.trimIndent()

        val gitignorePath = projectDir.resolve(".gitignore")
        try {
            // Only create if it doesn't exist
            if (!Files.exists(gitignorePath)) {
                Files.writeString(gitignorePath, gitignoreContent)
            }
        } catch (e: Exception) {
            fail("Failed to create .gitignore: ${e.message}")
        }

        echo("Created project '$name' in ${projectDir.toAbsolutePath()}")
    }

    private fun fail(message: String): Nothing {
        echo("error: $message", err = true)
        throw ProgramResult(1)
    }
}
