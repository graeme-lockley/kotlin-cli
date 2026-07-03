package kli.run

import kli.cache.ProjectCacheLayout
import kli.cache.ProjectCacheLayouts
import kli.project.ProjectConfig
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.resolver.DependencyResolutionResult
import kli.resolver.DependencyResolver
import kli.resolver.MavenDependencyResolver
import java.nio.file.Path

sealed interface RunWorkflowOutcome {
    data class Success(
        val plan: RunPlan,
    ) : RunWorkflowOutcome

    data class Failure(
        val errors: List<String>,
    ) : RunWorkflowOutcome
}

data class RunPlan(
    val projectRoot: Path,
    val config: ProjectConfig,
    val mainClass: String,
    val programArgs: List<String>,
    val cacheLayout: ProjectCacheLayout,
    val dependencies: DependencyResolutionResult,
)

class RunWorkflow(
    private val cwd: () -> Path,
    private val dependencyResolver: DependencyResolver = MavenDependencyResolver(),
    private val userHome: String = System.getProperty("user.home"),
) {
    fun prepare(mainClass: String, programArgs: List<String>): RunWorkflowOutcome {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: return RunWorkflowOutcome.Failure(listOf("No project.json found in current directory or parents"))

        val projectFile = projectRoot.resolve("project.json")
        val parsed = ProjectConfigParser.load(projectFile, strictUnknownFields = false)
        if (!parsed.isValid) {
            return RunWorkflowOutcome.Failure(parsed.errors)
        }

        val config = parsed.config ?: return RunWorkflowOutcome.Failure(listOf("project.json could not be parsed"))
        val dependencies = try {
            dependencyResolver.resolve(config)
        } catch (ex: Exception) {
            return RunWorkflowOutcome.Failure(listOf("Dependency resolution failed: ${ex.message}"))
        }

        val cacheLayout = ProjectCacheLayouts.forProject(projectRoot, userHome)
        ProjectCacheLayouts.ensureDirectories(cacheLayout)

        return RunWorkflowOutcome.Success(
            plan = RunPlan(
                projectRoot = projectRoot,
                config = config,
                mainClass = mainClass,
                programArgs = programArgs,
                cacheLayout = cacheLayout,
                dependencies = dependencies,
            ),
        )
    }
}
