package gg.glowstone.rcf.folia.modules.professions

import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.BlockData
import org.bukkit.event.block.BlockDropItemEvent
import java.util.concurrent.ThreadLocalRandom

internal fun roll(chance: Double) = chance > 0 && ThreadLocalRandom.current().nextDouble() < chance

internal fun BlockData.isMatureCrop() = (this as? Ageable)?.let { it.age == it.maximumAge } == true

internal fun Material.isOre() = name.endsWith("_ORE") || this == Material.ANCIENT_DEBRIS

/** Drops one more of the block's first non-seed drop. */
internal fun BlockDropItemEvent.dropOneExtra() {
    val item = items.firstOrNull { it.itemStack.type != Material.AIR && it.itemStack.type !in seeds } ?: return
    block.world.dropItemNaturally(block.location, item.itemStack.clone().apply { amount = 1 })
}

private val seeds = setOf(Material.WHEAT_SEEDS, Material.BEETROOT_SEEDS)
