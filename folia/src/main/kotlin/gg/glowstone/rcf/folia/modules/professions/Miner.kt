package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent

internal object Miner : Profession(
    id = "miner",
    displayName = "Miner",
    color = 0xAAAAAA,
    perks = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.25),
    perkLabel = "extra ore drop chance",
) {
    private val stones = setOf(Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE, Material.DIORITE, Material.ANDESITE)

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        // Placed ores and stone give no XP or bonus drops when broken, so place/break loops can't farm them.
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBreak(event: BlockBreakEvent) {
            val type = event.block.type
            if (!type.isOre() && type !in stones) return
            if (ctx.blocks.isPlaced(event.block)) return
            if (type.isOre()) ctx.award(event.player, Miner, 5.0, cooldownMs = 2_000, key = "miner_ore")
            else ctx.award(event.player, Miner, 1.0, cooldownMs = 2_000, key = "miner_stone")
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onDrops(event: BlockDropItemEvent) {
            if (!event.blockState.type.isOre() || ctx.blocks.isPlaced(event.block)) return
            if (roll(ctx.perk(event.player, Miner))) event.dropOneExtra()
        }
    })
}
