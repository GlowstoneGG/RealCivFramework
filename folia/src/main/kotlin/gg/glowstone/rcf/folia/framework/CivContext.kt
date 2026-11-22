package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.blocks.RecentBlockActions
import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.RealCivFrameworkPlugin
import org.bukkit.NamespacedKey
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What modules and professions get: storage, cached ranks, XP awards with cooldowns, and placed-block tracking. */
internal class CivContext(
    val plugin: RealCivFrameworkPlugin,
    val professions: ProfessionService,
    val blocks: RecentBlockActions,
) : Listener {
    private data class CooldownKey(val player: UUID, val key: String)

    private val lastAward = ConcurrentHashMap<CooldownKey, Long>()

    /** Cached, so it is safe from any thread. Unloaded professions and offline players read as Novice. */
    fun rank(player: UUID, profession: Profession): SkillLevel = professions.cachedRank(player, profession)

    fun rank(player: Player, profession: Profession): SkillLevel = rank(player.uniqueId, profession)

    fun perk(player: Player, profession: Profession): Double = profession.perk(rank(player, profession))

    /**
     * Awards XP unless [key] was awarded to this player within [cooldownMs].
     * Callers must be on the player's owning region.
     */
    fun award(player: Player, profession: Profession, amount: Double, cooldownMs: Long = 0, key: String = profession.id) {
        if (amount <= 0.0) return
        if (cooldownMs > 0) {
            val cooldownKey = CooldownKey(player.uniqueId, key)
            val now = System.currentTimeMillis()
            if (now - (lastAward[cooldownKey] ?: 0L) < cooldownMs) return
            lastAward[cooldownKey] = now
        }
        val applied = professions.add(player, profession, amount)
        if (applied > 0) plugin.server.pluginManager.callEvent(ProfessionXpGainEvent(player, profession.id, applied))
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        professions.refresh(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        lastAward.keys.removeIf { it.player == event.player.uniqueId }
        professions.forget(event.player.uniqueId)
    }
}

internal fun Block.position() = RecentBlockActions.Position(world.uid, x, y, z)

/** Keys under the fixed `rcf` namespace, so forks that rename the plugin keep their data. */
internal fun civKey(name: String) = NamespacedKey(ProfessionService.NAMESPACE, name)
