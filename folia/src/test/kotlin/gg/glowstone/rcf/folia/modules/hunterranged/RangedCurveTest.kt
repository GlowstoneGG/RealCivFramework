package gg.glowstone.rcf.folia.modules.hunterranged

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RangedCurveTest {
    @Test
    fun `damage rises to the golden window, peaks inside it, and falls off beyond`() {
        assertEquals(4.0, RangedCurve.damage(crossbow = false, level = 0, distance = 0.0))
        assertEquals(15.0, RangedCurve.damage(crossbow = false, level = 0, distance = 45.0))
        assertEquals(15.0, RangedCurve.damage(crossbow = true, level = 0, distance = 56.0))
        val samples = (0..43).map { RangedCurve.damage(crossbow = false, level = 0, distance = it.toDouble()) }
        assertTrue(samples.zipWithNext().all { (a, b) -> b > a })
        assertTrue(samples.last() < 8.0)
        val far = RangedCurve.damage(crossbow = false, level = 0, distance = 46.0 + 18)
        assertTrue(far in 4.0..8.0 && far < RangedCurve.damage(crossbow = false, level = 0, distance = 47.0))
    }

    @Test
    fun `higher Hunter levels widen the window`() {
        assertTrue(RangedCurve.damage(crossbow = false, level = 0, distance = 40.0) < 15.0)
        assertEquals(15.0, RangedCurve.damage(crossbow = false, level = 5, distance = 40.0))
        assertEquals(15.0, RangedCurve.damage(crossbow = false, level = 5, distance = 51.0))
        assertTrue(RangedCurve.damage(crossbow = true, level = 0, distance = 60.0) < 15.0)
        assertEquals(15.0, RangedCurve.damage(crossbow = true, level = 5, distance = 60.0))
    }
}
