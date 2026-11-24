package gg.glowstone.rcf.folia.modules.patdown

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.modules.downed.Downed
import gg.glowstone.rcf.folia.modules.professions.Hunter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sneak + right-click a player with a spyglass to search them. Downed players can be searched by anyone and robbed;
 * standing players only by Hunters, after a windup, and only to look.
 */
internal object PatDown : Module("pat-down") {
    override val reads = listOf(Hunter)

    private const val WINDUP_TICKS = 100
    private const val PERIOD = 5L
    private const val REACH_SQUARED = 3.0 * 3.0

    // Just over two redraws, so the line drops soon after the windup ends.
    private const val PROGRESS_MS = 600L

    private val searching = ConcurrentHashMap.newKeySet<UUID>()

    private class ReadOnly : InventoryHolder {
        lateinit var view: Inventory
        override fun getInventory() = view
    }

    /** A snapshot of [target]'s inventory: storage, then armour and off hand. Callers must own [target]'s region. */
    private fun snapshot(target: Player): Inventory {
        val holder = ReadOnly()
        holder.view = Bukkit.createInventory(holder, 45, Component.text("${target.name}'s inventory"))
        val inventory = target.inventory
        (inventory.storageContents + inventory.armorContents + inventory.itemInOffHand).forEachIndexed { slot, item ->
            if (item != null) holder.view.setItem(slot, item.clone())
        }
        return holder.view
    }

    private fun inReach(searcher: Player, target: Player) = Bukkit.isOwnedByCurrentRegion(target) &&
        target.world == searcher.world && target.location.distanceSquared(searcher.location) <= REACH_SQUARED

    private fun windup(searcher: Player, target: Player) {
        if (!searching.add(searcher.uniqueId)) return
        var elapsed = 0
        searcher.scheduler.runAtFixedRate(ctx.plugin, { task ->
            val holding = searcher.inventory.itemInMainHand.type == Material.SPYGLASS
            if (!searcher.isSneaking || !holding || !inReach(searcher, target)) {
                searching.remove(searcher.uniqueId)
                ctx.actionBar.show(searcher, "pat-down", Component.text("Search interrupted", NamedTextColor.RED))
                task.cancel()
                return@runAtFixedRate
            }
            elapsed += PERIOD.toInt()
            if (elapsed < WINDUP_TICKS) {
                ctx.actionBar.show(searcher, "pat-down", Component.text("Searching ${target.name}..."), ttlMs = PROGRESS_MS)
                return@runAtFixedRate
            }
            searching.remove(searcher.uniqueId)
            task.cancel()
            searcher.openInventory(snapshot(target))
            ctx.actionBar.show(target, "pat-down", Component.text("${searcher.name} searched you"))
        }, { searching.remove(searcher.uniqueId) }, 1L, PERIOD)
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onInteract(event: PlayerInteractEntityEvent) {
            val searcher = event.player
            val target = event.rightClicked as? Player ?: return
            if (event.hand != EquipmentSlot.HAND || !searcher.isSneaking) return
            if (searcher.inventory.itemInMainHand.type != Material.SPYGLASS) return
            event.isCancelled = true
            when {
                Downed.isDowned(target.uniqueId) -> if (inReach(searcher, target)) {
                    searcher.openInventory(target.inventory)
                    // Robbing is only allowed while they stay downed and beside you.
                    ctx.closeWhenInvalid(searcher, target.inventory) { Downed.isDowned(target.uniqueId) && inReach(searcher, target) }
                }
                ctx.rank(searcher, Hunter) >= SkillLevel.APPRENTICE -> windup(searcher, target)
                else -> ctx.actionBar.show(searcher, "pat-down", Component.text("Only Hunters can search a standing player"))
            }
        }

        @EventHandler(ignoreCancelled = true)
        fun onClick(event: InventoryClickEvent) {
            if (event.inventory.holder is ReadOnly) event.isCancelled = true
        }

        @EventHandler(ignoreCancelled = true)
        fun onDrag(event: InventoryDragEvent) {
            if (event.inventory.holder is ReadOnly) event.isCancelled = true
        }
    })
}
