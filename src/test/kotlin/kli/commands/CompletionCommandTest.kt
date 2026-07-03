package kli.commands

import kli.runCli
import kotlin.test.Test
import kotlin.test.assertEquals

class CompletionCommandTest {
    
    @Test
    fun completion_help_returns_success() {
        val code = runCli(arrayOf("completion", "--help"))
        assertEquals(0, code)
    }
    
    @Test
    fun completion_bash_returns_success() {
        val code = runCli(arrayOf("completion", "bash"))
        assertEquals(0, code)
    }
    
    @Test
    fun completion_zsh_returns_success() {
        val code = runCli(arrayOf("completion", "zsh"))
        assertEquals(0, code)
    }
    
    @Test
    fun completion_invalid_shell_returns_failure() {
        val code = runCli(arrayOf("completion", "invalid"))
        assertEquals(1, code)
    }
}
