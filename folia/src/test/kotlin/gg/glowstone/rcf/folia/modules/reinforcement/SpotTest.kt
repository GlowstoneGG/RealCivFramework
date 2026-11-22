package gg.glowstone.rcf.folia.modules.reinforcement

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpotTest {
    @Test
    fun `spots round-trip across the full world height and every tier`() {
        for (tier in Tier.entries) for (y in listOf(-2048, -64, -1, 0, 64, 319, 2047)) for (x in listOf(0, 15)) {
            val spot = Spot(x, y, 15 - x, tier)
            assertEquals(spot, Spot.unpack(spot.pack()))
        }
    }

    @Test
    fun `out of range spots are rejected`() {
        assertFailsWith<IllegalArgumentException> { Spot(16, 0, 0, Tier.WOODEN) }
        assertFailsWith<IllegalArgumentException> { Spot(0, 2048, 0, Tier.WOODEN) }
    }
}
