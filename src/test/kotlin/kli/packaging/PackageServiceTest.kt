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
        Files.writeString(root.resolve("project.json"), "{\"name\":\"demo\",\"version\":\"1.2.3\",\"deps\":[]}")
        val toolsDir = Files.createDirectories(root.resolve("tools"))
        val sourceFile = toolsDir.resolve("Server.kt")
        Files.writeString(sourceFile, "fun main() {}")

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
        assertEquals("demo", fakeInstaller.lastArtifact)
        assertEquals("1.2.3", fakeInstaller.lastVersion)
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

        override fun build(classesDir: Path, outputJar: Path) {
            lastOutputJar = outputJar
            Files.createDirectories(outputJar.parent)
            Files.writeString(outputJar, "jar")
        }
    }

    private class FakeInstaller : MavenInstaller {
        var lastArtifact: String? = null
        var lastVersion: String? = null

        override fun install(jar: Path, artifact: String, version: String, userHome: String): Path {
            lastArtifact = artifact
            lastVersion = version
            return jar
        }
    }
}
