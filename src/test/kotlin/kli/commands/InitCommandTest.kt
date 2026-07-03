package kli.commands

import java.nio.file.Files
import kli.runCli
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InitCommandTest {
    @Test
    fun init_without_name_creates_project_in_current_directory() {
        val projectDir = Files.createTempDirectory("kli-init-current")
        val code = runCli(arrayOf("init")) { projectDir }

        assertEquals(0, code)
        assertTrue(Files.exists(projectDir.resolve("project.json")))
        assertTrue(Files.exists(projectDir.resolve("tools/CLI.kt")))
        assertTrue(Files.exists(projectDir.resolve(".gitignore")))
    }

    @Test
    fun init_with_name_creates_project_in_subdirectory() {
        val parentDir = Files.createTempDirectory("kli-init-parent")
        val code = runCli(arrayOf("init", "my-project")) { parentDir }

        assertEquals(0, code)
        val projectDir = parentDir.resolve("my-project")
        assertTrue(Files.exists(projectDir.resolve("project.json")))
        assertTrue(Files.exists(projectDir.resolve("tools/CLI.kt")))
        assertTrue(Files.exists(projectDir.resolve(".gitignore")))
    }

    @Test
    fun init_creates_valid_project_json_with_correct_name() {
        val projectDir = Files.createTempDirectory("kli-init-json")
        val code = runCli(arrayOf("init")) { projectDir }

        assertEquals(0, code)
        val projectJsonContent = projectDir.resolve("project.json").readText()
        assertTrue(projectJsonContent.contains("\"name\":"), "project.json should contain name field")
        assertTrue(projectJsonContent.contains("\"version\": \"0.1.0\""), "project.json should contain version")
        assertTrue(projectJsonContent.contains("\"deps\": []"), "project.json should contain empty deps")
        assertTrue(projectJsonContent.contains("\"target\": \"21\""), "project.json should contain target 21")
    }

    @Test
    fun init_with_subdirectory_includes_correct_project_name() {
        val parentDir = Files.createTempDirectory("kli-init-subdir-name")
        val code = runCli(arrayOf("init", "test-app")) { parentDir }

        assertEquals(0, code)
        val projectJsonContent = parentDir.resolve("test-app/project.json").readText()
        assertTrue(projectJsonContent.contains("\"name\": \"test-app\""), "project.json should contain correct project name")
    }

    @Test
    fun init_fails_if_project_json_exists() {
        val projectDir = Files.createTempDirectory("kli-init-overwrite")
        projectDir.resolve("project.json").toFile().writeText("old content")

        val code = runCli(arrayOf("init")) { projectDir }

        assertEquals(1, code)
        val content = projectDir.resolve("project.json").readText()
        assertEquals("old content", content, "old content should not be replaced")
    }

    @Test
    fun init_creates_gitignore_only_once() {
        val projectDir = Files.createTempDirectory("kli-init-gitignore")
        projectDir.resolve(".gitignore").toFile().writeText("existing gitignore")

        val code = runCli(arrayOf("init")) { projectDir }

        assertEquals(0, code)
        val gitignoreContent = projectDir.resolve(".gitignore").readText()
        assertEquals("existing gitignore", gitignoreContent, ".gitignore should not be overwritten")
    }

    @Test
    fun init_creates_cli_kt_with_correct_content() {
        val projectDir = Files.createTempDirectory("kli-init-cli-kt")
        val code = runCli(arrayOf("init")) { projectDir }

        assertEquals(0, code)
        val cliKtContent = projectDir.resolve("tools/CLI.kt").readText()
        assertTrue(cliKtContent.contains("package tools"), "CLI.kt should contain package declaration")
        assertTrue(cliKtContent.contains("fun main()"), "CLI.kt should contain main function")
        assertTrue(cliKtContent.contains("println"), "CLI.kt should contain println")
    }
}
