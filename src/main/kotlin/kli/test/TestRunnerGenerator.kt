package kli.test

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

object TestRunnerGenerator {
    fun generate(classesDir: Path, outputFile: Path, selectedTestFilesCount: Int): Path {
        Files.createDirectories(outputFile.parent)

        val escapedClassesDir = classesDir.toAbsolutePath().normalize().toString().replace("\\", "\\\\")
        outputFile.writeText(
            """
            package __test_runner__

            import java.io.OutputStream
            import java.io.PrintStream
            import org.junit.platform.engine.TestExecutionResult
            import org.junit.platform.engine.support.descriptor.ClassSource
            import org.junit.platform.engine.support.descriptor.MethodSource
            import org.junit.platform.launcher.TestExecutionListener
            import org.junit.platform.launcher.TestIdentifier
            import org.junit.platform.engine.discovery.DiscoverySelectors
            import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
            import org.junit.platform.launcher.core.LauncherFactory
            import org.junit.platform.launcher.listeners.SummaryGeneratingListener

            private val ANSI_RESET = "${'$'}{27.toChar()}[0m"
            private val ANSI_GREEN = "${'$'}{27.toChar()}[32m"
            private val ANSI_RED = "${'$'}{27.toChar()}[31m"
            private val ANSI_LIGHT_GRAY = "${'$'}{27.toChar()}[90m"

            private data class ClassSummary(
                var passed: Int = 0,
                var failed: Int = 0,
                var durationNanos: Long = 0,
            )

            private class PrefixedOutputStream(
                private val delegate: PrintStream,
                private val currentSourceLabel: () -> String?,
            ) : OutputStream() {
                private val buffer = StringBuilder()

                override fun write(b: Int) {
                    val ch = b.toChar()
                    if (ch == '\n') {
                        flushBufferedLine()
                    } else if (ch != '\r') {
                        buffer.append(ch)
                    }
                }

                override fun flush() {
                    if (buffer.isNotEmpty()) {
                        flushBufferedLine()
                    }
                    delegate.flush()
                }

                private fun flushBufferedLine() {
                    val text = buffer.toString()
                    buffer.setLength(0)
                    val label = currentSourceLabel()
                    if (label.isNullOrBlank()) {
                        delegate.println(text)
                    } else {
                        delegate.println(color(label, ANSI_LIGHT_GRAY) + color(" | ", ANSI_LIGHT_GRAY) + text)
                    }
                }
            }

            private class ClassReportListener : TestExecutionListener {
                private val startedAt = mutableMapOf<String, Long>()
                private val classSummaries = linkedMapOf<String, ClassSummary>()
                private val activePrefixesById = linkedMapOf<String, String>()
                private var activeSourcePrefix: String? = null

                fun activeSourceFileLabel(): String? {
                    return activeSourcePrefix
                }

                override fun executionStarted(testIdentifier: TestIdentifier) {
                    val prefix = sourcePrefixFor(testIdentifier)
                    if (prefix != null) {
                        activePrefixesById[testIdentifier.uniqueId] = prefix
                        activeSourcePrefix = prefix
                    }

                    if (testIdentifier.isTest) {
                        startedAt[testIdentifier.uniqueId] = System.nanoTime()
                    }
                }

                override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
                    if (!testIdentifier.isTest) {
                        return
                    }

                    val start = startedAt.remove(testIdentifier.uniqueId) ?: System.nanoTime()
                    val duration = System.nanoTime() - start
                    val className = classNameFor(testIdentifier)
                    val summary = classSummaries.getOrPut(className) { ClassSummary() }
                    summary.durationNanos += duration

                    if (testExecutionResult.status == TestExecutionResult.Status.SUCCESSFUL) {
                        summary.passed += 1
                    } else {
                        summary.failed += 1
                    }

                    activePrefixesById.remove(testIdentifier.uniqueId)
                    activeSourcePrefix = activePrefixesById.values.lastOrNull()
                }

                fun printReport() {
                    classSummaries.forEach { (className, summary) ->
                        val durationMs = nanosToMs(summary.durationNanos)
                        val countParts = mutableListOf<String>()
                        if (summary.passed > 0) {
                            countParts += color("✓${'$'}{summary.passed}", ANSI_GREEN)
                        }
                        if (summary.failed > 0) {
                            countParts += color("✗${'$'}{summary.failed}", ANSI_RED)
                        }
                        if (countParts.isEmpty()) {
                            countParts += "0"
                        }
                        val countText = countParts.joinToString(" ")

                        println(className + " (${ '$' }countText ${ '$' }{durationMs}ms)")
                    }
                }

                private fun classNameFor(testIdentifier: TestIdentifier): String {
                    val source = testIdentifier.source.orElse(null)
                    if (source is ClassSource) {
                        return source.className
                    }
                    if (source is MethodSource) {
                        return source.className
                    }

                    val displayName = testIdentifier.displayName
                    return displayName.substringBefore('(')
                }

                private fun classNameToFilePath(className: String): String {
                    return className.replace('.', '/') + ".kt"
                }

                private fun methodNameFor(testIdentifier: TestIdentifier): String {
                    val source = testIdentifier.source.orElse(null)
                    if (source is MethodSource) {
                        return source.methodName
                    }
                    val display = testIdentifier.displayName
                    return display.removeSuffix("()")
                }

                private fun sourcePrefixFor(testIdentifier: TestIdentifier): String? {
                    val source = testIdentifier.source.orElse(null)
                    return when (source) {
                        is MethodSource -> {
                            val className = source.className
                            val filePath = classNameToFilePath(className)
                            val methodName = source.methodName + "()"
                            "${'$'}filePath ${'$'}className ${'$'}methodName"
                        }

                        is ClassSource -> {
                            val className = source.className
                            val filePath = classNameToFilePath(className)
                            val phase = if (testIdentifier.isTest) testIdentifier.displayName else "<lifecycle>"
                            "${'$'}filePath ${'$'}className ${'$'}phase"
                        }

                        else -> {
                            if (!testIdentifier.isTest) {
                                null
                            } else {
                                val className = classNameFor(testIdentifier)
                                val filePath = classNameToFilePath(className)
                                val methodName = methodNameFor(testIdentifier)
                                "${'$'}filePath ${'$'}className ${'$'}methodName"
                            }
                        }
                    }
                }
            }

            private fun color(text: String, ansi: String): String = "${'$'}ansi${'$'}text${'$'}ANSI_RESET"

            private fun nanosToMs(nanos: Long): Long = nanos / 1_000_000

            private fun classNameForFailure(testIdentifier: TestIdentifier): String {
                val source = testIdentifier.source.orElse(null)
                return when (source) {
                    is MethodSource -> source.className
                    is ClassSource -> source.className
                    else -> testIdentifier.displayName.substringBefore('(')
                }
            }

            private fun methodNameForFailure(testIdentifier: TestIdentifier): String {
                val source = testIdentifier.source.orElse(null)
                if (source is MethodSource) {
                    return source.methodName
                }
                return testIdentifier.displayName.removeSuffix("()")
            }

            private fun firstUsefulFrame(className: String, throwable: Throwable): StackTraceElement? {
                return throwable.stackTrace.firstOrNull { it.className == className }
                    ?: throwable.stackTrace.firstOrNull { frame ->
                        !frame.className.startsWith("org.junit.") &&
                            !frame.className.startsWith("java.") &&
                            !frame.className.startsWith("kotlin.")
                    }
            }

            private fun displayFilePath(frame: StackTraceElement): String {
                val fileName = frame.fileName ?: "UnknownFile"
                val packageName = frame.className.substringBeforeLast('.', "")
                if (packageName.isBlank()) {
                    return fileName
                }
                val packagePath = packageName.replace('.', '/')
                return "${'$'}packagePath/${'$'}fileName"
            }

            private fun formatFailureMessage(message: String): String {
                val assertionPattern = Regex("^expected: <(.*?)> but was: <(.*?)>${'$'}")
                val match = assertionPattern.matchEntire(message)
                if (match != null) {
                    val expected = match.groupValues[1]
                    val actual = match.groupValues[2]
                    return color("expected: <", ANSI_LIGHT_GRAY) +
                        expected +
                        color("> but was: <", ANSI_LIGHT_GRAY) +
                        actual +
                        color(">", ANSI_LIGHT_GRAY)
                }
                return color(message, ANSI_LIGHT_GRAY)
            }

            fun runTests(): Int {
                val requestBuilder = LauncherDiscoveryRequestBuilder.request()
                requestBuilder.selectors(DiscoverySelectors.selectClasspathRoots(setOf(java.nio.file.Path.of("$escapedClassesDir"))))
                val request = requestBuilder.build()

                val launcher = LauncherFactory.create()
                val listener = SummaryGeneratingListener()
                val classReportListener = ClassReportListener()
                launcher.registerTestExecutionListeners(listener, classReportListener)

                val originalOut = System.out
                val prefixedOut = PrintStream(
                    PrefixedOutputStream(originalOut) { classReportListener.activeSourceFileLabel() },
                    true,
                    Charsets.UTF_8,
                )

                val suiteStartedAt = System.nanoTime()
                try {
                    System.setOut(prefixedOut)
                    launcher.execute(request)
                } finally {
                    prefixedOut.flush()
                    System.setOut(originalOut)
                }
                val suiteDurationMs = nanosToMs(System.nanoTime() - suiteStartedAt)

                val summary = listener.summary
                classReportListener.printReport()

                val resultParts = mutableListOf<String>()
                if (summary.testsSucceededCount > 0L) {
                    resultParts += color("${'$'}{summary.testsSucceededCount} passed", ANSI_GREEN)
                }
                if (summary.testsFailedCount > 0L) {
                    resultParts += color("${'$'}{summary.testsFailedCount} failed", ANSI_RED)
                }
                if (resultParts.isEmpty()) {
                    resultParts += "0 total"
                }

                val summaryLine = "$selectedTestFilesCount test file(s), " +
                    resultParts.joinToString(", ") +
                    " (${ '$' }{suiteDurationMs}ms)"
                println(summaryLine)

                summary.failures.forEachIndexed { index, failure ->
                    val className = classNameForFailure(failure.testIdentifier)
                    val methodName = methodNameForFailure(failure.testIdentifier)
                    val failPrefix = color("Fail ${'$'}{index + 1}", ANSI_RED)
                    println("${'$'}failPrefix: ${'$'}className.${'$'}methodName")
                    val frame = firstUsefulFrame(className, failure.exception)
                    if (frame != null) {
                        val fileName = displayFilePath(frame)
                        val line = if (frame.lineNumber > 0) ":${'$'}{frame.lineNumber}" else ""
                        println(color("  at ${'$'}fileName${'$'}line", ANSI_LIGHT_GRAY))
                    }
                    println("  " + formatFailureMessage(failure.exception.message ?: "no message"))
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
