package kli.resolver

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MavenCoordinateTest {
    @Test
    fun parses_valid_coordinate() {
        val coordinate = MavenCoordinate.parse("io.ktor:ktor-server-core:3.0.0")

        assertEquals("io.ktor", coordinate.group)
        assertEquals("ktor-server-core", coordinate.artifact)
        assertEquals("3.0.0", coordinate.version)
    }

    @Test
    fun rejects_invalid_coordinate_shapes() {
        assertFailsWith<IllegalArgumentException> { MavenCoordinate.parse("bad") }
        assertFailsWith<IllegalArgumentException> { MavenCoordinate.parse("a:b") }
        assertFailsWith<IllegalArgumentException> { MavenCoordinate.parse("a:b:c:d") }
        assertFailsWith<IllegalArgumentException> { MavenCoordinate.parse("a::c") }
    }
}
