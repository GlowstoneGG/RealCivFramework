package gg.glowstone.rcf.folia.modules.instinct

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.ProfessionRankChangeEvent
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.professions.Hunter
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Players vanish from each other's locator bar; Hunters instead see nearby monsters that are breaking blocks.
 * Only transient attribute modifiers are used, so nothing is saved to player or entity data.
 */
internal object Instinct : Module("instinct") {
    override val reads = listOf(Hunter)

    private val key = civKey("instinct")
    private const val MARK_TICKS = 300L
    private const val MARK_RANGE = 16.0
    private const val DEDUPE_MS = 5_000L

    private val markedAt = ConcurrentHashMap<UUID, Long>()

    private fun receiveRange(rank: SkillLevel) = when (rank) {
        SkillLevel.NOVICE -> 0.0
        SkillLevel.APPRENTICE -> 8.0
        SkillLevel.JOURNEYMAN -> 12.0
        else -> 16.0
    }

    /** Sets [attribute] to exactly [value] with a transient modifier. Callers must own the entity's region. */
    private fun LivingEntity.setTransient(attribute: Attribute, value: Double) {
        val instance = getAttribute(attribute) ?: run { registerAttribute(attribute); getAttribute(attribute) } ?: return
        instance.removeModifier(key)
        instance.addTransientModifier(AttributeModifier(key, value - instance.baseValue, AttributeModifier.Operation.ADD_NUMBER))
    }

    private fun apply(ctx: CivContext, player: Player) {
        player.setTransient(Attribute.WAYPOINT_TRANSMIT_RANGE, 0.0)
        player.setTransient(Attribute.WAYPOINT_RECEIVE_RANGE, receiveRange(ctx.rank(player, Hunter)))
    }

    override fun start(ctx: CivContext) {
        for (player in ctx.plugin.server.onlinePlayers) player.scheduler.run(ctx.plugin, { apply(ctx, player) }, null)
    }

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onJoin(event: PlayerJoinEvent) = apply(ctx, event.player)

        @EventHandler
        fun onRankChange(event: ProfessionRankChangeEvent) = apply(ctx, event.player)

        // shortcut: vanilla mobs rarely break blocks, so this fires mostly on door-breaking zombies, endermen and ravagers; hook a mob-AI module's block-break start once one exists.
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBlockChange(event: EntityChangeBlockEvent) {
            val mob = event.entity as? Monster ?: return
            val now = System.currentTimeMillis()
            if (now - (markedAt[mob.uniqueId] ?: 0L) < DEDUPE_MS) return
            markedAt[mob.uniqueId] = now
            mob.setTransient(Attribute.WAYPOINT_TRANSMIT_RANGE, MARK_RANGE)
            mob.scheduler.runDelayed(ctx.plugin, {
                // A later trigger owns the mark and its own removal.
                if (markedAt.remove(mob.uniqueId, now)) mob.getAttribute(Attribute.WAYPOINT_TRANSMIT_RANGE)?.removeModifier(key)
            }, { markedAt.remove(mob.uniqueId, now) }, MARK_TICKS)
        }
    })
}
