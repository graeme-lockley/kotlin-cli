package kli.project

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DependencyManagerTest {
    private lateinit var tempDir: Path
    private lateinit var projectJsonPath: Path

    @BeforeEach
    fun setup() {
        tempDir = Files.createTempDirectory("dependency-manager-test")
        projectJsonPath = tempDir.resolve("project.json")
    }

    @Test
    fun list_returns_empty_when_no_dependencies() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.list()
        
        result.onSuccess { deps ->
            assertEquals(0, deps.runtimeDeps.size)
            assertEquals(0, deps.testDeps.size)
        }
    }

    @Test
    fun list_returns_dependencies() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0", "org.jetbrains:annotations:21.0.0"],
              "testDeps": ["org.junit:junit:4.13.2"]
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.list()
        
        result.onSuccess { deps ->
            assertEquals(2, deps.runtimeDeps.size)
            assertEquals(1, deps.testDeps.size)
            assertTrue(deps.runtimeDeps.contains("io.ktor:ktor-core:2.0.0"))
            assertTrue(deps.testDeps.contains("org.junit:junit:4.13.2"))
        }
    }

    @Test
    fun add_runtime_dependency() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.add("io.ktor:ktor-core:2.0.0", "runtime")
        
        assertIs<DependencyOutcome.Success>(result)
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            assertTrue(deps.runtimeDeps.contains("io.ktor:ktor-core:2.0.0"))
        }
    }

    @Test
    fun add_test_dependency() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.add("org.junit:junit:4.13.2", "test")
        
        assertIs<DependencyOutcome.Success>(result)
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            assertTrue(deps.testDeps.contains("org.junit:junit:4.13.2"))
        }
    }

    @Test
    fun add_duplicate_fails() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.add("io.ktor:ktor-core:2.0.0", "runtime")
        
        assertIs<DependencyOutcome.Failure>(result)
        assertTrue(result.message.contains("already exists"))
    }

    @Test
    fun remove_exact_coordinate() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0", "org.jetbrains:annotations:21.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.remove("io.ktor:ktor-core:2.0.0", null, true)
        
        assertIs<DependencyOutcome.Success>(result)
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            assertEquals(1, deps.runtimeDeps.size)
            assertTrue(deps.runtimeDeps.contains("org.jetbrains:annotations:21.0.0"))
        }
    }

    @Test
    fun remove_partial_coordinate() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.remove("io.ktor:ktor-core", null, true)
        
        assertIs<DependencyOutcome.Success>(result)
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            assertEquals(0, deps.runtimeDeps.size)
        }
    }

    @Test
    fun remove_nonexistent_fails() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": [],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.remove("io.ktor:ktor-core", null, true)
        
        assertIs<DependencyOutcome.Failure>(result)
        assertTrue(result.message.contains("not found"))
    }

    @Test
    fun upgrade_all_dependencies() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.upgrade(null, null, null, false)
        
        assertIs<DependencyOutcome.Success>(result)
    }

    @Test
    fun upgrade_specific_dependency() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.upgrade("io.ktor:ktor-core", "3.0.0", null, false)
        
        assertIs<DependencyOutcome.Success>(result)
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            assertTrue(deps.runtimeDeps.contains("io.ktor:ktor-core:3.0.0"))
        }
    }

    @Test
    fun upgrade_dry_run_doesnt_modify() {
        projectJsonPath.writeText("""
            {
              "name": "test",
              "version": "1.0.0",
              "deps": ["io.ktor:ktor-core:2.0.0"],
              "testDeps": []
            }
        """.trimIndent())
        
        val manager = DependencyManager(projectJsonPath)
        val result = manager.upgrade("io.ktor:ktor-core", "3.0.0", null, true)
        
        assertIs<DependencyOutcome.Success>(result)
        assertTrue(result.message.contains("Dry run"))
        
        val manager2 = DependencyManager(projectJsonPath)
        val listResult = manager2.list()
        listResult.onSuccess { deps ->
            // Should still be old version
            assertTrue(deps.runtimeDeps.contains("io.ktor:ktor-core:2.0.0"))
        }
    }
}
