package gg.glowstone.rcf.folia.modules.survival

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.block.data.Lightable
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Sleeping near a lit campfire slowly heals, up to a daily cap. */
internal object CampfireRest : Module("campfire-rest") {
    private const val INTERVAL_TICKS = 900L
    private const val HEAL = 1.0
    private const val DAILY_CAP = 5
    private const val RADIUS = 4
    private const val HEIGHT = 2

    private val dayKey = civKey("rest_day")
    private val healedKey = civKey("rest_healed")
    private val resting = ConcurrentHashMap.newKeySet<UUID>()

    private fun Player.nearLitCampfire(): Boolean {
        val origin = location.block
        for (dx in -RADIUS..RADIUS) for (dy in -HEIGHT..HEIGHT) for (dz in -RADIUS..RADIUS) {
            val block = origin.getRelative(dx, dy, dz)
            if (block.type != Material.CAMPFIRE && block.type != Material.SOUL_CAMPFIRE) continue
            if ((block.blockData as? Lightable)?.isLit == true) return true
        }
        return false
    }

    private fun rest(player: Player) {
        if (!player.nearLitCampfire()) {
            player.sendActionBar("It's chilly without a fire")
            return
        }
        val max = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: return
        if (player.health >= max) return
        val pdc = player.persistentDataContainer
        val day = player.world.fullTime / 24_000
        val healed = if (pdc.get(dayKey, PersistentDataType.LONG) == day) pdc.get(healedKey, PersistentDataType.INTEGER) ?: 0 else 0
        if (healed >= DAILY_CAP) return
        player.heal(HEAL)
        pdc.set(dayKey, PersistentDataType.LONG, day)
        pdc.set(healedKey, PersistentDataType.INTEGER, healed + 1)
        player.sendActionBar("Resting by the fire")
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onSleep(event: PlayerBedEnterEvent) {
            if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
            val player = event.player
            if (!resting.add(player.uniqueId)) return
            player.scheduler.runAtFixedRate(ctx.plugin, { task ->
                if (player.isSleeping) rest(player) else {
                    resting.remove(player.uniqueId)
                    task.cancel()
                }
            }, { resting.remove(player.uniqueId) }, INTERVAL_TICKS, INTERVAL_TICKS)
        }
    })
}
