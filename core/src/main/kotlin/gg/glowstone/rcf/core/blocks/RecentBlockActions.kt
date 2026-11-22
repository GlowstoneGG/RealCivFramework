package gg.glowstone.rcf.core.blocks

import java.util.LinkedHashMap
import java.util.UUID

/** Bounded, expiring memory of recent block actions: placed blocks just broken, and builder awards per position. */
class RecentBlockActions(
    private val ttlMillis: Long = 86_400_000,
    private val capacity: Int = 200_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Position(val world: UUID, val x: Int, val y: Int, val z: Int)

    private val builderAwards = LinkedHashMap<Pair<UUID, Position>, Long>()
    private val placedBreaks = LinkedHashMap<Position, Long>()

    @Synchronized
    fun markPlacedBreak(position: Position) {
        remember(placedBreaks, position)
    }

    /** Called when a new block takes the position, so it isn't mistaken for the one that broke. */
    @Synchronized
    fun forgetPlacedBreak(position: Position) {
        placedBreaks.remove(position)
    }

    /** True for 10 seconds after a placed block at this position broke. */
    @Synchronized
    fun wasPlacedBreak(position: Position): Boolean {
        val markedAt = placedBreaks[position] ?: return false
        return clock() - markedAt < 10_000
    }

    /** Returns false for a recently rewarded placement by this player at this location. */
    @Synchronized
    fun allowBuilderAward(player: UUID, position: Position): Boolean {
        val key = player to position
        val previous = builderAwards[key]
        if (previous != null && clock() - previous < ttlMillis) return false
        remember(builderAwards, key)
        return true
    }

    private fun <K> remember(map: LinkedHashMap<K, Long>, key: K) {
        map.remove(key)
        map[key] = clock()
        while (map.size > capacity) map.remove(map.keys.first())
    }
}
