package gg.glowstone.rcf.folia.modules.survival

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType

/** After respawning at a bed or anchor, it rests for a while; dying again in that time sends you to world spawn. */
internal object BedCooldown : Module("bed-cooldown") {
    private const val COOLDOWN_MS = 10 * 60_000L
    private val lastBedRespawnKey = civKey("last_bed_respawn")

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH)
        fun onRespawn(event: PlayerRespawnEvent) {
            if (event.respawnReason != PlayerRespawnEvent.RespawnReason.DEATH || !(event.isBedSpawn || event.isAnchorSpawn)) return
            val player = event.player
            val now = System.currentTimeMillis()
            val last = player.persistentDataContainer.get(lastBedRespawnKey, PersistentDataType.LONG)
            if (last == null || now - last >= COOLDOWN_MS) {
                player.persistentDataContainer.set(lastBedRespawnKey, PersistentDataType.LONG, now)
                return
            }
            event.respawnLocation = Bukkit.getWorlds().first().spawnLocation.add(0.5, 0.0, 0.5)
            val minutes = (COOLDOWN_MS - (now - last) + 59_999) / 60_000
            player.scheduler.run(ctx.plugin, {
                player.sendMessage("Your respawn point is still resting, so you woke at world spawn. It's ready again in $minutes min.")
            }, null)
        }
    })
}
