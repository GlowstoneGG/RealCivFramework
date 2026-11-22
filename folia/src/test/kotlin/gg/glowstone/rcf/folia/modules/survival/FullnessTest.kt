package gg.glowstone.rcf.folia.modules.survival

import kotlin.test.Test
import kotlin.test.assertEquals

class FullnessTest {
    @Test
    fun `repeating a food reduces its gain by a fifth each time down to a floor`() {
        assertEquals(25.0, Fullness.mealGain(5, 0))
        assertEquals(15.0, Fullness.mealGain(5, 2), 1e-9)
        assertEquals(5.0, Fullness.mealGain(5, 4), 1e-9)
        assertEquals(5.0, Fullness.mealGain(5, 5), 1e-9)
    }
}
