package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent

internal object Builder : Profession(
    id = "builder",
    displayName = "Builder",
    color = 0xF1C4D9,
    perks = listOf(0.0, 0.08, 0.16, 0.24, 0.32, 0.40),
    perkLabel = "less fall damage",
) {
    private val cheapBlocks = setOf(Material.DIRT, Material.COBBLESTONE, Material.STONE, Material.SAND, Material.GRAVEL)

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onPlace(event: BlockPlaceEvent) {
            val block = event.block
            if (!block.type.isSolid || block.type in cheapBlocks) return
            if (ctx.blocks.allowBuilderAward(event.player.uniqueId, block)) {
                ctx.award(event.player, Builder, 1.0, cooldownMs = 5_000)
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onFall(event: EntityDamageEvent) {
            if (event.cause != EntityDamageEvent.DamageCause.FALL) return
            val player = event.entity as? Player ?: return
            event.damage *= (1.0 - ctx.perk(player, Builder)).coerceIn(0.0, 1.0)
        }
    })
}
