package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.core.materials.MaterialType
import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.minecraftKey
import gg.glowstone.rcf.folia.materials.getMaterialType
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.inventory.meta.Damageable
import kotlin.math.ceil

internal object Blacksmith : Profession(
    id = "blacksmith",
    displayName = "Blacksmith",
    color = 0x555555,
    perks = listOf(0.0, 0.04, 0.08, 0.12, 0.16, 0.20),
    perkLabel = "crafted equipment durability",
) {
    override val recipes = mapOf(minecraftKey("anvil") to SkillLevel.APPRENTICE)

    private fun Material.isEquipment() = getMaterialType() != MaterialType.OTHER

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH)
        fun onPrepare(event: PrepareItemCraftEvent) {
            val player = event.view.player as? Player ?: return
            val recipeResult = event.recipe?.result ?: return
            if (!recipeResult.type.isEquipment()) return
            val result = event.inventory.result?.clone() ?: return
            val meta = result.itemMeta as? Damageable ?: return
            val source = recipeResult.itemMeta as? Damageable
            val base = if (source?.hasMaxDamage() == true) source.maxDamage else result.type.maxDurability.toInt()
            if (base <= 0) return
            meta.setMaxDamage(ceil(base * (1.0 + ctx.perk(player, Blacksmith))).toInt())
            result.itemMeta = meta
            event.inventory.result = result
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onCraft(event: CraftItemEvent) {
            if (event.action == InventoryAction.NOTHING || !event.recipe.result.type.isEquipment()) return
            val player = event.whoClicked as? Player ?: return
            ctx.award(player, Blacksmith, 3.0, cooldownMs = 2_000)
        }
    })
}
