package kli.test

import kli.project.ProjectConfig
import kli.project.TestDependencyDefaults
import kli.resolver.DependencyResolutionResult
import kli.resolver.DependencyResolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestWorkflowTest {
    @Test
    fun prepare_fails_when_project_json_missing() {
        val cwd = Files.createTempDirectory("kli-test-missing")
        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { cwd },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare(null)

        assertTrue(result is TestWorkflowOutcome.Failure)
        assertTrue((result as TestWorkflowOutcome.Failure).errors.first().contains("No project.json"))
    }

    @Test
    fun prepare_succeeds_with_all_tests_when_no_path_filter() {
        val root = Files.createTempDirectory("kli-test-workflow-all")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/OneTest.kt").writeText("class OneTest")
        root.resolve("tools/TwoTest.kt").writeText("class TwoTest")
        root.resolve("tools/Helper.kt").writeText("class Helper")

        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare(null)

        assertTrue(result is TestWorkflowOutcome.Success)
        val plan = (result as TestWorkflowOutcome.Success).plan
        assertEquals(2, plan.selectedTests.size)
        assertEquals(3, plan.compileSources.size)
    }

    @Test
    fun prepare_scopes_to_single_file_when_path_filter_is_file() {
        val root = Files.createTempDirectory("kli-test-workflow-file")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/OneTest.kt").writeText("class OneTest")
        root.resolve("tools/TwoTest.kt").writeText("class TwoTest")

        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare("tools/TwoTest.kt")

        assertTrue(result is TestWorkflowOutcome.Success)
        val plan = (result as TestWorkflowOutcome.Success).plan
        assertEquals(1, plan.selectedTests.size)
        assertTrue(plan.selectedTests.first().fileName.toString() == "TwoTest.kt")
    }

    @Test
    fun prepare_scopes_to_directory_when_path_filter_is_directory() {
        val root = Files.createTempDirectory("kli-test-workflow-dir")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools/api").toFile().mkdirs()
        root.resolve("tools/cli").toFile().mkdirs()
        root.resolve("tools/api/ApiTest.kt").writeText("class ApiTest")
        root.resolve("tools/cli/CliTest.kt").writeText("class CliTest")

        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare("tools/api")

        assertTrue(result is TestWorkflowOutcome.Success)
        val plan = (result as TestWorkflowOutcome.Success).plan
        assertEquals(1, plan.selectedTests.size)
        assertTrue(plan.selectedTests.first().toString().contains("ApiTest.kt"))
    }

    @Test
    fun prepare_fails_for_missing_path_filter_target() {
        val root = Files.createTempDirectory("kli-test-workflow-missing-path")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/OneTest.kt").writeText("class OneTest")

        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare("missing")

        assertTrue(result is TestWorkflowOutcome.Failure)
        assertTrue((result as TestWorkflowOutcome.Failure).errors.first().contains("No matching test files"))
    }

    @Test
    fun prepare_injects_default_test_deps_when_test_deps_are_missing() {
        val root = Files.createTempDirectory("kli-test-workflow-default-test-deps")
        root.resolve("project.json").writeText("""{"deps":[]}""")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/SampleTest.kt").writeText("class SampleTest")

        val home = Files.createTempDirectory("kli-home").toString()
        val workflow = TestWorkflow(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            userHome = home,
        )

        val result = workflow.prepare(null)

        assertTrue(result is TestWorkflowOutcome.Success)
        val plan = (result as TestWorkflowOutcome.Success).plan
        assertEquals(TestDependencyDefaults.defaults, plan.config.testDeps)
    }

    private class FakeResolver : DependencyResolver {
        override fun resolve(config: ProjectConfig): DependencyResolutionResult {
            return DependencyResolutionResult(runtimeClasspath = emptyList(), testClasspath = emptyList())
        }
    }
}
