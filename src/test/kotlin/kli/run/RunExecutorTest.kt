package kli.run

import kli.cache.ProjectCacheLayouts
import kli.compiler.CompilationResult
import kli.compiler.KotlinCompiler
import kli.project.ProjectConfig
import kli.resolver.DependencyResolutionResult
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunExecutorTest {
    @Test
    fun returns_failure_when_compilation_fails() {
        val projectRoot = Files.createTempDirectory("kli-run-exec-fail")
        val source = projectRoot.resolve("tools/Server.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "fun main() {}")

        val plan = createPlan(projectRoot, listOf(source))
        val compiler = FakeCompiler(CompilationResult(success = false, message = "nope"))
        val runner = FakeRunner(0)
        val executor = RunExecutor(compiler, runner)

        val result = executor.execute(plan)

        assertTrue(result is RunExecutionOutcome.Failure)
        assertEquals("nope", (result as RunExecutionOutcome.Failure).message)
        assertEquals(0, runner.invocations)
    }

    @Test
    fun invokes_runner_with_kt_suffix_main_class() {
        val projectRoot = Files.createTempDirectory("kli-run-exec-ok")
        val source = projectRoot.resolve("tools/Server.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "fun main() {}")

        val plan = createPlan(projectRoot, listOf(source), mainClass = "tools.Server")
        val compiler = FakeCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val executor = RunExecutor(compiler, runner)

        val result = executor.execute(plan)

        assertTrue(result is RunExecutionOutcome.Success)
        assertEquals(1, runner.invocations)
        assertEquals("tools.ServerKt", runner.lastMainClass)
    }

    private fun createPlan(
        projectRoot: Path,
        sourceFiles: List<Path>,
        mainClass: String = "tools.Server",
    ): RunPlan {
        val home = Files.createTempDirectory("kli-home").toString()
        val layout = ProjectCacheLayouts.forProject(projectRoot, home)
        ProjectCacheLayouts.ensureDirectories(layout)

        return RunPlan(
            projectRoot = projectRoot,
            config = ProjectConfig(),
            mainClass = mainClass,
            mainSourceFile = sourceFiles.first(),
            sourceFiles = sourceFiles,
            programArgs = listOf("--port", "8080"),
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
        ): CompilationResult = result
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
