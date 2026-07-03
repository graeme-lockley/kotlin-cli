package kli.run

import kli.cache.CompilationManifest
import kli.cache.ManifestStore
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

    @Test
    fun skips_compilation_when_manifest_is_up_to_date() {
        val projectRoot = Files.createTempDirectory("kli-run-exec-skip")
        val source = projectRoot.resolve("tools/Server.kt")
        Files.createDirectories(source.parent)
        Files.writeString(source, "fun main() {}")

        val plan = createPlan(projectRoot, listOf(source), mainClass = "tools.Server")
        val classFile = plan.cacheLayout.classesDir.resolve("tools/ServerKt.class")
        Files.createDirectories(classFile.parent)
        Files.writeString(classFile, "bytecode")

        val compiler = CountingCompiler(CompilationResult(success = true))
        val runner = FakeRunner(0)
        val store = ManifestStore()
        val sourceHash = kli.cache.SourceHasher.sha256(source)
        store.save(
            plan.cacheLayout.manifestFile,
            CompilationManifest(
                sourceHashes = mapOf("tools/Server.kt" to sourceHash),
                classpathFingerprint = kli.cache.IncrementalCompilation.classpathFingerprint(
                    listOf(Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())),
                ),
            ),
        )
        val executor = RunExecutor(compiler, runner, store)

        val result = executor.execute(plan)

        assertTrue(result is RunExecutionOutcome.Success)
        assertEquals(0, compiler.invocations)
        assertEquals(1, runner.invocations)
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

    private class CountingCompiler(
        private val result: CompilationResult,
    ) : KotlinCompiler {
        var invocations: Int = 0

        override fun compile(
            sourceFiles: List<Path>,
            outputDirectory: Path,
            classpath: List<Path>,
            jvmTarget: String,
        ): CompilationResult {
            invocations += 1
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
