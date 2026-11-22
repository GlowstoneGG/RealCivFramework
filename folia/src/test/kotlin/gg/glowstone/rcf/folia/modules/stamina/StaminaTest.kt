package gg.glowstone.rcf.folia.modules.stamina

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaminaTest {
    @Test
    fun `slow sprint movement costs stamina but a stopped sprint does not`() {
        assertTrue(Stamina.isMovingSprint(true, false, false, 0.02 * 0.02))
        assertFalse(Stamina.isMovingSprint(true, false, false, 0.0))
        assertFalse(Stamina.isMovingSprint(true, false, false, 1e-14))
    }

    @Test
    fun `walking riding and winded movement do not count as sprinting`() {
        assertFalse(Stamina.isMovingSprint(false, false, false, 1.0))
        assertFalse(Stamina.isMovingSprint(true, true, false, 1.0))
        assertFalse(Stamina.isMovingSprint(true, false, true, 1.0))
    }

    @Test
    fun `armour, Hunter rank and carrying combine multiplicatively`() {
        assertEquals(1.0, Stamina.costMultiplier(0.0, 0, null), 1e-9)
        assertEquals(1.72, Stamina.costMultiplier(72.0, 0, null), 1e-9)
        assertEquals(0.6, Stamina.costMultiplier(0.0, 5, null), 1e-9)
        assertEquals(2.0, Stamina.costMultiplier(0.0, 0, 0), 1e-9)
        assertEquals(1.0, Stamina.costMultiplier(0.0, 0, 5), 1e-9)
    }

    @Test
    fun `regen doubles over five seconds of rest and halves when hungry`() {
        assertEquals(6.0, Stamina.regenRate(6.0, 0.0, false), 1e-9)
        assertEquals(9.0, Stamina.regenRate(6.0, 2.5, false), 1e-9)
        assertEquals(12.0, Stamina.regenRate(6.0, 60.0, false), 1e-9)
        assertEquals(6.0, Stamina.regenRate(6.0, 60.0, true), 1e-9)
    }
}
