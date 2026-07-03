package kli.resolver

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
