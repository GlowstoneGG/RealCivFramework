package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.entity.Player

/** Fired on the player's owning region after stored XP changes a rank. */
class ProfessionRankChangeEvent(
    val player: Player,
    val professionId: String,
    val previous: SkillLevel,
    val current: SkillLevel,
) : Event() {
    override fun getHandlers(): HandlerList = handlerList

    companion object {
        @JvmStatic
        val handlerList = HandlerList()
    }
}
