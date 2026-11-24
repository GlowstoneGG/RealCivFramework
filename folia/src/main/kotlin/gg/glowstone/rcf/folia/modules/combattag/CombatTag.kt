package gg.glowstone.rcf.folia.modules.combattag

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.downed.Downed
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Hitting or being hit by another player tags both for 60 seconds. */
internal object CombatTag : Module("combat-tag") {
    private const val DURATION_MS = 60_000L
    private val expiresKey = civKey("combat_expires_at")

    private val expiresAt = ConcurrentHashMap<UUID, Long>()
    private val bars = ConcurrentHashMap<UUID, BossBar>()

    fun isTagged(player: UUID): Boolean = (expiresAt[player] ?: 0L) > System.currentTimeMillis()

    /** Callers must be on the player's region. */
    private fun tag(player: Player, until: Long = System.currentTimeMillis() + DURATION_MS) {
        expiresAt[player.uniqueId] = until
        player.persistentDataContainer.set(expiresKey, PersistentDataType.LONG, until)
        if (bars.containsKey(player.uniqueId)) return
        val bar = BossBar.bossBar(Component.text("In combat", NamedTextColor.RED), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS)
        bars[player.uniqueId] = bar
        player.showBossBar(bar)
        player.scheduler.runAtFixedRate(ctx.plugin, { task ->
            val left = (expiresAt[player.uniqueId] ?: 0L) - System.currentTimeMillis()
            if (left > 0) {
                bar.name(Component.text("In combat: ${(left + 999) / 1000}s", NamedTextColor.RED))
                bar.progress((left / DURATION_MS.toFloat()).coerceIn(0f, 1f))
                return@runAtFixedRate
            }
            task.cancel()
            player.hideBossBar(bar)
            bars.remove(player.uniqueId, bar)
            expiresAt.remove(player.uniqueId)
            player.persistentDataContainer.remove(expiresKey)
        }, { bars.remove(player.uniqueId, bar) }, 1L, 20L)
    }

    override fun listeners() = listOf(object : Listener {
        // Hits that down or finish a player are cancelled by Downed but still count as combat.
        @EventHandler(priority = EventPriority.MONITOR)
        fun onHit(event: EntityDamageByEntityEvent) {
            if (event.isCancelled && !Downed.replacedHit(event)) return
            val victim = event.entity as? Player ?: return
            val attacker = event.damager as? Player ?: (event.damager as? Projectile)?.shooter as? Player ?: return
            if (attacker == victim) return
            tag(victim)
            attacker.scheduler.run(ctx.plugin, { tag(attacker) }, null)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val until = event.player.persistentDataContainer.get(expiresKey, PersistentDataType.LONG) ?: return
            if (until > System.currentTimeMillis()) tag(event.player, until)
            else event.player.persistentDataContainer.remove(expiresKey)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            expiresAt.remove(event.player.uniqueId)
            bars.remove(event.player.uniqueId)
        }
    })
}
