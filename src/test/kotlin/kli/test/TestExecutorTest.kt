package kli.test

import kli.cache.ManifestStore
import kli.cache.ProjectCacheLayouts
import kli.compiler.CompilationResult
import kli.compiler.KotlinCompiler
import kli.project.ProjectConfig
import kli.resolver.DependencyResolutionResult
import kli.run.ProgramRunner
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TestExecutorTest {
    @Test
    fun returns_failure_when_compilation_fails() {
        val plan = createPlan()
        val compiler = FakeCompiler(CompilationResult(success = false, message = "compile failed"))
        val runner = FakeRunner(0)
        val executor = TestExecutor(compiler, runner, ManifestStore())

        val result = executor.execute(plan)

        assertTrue(result is TestExecutionOutcome.Failure)
        assertEquals("compile failed", (result as TestExecutionOutcome.Failure).message)
        assertEquals(0, runner.invocations)
    }

    @Test
    fun invokes_runner_when_compilation_succeeds() {
        val plan = createPlan()
        val compiler = FakeCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val executor = TestExecutor(compiler, runner, ManifestStore())

        val result = executor.execute(plan)

        assertTrue(result is TestExecutionOutcome.Success)
        assertEquals(1, runner.invocations)
        assertEquals("__test_runner__.TestRunnerKt", runner.lastMainClass)
    }

    @Test
    fun copies_resources_into_cache_resources_dir() {
        val plan = createPlan(resources = listOf("config/**"))
        val resourceFile = plan.projectRoot.resolve("config/app.conf")
        Files.createDirectories(resourceFile.parent)
        resourceFile.writeText("enabled=true")

        val compiler = FakeCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val executor = TestExecutor(compiler, runner, ManifestStore())

        val result = executor.execute(plan)

        assertTrue(result is TestExecutionOutcome.Success)
        val copied = plan.cacheLayout.resourcesDir.resolve("config/app.conf")
        assertTrue(copied.exists())
        assertEquals("enabled=true", copied.readText())
    }

    @Test
    fun reports_compiled_sources_with_duration_when_recompiling() {
        val plan = createPlan()
        val compiler = FakeCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val events = mutableListOf<Pair<Path, Long>>()
        val executor = TestExecutor(
            compiler = compiler,
            programRunner = runner,
            manifestStore = ManifestStore(),
            onCompiledSource = { file, durationMs -> events += file to durationMs },
        )

        val result = executor.execute(plan)

        assertTrue(result is TestExecutionOutcome.Success)
        assertTrue(events.isNotEmpty())
        assertTrue(events.all { it.second >= 0 })
    }

    @Test
    fun recompiles_only_changed_test_source_on_subsequent_runs() {
        val plan = createPlan()
        val compiler = RecordingCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val store = ManifestStore()
        val executor = TestExecutor(compiler, runner, store)

        val first = executor.execute(plan)
        assertTrue(first is TestExecutionOutcome.Success)
        assertTrue(compiler.lastSourceFiles.any { it.fileName.toString() == "AppTest.kt" })

        compiler.lastSourceFiles = emptyList()
        val testFile = plan.selectedTests.first()
        testFile.writeText("class AppTest { fun changed() = true }")

        val second = executor.execute(plan)
        assertTrue(second is TestExecutionOutcome.Success)
        assertEquals(listOf(testFile.toAbsolutePath().normalize()), compiler.lastSourceFiles)
    }

    @Test
    fun reports_non_null_error_message_when_runner_exception_has_no_message() {
        val plan = createPlan()
        val compiler = FakeCompiler(CompilationResult(success = true))
        val runner = object : ProgramRunner {
            override fun run(mainClass: String, classpath: List<Path>, programArgs: List<String>): Int {
                throw IllegalStateException()
            }
        }
        val executor = TestExecutor(compiler, runner, ManifestStore())

        val result = executor.execute(plan)

        assertTrue(result is TestExecutionOutcome.Failure)
        val message = (result as TestExecutionOutcome.Failure).message
        assertNotEquals("Test execution failed: null", message)
        assertTrue(message.startsWith("Test execution failed:"))
    }

    private fun createPlan(resources: List<String> = emptyList()): TestPlan {
        val root = Files.createTempDirectory("kli-test-executor")
        val source = root.resolve("tools/App.kt")
        val test = root.resolve("tools/AppTest.kt")
        Files.createDirectories(source.parent)
        source.writeText("class App")
        test.writeText("class AppTest")

        val home = Files.createTempDirectory("kli-home").toString()
        val layout = ProjectCacheLayouts.forProject(root, home)
        ProjectCacheLayouts.ensureDirectories(layout)

        return TestPlan(
            projectRoot = root,
            config = ProjectConfig(resources = resources),
            pathFilter = null,
            runSources = listOf(source),
            selectedTests = listOf(test),
            compileSources = listOf(source, test),
            cacheLayout = layout,
            dependencies = DependencyResolutionResult(
                runtimeClasspath = emptyList(),
                testClasspath = emptyList(),
            ),
        )
    }

    private class FakeCompiler(
        private val result: CompilationResult,
    ) : KotlinCompiler {
        override fun compile(
            sourceFiles: List<Path>,
            outputDirectory: Path,
            classpath: List<Path>,
            jvmTarget: String,
        ): CompilationResult {
            if (result.success) {
                val classFile = outputDirectory.resolve("__test_runner__/TestRunnerKt.class")
                Files.createDirectories(classFile.parent)
                classFile.writeText("bytecode")
            }
            return result
        }
    }

    private class RecordingCompiler(
        private val result: CompilationResult,
    ) : KotlinCompiler {
        var lastSourceFiles: List<Path> = emptyList()

        override fun compile(
            sourceFiles: List<Path>,
            outputDirectory: Path,
            classpath: List<Path>,
            jvmTarget: String,
        ): CompilationResult {
            lastSourceFiles = sourceFiles.map { it.toAbsolutePath().normalize() }.sortedBy { it.toString() }
            val classFile = outputDirectory.resolve("__test_runner__/TestRunnerKt.class")
            Files.createDirectories(classFile.parent)
            classFile.writeText("bytecode")
            return result
        }
    }

    private class FakeRunner(
        private val exitCode: Int,
    ) : ProgramRunner {
        var invocations: Int = 0
        var lastMainClass: String? = null

        override fun run(mainClass: String, classpath: List<Path>, programArgs: List<String>): Int {
            invocations += 1
            lastMainClass = mainClass
            return exitCode
        }
    }
}
