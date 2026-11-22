package gg.glowstone.rcf.folia.modules.carry

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.downed.Downed
import gg.glowstone.rcf.folia.modules.professions.Healer
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Anyone can pick up a downed player and carry them on their shoulders. Healers carry with less slowdown. */
internal object Carry : Module("carry", dependsOn = listOf(Downed)) {
    override val reads = listOf(Healer)

    private val slowKey = civKey("carry_slow")

    /** Carrier to the player they carry. */
    private val carrying = ConcurrentHashMap<UUID, Player>()
    private val releasing = ConcurrentHashMap.newKeySet<UUID>()

    /** Safe from any thread; reads false when this module is off. */
    fun isCarrying(carrier: UUID): Boolean = carrying.containsKey(carrier)

    private fun pickUp(carrier: Player, target: Player) {
        if (!carrier.addPassenger(target)) return
        carrying[carrier.uniqueId] = target
        val slow = 0.30 - 0.04 * ctx.rank(carrier, Healer).ordinal
        carrier.getAttribute(Attribute.MOVEMENT_SPEED)?.run {
            removeModifier(slowKey)
            addTransientModifier(AttributeModifier(slowKey, -slow, AttributeModifier.Operation.MULTIPLY_SCALAR_1))
        }
        // Covers every way the carry can end: revive, death, quit, the carrier being downed.
        carrier.scheduler.runAtFixedRate(ctx.plugin, { task ->
            if (carrying[carrier.uniqueId] != target) {
                task.cancel()
                return@runAtFixedRate
            }
            if (!target.isValid || target.vehicle != carrier || !Downed.isDowned(target.uniqueId) || Downed.isDowned(carrier.uniqueId)) {
                drop(carrier)
                task.cancel()
            }
        }, { carrying.remove(carrier.uniqueId) }, 10L, 10L)
    }

    private fun drop(carrier: Player) {
        val target = carrying.remove(carrier.uniqueId) ?: return
        carrier.getAttribute(Attribute.MOVEMENT_SPEED)?.removeModifier(slowKey)
        if (target.vehicle == carrier) {
            releasing += target.uniqueId
            try {
                carrier.removePassenger(target)
            } finally {
                releasing -= target.uniqueId
            }
        }
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onInteract(event: PlayerInteractEntityEvent) {
            val carrier = event.player
            val target = event.rightClicked as? Player ?: return
            if (event.hand != EquipmentSlot.HAND || carrier.isSneaking || !carrier.inventory.itemInMainHand.type.isAir) return
            if (!Downed.isDowned(target.uniqueId) || target.isInsideVehicle || carrier.passengers.isNotEmpty()) return
            if (!Bukkit.isOwnedByCurrentRegion(target)) return
            event.isCancelled = true
            pickUp(carrier, target)
        }

        @EventHandler(ignoreCancelled = true)
        fun onSneak(event: PlayerToggleSneakEvent) {
            if (event.isSneaking) drop(event.player)
        }

        @EventHandler(ignoreCancelled = true)
        fun onDismount(event: EntityDismountEvent) {
            val target = event.entity as? Player ?: return
            if (event.isCancellable && Downed.isDowned(target.uniqueId) && target.uniqueId !in releasing) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.LOW)
        fun onQuit(event: PlayerQuitEvent) {
            drop(event.player)
        }
    })
}
