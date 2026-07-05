package kli.commands

import kli.runCli
import java.nio.file.Files
import kli.project.TestDependencyDefaults
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IdeaSetupCommandTest {
    @Test
    fun idea_setup_generates_gradle_files_from_project_config() {
        val root = Files.createTempDirectory("kli-idea-setup")
        root.resolve("project.json").writeText(
            """
            {
              "name": "ai-experiment",
              "target": "21",
              "deps": ["com.google.code.gson:gson:2.13.1"],
              "testDeps": ["org.junit.jupiter:junit-jupiter:5.13.4"],
              "sources": ["tools", "services"],
              "repos": ["https://repo.maven.apache.org/maven2"]
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("idea-setup")) { root }

        assertEquals(0, code)
        val settings = root.resolve("settings.gradle.kts").readText()
        val build = root.resolve("build.gradle.kts").readText()

        assertTrue(settings.contains("rootProject.name = \"ai-experiment\""))
        assertTrue(build.contains("kotlin(\"jvm\") version \"2.0.21\""))
        assertTrue(build.contains("implementation(\"com.google.code.gson:gson:2.13.1\")"))
        assertTrue(build.contains("testImplementation(\"org.junit.jupiter:junit-jupiter:5.13.4\")"))
        assertTrue(build.contains("kotlin.srcDirs(\"tools\", \"services\")"))
    }

    @Test
    fun idea_setup_fails_without_project_json() {
        val root = Files.createTempDirectory("kli-idea-setup-missing")

        val code = runCli(arrayOf("idea-setup")) { root }

        assertEquals(1, code)
    }

    @Test
    fun idea_setup_refuses_to_overwrite_without_force() {
        val root = Files.createTempDirectory("kli-idea-setup-overwrite")
        root.resolve("project.json").writeText("""{"name":"demo"}""")
        root.resolve("settings.gradle.kts").writeText("old-settings")
        root.resolve("build.gradle.kts").writeText("old-build")

        val code = runCli(arrayOf("idea-setup")) { root }

        assertEquals(1, code)
        assertEquals("old-settings", root.resolve("settings.gradle.kts").readText())
        assertEquals("old-build", root.resolve("build.gradle.kts").readText())
    }

    @Test
    fun idea_setup_overwrites_with_force() {
        val root = Files.createTempDirectory("kli-idea-setup-force")
        root.resolve("project.json").writeText("""{"name":"demo"}""")
        root.resolve("settings.gradle.kts").writeText("old-settings")
        root.resolve("build.gradle.kts").writeText("old-build")

        val code = runCli(arrayOf("idea-setup", "--force")) { root }

        assertEquals(0, code)
        assertTrue(root.resolve("settings.gradle.kts").readText().contains("rootProject.name = \"demo\""))
        assertTrue(root.resolve("build.gradle.kts").readText().contains("plugins"))
    }

    @Test
    fun idea_setup_includes_default_test_deps_when_test_deps_missing() {
        val root = Files.createTempDirectory("kli-idea-setup-default-test-deps")
        root.resolve("project.json").writeText(
            """
            {
              "name": "demo",
              "deps": []
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("idea-setup")) { root }

        assertEquals(0, code)
        val build = root.resolve("build.gradle.kts").readText()
        TestDependencyDefaults.defaults.forEach { dep ->
            assertTrue(build.contains("testImplementation(\"$dep\")"))
        }
    }
}
