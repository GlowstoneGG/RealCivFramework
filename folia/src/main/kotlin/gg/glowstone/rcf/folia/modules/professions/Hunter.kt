package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.minecraftKey
import org.bukkit.attribute.Attribute
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerFishEvent
import kotlin.math.floor
import kotlin.math.roundToInt

/** The fighter: kill XP scales with the victim's health, and kills drop more loot at higher ranks. */
internal object Hunter : Profession(
    id = "hunter",
    displayName = "Hunter",
    color = 0xE03E2D,
    perks = listOf(0.0, 0.2, 0.4, 0.6, 0.8, 1.0),
    perkLabel = "more mob drops",
) {
    override val recipes = linkedMapOf(
        minecraftKey("fishing_rod") to SkillLevel.APPRENTICE,
        minecraftKey("bow") to SkillLevel.APPRENTICE,
        minecraftKey("crossbow") to SkillLevel.JOURNEYMAN,
    )

    override fun describePerk(rank: SkillLevel) = "+${(perk(rank) * 100).roundToInt()}% mob drops"

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onKill(event: EntityDeathEvent) {
            val victim = event.entity
            if (victim is Player || victim is ArmorStand || victim is Mannequin) return
            val killer = victim.killer ?: return
            // The rank cache is safe to read here, on the victim's region.
            val bonus = ctx.perk(killer, Hunter)
            if (bonus > 0) {
                val extra = event.drops.mapNotNull { stack ->
                    val amount = floor(stack.amount * bonus).toInt() + if (roll(stack.amount * bonus % 1.0)) 1 else 0
                    if (amount > 0) stack.clone().apply { this.amount = amount } else null
                }
                event.drops += extra
            }
            val xp = victim.getAttribute(Attribute.MAX_HEALTH)?.value ?: return
            killer.scheduler.run(ctx.plugin, { ctx.award(killer, Hunter, xp, cooldownMs = 1_000, key = "hunter_kill") }, null)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onFish(event: PlayerFishEvent) {
            if (event.state == PlayerFishEvent.State.CAUGHT_FISH) ctx.award(event.player, Hunter, 3.0)
        }
    })
}
