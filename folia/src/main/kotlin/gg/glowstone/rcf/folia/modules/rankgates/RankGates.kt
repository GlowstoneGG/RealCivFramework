package gg.glowstone.rcf.folia.modules.rankgates

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.modules.professions.Librarian
import gg.glowstone.rcf.folia.modules.professions.Miner
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.enchantment.EnchantItemEvent
import org.bukkit.event.enchantment.PrepareItemEnchantEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta

/** Profession ranks gate ore drops, workstations and enchantment levels, as set in `rank-gates.yml`. */
internal object RankGates : Module("rank-gates") {
    override val reads = listOf(Miner, Librarian)

    private fun Player.deny(message: String) = ctx.actionBar.show(this, "rank-gates", Component.text(message, NamedTextColor.RED))

    private fun ItemStack.allEnchants(): Map<Enchantment, Int> =
        (itemMeta as? EnchantmentStorageMeta)?.storedEnchants ?: enchantments

    override fun listeners(): List<Listener> {
        val file = ctx.plugin.dataFolder.resolve("rank-gates.yml")
        if (!file.exists()) ctx.plugin.saveResource("rank-gates.yml", false)
        val rules = GateRules.parse(YamlConfiguration.loadConfiguration(file), ctx.plugin.logger::warning)

        fun maxLevel(player: Player, enchantment: Enchantment) = rules.maxLevel(ctx.rank(player, Librarian), enchantment.key)

        return listOf(object : Listener {
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            fun onMine(event: BlockBreakEvent) {
                val needed = rules.miningRank(event.block.type) ?: return
                val player = event.player
                if (player.gameMode == GameMode.CREATIVE || ctx.rank(player, Miner) >= needed) return
                event.isDropItems = false
                event.expToDrop = 0
                player.deny("Needs Miner ${needed.displayName} to get anything from this")
            }

            @EventHandler(priority = EventPriority.HIGH)
            fun onUse(event: PlayerInteractEvent) {
                if (event.action != Action.RIGHT_CLICK_BLOCK || event.useInteractedBlock() == Event.Result.DENY) return
                val needs = rules.station(event.clickedBlock?.type ?: return)
                val player = event.player
                if (needs.isEmpty() || player.gameMode == GameMode.CREATIVE) return
                val met = needs.any { need ->
                    val profession = ctx.professions.catalog[need.professionId]
                    // A profession that isn't loaded can't gate anything.
                    profession == null || ctx.rank(player, profession) >= need.rank
                }
                if (met) return
                event.setUseInteractedBlock(Event.Result.DENY)
                player.deny("Needs " + needs.joinToString(" or ") { "${ctx.professions.catalog[it.professionId]!!.displayName} ${it.rank.displayName}" })
            }

            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            fun onEnchantOffers(event: PrepareItemEnchantEvent) {
                val offers = event.offers
                for (i in offers.indices) {
                    val offer = offers[i] ?: continue
                    if (offer.enchantmentLevel > maxLevel(event.enchanter, offer.enchantment)) offers[i] = null
                }
            }

            @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
            fun onEnchant(event: EnchantItemEvent) {
                val player = event.enchanter
                event.enchantsToAdd.entries.removeIf { (enchantment, level) -> level > maxLevel(player, enchantment) }
                if (event.enchantsToAdd.isNotEmpty()) return
                event.isCancelled = true
                player.deny("Your Librarian rank is too low for this enchantment")
            }

            // Blocks results that add an enchantment above the player's limit. Repairing gear that already has it is fine.
            @EventHandler(priority = EventPriority.HIGH)
            fun onAnvil(event: PrepareAnvilEvent) {
                val player = event.view.player as? Player ?: return
                val result = event.result ?: return
                val existing = event.inventory.firstItem?.allEnchants().orEmpty()
                val locked = result.allEnchants().any { (enchantment, level) ->
                    level > maxLevel(player, enchantment) && level > (existing[enchantment] ?: 0)
                }
                if (!locked) return
                event.result = null
                player.deny("Your Librarian rank is too low for this enchantment")
            }
        })
    }
}
