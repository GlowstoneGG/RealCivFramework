package gg.glowstone.rcf.folia.modules.corpses

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Pose
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A player's drops stay with their corpse for 15 minutes instead of scattering. Anyone can loot it. */
internal object Corpses : Module("corpses") {
    private const val LIFETIME_MS = 15 * 60 * 1000L

    // Vanilla's container reach.
    private const val REACH_SQUARED = 8.0 * 8.0
    private val itemsKey = civKey("corpse_items")
    private val ownerKey = civKey("corpse_owner")
    private val expiresKey = civKey("corpse_expires_at")

    /** Corpses currently open, so only one player loots each at a time. */
    private val looting = ConcurrentHashMap.newKeySet<UUID>()

    private class Loot(val corpse: Mannequin) : InventoryHolder {
        lateinit var view: Inventory
        override fun getInventory() = view
    }

    private fun items(corpse: Mannequin): List<ItemStack> =
        corpse.persistentDataContainer.get(itemsKey, PersistentDataType.BYTE_ARRAY)
            ?.let { ItemStack.deserializeItemsFromBytes(it).filterNotNull().filterNot { item -> item.isEmpty } }
            ?: emptyList()

    private fun armExpiry(corpse: Mannequin, delayTicks: Long? = null) {
        val expiresAt = corpse.persistentDataContainer.get(expiresKey, PersistentDataType.LONG) ?: return
        val delay = delayTicks ?: ((expiresAt - System.currentTimeMillis()) / 50).coerceAtLeast(1)
        corpse.scheduler.runDelayed(ctx.plugin, {
            // Wait for an open loot window to close, or its items would be dropped twice.
            if (corpse.uniqueId in looting) return@runDelayed armExpiry(corpse, 20)
            for (item in items(corpse)) corpse.world.dropItemNaturally(corpse.location, item)
            corpse.remove()
        }, null, delay)
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onDeath(event: PlayerDeathEvent) {
            val drops = event.drops.filterNot { it.isEmpty }
            if (drops.isEmpty()) return
            val player = event.player
            val corpse = player.world.spawn(player.location, Mannequin::class.java) { corpse ->
                corpse.setProfile(ResolvableProfile.resolvableProfile(player.playerProfile))
                listOf(Pose.SLEEPING, Pose.SWIMMING).firstOrNull { it in Mannequin.validPoses() }?.let { corpse.setPose(it, true) }
                corpse.isImmovable = true
                corpse.isInvulnerable = true
                corpse.isPersistent = true
                corpse.customName(Component.text("${player.name}'s corpse"))
                corpse.isCustomNameVisible = true
                corpse.setDescription(null)
                val pdc = corpse.persistentDataContainer
                pdc.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(drops))
                pdc.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
                pdc.set(expiresKey, PersistentDataType.LONG, System.currentTimeMillis() + LIFETIME_MS)
            }
            event.drops.clear()
            armExpiry(corpse)
        }

        @EventHandler(ignoreCancelled = true)
        fun onInteract(event: PlayerInteractEntityEvent) {
            val corpse = event.rightClicked as? Mannequin ?: return
            if (!corpse.persistentDataContainer.has(itemsKey)) return
            event.isCancelled = true
            if (event.hand != EquipmentSlot.HAND || !looting.add(corpse.uniqueId)) return
            val loot = Loot(corpse)
            loot.view = Bukkit.createInventory(loot, 54, corpse.customName() ?: Component.text("Corpse"))
            loot.view.addItem(*items(corpse).toTypedArray())
            val player = event.player
            player.openInventory(loot.view)
            // Close while the corpse is still loaded beside them, so the close handler can save what's left.
            ctx.closeWhenInvalid(player, loot.view) {
                Bukkit.isOwnedByCurrentRegion(corpse) && corpse.isValid && corpse.world == player.world &&
                    corpse.location.distanceSquared(player.location) <= REACH_SQUARED
            }
        }

        @EventHandler
        fun onClose(event: InventoryCloseEvent) {
            val loot = event.inventory.holder as? Loot ?: return
            val remaining = loot.view.contents.filterNotNull().filterNot { it.isEmpty }
            val corpse = loot.corpse
            val task = corpse.scheduler.run(ctx.plugin, {
                looting.remove(corpse.uniqueId)
                if (remaining.isEmpty()) corpse.remove()
                else corpse.persistentDataContainer.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(remaining))
            }, { looting.remove(corpse.uniqueId) })
            // Already unloaded: neither callback runs, and a stuck mark would block looting and expiry forever.
            if (task == null) looting.remove(corpse.uniqueId)
        }

        @EventHandler
        fun onLoad(event: EntitiesLoadEvent) {
            for (corpse in event.entities) {
                if (corpse is Mannequin && corpse.persistentDataContainer.has(expiresKey)) armExpiry(corpse)
            }
        }
    })
}
