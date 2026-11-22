package gg.glowstone.rcf.folia.framework

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player

/** Fired on the player's owning region after gameplay awards XP through `CivContext.award`. Admin commands don't fire it. */
class ProfessionXpGainEvent(
    val player: Player,
    val professionId: String,
    val amount: Double,
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
