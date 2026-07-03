package kli.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectConfigParserTest {
    @Test
    fun parses_valid_config_with_defaults() {
        val dir = Files.createTempDirectory("kli-config-valid")
        val file = dir.resolve("project.json")
        Files.writeString(file, """
            {
              "deps": ["com.squareup.okio:okio:3.9.0"]
            }
        """.trimIndent())

        val result = ProjectConfigParser.load(file, strictUnknownFields = true)

        assertTrue(result.isValid)
        assertEquals("0.1.0", result.config?.version)
        assertEquals("21", result.config?.target)
        assertEquals(1, result.config?.deps?.size)
    }

    @Test
    fun rejects_unknown_fields_in_strict_mode() {
        val dir = Files.createTempDirectory("kli-config-unknown")
        val file = dir.resolve("project.json")
        Files.writeString(file, """
            {
              "deps": [],
              "extra": "nope"
            }
        """.trimIndent())

        val result = ProjectConfigParser.load(file, strictUnknownFields = true)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Unknown field: extra") })
    }

    @Test
    fun validates_field_types() {
        val dir = Files.createTempDirectory("kli-config-types")
        val file = dir.resolve("project.json")
        Files.writeString(file, """
            {
              "deps": [123],
              "publish": "bad"
            }
        """.trimIndent())

        val result = ProjectConfigParser.load(file, strictUnknownFields = true)

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("deps[0]") })
        assertTrue(result.errors.any { it.contains("publish") })
    }
}
