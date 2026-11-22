package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.enchantment.EnchantItemEvent

internal object Librarian : Profession(
    id = "librarian",
    displayName = "Librarian",
    color = 0x3598DB,
    perks = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.25),
    perkLabel = "enchant cost discount",
) {
    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onDiscount(event: EnchantItemEvent) {
            val multiplier = (1.0 - ctx.perk(event.enchanter, Librarian)).coerceIn(0.0, 1.0)
            event.expLevelCost = (event.expLevelCost * multiplier).toInt().coerceAtLeast(1)
        }

        // Runs after the discount, so XP follows the levels actually spent.
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onEnchant(event: EnchantItemEvent) {
            ctx.award(event.enchanter, Librarian, 2.0 + event.expLevelCost, cooldownMs = 1_000)
        }
    })
}
