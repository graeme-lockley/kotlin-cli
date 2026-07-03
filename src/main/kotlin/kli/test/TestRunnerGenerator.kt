package kli.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object TestRunnerGenerator {
    fun generate(classesDir: Path, outputFile: Path): Path {
        Files.createDirectories(outputFile.parent)

        val escapedClassesDir = classesDir.toAbsolutePath().normalize().toString().replace("\\", "\\\\")
        outputFile.writeText(
            """
            package __test_runner__

            import org.junit.platform.engine.discovery.DiscoverySelectors
            import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
            import org.junit.platform.launcher.core.LauncherFactory
            import org.junit.platform.launcher.listeners.SummaryGeneratingListener

            fun runTests(): Int {
                val requestBuilder = LauncherDiscoveryRequestBuilder.request()
                requestBuilder.selectors(DiscoverySelectors.selectClasspathRoots(setOf(java.nio.file.Path.of("$escapedClassesDir"))))
                val request = requestBuilder.build()

                val launcher = LauncherFactory.create()
                val listener = SummaryGeneratingListener()
                launcher.registerTestExecutionListeners(listener)
                launcher.execute(request)

                val summary = listener.summary
                println("Tests run: ${'$'}{summary.testsFoundCount}, Passed: ${'$'}{summary.testsSucceededCount}, Failed: ${'$'}{summary.testsFailedCount}")

                summary.failures.forEachIndexed { index, failure ->
                    println("FAIL ${'$'}{index + 1}: ${'$'}{failure.testIdentifier.displayName}")
                    println("  ${'$'}{failure.exception.message ?: "no message"}")
                }

                return if (summary.testsFailedCount > 0L) 1 else 0
            }

            fun main() {
                val code = runTests()
                if (code != 0) {
                    throw IllegalStateException("Test failures detected")
                }
            }
            """.trimIndent(),
        )

        return outputFile
    }
}
