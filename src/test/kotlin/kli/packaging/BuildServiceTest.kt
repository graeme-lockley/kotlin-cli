package kli.packaging

import kli.cache.ProjectCacheLayouts
import kli.project.ProjectConfig
import kli.project.ProjectConfigParser
import kli.project.ProjectRootFinder
import kli.run.SourceLocator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class BuildServiceTest {
    private lateinit var tempDir: Path
    private lateinit var projectRoot: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("build-service-test")
        projectRoot = tempDir
    }

    @Test
    fun build_without_project_json_fails() {
        val service = BuildService(cwd = { projectRoot })
        val result = service.build(null)
        assertIs<BuildOutcome.Failure>(result)
        assertEquals("No project.json found in current directory or parents", result.message)
    }

    @Test
    fun build_with_valid_project_creates_jar() {
        // Create project structure
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val toolsDir = projectRoot.resolve("tools").createDirectories()
        toolsDir.resolve("Main.kt").writeText("""
            package tools
            
            fun main() {
                println("Hello from build test")
            }
        """.trimIndent())
        
        val service = BuildService(cwd = { projectRoot })
        val result = service.build(null)
        
        assertIs<BuildOutcome.Success>(result)
        val outputJar = projectRoot.resolve("dist").resolve("test-app-1.0.0.jar")
        assertTrue(Files.exists(outputJar), "Output JAR should exist at $outputJar")
    }

    @Test
    fun build_with_output_override_uses_custom_path() {
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val toolsDir = projectRoot.resolve("tools").createDirectories()
        toolsDir.resolve("Main.kt").writeText("""
            package tools
            
            fun main() {
                println("Hello")
            }
        """.trimIndent())
        
        val customOutput = projectRoot.resolve("custom.jar")
        val service = BuildService(cwd = { projectRoot })
        val result = service.build(customOutput)
        
        assertIs<BuildOutcome.Success>(result)
        assertEquals(customOutput, result.outputJar)
        assertTrue(Files.exists(customOutput), "Output JAR should exist at custom location")
    }

    @Test
    fun build_with_no_sources_fails() {
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "test-app",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val service = BuildService(cwd = { projectRoot })
        val result = service.build(null)
        
        assertIs<BuildOutcome.Failure>(result)
        assertEquals("No Kotlin source files found for building", result.message)
    }

    @Test
    fun build_includes_kotlin_stdlib_for_dispatcher() {
        // Create project with no dependencies to verify Kotlin stdlib is included
        val projectJson = projectRoot.resolve("project.json")
        projectJson.writeText("""
            {
              "name": "stdlib-test",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val mainDir = projectRoot.resolve("src").createDirectories()
        mainDir.resolve("App.kt").writeText("""
            package src
            
            fun main() {
                println("App running")
            }
        """.trimIndent())
        
        val service = BuildService(cwd = { projectRoot })
        val result = service.build(null)
        
        assertIs<BuildOutcome.Success>(result)
        val outputJar = result.outputJar
        
        // Verify Kotlin stdlib classes are in the JAR
        JarFile(outputJar.toFile()).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toSet()
            
            // Check for Kotlin stdlib classes
            assertTrue(
                entries.any { it.startsWith("kotlin/") && it.endsWith(".class") },
                "Built JAR should contain Kotlin stdlib classes for dispatcher to run"
            )
            
            // Check for dispatcher class
            assertTrue(
                entries.contains("kli/dispatcher/MainDispatcherKt.class"),
                "Built JAR should contain dispatcher class"
            )
        }
    }
}
