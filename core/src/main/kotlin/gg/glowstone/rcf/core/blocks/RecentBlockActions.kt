package gg.glowstone.rcf.core.blocks

import java.util.LinkedHashMap
import java.util.UUID

/** Bounded, expiring protection against place/break and same-position XP loops. */
class RecentBlockActions(
    private val ttlMillis: Long = 86_400_000,
    private val capacity: Int = 200_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Position(val world: UUID, val x: Int, val y: Int, val z: Int)

    private val placed = LinkedHashMap<Position, Long>()
    private val builderAwards = LinkedHashMap<Pair<UUID, Position>, Long>()
    private val placedBreaks = LinkedHashMap<Position, Long>()

    @Synchronized
    fun markPlaced(position: Position) {
        remember(placed, position)
    }

    /** Consumes a marker so the next natural block at this position can award XP. */
    @Synchronized
    fun wasPlaced(position: Position): Boolean {
        val markedAt = placed.remove(position) ?: return false
        val recent = clock() - markedAt < ttlMillis
        if (recent) remember(placedBreaks, position)
        return recent
    }

    @Synchronized
    fun wasPlacedBreak(position: Position): Boolean {
        val markedAt = placedBreaks.remove(position) ?: return false
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
