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
) {
    fun execute(plan: RunPlan): RunExecutionOutcome {
        val compileClasspath = buildCompileClasspath(plan)
        val sourceHashes = IncrementalCompilation.computeSourceHashes(plan.projectRoot, plan.sourceFiles)
        val classpathFingerprint = IncrementalCompilation.classpathFingerprint(compileClasspath)
        val configFingerprint = IncrementalCompilation.configFingerprint(plan.config)
        val existingManifest = manifestStore.load(plan.cacheLayout.manifestFile)

        val upToDate = IncrementalCompilation.isUpToDate(
            manifest = existingManifest,
            sourceHashes = sourceHashes,
            classpathFingerprint = classpathFingerprint,
            configFingerprint = configFingerprint,
            classesDir = plan.cacheLayout.classesDir,
        )

        if (!upToDate) {
            val compilation = compiler.compile(
                sourceFiles = plan.sourceFiles,
                outputDirectory = plan.cacheLayout.classesDir,
                classpath = compileClasspath,
                jvmTarget = plan.config.target,
            )

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
            add(stdlibPath)
            addAll(plan.dependencies.runtimeClasspath)
        }.distinct()
    }

    private fun buildCompileClasspath(plan: RunPlan): List<Path> {
        return buildList {
            add(kotlinStdlibPath())
            addAll(plan.dependencies.runtimeClasspath)
        }.distinct()
    }

    private fun kotlinStdlibPath(): Path = Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())
}
