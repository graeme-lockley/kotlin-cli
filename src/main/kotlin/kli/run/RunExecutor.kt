package kli.run

import kli.cache.CompilationManifest
import kli.cache.IncrementalCompilation
import kli.cache.ManifestStore
import kli.compiler.CompilationResult
import kli.compiler.EmbeddableKotlinCompiler
import kli.compiler.KotlinCompiler
import java.nio.file.Path

sealed interface RunExecutionOutcome {
    data class Success(
        val exitCode: Int,
    ) : RunExecutionOutcome

    data class Failure(
        val message: String,
    ) : RunExecutionOutcome
}

class RunExecutor(
    private val compiler: KotlinCompiler = EmbeddableKotlinCompiler(),
    private val programRunner: ProgramRunner = ReflectiveProgramRunner(),
    private val manifestStore: ManifestStore = ManifestStore(),
    private val onCompiledSource: (Path, Long) -> Unit = { _, _ -> },
    private val onRuntimeDiagnostics: (RunPlan, List<Path>, List<Path>, String) -> Unit = { _, _, _, _ -> },
) {
    fun execute(plan: RunPlan): RunExecutionOutcome {
        val normalizedSources = plan.sourceFiles
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .sortedBy { it.toString() }
        val sourceByKey = normalizedSources.associateBy { source ->
            plan.projectRoot.toAbsolutePath().normalize().relativize(source).toString()
        }
        val compileClasspath = buildCompileClasspath(plan)
        val sourceHashes = IncrementalCompilation.computeSourceHashes(plan.projectRoot, normalizedSources)
        val classpathFingerprint = IncrementalCompilation.classpathFingerprint(compileClasspath)
        val configFingerprint = IncrementalCompilation.configFingerprint(plan.config)
        val existingManifest = manifestStore.load(plan.cacheLayout.manifestFile)
        val requiresFullRecompile = IncrementalCompilation.requiresFullRecompile(
            manifest = existingManifest,
            sourceHashes = sourceHashes,
            classpathFingerprint = classpathFingerprint,
            configFingerprint = configFingerprint,
        )
        val changedSourceKeys = IncrementalCompilation.changedSourceKeys(existingManifest, sourceHashes)
        val changedSources = changedSourceKeys
            .mapNotNull { key -> sourceByKey[key] }
            .sortedBy { it.toString() }
        val classesMissing = !IncrementalCompilation.hasClassFiles(plan.cacheLayout.classesDir)

        val upToDate = IncrementalCompilation.isUpToDate(
            manifest = existingManifest,
            sourceHashes = sourceHashes,
            classpathFingerprint = classpathFingerprint,
            configFingerprint = configFingerprint,
            classesDir = plan.cacheLayout.classesDir,
        )

        if (!upToDate) {
            val compileTargets = when {
                requiresFullRecompile || classesMissing -> normalizedSources
                changedSources.isNotEmpty() -> changedSources
                else -> normalizedSources
            }
            val startNanos = System.nanoTime()
            val compilation = compiler.compile(
                sourceFiles = compileTargets,
                outputDirectory = plan.cacheLayout.classesDir,
                classpath = compileClasspath,
                jvmTarget = plan.config.target,
            )
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            compileTargets.forEach { source -> onCompiledSource(source, durationMs) }

            if (!compilation.success) {
                return RunExecutionOutcome.Failure(compilation.message ?: "Compilation failed")
            }

            manifestStore.save(
                plan.cacheLayout.manifestFile,
                CompilationManifest(
                    sourceHashes = sourceHashes,
                    classpathFingerprint = classpathFingerprint,
                    configFingerprint = configFingerprint,
                ),
            )
        }

        val runtimeClasspath = buildRuntimeClasspath(plan)
        val jvmMainClass = "${plan.mainClass}Kt"
        onRuntimeDiagnostics(plan, compileClasspath, runtimeClasspath, jvmMainClass)

        return try {
            val exitCode = programRunner.run(jvmMainClass, runtimeClasspath, plan.programArgs)
            RunExecutionOutcome.Success(exitCode)
        } catch (ex: Exception) {
            RunExecutionOutcome.Failure("Program execution failed: ${ex.message}")
        }
    }

    private fun buildRuntimeClasspath(plan: RunPlan): List<Path> {
        val stdlibPath = kotlinStdlibPath()
        return buildList {
            add(plan.cacheLayout.classesDir)
            add(plan.cacheLayout.resourcesDir)
            add(stdlibPath)
            addAll(plan.dependencies.runtimeClasspath)
        }.distinct()
    }

    private fun buildCompileClasspath(plan: RunPlan): List<Path> {
        return buildList {
            add(plan.cacheLayout.classesDir)
            add(kotlinStdlibPath())
            addAll(plan.dependencies.runtimeClasspath)
        }.distinct()
    }

    private fun kotlinStdlibPath(): Path = Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())
}
