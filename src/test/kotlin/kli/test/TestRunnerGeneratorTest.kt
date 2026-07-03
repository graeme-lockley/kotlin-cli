package kli.test

import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class TestRunnerGeneratorTest {
    @Test
    fun generates_colorized_class_and_total_report_sections() {
        val classesDir = Files.createTempDirectory("kli-test-runner-classes")
        val outputFile = Files.createTempDirectory("kli-test-runner-out").resolve("TestRunner.kt")

        TestRunnerGenerator.generate(classesDir, outputFile, selectedTestFilesCount = 2)

        val generated = outputFile.readText()
        assertTrue(generated.contains("class ClassReportListener"))
        assertTrue(generated.contains("MethodSource"))
        assertTrue(generated.contains("ANSI_GREEN"))
        assertTrue(generated.contains("ANSI_RED"))
        assertTrue(generated.contains("ANSI_LIGHT_GRAY"))
        assertTrue(generated.contains("test file(s), "))
        assertTrue(generated.contains("Fail "))
    }
}
