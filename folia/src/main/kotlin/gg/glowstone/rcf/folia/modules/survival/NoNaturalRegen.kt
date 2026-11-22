package gg.glowstone.rcf.folia.modules.survival

import gg.glowstone.rcf.folia.framework.Module
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason

/** Players don't heal from a full food bar or peaceful regen. Gamerules are left alone. */
internal object NoNaturalRegen : Module("no-natural-regen") {
    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onRegen(event: EntityRegainHealthEvent) {
            if (event.entity is Player && (event.regainReason == RegainReason.SATIATED || event.regainReason == RegainReason.REGEN)) {
                event.isCancelled = true
            }
        }
    })
}
