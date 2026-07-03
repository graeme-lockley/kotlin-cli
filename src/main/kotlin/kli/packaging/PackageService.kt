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

        val dispatcherSource = writeDispatcherSource(layout.generatedDir)
        val compilationSources = sourceFiles + dispatcherSource

        val compileClasspath = buildList {
            add(Path.of(kotlin.Unit::class.java.protectionDomain.codeSource.location.toURI()))
            addAll(dependencies.runtimeClasspath)
        }.distinct()

        val compilation = compiler.compile(
            sourceFiles = compilationSources,
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
        jarBuilder.build(
            classesDir = layout.classesDir,
            runtimeDependencies = dependencies.runtimeClasspath,
            additionalEntries = resources,
            outputJar = outputJar,
            mainClass = DISPATCHER_MAIN_CLASS,
        )

        val installed = installer.install(outputJar, artifact, config.version, userHome)
        return PackageOutcome.Success(outputJar = outputJar, installedJar = installed)
    }

    private fun writeDispatcherSource(generatedDir: Path): Path {
        val packageDir = generatedDir.resolve("kli/dispatcher")
        Files.createDirectories(packageDir)
        val sourceFile = packageDir.resolve("MainDispatcher.kt")
        Files.writeString(
            sourceFile,
            """
            package kli.dispatcher

            fun main(args: Array<String>) {
                require(args.isNotEmpty()) { "Expected <qualified-name> as first argument" }
                val qualifiedName = args[0]
                val mainArgs = args.copyOfRange(1, args.size)
                val klass = Class.forName("$" + "{qualifiedName}Kt")
                val method = klass.getMethod("main", Array<String>::class.java)
                method.invoke(null, mainArgs as Any)
            }
            """.trimIndent(),
        )
        return sourceFile
    }

    private companion object {
        const val DISPATCHER_MAIN_CLASS = "kli.dispatcher.MainDispatcherKt"
    }
}
