package gg.glowstone.rcf.folia.modules.names

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NamePoolTest {
    @Test
    fun `hands out every valid combination once, ignoring case, then runs out`() {
        // "Bad Name" has a space and "Bartholomewson" makes nothing under 17 characters, so only two names exist.
        val pool = NamePool(listOf("Alden", "Bad Name", "Bartholomewson", "Alden"), listOf("Ash", "Underhill"), random = Random(1))
        assertEquals(2, pool.size)
        assertTrue(pool.claim("ALDEN_ASH"))
        assertEquals("Alden_Underhill", pool.next())
        assertNull(pool.next())
        pool.release("alden_ash")
        assertEquals("Alden_Ash", pool.next())
    }

    @Test
    fun `names the lists can't make can still be claimed, so admin-set names stay unique`() {
        val pool = NamePool(emptyList(), emptyList())
        assertNull(pool.next())
        assertTrue(pool.claim("Custom_Name"))
        assertFalse(pool.claim("custom_name"))
        assertTrue(pool.isTaken("CUSTOM_NAME"))
    }
}
