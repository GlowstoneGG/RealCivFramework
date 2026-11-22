package gg.glowstone.rcf.folia.modules.revive

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.modules.downed.Downed
import gg.glowstone.rcf.folia.modules.professions.Healer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Healers revive downed players by channelling beside them, and bandage hurt players who are still standing. */
internal object Revive : Module("revive", dependsOn = listOf(Downed)) {
    override val reads = listOf(Healer)

    private const val REACH_SQUARED = 2.5 * 2.5
    private const val PERIOD = 5L
    private const val BANDAGE_COOLDOWN_MS = 7_500L

    // Just over two redraws, so the bar drops soon after the channel ends.
    private const val PROGRESS_MS = 600L

    private val channelling = ConcurrentHashMap.newKeySet<UUID>()
    private val lastBandage = ConcurrentHashMap<UUID, Long>()

    private fun channelTicks(level: Int) = 100 - 15 * (level - 1)

    private fun startRevive(healer: Player, target: Player, level: Int) {
        if (!channelling.add(healer.uniqueId)) return
        val total = channelTicks(level)
        var elapsed = 0
        healer.scheduler.runAtFixedRate(ctx.plugin, { task ->
            val inReach = Bukkit.isOwnedByCurrentRegion(target) && target.world == healer.world &&
                target.location.distanceSquared(healer.location) <= REACH_SQUARED
            if (!healer.isSneaking || !inReach || !Downed.isDowned(target.uniqueId)) {
                channelling.remove(healer.uniqueId)
                ctx.actionBar.show(healer, "revive", Component.text("Revive interrupted", NamedTextColor.RED))
                task.cancel()
                return@runAtFixedRate
            }
            elapsed += PERIOD.toInt()
            if (elapsed < total) {
                val progress = Component.text("Reviving ${target.name} ${progressBar(elapsed, total)}", NamedTextColor.GREEN)
                ctx.actionBar.show(healer, "revive", progress, ttlMs = PROGRESS_MS)
                ctx.actionBar.show(target, "revive", Component.text("${healer.name} is reviving you ${progressBar(elapsed, total)}", NamedTextColor.GREEN), ttlMs = PROGRESS_MS)
                return@runAtFixedRate
            }
            channelling.remove(healer.uniqueId)
            task.cancel()
            target.scheduler.run(ctx.plugin, { Downed.revive(target, level * 2.0) }, null)
            ctx.award(healer, Healer, 20.0)
        }, { channelling.remove(healer.uniqueId) }, 1L, PERIOD)
    }

    private fun bandage(healer: Player, target: Player, level: Int) {
        val now = System.currentTimeMillis()
        if (now - (lastBandage[healer.uniqueId] ?: 0L) < BANDAGE_COOLDOWN_MS) return
        if (!Bukkit.isOwnedByCurrentRegion(target)) return
        val max = target.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        val restored = (4.0 + 1.5 * (level - 1)).coerceAtMost(max - target.health)
        if (restored <= 0.0) return
        lastBandage[healer.uniqueId] = now
        target.health += restored
        ctx.actionBar.show(target, "revive", Component.text("${healer.name} bandaged you", NamedTextColor.GREEN))
        ctx.award(healer, Healer, 5.0)
    }

    private fun progressBar(elapsed: Int, total: Int): String {
        val filled = (10 * elapsed / total).coerceIn(0, 10)
        return "▮".repeat(filled) + "▯".repeat(10 - filled)
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(ignoreCancelled = true)
        fun onInteract(event: PlayerInteractEntityEvent) {
            val healer = event.player
            val target = event.rightClicked as? Player ?: return
            if (event.hand != EquipmentSlot.HAND || !healer.isSneaking || !healer.inventory.itemInMainHand.type.isAir) return
            val rank = ctx.rank(healer, Healer)
            if (rank == SkillLevel.NOVICE) return
            event.isCancelled = true
            if (Downed.isDowned(target.uniqueId)) startRevive(healer, target, rank.ordinal) else bandage(healer, target, rank.ordinal)
        }
    })
}
