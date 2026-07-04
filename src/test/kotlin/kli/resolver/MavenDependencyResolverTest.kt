package kli.resolver

import kli.project.ProjectConfig
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MavenDependencyResolverTest {
    @Test
    fun builds_remote_repositories_from_configured_urls() {
        val resolver = MavenDependencyResolver(userHome = "/tmp")

        val repositories = resolver.buildRemoteRepositories(
            listOf(
                "https://repo.maven.apache.org/maven2",
                "https://repo1.maven.org/maven2",
            ),
        )

        assertEquals(2, repositories.size)
        assertEquals("repo-0", repositories[0].id)
        assertEquals("https://repo.maven.apache.org/maven2", repositories[0].url.toString())
        assertEquals("repo-1", repositories[1].id)
    }

    @Test
    fun resolves_transitives_even_when_root_artifact_is_cached() {
        val userHome = Files.createTempDirectory("kli-resolver-home")
        val coordinate = "com.squareup.okhttp3:okhttp:4.12.0"
        val config = ProjectConfig(
            deps = listOf(coordinate),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val resolver = MavenDependencyResolver(userHome = userHome.toString())

            val firstResolution = resolver.resolve(config)
            assertTrue(firstResolution.runtimeClasspath.any { it.fileName.toString().contains("okio") })

            val okioCacheDir = userHome
                .resolve(".m2")
                .resolve("repository")
                .resolve("com")
                .resolve("squareup")
                .resolve("okio")
            deleteDirectoryRecursively(okioCacheDir)

            val secondResolution = resolver.resolve(config)
            assertTrue(
                secondResolution.runtimeClasspath.any { it.fileName.toString().contains("okio") },
                "Expected transitive okio dependency to be resolved even when okhttp is already cached",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    @Test
    fun resolve_trees_preserves_requested_root_coordinate() {
        val userHome = Files.createTempDirectory("kli-resolver-tree-home")
        val coordinate = "com.squareup.okhttp3:okhttp:4.12.0"
        val config = ProjectConfig(
            deps = listOf(coordinate),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val resolver = MavenDependencyResolver(userHome = userHome.toString())
            val treeResult = resolver.resolveTrees(config)

            assertEquals(1, treeResult.runtime.size)
            assertEquals(coordinate, treeResult.runtime.first().coordinate)
            assertTrue(
                treeResult.runtime.first().children.isNotEmpty(),
                "Expected okhttp tree to include transitives",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    @Test
    fun resolves_jackson_core_for_openai4j_runtime() {
        val userHome = Files.createTempDirectory("kli-resolver-openai4j-home")
        val config = ProjectConfig(
            deps = listOf("dev.ai4j:openai4j:0.22.0"),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val resolver = MavenDependencyResolver(userHome = userHome.toString())
            val result = resolver.resolve(config)
            val resolvedNames = result.runtimeClasspath.map { it.fileName.toString() }.sorted()

            assertTrue(
                resolvedNames.any { it.startsWith("jackson-core-") },
                "Expected jackson-core JAR to be present in openai4j runtime classpath. Resolved: ${resolvedNames.joinToString(", ")}",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    @Test
    fun resolves_jtokkit_for_langchain4j_open_ai_runtime() {
        val userHome = Files.createTempDirectory("kli-resolver-langchain4j-home")
        val config = ProjectConfig(
            deps = listOf("dev.langchain4j:langchain4j-open-ai:0.35.0"),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val resolver = MavenDependencyResolver(userHome = userHome.toString())
            val result = resolver.resolve(config)
            val resolvedNames = result.runtimeClasspath.map { it.fileName.toString() }.sorted()

            assertTrue(
                resolvedNames.any { it.startsWith("jtokkit-") },
                "Expected jtokkit JAR to be present in langchain4j-open-ai runtime classpath. Resolved: ${resolvedNames.joinToString(", ")}",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    @Test
    fun does_not_report_download_for_cached_root_dependency() {
        val userHome = Files.createTempDirectory("kli-resolver-cached-home")
        val coordinate = "org.jetbrains:annotations:24.1.0"
        val config = ProjectConfig(
            deps = listOf(coordinate),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val firstEvents = mutableListOf<String>()
            val firstResolver = MavenDependencyResolver(
                userHome = userHome.toString(),
                onDependencyDownloaded = { c, _ -> firstEvents += c },
            )
            firstResolver.resolve(config)

            val secondEvents = mutableListOf<String>()
            val secondResolver = MavenDependencyResolver(
                userHome = userHome.toString(),
                onDependencyDownloaded = { c, _ -> secondEvents += c },
            )
            secondResolver.resolve(config)

            assertTrue(firstEvents.contains(coordinate))
            assertTrue(
                secondEvents.none { it == coordinate },
                "Expected no download event for cached root dependency",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    @Test
    fun resolve_trees_includes_jtokkit_for_langchain4j_open_ai() {
        val userHome = Files.createTempDirectory("kli-resolver-langchain4j-tree-home")
        val config = ProjectConfig(
            deps = listOf("dev.langchain4j:langchain4j-open-ai:0.35.0"),
            repos = listOf("https://repo.maven.apache.org/maven2"),
        )

        try {
            val resolver = MavenDependencyResolver(userHome = userHome.toString())
            val tree = resolver.resolveTrees(config)

            fun flatten(nodes: List<DependencyTreeNode>): List<String> {
                return nodes.flatMap { listOf(it.coordinate) + flatten(it.children) }
            }

            val allCoordinates = flatten(tree.runtime)
            assertTrue(
                allCoordinates.any { it.startsWith("com.knuddels:jtokkit:") },
                "Expected jtokkit to appear in langchain4j-open-ai dependency tree. Tree: ${allCoordinates.joinToString(", ")}",
            )
        } finally {
            deleteDirectoryRecursively(userHome)
        }
    }

    private fun deleteDirectoryRecursively(path: Path) {
        if (!Files.exists(path)) {
            return
        }

        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach { it.deleteIfExists() }
    }
}
