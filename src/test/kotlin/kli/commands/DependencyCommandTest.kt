package kli.commands

import com.google.gson.JsonParser
import kli.resolver.DependencyTreeNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DependencyCommandTest {
    @Test
    fun render_forest_outputs_ascii_tree_for_single_root() {
        val root = DependencyTreeNode(
            coordinate = "com.example:root:1.0.0",
            children = listOf(
                DependencyTreeNode(
                    coordinate = "com.example:child-a:1.0.0",
                    children = listOf(
                        DependencyTreeNode(
                            coordinate = "com.example:grandchild-a:1.0.0",
                            children = emptyList(),
                        ),
                    ),
                ),
                DependencyTreeNode(
                    coordinate = "com.example:child-b:1.0.0",
                    children = listOf(
                        DependencyTreeNode(
                            coordinate = "com.example:grandchild:1.0.0",
                            children = emptyList(),
                        ),
                    ),
                ),
            ),
        )

        val lines = renderForest(listOf(root), useColor = false)

        assertEquals(
            listOf(
                "- com.example:root:1.0.0",
                "    +-- com.example:child-a:1.0.0",
                "    |       +-- com.example:grandchild-a:1.0.0",
                "    +-- com.example:child-b:1.0.0",
                "            +-- com.example:grandchild:1.0.0",
            ),
            lines,
        )
    }

    @Test
    fun render_forest_outputs_multiple_roots() {
        val roots = listOf(
            DependencyTreeNode(
                coordinate = "com.example:first:1.0.0",
                children = listOf(
                    DependencyTreeNode(
                        coordinate = "com.example:first-child:1.0.0",
                        children = emptyList(),
                    ),
                ),
            ),
            DependencyTreeNode(
                coordinate = "com.example:second:1.0.0",
                children = emptyList(),
            ),
        )

        val lines = renderForest(roots, useColor = false)

        assertEquals(
            listOf(
                "- com.example:first:1.0.0",
                "    +-- com.example:first-child:1.0.0",
                "- com.example:second:1.0.0",
            ),
            lines,
        )
    }

    @Test
    fun render_forest_colorizes_tree_glyphs_and_colons() {
        val lines = renderForest(
            listOf(
                DependencyTreeNode(
                    coordinate = "com.example:root:1.0.0",
                    children = emptyList(),
                ),
            ),
            useColor = true,
        )

        val line = lines.first()
        assertTrue(line.contains("\u001B[90m- \u001B[0m"))
        assertTrue(line.contains("com.example\u001B[90m:\u001B[0mroot\u001B[90m:\u001B[0m1.0.0"))
    }

    @Test
    fun build_list_json_contains_scope_and_dependency_arrays() {
        val json = buildListJson(
            scope = "runtime",
            runtimeDeps = listOf("a:b:1"),
            testDeps = emptyList(),
        )

        val parsed = JsonParser.parseString(json).asJsonObject
        assertEquals("runtime", parsed.get("scope").asString)
        assertEquals(1, parsed.getAsJsonArray("runtimeDeps").size())
        assertEquals(0, parsed.getAsJsonArray("testDeps").size())
    }

    @Test
    fun build_tree_json_contains_runtime_and_test_trees() {
        val runtime = listOf(DependencyTreeNode("a:b:1", emptyList()))
        val test = listOf(DependencyTreeNode("x:y:2", emptyList()))

        val json = buildTreeJson(
            scope = "all",
            runtimeTrees = runtime,
            testTrees = test,
        )

        val parsed = JsonParser.parseString(json).asJsonObject
        assertEquals("all", parsed.get("scope").asString)
        assertEquals(1, parsed.getAsJsonArray("runtime").size())
        assertEquals(1, parsed.getAsJsonArray("test").size())
    }
}
