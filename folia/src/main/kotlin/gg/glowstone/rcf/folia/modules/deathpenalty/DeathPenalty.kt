package gg.glowstone.rcf.folia.modules.deathpenalty

import gg.glowstone.rcf.folia.framework.Module
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent

/** Hardcore progression: a real death wipes all profession XP. Off by default. */
internal object DeathPenalty : Module("death-penalty", enabledByDefault = false) {
    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onDeath(event: PlayerDeathEvent) {
            ctx.professions.clear(event.player)
        }
    })
}
