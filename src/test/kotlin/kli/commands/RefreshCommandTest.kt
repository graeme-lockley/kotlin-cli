package kli.commands

import kli.runCli
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class RefreshCommandTest {
    @Test
    fun refresh_succeeds_with_project() {
        val projectDir = Files.createTempDirectory("kli-refresh-clear")
        projectDir.resolve("project.json").writeText("{}")

        val code = runCli(arrayOf("refresh")) { projectDir }

        assertEquals(0, code, "refresh command should succeed with valid project")
    }

    @Test
    fun refresh_with_no_manifest_still_succeeds() {
        val projectDir = Files.createTempDirectory("kli-refresh-no-manifest")
        projectDir.resolve("project.json").writeText("{}")

        val code = runCli(arrayOf("refresh")) { projectDir }

        assertEquals(0, code, "refresh should succeed even if no manifest exists")
    }

    @Test
    fun refresh_with_no_project_json_fails() {
        val projectDir = Files.createTempDirectory("kli-refresh-no-project")
        val code = runCli(arrayOf("refresh")) { projectDir }

        assertEquals(1, code)
    }
}
