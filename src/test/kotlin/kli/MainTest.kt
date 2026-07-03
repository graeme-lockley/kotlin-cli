package kli

import kotlin.test.Test
import kotlin.test.assertEquals

class MainTest {
    @Test
    fun app_name_is_stable() {
        assertEquals("kli", Kli().commandName)
    }
}
