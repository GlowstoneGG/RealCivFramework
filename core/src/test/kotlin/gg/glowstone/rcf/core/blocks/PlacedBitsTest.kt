package gg.glowstone.rcf.core.blocks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacedBitsTest {
    @Test
    fun `every block of a section gets its own bit, negative coordinates included`() {
        val seen = (0 until 16).flatMap { x -> (0 until 16).flatMap { y -> (0 until 16).map { z -> PlacedBits.index(x, y, z) } } }
        assertEquals((0 until 4096).toList(), seen.sorted())
        assertEquals(PlacedBits.index(15, 15, 15), PlacedBits.index(-1, -1, -1))
        assertEquals(-1, PlacedBits.section(-1))
        assertEquals(-4, PlacedBits.section(-64))
        assertEquals(0, PlacedBits.section(15))
    }

    @Test
    fun `bits set and clear independently and an empty section drops`() {
        val a = PlacedBits.index(3, 70, -5)
        val b = PlacedBits.index(15, 15, 15)
        var words = PlacedBits.set(null, a, true)
        words = PlacedBits.set(words, b, true)
        assertTrue(PlacedBits.get(words, a))
        assertTrue(PlacedBits.get(words, b))
        assertFalse(PlacedBits.get(words, PlacedBits.index(4, 70, -5)))
        words = PlacedBits.set(words, a, false)
        assertFalse(PlacedBits.get(words, a))
        assertTrue(PlacedBits.get(words, b))
        assertNull(PlacedBits.set(words, b, false))
        assertNull(PlacedBits.set(null, a, false))
    }

    @Test
    fun `malformed stored data reads as natural`() {
        assertFalse(PlacedBits.get(LongArray(3) { -1L }, 0))
        assertTrue(PlacedBits.get(PlacedBits.set(LongArray(3), 0, true), 0))
    }
}
