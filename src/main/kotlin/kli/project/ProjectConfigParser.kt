package kli.project

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Path
import kotlin.io.path.readText

object ProjectConfigParser {
    private val allowedKeys = setOf(
        "name",
        "version",
        "target",
        "deps",
        "testDeps",
        "sources",
        "resources",
        "jvmArgs",
        "repos",
        "publish",
    )

    fun load(file: Path, strictUnknownFields: Boolean): ProjectConfigLoadResult {
        val raw = try {
            file.readText()
        } catch (ex: Exception) {
            return ProjectConfigLoadResult(
                errors = listOf(
                    "Unable to read ${file.fileName}: ${ex.message}",
                    "Hint: Ensure project.json exists and is readable.",
                ),
            )
        }

        val root = try {
            JsonParser.parseString(raw)
        } catch (ex: Exception) {
            return ProjectConfigLoadResult(
                errors = listOf(
                    "Invalid JSON in ${file.fileName}: ${ex.message}",
                    "Hint: Check JSON syntax. Use a JSON validator or 'kli project-lint'.",
                ),
            )
        }

        if (!root.isJsonObject) {
            return ProjectConfigLoadResult(
                errors = listOf(
                    "project.json must be a JSON object",
                ),
            )
        }

        return parseObject(root.asJsonObject, strictUnknownFields)
    }

    private fun parseObject(json: JsonObject, strictUnknownFields: Boolean): ProjectConfigLoadResult {
        val errors = mutableListOf<String>()

        if (strictUnknownFields) {
            for ((key, _) in json.entrySet()) {
                if (key !in allowedKeys) {
                    errors += "Unknown field: $key"
                }
            }
            if (errors.isNotEmpty()) {
                errors += "Hint: Check project.json for typos. Run 'kli project-lint' for detailed validation."
            }
        }

        val name = stringField(json, "name", required = false, errors = errors)
        val version = stringField(json, "version", required = false, errors = errors) ?: "0.1.0"
        val target = when {
            !json.has("target") -> "21"
            json.get("target").isJsonPrimitive && json.get("target").asJsonPrimitive.isString -> json.get("target").asString
            json.get("target").isJsonPrimitive && json.get("target").asJsonPrimitive.isNumber -> json.get("target").asNumber.toString()
            else -> {
                errors += "Field 'target' must be a string or number (e.g., \"21\" or 21), got: ${json.get("target")}"
                "21"
            }
        }

        val deps = stringArrayField(json, "deps", errors)
        val testDeps = stringArrayField(json, "testDeps", errors)
        val sources = stringArrayField(json, "sources", errors, defaultValue = listOf("."))
        val resources = stringArrayField(json, "resources", errors)
        val jvmArgs = stringArrayField(json, "jvmArgs", errors)
        val repos = stringArrayField(
            json,
            "repos",
            errors,
            defaultValue = listOf("https://repo.maven.apache.org/maven2"),
        )

        val publish = parsePublish(json, errors)

        if (errors.isNotEmpty()) {
            return ProjectConfigLoadResult(errors = errors)
        }

        return ProjectConfigLoadResult(
            config = ProjectConfig(
                name = name,
                version = version,
                target = target,
                deps = deps,
                testDeps = testDeps,
                sources = sources,
                resources = resources,
                jvmArgs = jvmArgs,
                repos = repos,
                publish = publish,
            ),
        )
    }

    private fun parsePublish(json: JsonObject, errors: MutableList<String>): PublishConfig? {
        if (!json.has("publish")) {
            return null
        }

        val element = json.get("publish")
        if (!element.isJsonObject) {
            errors += "Field publish must be an object"
            return null
        }

        val publishObject = element.asJsonObject
        if (publishObject.has("repoId")) {
            val repoId = publishObject.get("repoId")
            if (!(repoId.isJsonPrimitive && repoId.asJsonPrimitive.isString)) {
                errors += "Field publish.repoId must be a string"
                return null
            }
        }

        if (publishObject.has("registry")) {
            val registry = publishObject.get("registry")
            if (!(registry.isJsonPrimitive && registry.asJsonPrimitive.isString)) {
                errors += "Field publish.registry must be a string"
                return null
            }
        }

        return PublishConfig(
            registry = publishObject.get("registry")?.asString,
            repoId = publishObject.get("repoId")?.asString,
        )
    }

    private fun stringField(
        json: JsonObject,
        key: String,
        required: Boolean,
        errors: MutableList<String>,
    ): String? {
        if (!json.has(key)) {
            if (required) {
                errors += "Missing required field: $key"
            }
            return null
        }

        val element = json.get(key)
        if (!(element.isJsonPrimitive && element.asJsonPrimitive.isString)) {
            errors += "Field $key must be a string"
            return null
        }

        return element.asString
    }

    private fun stringArrayField(
        json: JsonObject,
        key: String,
        errors: MutableList<String>,
        defaultValue: List<String> = emptyList(),
    ): List<String> {
        if (!json.has(key)) {
            return defaultValue
        }

        val element = json.get(key)
        if (!element.isJsonArray) {
            errors += "Field $key must be an array of strings"
            return emptyList()
        }

        val values = mutableListOf<String>()
        element.asJsonArray.forEachIndexed { index, entry ->
            if (!(entry.isJsonPrimitive && entry.asJsonPrimitive.isString)) {
                errors += "Field $key[$index] must be a string"
            } else {
                values += entry.asString
            }
        }

        return values
    }
}
