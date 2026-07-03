package kli.resolver

import kli.cache.KliPaths
import kli.project.ProjectConfig
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import java.nio.file.Path

class MavenDependencyResolver(
    private val userHome: String = System.getProperty("user.home"),
) {
    fun resolve(config: ProjectConfig): DependencyResolutionResult {
        val repositorySystem = createRepositorySystem()
        val session = MavenRepositorySystemUtils.newSession()
        session.setArtifactDescriptorPolicy(SimpleArtifactDescriptorPolicy(true, true))

        val localRepository = LocalRepository(KliPaths.m2(userHome).toFile())
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepository)

        val repositories = buildRemoteRepositories(config.repos)

        val runtimeClasspath = resolveScope(
            repositorySystem = repositorySystem,
            session = session,
            repositories = repositories,
            coordinates = config.deps,
            scope = "runtime",
        )

        val testClasspath = resolveScope(
            repositorySystem = repositorySystem,
            session = session,
            repositories = repositories,
            coordinates = config.testDeps,
            scope = "test",
        )

        return DependencyResolutionResult(
            runtimeClasspath = runtimeClasspath,
            testClasspath = testClasspath,
        )
    }

    internal fun buildRemoteRepositories(repoUrls: List<String>): List<RemoteRepository> {
        return repoUrls.mapIndexed { index, url ->
            RemoteRepository.Builder("repo-$index", "default", url).build()
        }
    }

    private fun createRepositorySystem(): RepositorySystem {
        val locator = MavenRepositorySystemUtils.newServiceLocator()
        locator.addService(RepositoryConnectorFactory::class.java, BasicRepositoryConnectorFactory::class.java)
        locator.addService(TransporterFactory::class.java, HttpTransporterFactory::class.java)
        return locator.getService(RepositorySystem::class.java)
            ?: error("Failed to initialize Maven repository system")
    }

    private fun resolveScope(
        repositorySystem: RepositorySystem,
        session: org.eclipse.aether.RepositorySystemSession,
        repositories: List<RemoteRepository>,
        coordinates: List<String>,
        scope: String,
    ): List<Path> {
        if (coordinates.isEmpty()) {
            return emptyList()
        }

        val classpath = linkedSetOf<Path>()
        for (coordinate in coordinates) {
            MavenCoordinate.parse(coordinate)
            val collectRequest = CollectRequest().apply {
                root = org.eclipse.aether.graph.Dependency(DefaultArtifact(coordinate), scope)
                this.repositories = repositories
            }
            val result = repositorySystem.resolveDependencies(session, DependencyRequest(collectRequest, null))
            result.artifactResults
                .mapNotNull { it.artifact?.file?.toPath() }
                .forEach { classpath.add(it) }
        }

        return classpath.toList()
    }
}
