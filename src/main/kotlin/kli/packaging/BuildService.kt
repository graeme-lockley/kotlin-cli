package kli.packaging

import kli.cache.ProjectCacheLayouts
import kli.compiler.EmbeddableKotlinCompiler
import kli.compiler.KotlinCompiler
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.resolver.DependencyResolver
import kli.resolver.MavenDependencyResolver
import kli.run.SourceLocator
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeBytes

sealed interface BuildOutcome {
    data class Success(
        val outputJar: Path,
    ) : BuildOutcome

    data class Failure(
        val message: String,
    ) : BuildOutcome
}

class BuildService(
    private val cwd: () -> Path,
    private val dependencyResolver: DependencyResolver = MavenDependencyResolver(),
    private val compiler: KotlinCompiler = EmbeddableKotlinCompiler(),
    private val jarBuilder: JarBuilder = SimpleJarBuilder(),
    private val userHome: String = System.getProperty("user.home"),
    private val onCompiledSource: (Path, Long) -> Unit = { _, _ -> },
) {
    fun build(outputOverride: Path?): BuildOutcome {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: return BuildOutcome.Failure("No project.json found in current directory or parents")

        val configResult = ProjectConfigParser.load(projectRoot.resolve("project.json"), strictUnknownFields = false)
        if (!configResult.isValid) {
            return BuildOutcome.Failure(configResult.errors.joinToString("; "))
        }
        val config = configResult.config ?: return BuildOutcome.Failure("Unable to parse project.json")

        val sourceFiles = SourceLocator.discoverRunSources(projectRoot, config)
        if (sourceFiles.isEmpty()) {
            return BuildOutcome.Failure("No Kotlin source files found for building")
        }

        val dependencies = try {
            dependencyResolver.resolve(config)
        } catch (ex: Exception) {
            return BuildOutcome.Failure("Dependency resolution failed: ${ex.message}")
        }

        val layout = ProjectCacheLayouts.forProject(projectRoot, userHome)
        ProjectCacheLayouts.ensureDirectories(layout)

        val compileClasspath = buildList {
            add(Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()))
            addAll(dependencies.runtimeClasspath)
        }.distinct()

        val startNanos = System.nanoTime()
        val compilation = compiler.compile(
            sourceFiles = sourceFiles,
            outputDirectory = layout.classesDir,
            classpath = compileClasspath,
            jvmTarget = config.target,
        )
        val durationMs = (System.nanoTime() - startNanos) / 1_000_000
        sourceFiles
            .map { it.toAbsolutePath().normalize() }
            .distinct()
            .sortedBy { it.toString() }
            .forEach { source -> onCompiledSource(source, durationMs) }

        if (!compilation.success) {
            return BuildOutcome.Failure(compilation.message ?: "Compilation failed")
        }

        val artifact = config.name ?: projectRoot.fileName.toString()
        val outputJar = outputOverride ?: projectRoot.resolve("dist").resolve("$artifact-${config.version}.jar")
        val resources = ResourceCollector.collect(projectRoot, config.resources)
        val dispatcherEntries = extractDispatcherEntries(layout.generatedDir)
        
        // Include Kotlin stdlib in the fat jar so the dispatcher can run
        val kotlinStdlib = Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI())
        val allRuntimeDeps = (listOf(kotlinStdlib) + dependencies.runtimeClasspath).distinct()
        
        jarBuilder.build(
            classesDir = layout.classesDir,
            runtimeDependencies = allRuntimeDeps,
            additionalEntries = resources + dispatcherEntries,
            outputJar = outputJar,
            mainClass = DISPATCHER_MAIN_CLASS,
        )

        return BuildOutcome.Success(outputJar = outputJar)
    }

    private fun extractDispatcherEntries(generatedDir: Path): Map<String, Path> {
        val dispatcherResource = "kli/dispatcher/MainDispatcherKt.class"
        val stream = this::class.java.classLoader.getResourceAsStream(dispatcherResource)
            ?: return emptyMap()

        val target = generatedDir.resolve(dispatcherResource)
        Files.createDirectories(target.parent)
        stream.use { input ->
            target.writeBytes(input.readBytes())
        }
        return mapOf(dispatcherResource to target)
    }

    private companion object {
        const val DISPATCHER_MAIN_CLASS = "kli.dispatcher.MainDispatcherKt"
    }
}
