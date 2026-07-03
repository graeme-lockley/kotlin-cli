package kli

import java.nio.file.Files
import kotlin.io.path.writeText
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
    fun no_args_print_help_and_return_exit_code_0() {
        val code = runCli(emptyArray())
        assertEquals(0, code)
    }

    @Test
    fun help_flag_returns_exit_code_0() {
        val code = runCli(arrayOf("--help"))
        assertEquals(0, code)
    }

    @Test
    fun command_failures_return_exit_code_1() {
        val cwd = Files.createTempDirectory("kli-main-test")
        val code = runCli(arrayOf("project-lint")) { cwd }
        assertEquals(1, code)
    }

    @Test
    fun test_command_returns_exit_code_1_without_project() {
        val cwd = Files.createTempDirectory("kli-main-test-test")
        val code = runCli(arrayOf("test")) { cwd }
        assertEquals(1, code)
    }

    @Test
    fun test_command_returns_exit_code_0_when_test_files_discovered() {
        val root = Files.createTempDirectory("kli-main-test-project")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/SampleTest.kt").writeText(
            """
            import kotlin.test.Test

            class SampleTest {
                @Test
                fun passes() {
                    // pass
                }
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("test")) { root }
        assertEquals(0, code)
    }

    @Test
    fun test_command_returns_exit_code_1_for_non_matching_path_filter() {
        val root = Files.createTempDirectory("kli-main-test-project-filter")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/SampleTest.kt").writeText("class SampleTest")

        val code = runCli(arrayOf("test", "src")) { root }
        assertEquals(1, code)
    }

    @Test
    fun test_command_returns_exit_code_1_when_a_test_fails() {
        val root = Files.createTempDirectory("kli-main-test-project-failing")
        root.resolve("project.json").writeText("{}")
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/FailingTest.kt").writeText(
            """
            import kotlin.test.Test

            class FailingTest {
                @Test
                fun fails() {
                    throw IllegalStateException("boom")
                }
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("test")) { root }
        assertEquals(1, code)
    }

    @Test
    fun test_command_resolves_test_deps_without_adding_them_to_runtime_config() {
        val root = Files.createTempDirectory("kli-main-test-project-testdeps")
        root.resolve("project.json").writeText(
            """
            {
              "testDeps": ["com.google.code.gson:gson:2.13.1"]
            }
            """.trimIndent(),
        )
        root.resolve("tools").toFile().mkdirs()
        root.resolve("tools/App.kt").writeText(
            """
            class App {
                fun name(): String = "demo"
            }
            """.trimIndent(),
        )
        root.resolve("tools/GsonBackedTest.kt").writeText(
            """
            import com.google.gson.JsonParser
            import kotlin.test.Test

            class GsonBackedTest {
                @Test
                fun parses_json_using_test_only_dependency() {
                    val parsed = JsonParser.parseString("{\"ok\":true}").asJsonObject
                    check(parsed.get("ok").asBoolean)
                }
            }
            """.trimIndent(),
        )

        val code = runCli(arrayOf("test")) { root }
        assertEquals(0, code)
    }

    @Test
    fun version_flag_returns_exit_code_0() {
        val code = runCli(arrayOf("--version"))
        assertEquals(0, code)
    }
}
