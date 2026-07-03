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

sealed interface PackageOutcome {
    data class Success(
        val outputJar: Path,
        val installedJar: Path,
    ) : PackageOutcome

    data class Failure(
        val message: String,
    ) : PackageOutcome
}

class PackageService(
    private val cwd: () -> Path,
    private val dependencyResolver: DependencyResolver = MavenDependencyResolver(),
    private val compiler: KotlinCompiler = EmbeddableKotlinCompiler(),
    private val jarBuilder: JarBuilder = SimpleJarBuilder(),
    private val installer: MavenInstaller = LocalMavenInstaller(),
    private val userHome: String = System.getProperty("user.home"),
) {
    fun build(outputOverride: Path?): PackageOutcome {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: return PackageOutcome.Failure("No project.json found in current directory or parents")

        val configResult = ProjectConfigParser.load(projectRoot.resolve("project.json"), strictUnknownFields = false)
        if (!configResult.isValid) {
            return PackageOutcome.Failure(configResult.errors.joinToString("; "))
        }
        val config = configResult.config ?: return PackageOutcome.Failure("Unable to parse project.json")

        val sourceFiles = SourceLocator.discoverRunSources(projectRoot, config)
        if (sourceFiles.isEmpty()) {
            return PackageOutcome.Failure("No Kotlin source files found for packaging")
        }

        val dependencies = try {
            dependencyResolver.resolve(config)
        } catch (ex: Exception) {
            return PackageOutcome.Failure("Dependency resolution failed: ${ex.message}")
        }

        val layout = ProjectCacheLayouts.forProject(projectRoot, userHome)
        ProjectCacheLayouts.ensureDirectories(layout)

        val compileClasspath = buildList {
            add(Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()))
            addAll(dependencies.runtimeClasspath)
        }.distinct()

        val compilation = compiler.compile(
            sourceFiles = sourceFiles,
            outputDirectory = layout.classesDir,
            classpath = compileClasspath,
            jvmTarget = config.target,
        )

        if (!compilation.success) {
            return PackageOutcome.Failure(compilation.message ?: "Compilation failed")
        }

        val artifact = config.name ?: projectRoot.fileName.toString()
        val outputJar = outputOverride ?: projectRoot.resolve("dist").resolve("$artifact-${config.version}.jar")
        val resources = ResourceCollector.collect(projectRoot, config.resources)
        val dispatcherEntries = extractDispatcherEntries(layout.generatedDir)
        jarBuilder.build(
            classesDir = layout.classesDir,
            runtimeDependencies = dependencies.runtimeClasspath,
            additionalEntries = resources + dispatcherEntries,
            outputJar = outputJar,
            mainClass = DISPATCHER_MAIN_CLASS,
        )

        val installed = installer.install(outputJar, DEFAULT_GROUP_ID, artifact, config.version, userHome)
        return PackageOutcome.Success(outputJar = outputJar, installedJar = installed)
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
        const val DEFAULT_GROUP_ID = "io.kli.local"
        const val DISPATCHER_MAIN_CLASS = "kli.dispatcher.MainDispatcherKt"
    }
}
