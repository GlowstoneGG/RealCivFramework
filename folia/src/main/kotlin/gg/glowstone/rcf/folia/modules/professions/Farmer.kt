package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.entity.EntityBreedEvent

internal object Farmer : Profession(
    id = "farmer",
    displayName = "Farmer",
    color = 0x2DC26B,
    perks = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.25),
    perkLabel = "extra crop drop chance",
) {
    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onHarvest(event: BlockBreakEvent) {
            if (event.block.blockData.isMatureCrop()) ctx.award(event.player, Farmer, 3.0)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBreed(event: EntityBreedEvent) {
            val player = event.breeder as? Player ?: return
            player.scheduler.run(ctx.plugin, { ctx.award(player, Farmer, 5.0) }, null)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onDrops(event: BlockDropItemEvent) {
            if (event.blockState.blockData.isMatureCrop() && roll(ctx.perk(event.player, Farmer))) event.dropOneExtra()
        }
    })
}
