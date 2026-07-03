package kli.run

import kli.project.ProjectConfig
import kli.resolver.DependencyResolutionResult
import kli.resolver.DependencyResolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunWorkflowTest {
    @Test
    fun prepare_fails_when_project_json_missing() {
        val cwd = Files.createTempDirectory("kli-run-missing")
        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = RunWorkflow(
            cwd = { cwd },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare("tools.Server", emptyList())

        assertTrue(result is RunWorkflowOutcome.Failure)
        assertTrue((result as RunWorkflowOutcome.Failure).errors.first().contains("No project.json"))
    }

    @Test
    fun prepare_fails_when_config_invalid() {
        val root = Files.createTempDirectory("kli-run-invalid")
        Files.writeString(root.resolve("project.json"), "{\"deps\": [123]}")
        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = RunWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare("tools.Server", emptyList())

        assertTrue(result is RunWorkflowOutcome.Failure)
        assertTrue((result as RunWorkflowOutcome.Failure).errors.any { it.contains("deps[0]") })
    }

    @Test
    fun prepare_succeeds_and_creates_cache_layout() {
        val root = Files.createTempDirectory("kli-run-ok")
        Files.writeString(
            root.resolve("project.json"),
            """
            {
              "deps": ["org.jetbrains.kotlin:kotlin-stdlib:2.0.21"]
            }
            """.trimIndent(),
        )

        val fakeDependency = Files.createTempFile("dep", ".jar")
        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = RunWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(
                runtimeClasspath = listOf(fakeDependency),
                testClasspath = emptyList(),
            ),
            userHome = home,
        )

        val result = workflow.prepare("tools.Server", listOf("--port", "8080"))

        assertTrue(result is RunWorkflowOutcome.Success)
        val plan = (result as RunWorkflowOutcome.Success).plan
        assertEquals("tools.Server", plan.mainClass)
        assertEquals(listOf("--port", "8080"), plan.programArgs)
        assertEquals(1, plan.dependencies.runtimeClasspath.size)
        assertTrue(Files.isDirectory(plan.cacheLayout.classesDir))
        assertTrue(Files.isDirectory(plan.cacheLayout.resourcesDir))
        assertTrue(Files.isDirectory(plan.cacheLayout.generatedDir))
    }

    @Test
    fun prepare_fails_when_dependency_resolution_throws() {
        val root = Files.createTempDirectory("kli-run-resolve-fail")
        Files.writeString(root.resolve("project.json"), "{\"deps\":[]}")
        val home = Files.createTempDirectory("kli-home").toString()

        val workflow = RunWorkflow(
            cwd = { root },
            dependencyResolver = ThrowingResolver(),
            userHome = home,
        )

        val result = workflow.prepare("tools.Server", emptyList())

        assertTrue(result is RunWorkflowOutcome.Failure)
        assertTrue((result as RunWorkflowOutcome.Failure).errors.first().contains("Dependency resolution failed"))
    }

    private class FakeResolver(
        private val runtimeClasspath: List<Path> = emptyList(),
        private val testClasspath: List<Path> = emptyList(),
    ) : DependencyResolver {
        override fun resolve(config: ProjectConfig): DependencyResolutionResult {
            return DependencyResolutionResult(
                runtimeClasspath = runtimeClasspath,
                testClasspath = testClasspath,
            )
        }
    }

    private class ThrowingResolver : DependencyResolver {
        override fun resolve(config: ProjectConfig): DependencyResolutionResult {
            error("boom")
        }
    }
}
