package kli.packaging

import kli.compiler.CompilationResult
import kli.compiler.KotlinCompiler
import kli.project.ProjectConfig
import kli.resolver.DependencyResolutionResult
import kli.resolver.DependencyResolver
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageServiceTest {
    @Test
    fun fails_when_project_json_is_missing() {
        val cwd = Files.createTempDirectory("kli-package-missing")
        val service = PackageService(
            cwd = { cwd },
            dependencyResolver = FakeResolver(),
            compiler = FakeCompiler(CompilationResult(true)),
            jarBuilder = FakeJarBuilder(),
            installer = FakeInstaller(),
            userHome = Files.createTempDirectory("kli-home").toString(),
        )

        val result = service.build(outputOverride = null)

        assertTrue(result is PackageOutcome.Failure)
    }

    @Test
    fun builds_output_jar_and_installs_to_local_m2() {
        val root = Files.createTempDirectory("kli-package-ok")
        Files.writeString(root.resolve("project.json"), "{\"name\":\"demo\",\"version\":\"1.2.3\",\"deps\":[],\"resources\":[\"config/**\"]}")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        val sourceFile = toolsDir.resolve("Server.kt")
        Files.writeString(sourceFile, "fun main() {}")
        val configDir = Files.createDirectories(root.resolve("config"))
        Files.writeString(configDir.resolve("app.conf"), "mode=test")

        val outputJar = root.resolve("dist/custom.jar")
        val fakeJarBuilder = FakeJarBuilder()
        val fakeInstaller = FakeInstaller()

        val service = PackageService(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            compiler = FakeCompiler(CompilationResult(true)),
            jarBuilder = fakeJarBuilder,
            installer = fakeInstaller,
            userHome = Files.createTempDirectory("kli-home").toString(),
        )

        val result = service.build(outputOverride = outputJar)

        assertTrue(result is PackageOutcome.Success)
        result as PackageOutcome.Success
        assertEquals(outputJar.toAbsolutePath().normalize(), result.outputJar.toAbsolutePath().normalize())
        assertEquals(outputJar.toAbsolutePath().normalize(), fakeJarBuilder.lastOutputJar?.toAbsolutePath()?.normalize())
        assertEquals("kli.dispatcher.MainDispatcherKt", fakeJarBuilder.lastMainClass)
        assertTrue(fakeJarBuilder.lastRuntimeDependencies?.isEmpty() == true)
        assertTrue(fakeJarBuilder.lastAdditionalEntries?.containsKey("config/app.conf") == true)
        assertEquals("io.kli.local", fakeInstaller.lastGroupId)
        assertEquals("demo", fakeInstaller.lastArtifact)
        assertEquals("1.2.3", fakeInstaller.lastVersion)
    }

    @Test
    fun forwards_runtime_dependencies_to_jar_builder() {
        val root = Files.createTempDirectory("kli-package-deps")
        Files.writeString(root.resolve("project.json"), "{\"name\":\"demo\",\"version\":\"1.2.3\",\"deps\":[]}")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        val sourceFile = toolsDir.resolve("Server.kt")
        Files.writeString(sourceFile, "fun main() {}")

        val depJar = Files.createTempFile("dep", ".jar")
        Files.writeString(depJar, "placeholder")

        val fakeJarBuilder = FakeJarBuilder()

        val service = PackageService(
            cwd = { root },
            dependencyResolver = object : DependencyResolver {
                override fun resolve(config: ProjectConfig): DependencyResolutionResult {
                    return DependencyResolutionResult(runtimeClasspath = listOf(depJar), testClasspath = emptyList())
                }
            },
            compiler = FakeCompiler(CompilationResult(true)),
            jarBuilder = fakeJarBuilder,
            installer = FakeInstaller(),
            userHome = Files.createTempDirectory("kli-home").toString(),
        )

        val result = service.build(outputOverride = root.resolve("dist/out.jar"))

        assertTrue(result is PackageOutcome.Success)
        assertEquals(listOf(depJar), fakeJarBuilder.lastRuntimeDependencies)
    }

    @Test
    fun reports_compiled_sources_with_duration() {
        val root = Files.createTempDirectory("kli-package-progress")
        Files.writeString(root.resolve("project.json"), "{\"name\":\"demo\",\"version\":\"1.2.3\",\"deps\":[]}")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        val sourceFile = toolsDir.resolve("Server.kt")
        Files.writeString(sourceFile, "fun main() {}")

        val events = mutableListOf<Pair<Path, Long>>()
        val service = PackageService(
            cwd = { root },
            dependencyResolver = FakeResolver(),
            compiler = FakeCompiler(CompilationResult(true)),
            jarBuilder = FakeJarBuilder(),
            installer = FakeInstaller(),
            userHome = Files.createTempDirectory("kli-home").toString(),
            onCompiledSource = { file, durationMs -> events += file to durationMs },
        )

        val result = service.build(outputOverride = root.resolve("dist/out.jar"))

        assertTrue(result is PackageOutcome.Success)
        assertEquals(1, events.size)
        assertEquals(sourceFile.toAbsolutePath().normalize(), events.first().first)
        assertTrue(events.first().second >= 0)
    }

    private class FakeResolver : DependencyResolver {
        override fun resolve(config: ProjectConfig): DependencyResolutionResult {
            return DependencyResolutionResult(runtimeClasspath = emptyList(), testClasspath = emptyList())
        }
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
            Files.createDirectories(outputDirectory)
            Files.createDirectories(outputDirectory.resolve("tools"))
            Files.writeString(outputDirectory.resolve("tools/ServerKt.class"), "bytecode")
            return result
        }
    }

    private class FakeJarBuilder : JarBuilder {
        var lastOutputJar: Path? = null
        var lastMainClass: String? = null
        var lastRuntimeDependencies: List<Path>? = null
        var lastAdditionalEntries: Map<String, Path>? = null

        override fun build(
            classesDir: Path,
            runtimeDependencies: List<Path>,
            additionalEntries: Map<String, Path>,
            outputJar: Path,
            mainClass: String,
        ) {
            lastOutputJar = outputJar
            lastMainClass = mainClass
            lastRuntimeDependencies = runtimeDependencies
            lastAdditionalEntries = additionalEntries
            Files.createDirectories(outputJar.parent)
            Files.writeString(outputJar, "jar")
        }
    }

    private class FakeInstaller : MavenInstaller {
        var lastGroupId: String? = null
        var lastArtifact: String? = null
        var lastVersion: String? = null

        override fun install(jar: Path, groupId: String, artifact: String, version: String, userHome: String): Path {
            lastGroupId = groupId
            lastArtifact = artifact
            lastVersion = version
            return jar
        }
    }
}
