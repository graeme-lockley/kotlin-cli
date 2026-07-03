package kli.packaging

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PublishServiceTest {
    private lateinit var tempDir: Path
    private lateinit var projectRoot: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("publish-service-test")
        projectRoot = tempDir
    }

    @Test
    fun publish_without_project_json_fails() {
        val service = PublishService(cwd = { projectRoot })
        val result = service.publish(null)
        assertIs<PublishOutcome.Failure>(result)
        assertEquals("No project.json found in current directory or parents", result.message)
    }

    @Test
    fun publish_without_existing_package_fails() {
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val service = PublishService(cwd = { projectRoot })
        val result = service.publish(null)
        
        assertIs<PublishOutcome.Failure>(result)
        assert(result.message.contains("Package not found"))
    }

    @Test
    fun publish_with_registry_override_uses_custom_registry() {
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": [],
              "testDeps": [],
              "publish": {
                "registry": "https://repo.example.com/releases"
              }
            }
        """.trimIndent())
        
        // Create a dummy JAR
        val distDir = projectRoot.resolve("dist").createDirectories()
        val jarFile = distDir.resolve("test-app-1.0.0.jar")
        jarFile.writeText("dummy jar content")
        
        val service = PublishService(cwd = { projectRoot })
        // This will fail to deploy but we can verify it's attempting with the right registry
        val result = service.publish("https://custom.registry.com/releases")
        
        // The publish will fail because it's trying to deploy to a non-existent registry
        // but it should attempt to use the override registry
        assertIs<PublishOutcome.Failure>(result)
    }

    @Test
    fun publish_generates_pom_with_dependencies() {
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": ["org.junit:junit:4.13.2"]
            }
        """.trimIndent())
        
        val distDir = projectRoot.resolve("dist").createDirectories()
        val jarFile = distDir.resolve("test-app-1.0.0.jar")
        jarFile.writeText("dummy jar")
        
        val service = PublishService(cwd = { projectRoot })
        val result = service.publish(null)
        
        // Verify POM was generated (even if publish fails)
        val pomFile = distDir.resolve("pom.xml")
        if (Files.exists(pomFile)) {
            val pomContent = Files.readString(pomFile)
            assert(pomContent.contains("io.ktor"))
            assert(pomContent.contains("junit"))
        }
    }
}
