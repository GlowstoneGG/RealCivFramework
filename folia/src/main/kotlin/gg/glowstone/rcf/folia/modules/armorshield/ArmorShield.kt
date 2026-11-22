package gg.glowstone.rcf.folia.modules.armorshield

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import net.kyori.adventure.text.Component
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A full set of armour absorbs the first strong PvP melee hit, then recharges for ten minutes. */
internal object ArmorShield : Module("armor-shield") {
    private const val RECHARGE_MS = 600_000L
    private val rechargeKey = civKey("armor_shield_recharge_ms")
    private val armorSuffixes = listOf("_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS")
    private val weaponSuffixes = listOf("_SWORD", "_AXE", "_SPEAR")

    // Absent means the shield is ready.
    private val readyAt = ConcurrentHashMap<UUID, Long>()

    private fun fullArmor(player: Player) = player.inventory.armorContents.let { armor ->
        armor.size == 4 && armor.all { item -> item != null && armorSuffixes.any(item.type.name::endsWith) }
    }

    /** Callers must be on the player's owning region. */
    private fun scheduleReadyNotice(player: Player, at: Long) {
        val ticks = ((at - System.currentTimeMillis()) / 50).coerceAtLeast(1)
        player.scheduler.runDelayed(ctx.plugin, {
            if (readyAt.remove(player.uniqueId, at)) player.sendActionBar(Component.text("Your armour shield has recharged"))
        }, null, ticks)
    }

    override fun listeners() = listOf(object : Listener {
        // Before downed (HIGH), so an absorbed hit can't down the player.
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        fun onHit(event: EntityDamageByEntityEvent) {
            val victim = event.entity as? Player ?: return
            val attacker = event.damager as? Player ?: return
            if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
            if (weaponSuffixes.none(attacker.inventory.itemInMainHand.type.name::endsWith)) return
            if (attacker.attackCooldown < 0.85f || event.finalDamage < 2.0 || !fullArmor(victim)) return
            val now = System.currentTimeMillis()
            if ((readyAt[victim.uniqueId] ?: 0L) > now) return
            event.isCancelled = true
            val at = now + RECHARGE_MS
            readyAt[victim.uniqueId] = at
            victim.world.playSound(victim.location, Sound.ITEM_SHIELD_BREAK, 1f, 0.8f)
            victim.sendActionBar(Component.text("Your armour shield absorbed the blow"))
            attacker.sendActionBar(Component.text("${victim.name}'s armour absorbed the blow"))
            scheduleReadyNotice(victim, at)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onDeath(event: PlayerDeathEvent) {
            readyAt.remove(event.player.uniqueId)
        }

        // The recharge only counts down while the player is online.
        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            val remaining = (readyAt.remove(event.player.uniqueId) ?: return) - System.currentTimeMillis()
            if (remaining > 0) event.player.persistentDataContainer.set(rechargeKey, PersistentDataType.LONG, remaining)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val player = event.player
            val remaining = player.persistentDataContainer.get(rechargeKey, PersistentDataType.LONG) ?: return
            player.persistentDataContainer.remove(rechargeKey)
            val at = System.currentTimeMillis() + remaining
            readyAt[player.uniqueId] = at
            scheduleReadyNotice(player, at)
        }
    })
}
