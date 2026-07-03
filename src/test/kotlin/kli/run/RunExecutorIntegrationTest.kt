package kli.run

import kli.cache.ProjectCacheLayouts
import kli.project.ProjectConfig
import kli.resolver.DependencyResolutionResult
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class RunExecutorIntegrationTest {
    @Test
    fun compiles_and_runs_simple_project_main() {
        val projectRoot = Files.createTempDirectory("kli-run-integration")
        val toolsDir = Files.createDirectories(projectRoot.resolve("tools"))
        val outputFile = projectRoot.resolve("output.txt")

        Files.writeString(
            toolsDir.resolve("Server.kt"),
            """
            package tools

            import java.nio.file.Files
            import java.nio.file.Paths

            fun main(args: Array<String>) {
                Files.writeString(Paths.get(args[0]), "ok")
            }
            """.trimIndent(),
        )

        val sourceFile = toolsDir.resolve("Server.kt")
        val home = Files.createTempDirectory("kli-home").toString()
        val layout = ProjectCacheLayouts.forProject(projectRoot, home)
        ProjectCacheLayouts.ensureDirectories(layout)

        val plan = RunPlan(
            projectRoot = projectRoot,
            config = ProjectConfig(),
            mainClass = "tools.Server",
            mainSourceFile = sourceFile,
            sourceFiles = listOf(sourceFile),
            programArgs = listOf(outputFile.toString()),
            cacheLayout = layout,
            dependencies = DependencyResolutionResult(
                runtimeClasspath = emptyList(),
                testClasspath = emptyList(),
            ),
        )

        val result = RunExecutor().execute(plan)

        assertTrue(result is RunExecutionOutcome.Success)
        assertTrue(Files.isRegularFile(outputFile))
        assertTrue(Files.readString(outputFile) == "ok")
    }
}
