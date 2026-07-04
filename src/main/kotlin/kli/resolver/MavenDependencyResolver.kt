package kli.resolver

import kli.cache.KliPaths
import kli.project.ProjectConfig
import org.eclipse.aether.RepositorySystem
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.collection.CollectRequest
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.LocalRepository
import org.eclipse.aether.repository.RemoteRepository
import org.eclipse.aether.resolution.DependencyRequest
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory
import org.eclipse.aether.spi.connector.transport.TransporterFactory
import org.eclipse.aether.transport.http.HttpTransporterFactory
import org.eclipse.aether.util.repository.SimpleArtifactDescriptorPolicy
import org.apache.maven.repository.internal.MavenRepositorySystemUtils
import java.nio.file.Path

interface DependencyResolver {
    fun resolve(config: ProjectConfig): DependencyResolutionResult
}

data class DependencyTreeNode(
    val coordinate: String,
    val children: List<DependencyTreeNode>,
)

data class DependencyTreeResult(
    val runtime: List<DependencyTreeNode>,
    val test: List<DependencyTreeNode>,
)

class MavenDependencyResolver(
    private val userHome: String = System.getProperty("user.home"),
    private val onDependencyDownloaded: (String, Long) -> Unit = { _, _ -> },
) : DependencyResolver {
    override fun resolve(config: ProjectConfig): DependencyResolutionResult {
        val repositorySystem = createRepositorySystem()
        val session = createSession(repositorySystem)

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

    fun resolveTrees(config: ProjectConfig): DependencyTreeResult {
        val repositorySystem = createRepositorySystem()
        val session = createSession(repositorySystem)

        val repositories = buildRemoteRepositories(config.repos)

        return DependencyTreeResult(
            runtime = resolveScopeTree(
                repositorySystem = repositorySystem,
                session = session,
                repositories = repositories,
                coordinates = config.deps,
                scope = "runtime",
            ),
            test = resolveScopeTree(
                repositorySystem = repositorySystem,
                session = session,
                repositories = repositories,
                coordinates = config.testDeps,
                scope = "test",
            ),
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

    private fun createSession(repositorySystem: RepositorySystem): org.eclipse.aether.DefaultRepositorySystemSession {
        val session = MavenRepositorySystemUtils.newSession()
        session.setArtifactDescriptorPolicy(SimpleArtifactDescriptorPolicy(true, true))
        session.setSystemProperties(System.getProperties())

        val localRepository = LocalRepository(KliPaths.m2(userHome).toFile())
        session.localRepositoryManager = repositorySystem.newLocalRepositoryManager(session, localRepository)
        return session
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
            val wasCached = isCoordinateCached(session, coordinate)

            // Always resolve full graph so transitive dependencies are included.
            val collectRequest = CollectRequest().apply {
                root = org.eclipse.aether.graph.Dependency(DefaultArtifact(coordinate), scope)
                this.repositories = repositories
            }
            val startNanos = System.nanoTime()
            val result = repositorySystem.resolveDependencies(session, DependencyRequest(collectRequest, null))
            val durationMs = (System.nanoTime() - startNanos) / 1_000_000
            if (!wasCached) {
                onDependencyDownloaded(coordinate, durationMs)
            }
            result.artifactResults
                .mapNotNull { it.artifact?.file?.toPath() }
                .forEach { classpath.add(it) }
        }

        enrichJacksonCompanionArtifacts(
            classpath = classpath,
            repositorySystem = repositorySystem,
            session = session,
            repositories = repositories,
            scope = scope,
        )

        return classpath.toList()
    }

    private fun enrichJacksonCompanionArtifacts(
        classpath: LinkedHashSet<Path>,
        repositorySystem: RepositorySystem,
        session: org.eclipse.aether.RepositorySystemSession,
        repositories: List<RemoteRepository>,
        scope: String,
    ) {
        val databindVersions = classpath
            .mapNotNull { parseJacksonDatabindVersion(it.fileName.toString()) }
            .toSet()

        for (version in databindVersions) {
            val missingCoordinates = buildList {
                if (!classpath.any { it.fileName.toString().startsWith("jackson-core-$version") }) {
                    add("com.fasterxml.jackson.core:jackson-core:$version")
                }
                if (!classpath.any { it.fileName.toString().startsWith("jackson-annotations-$version") }) {
                    add("com.fasterxml.jackson.core:jackson-annotations:$version")
                }
            }

            for (coordinate in missingCoordinates) {
                val wasCached = isCoordinateCached(session, coordinate)
                val collectRequest = CollectRequest().apply {
                    root = org.eclipse.aether.graph.Dependency(DefaultArtifact(coordinate), scope)
                    this.repositories = repositories
                }

                val startNanos = System.nanoTime()
                val result = repositorySystem.resolveDependencies(session, DependencyRequest(collectRequest, null))
                val durationMs = (System.nanoTime() - startNanos) / 1_000_000
                if (!wasCached) {
                    onDependencyDownloaded(coordinate, durationMs)
                }
                result.artifactResults
                    .mapNotNull { it.artifact?.file?.toPath() }
                    .forEach { classpath.add(it) }
            }
        }
    }

    private fun parseJacksonDatabindVersion(fileName: String): String? {
        val prefix = "jackson-databind-"
        if (!fileName.startsWith(prefix) || !fileName.endsWith(".jar")) {
            return null
        }

        return fileName.removePrefix(prefix).removeSuffix(".jar")
    }

    private fun isCoordinateCached(session: org.eclipse.aether.RepositorySystemSession, coordinate: String): Boolean {
        return try {
            val artifact = DefaultArtifact(coordinate)
            val localManager = session.localRepositoryManager
            val localPath = localManager.getRepository().basedir.toPath()
                .resolve(localManager.getPathForLocalArtifact(artifact))
            java.nio.file.Files.exists(localPath)
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveScopeTree(
        repositorySystem: RepositorySystem,
        session: org.eclipse.aether.RepositorySystemSession,
        repositories: List<RemoteRepository>,
        coordinates: List<String>,
        scope: String,
    ): List<DependencyTreeNode> {
        if (coordinates.isEmpty()) {
            return emptyList()
        }

        return coordinates.map { coordinate ->
            MavenCoordinate.parse(coordinate)
            val collectRequest = CollectRequest().apply {
                root = org.eclipse.aether.graph.Dependency(DefaultArtifact(coordinate), scope)
                this.repositories = repositories
            }

            val collectResult = repositorySystem.collectDependencies(session, collectRequest)
            val rootNode = extractRootNode(collectResult.root, coordinate)

            toTree(rootNode)
        }
    }

    private fun extractRootNode(root: DependencyNode, requestedCoordinate: String): DependencyNode {
        if (root.dependency?.artifact != null) {
            return root
        }

        return root.children.firstOrNull()
            ?: error("No root dependency node found for $requestedCoordinate")
    }

    private fun toTree(node: DependencyNode): DependencyTreeNode {
        val artifact = node.dependency?.artifact
            ?: error("Dependency node missing artifact")

        val coordinate = "${artifact.groupId}:${artifact.artifactId}:${artifact.version}"
        return DependencyTreeNode(
            coordinate = coordinate,
            children = node.children.map(::toTree),
        )
    }
}
