package kli.test

import kli.cache.CompilationManifest
import kli.cache.IncrementalCompilation
import kli.cache.ManifestStore
import kli.cache.SourceHasher
import kli.compiler.CompilationResult
import kli.compiler.EmbeddableKotlinCompiler
import kli.compiler.KotlinCompiler
import kli.packaging.ResourceCollector
import kli.run.ProgramRunner
import kli.run.ReflectiveProgramRunner
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface TestExecutionOutcome {
    data class Success(
        val discoveredTests: Int,
    ) : TestExecutionOutcome

    data class Failure(
        val message: String,
    ) : TestExecutionOutcome
}

class TestExecutor(
    private val compiler: KotlinCompiler = EmbeddableKotlinCompiler(),
    private val programRunner: ProgramRunner = ReflectiveProgramRunner(),
    private val manifestStore: ManifestStore = ManifestStore(),
) {
    fun execute(plan: TestPlan): TestExecutionOutcome {
        if (plan.selectedTests.isEmpty()) {
            return TestExecutionOutcome.Failure("No tests selected for execution")
        }

        val runnerSource = TestRunnerGenerator.generate(
            classesDir = plan.cacheLayout.classesDir,
            outputFile = plan.cacheLayout.generatedDir.resolve("TestRunner.kt"),
        )

        val resourceFiles = syncResources(plan)
        val compileSources = (plan.compileSources + listOf(runnerSource))
            .distinct()
            .map { it.toAbsolutePath().normalize() }
            .sortedBy { it.toString() }

        val missingSources = compileSources.filterNot { Files.isRegularFile(it) }
        if (missingSources.isNotEmpty()) {
            return TestExecutionOutcome.Failure(
                "Missing source files: ${missingSources.joinToString(", ")}",
            )
        }

        val compileClasspath = buildCompileClasspath(plan)
        val sourceHashes = computeFileHashes(compileSources)
        val resourceHashes = computeFileHashes(resourceFiles)
        val allHashes = (sourceHashes + resourceHashes).toSortedMap()
        val classpathFingerprint = IncrementalCompilation.classpathFingerprint(compileClasspath)
        val configFingerprint = testConfigFingerprint(plan)
        val manifestFile = plan.cacheLayout.projectCacheDir.resolve("test-manifest.json")
        val existingManifest = manifestStore.load(manifestFile)

        val upToDate = IncrementalCompilation.isUpToDate(
            manifest = existingManifest,
            sourceHashes = allHashes,
            classpathFingerprint = classpathFingerprint,
            configFingerprint = configFingerprint,
            classesDir = plan.cacheLayout.classesDir,
        )

        if (!upToDate) {
            val compilation = compiler.compile(
                sourceFiles = compileSources,
                outputDirectory = plan.cacheLayout.classesDir,
                classpath = compileClasspath,
                jvmTarget = plan.config.target,
            )
            if (!compilation.success) {
                return TestExecutionOutcome.Failure(compilation.message ?: "Compilation failed")
            }

            manifestStore.save(
                manifestFile,
                CompilationManifest(
                    sourceHashes = allHashes,
                    classpathFingerprint = classpathFingerprint,
                    configFingerprint = configFingerprint,
                ),
            )
        }

        val runtimeClasspath = buildRuntimeClasspath(plan)
        return try {
            programRunner.run("__test_runner__.TestRunnerKt", runtimeClasspath, emptyList())
            TestExecutionOutcome.Success(discoveredTests = plan.selectedTests.size)
        } catch (ex: Exception) {
            TestExecutionOutcome.Failure("Test execution failed: ${ex.message}")
        }
    }

    private fun buildCompileClasspath(plan: TestPlan): List<Path> {
        return buildList {
            add(kotlinStdlibPath())
            addAll(testFrameworkClasspath())
            addAll(plan.dependencies.runtimeClasspath)
            addAll(plan.dependencies.testClasspath)
        }.distinct()
    }

    private fun buildRuntimeClasspath(plan: TestPlan): List<Path> {
        return buildList {
            add(plan.cacheLayout.classesDir)
            add(plan.cacheLayout.resourcesDir)
            add(kotlinStdlibPath())
            addAll(testFrameworkClasspath())
            addAll(plan.dependencies.runtimeClasspath)
            addAll(plan.dependencies.testClasspath)
        }.distinct()
    }

    private fun syncResources(plan: TestPlan): List<Path> {
        val resources = ResourceCollector.collect(plan.projectRoot, plan.config.resources)
        if (Files.exists(plan.cacheLayout.resourcesDir)) {
            Files.walk(plan.cacheLayout.resourcesDir)
                .sorted(Comparator.reverseOrder())
                .forEach { path ->
                    if (path != plan.cacheLayout.resourcesDir) {
                        Files.deleteIfExists(path)
                    }
                }
        }
        Files.createDirectories(plan.cacheLayout.resourcesDir)

        resources.forEach { (relativePath, sourcePath) ->
            val target = plan.cacheLayout.resourcesDir.resolve(relativePath)
            Files.createDirectories(target.parent)
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
        }

        return resources.values.toList()
    }

    private fun testConfigFingerprint(plan: TestPlan): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val payload = buildString {
            append(plan.config.target)
            append('|')
            append(plan.config.sources.sorted().joinToString(","))
            append('|')
            append(plan.config.deps.sorted().joinToString(","))
            append('|')
            append(plan.config.testDeps.sorted().joinToString(","))
            append('|')
            append(plan.config.repos.sorted().joinToString(","))
            append('|')
            append(plan.config.jvmArgs.joinToString(","))
            append('|')
            append(plan.config.resources.sorted().joinToString(","))
            append('|')
            append(plan.pathFilter ?: "")
            append('|')
            append(plan.selectedTests.map { plan.projectRoot.relativize(it).toString() }.sorted().joinToString(","))
        }
        return digest.digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun kotlinStdlibPath(): Path = Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())

    private fun testFrameworkClasspath(): List<Path> {
        return listOfNotNull(
            Path.of(kotlin.test.Test::class.java.protectionDomain.codeSource.location.toURI()),
            classpathPath("kotlin.test.AssertionsKt"),
            Path.of(kotlin.test.junit5.JUnit5Asserter::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.junit.platform.launcher.Launcher::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.junit.platform.engine.discovery.DiscoverySelectors::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.junit.platform.commons.JUnitException::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.junit.jupiter.engine.JupiterTestEngine::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.opentest4j.AssertionFailedError::class.java.protectionDomain.codeSource.location.toURI()),
            Path.of(org.apiguardian.api.API::class.java.protectionDomain.codeSource.location.toURI()),
        ).distinct()
    }

    private fun classpathPath(className: String): Path? {
        return runCatching {
            Path.of(Class.forName(className).protectionDomain.codeSource.location.toURI())
        }.getOrNull()
    }

    private fun computeFileHashes(files: List<Path>): Map<String, String> {
        return files
            .asSequence()
            .map { it.toAbsolutePath().normalize() }
            .filter { Files.isRegularFile(it) }
            .distinct()
            .sortedBy { it.toString() }
            .associate { file ->
                file.toString() to SourceHasher.sha256(file)
            }
    }
}
