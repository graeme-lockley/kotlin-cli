package kli.test

import kli.cache.ProjectCacheLayout
import kli.cache.ProjectCacheLayouts
import kli.project.ProjectConfig
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.resolver.DependencyResolutionResult
import kli.resolver.DependencyResolver
import kli.resolver.MavenDependencyResolver
import kli.run.SourceLocator
import java.nio.file.Files
import java.nio.file.Path

sealed interface TestWorkflowOutcome {
    data class Success(
        val plan: TestPlan,
    ) : TestWorkflowOutcome

    data class Failure(
        val errors: List<String>,
    ) : TestWorkflowOutcome
}

data class TestPlan(
    val projectRoot: Path,
    val config: ProjectConfig,
    val pathFilter: String?,
    val runSources: List<Path>,
    val selectedTests: List<Path>,
    val compileSources: List<Path>,
    val cacheLayout: ProjectCacheLayout,
    val dependencies: DependencyResolutionResult,
)

class TestWorkflow(
    private val cwd: () -> Path,
    private val dependencyResolver: DependencyResolver = MavenDependencyResolver(),
    private val userHome: String = System.getProperty("user.home"),
) {
    fun prepare(pathFilter: String?): TestWorkflowOutcome {
        val projectRoot = ProjectRootFinder.find(cwd())
            ?: return TestWorkflowOutcome.Failure(
                listOf(
                    "No project.json found in current directory or parents",
                    "Hint: Create a new project with: kli init [name]",
                ),
            )

        val projectFile = projectRoot.resolve("project.json")
        val parsed = ProjectConfigParser.load(projectFile, strictUnknownFields = false)
        if (!parsed.isValid) {
            return TestWorkflowOutcome.Failure(parsed.errors)
        }

        val config = parsed.config ?: return TestWorkflowOutcome.Failure(listOf("project.json could not be parsed"))
        val discovered = SourceLocator.discoverTestSources(projectRoot, config)
        val runSources = SourceLocator.discoverRunSources(projectRoot, config)
        val selected = selectTests(projectRoot, discovered, pathFilter)
            ?: return TestWorkflowOutcome.Failure(
                listOf(
                    "No matching test files found for path: $pathFilter",
                    "Check that the path exists and matches a *Test.kt file or directory.",
                ),
            )

        if (selected.isEmpty()) {
            return TestWorkflowOutcome.Failure(
                listOf(
                    "No test files found matching pattern *Test.kt",
                    "Hint: Create test files ending with 'Test.kt' in your project.",
                    "Example: tools/SampleTest.kt",
                ),
            )
        }

        val compileSources = (runSources + selected)
            .distinct()
            .sortedBy { it.toString() }

        val dependencies = try {
            dependencyResolver.resolve(config)
        } catch (ex: Exception) {
            return TestWorkflowOutcome.Failure(
                listOf(
                    "Dependency resolution failed: ${ex.message}",
                    "Hint: Check your 'deps' or 'testDeps' in project.json for correct Maven coordinates.",
                ),
            )
        }

        val cacheLayout = ProjectCacheLayouts.forProject(projectRoot, userHome)
        ProjectCacheLayouts.ensureDirectories(cacheLayout)

        return TestWorkflowOutcome.Success(
            plan = TestPlan(
                projectRoot = projectRoot,
                config = config,
                pathFilter = pathFilter,
                runSources = runSources,
                selectedTests = selected,
                compileSources = compileSources,
                cacheLayout = cacheLayout,
                dependencies = dependencies,
            ),
        )
    }

    private fun selectTests(projectRoot: Path, discoveredTests: List<Path>, pathFilter: String?): List<Path>? {
        if (pathFilter == null) {
            return discoveredTests
        }

        val target = projectRoot.resolve(pathFilter).normalize()
        if (!target.startsWith(projectRoot) || !Files.exists(target)) {
            return null
        }

        if (Files.isDirectory(target)) {
            return discoveredTests.filter { it.startsWith(target) }
        }

        if (Files.isRegularFile(target) && target.fileName.toString().endsWith("Test.kt")) {
            return discoveredTests.filter { it == target }
        }

        return emptyList()
    }
}
