package gg.glowstone.rcf.folia.modules.reinforcement

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.civKey
import org.bukkit.Chunk
import org.bukkit.Color
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.persistence.PersistentDataType

internal enum class Tier(
    val item: Material,
    val requiredRank: SkillLevel,
    val breakDivisor: Double,
    val nugget: Material?,
    val explosionSurvival: Double,
    val color: Color,
) {
    WOODEN(Material.STICK, SkillLevel.NOVICE, 1.5, null, 0.10, Color.fromRGB(0x8B5A2B)),
    COPPER(Material.COPPER_INGOT, SkillLevel.APPRENTICE, 5.0, Material.COPPER_NUGGET, 0.75, Color.fromRGB(0xD9783C)),
    IRON(Material.IRON_INGOT, SkillLevel.JOURNEYMAN, 9.0, Material.IRON_NUGGET, 0.95, Color.fromRGB(0xD8D8D8)),
    ;

    companion object {
        fun forItem(item: Material) = entries.firstOrNull { it.item == item }
    }
}

/** A reinforced position within a chunk. Packs into one long: tier, y + 2048 (12 bits), local z, local x. */
internal data class Spot(val localX: Int, val y: Int, val localZ: Int, val tier: Tier) {
    init {
        require(localX in 0..15 && localZ in 0..15 && y in -2048..2047) { "Spot out of range: $localX $y $localZ" }
    }

    fun pack(): Long = (tier.ordinal.toLong() shl 20) or ((y + 2048).toLong() shl 8) or (localZ.toLong() shl 4) or localX.toLong()

    fun matches(block: Block) = block.x and 15 == localX && block.z and 15 == localZ && block.y == y

    companion object {
        fun unpack(packed: Long) = Spot(
            localX = (packed and 15).toInt(),
            y = ((packed shr 8) and 0xFFF).toInt() - 2048,
            localZ = ((packed shr 4) and 15).toInt(),
            tier = Tier.entries[((packed shr 20) and 3).toInt()],
        )

        fun of(block: Block, tier: Tier) = Spot(block.x and 15, block.y, block.z and 15, tier)
    }
}

/** Reinforcements live in chunk PDC as (packed spot, created-at millis) pairs. Callers must own the chunk's region. */
internal object ReinforcementStore {
    private const val WOODEN_LIFETIME_MS = 2_400_000L
    private val key = civKey("reinforcements")

    data class Entry(val spot: Spot, val createdAt: Long)

    /** Wooden reinforcements older than 40 minutes no longer count; they're pruned on the next write. */
    fun entries(chunk: Chunk, now: Long = System.currentTimeMillis()): List<Entry> {
        val raw = chunk.persistentDataContainer.get(key, PersistentDataType.LONG_ARRAY) ?: return emptyList()
        return (0 until raw.size / 2).map { Entry(Spot.unpack(raw[it * 2]), raw[it * 2 + 1]) }
            .filterNot { it.spot.tier == Tier.WOODEN && now - it.createdAt >= WOODEN_LIFETIME_MS }
    }

    fun get(block: Block): Entry? = entries(block.chunk).firstOrNull { it.spot.matches(block) }

    fun tier(block: Block): Tier? = get(block)?.spot?.tier

    fun put(block: Block, tier: Tier, createdAt: Long = System.currentTimeMillis()) {
        write(block.chunk, entries(block.chunk).filterNot { it.spot.matches(block) } + Entry(Spot.of(block, tier), createdAt))
    }

    fun remove(block: Block): Entry? {
        val all = entries(block.chunk)
        val entry = all.firstOrNull { it.spot.matches(block) } ?: return null
        write(block.chunk, all - entry)
        return entry
    }

    private fun write(chunk: Chunk, entries: List<Entry>) {
        val pdc = chunk.persistentDataContainer
        if (entries.isEmpty()) pdc.remove(key)
        else pdc.set(key, PersistentDataType.LONG_ARRAY, entries.flatMap { listOf(it.spot.pack(), it.createdAt) }.toLongArray())
    }
}
