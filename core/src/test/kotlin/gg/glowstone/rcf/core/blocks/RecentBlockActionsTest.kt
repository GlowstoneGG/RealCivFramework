package gg.glowstone.rcf.core.blocks

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecentBlockActionsTest {
    @Test
    fun `placed breaks are remembered for ten seconds`() {
        var now = 1000L
        val recent = RecentBlockActions(clock = { now })
        val position = RecentBlockActions.Position(UUID.randomUUID(), 1, 64, 2)
        assertFalse(recent.wasPlacedBreak(position))
        recent.markPlacedBreak(position)
        assertTrue(recent.wasPlacedBreak(position))
        assertTrue(recent.wasPlacedBreak(position))
        now += 10_000
        assertFalse(recent.wasPlacedBreak(position))
        recent.markPlacedBreak(position)
        recent.forgetPlacedBreak(position)
        assertFalse(recent.wasPlacedBreak(position))
    }

    @Test
    fun `builder cannot repeatedly reward the same position within the window`() {
        var now = 0L
        val recent = RecentBlockActions(ttlMillis = 100, clock = { now })
        val position = RecentBlockActions.Position(UUID.randomUUID(), 4, 70, 5)
        val player = UUID.randomUUID()
        assertTrue(recent.allowBuilderAward(player, position))
        assertFalse(recent.allowBuilderAward(player, position))
        assertTrue(recent.allowBuilderAward(UUID.randomUUID(), position))
        now += 100
        assertTrue(recent.allowBuilderAward(player, position))
    }
}
