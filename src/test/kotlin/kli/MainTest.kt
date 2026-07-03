package kli

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun app_name_is_stable() {
        assertEquals("kli", Kli().commandName)
    }

    @Test
    fun usage_errors_return_exit_code_2() {
        val code = runCli(arrayOf("unknown-command"))
        assertEquals(2, code)
    }

    @Test
    fun command_failures_return_exit_code_1() {
        val cwd = Files.createTempDirectory("kli-main-test")
        val code = runCli(arrayOf("project-lint")) { cwd }
        assertEquals(1, code)
    }
}
