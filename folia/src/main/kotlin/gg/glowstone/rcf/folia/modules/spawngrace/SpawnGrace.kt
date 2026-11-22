package gg.glowstone.rcf.folia.modules.spawngrace

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.ProfessionXpGainEvent
import gg.glowstone.rcf.folia.framework.civKey
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * New players, and players respawning after death, get 3½ minutes in which mobs ignore them and only players can hurt them.
 * Fighting, taking non-fall damage, or earning profession XP ends it early.
 */
internal object SpawnGrace : Module("spawn-grace") {
    private const val DURATION_MS = 210_000L
    private val endsKey = civKey("spawn_grace_ends_at")

    private val endsAt = ConcurrentHashMap<UUID, Long>()
    private val bars = ConcurrentHashMap<UUID, BossBar>()

    fun hasGrace(player: UUID): Boolean = (endsAt[player] ?: 0L) > System.currentTimeMillis()

    /** Callers must be on the player's region. */
    private fun grant(player: Player, until: Long, announce: Boolean) {
        endsAt[player.uniqueId] = until
        player.persistentDataContainer.set(endsKey, PersistentDataType.LONG, until)
        if (announce) {
            player.showTitle(Title.title(
                Component.text("Spawn grace", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Mobs leave you alone for 3m 30s. Fighting or earning XP ends it.", NamedTextColor.LIGHT_PURPLE),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(500)),
            ))
        }
        if (bars.containsKey(player.uniqueId)) return
        val bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS)
        bars[player.uniqueId] = bar
        player.showBossBar(bar)
        player.scheduler.runAtFixedRate(ctx.plugin, { task ->
            if (bars[player.uniqueId] !== bar) {
                task.cancel()
                return@runAtFixedRate
            }
            val left = (endsAt[player.uniqueId] ?: 0L) - System.currentTimeMillis()
            if (left > 0) {
                val seconds = (left + 999) / 1000
                bar.name(Component.text("Spawn grace: %d:%02d".format(seconds / 60, seconds % 60), NamedTextColor.LIGHT_PURPLE))
                bar.progress((left / DURATION_MS.toFloat()).coerceIn(0f, 1f))
                return@runAtFixedRate
            }
            task.cancel()
            end(player)
        }, { bars.remove(player.uniqueId, bar) }, 1L, 20L)
    }

    /** Callers must be on the player's region. */
    private fun end(player: Player) {
        endsAt.remove(player.uniqueId)
        player.persistentDataContainer.remove(endsKey)
        bars.remove(player.uniqueId)?.let(player::hideBossBar)
    }

    /** The player behind a hit: the attacker, a projectile's shooter, or whoever caused it (TNT they lit). */
    private fun EntityDamageByEntityEvent.attackingPlayer(): Player? =
        damager as? Player ?: (damager as? Projectile)?.shooter as? Player ?: damageSource.causingEntity as? Player

    override fun listeners() = listOf(object : Listener {
        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val player = event.player
            val until = player.persistentDataContainer.get(endsKey, PersistentDataType.LONG)
            when {
                until != null && until > System.currentTimeMillis() -> grant(player, until, announce = false)
                !player.hasPlayedBefore() -> grant(player, System.currentTimeMillis() + DURATION_MS, announce = true)
                until != null -> player.persistentDataContainer.remove(endsKey)
            }
        }

        // Leaving the End also fires a respawn; only death grants grace.
        @EventHandler(priority = EventPriority.MONITOR)
        fun onRespawn(event: PlayerRespawnEvent) {
            if (event.respawnReason != PlayerRespawnEvent.RespawnReason.DEATH) return
            val player = event.player
            player.scheduler.run(ctx.plugin, { grant(player, System.currentTimeMillis() + DURATION_MS, announce = true) }, null)
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        fun onTarget(event: EntityTargetLivingEntityEvent) {
            val target = event.target as? Player ?: return
            if (hasGrace(target.uniqueId)) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        fun onHurt(event: EntityDamageByEntityEvent) {
            val victim = event.entity as? Player ?: return
            if (!hasGrace(victim.uniqueId)) return
            if (event.attackingPlayer() != null) {
                end(victim)
                return
            }
            event.isCancelled = true
            val mob = event.damageSource.causingEntity as? Mob ?: return
            mob.scheduler.run(ctx.plugin, { if (mob.target?.uniqueId == victim.uniqueId) mob.target = null }, null)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onAttack(event: EntityDamageByEntityEvent) {
            val attacker = event.attackingPlayer() ?: return
            if (event.finalDamage > 0 && hasGrace(attacker.uniqueId)) attacker.scheduler.run(ctx.plugin, { end(attacker) }, null)
        }

        // Covers hits that deal no damage, such as snowballs.
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onProjectile(event: ProjectileHitEvent) {
            val victim = event.hitEntity as? Player ?: return
            if (event.entity.shooter is Player && hasGrace(victim.uniqueId)) end(victim)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onEnvironment(event: EntityDamageEvent) {
            if (event is EntityDamageByEntityEvent || event.cause == EntityDamageEvent.DamageCause.FALL) return
            val player = event.entity as? Player ?: return
            if (event.finalDamage > 0 && hasGrace(player.uniqueId)) end(player)
        }

        @EventHandler
        fun onXp(event: ProfessionXpGainEvent) {
            if (hasGrace(event.player.uniqueId)) end(event.player)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onDeath(event: PlayerDeathEvent) {
            if (endsAt.containsKey(event.player.uniqueId)) end(event.player)
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            endsAt.remove(event.player.uniqueId)
            bars.remove(event.player.uniqueId)
        }
    })
}
