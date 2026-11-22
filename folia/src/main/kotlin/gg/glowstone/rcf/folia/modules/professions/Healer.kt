package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.Profession
import gg.glowstone.rcf.folia.framework.CivContext
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PotionSplashEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/** Perk is seconds of regeneration given to allies hit by the healer's healing splash potions. */
internal object Healer : Profession(
    id = "healer",
    displayName = "Healer",
    color = 0xC86AE3,
    perks = listOf(0.0, 3.0, 6.0, 9.0, 12.0, 15.0),
    perkLabel = "ally regeneration after healing splash",
) {
    override fun describePerk(rank: SkillLevel) = "${perk(rank).toInt()}s ally regeneration after healing splash"

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onSplash(event: PotionSplashEvent) {
            val shooter = event.potion.shooter as? Player ?: return
            val healing = event.potion.effects.any { it.type == PotionEffectType.INSTANT_HEALTH || it.type == PotionEffectType.REGENERATION }
            if (!healing) return
            val allies = event.affectedEntities.filterIsInstance<Player>()
                .filter { it.uniqueId != shooter.uniqueId && event.getIntensity(it) > 0.0 }
            if (allies.isEmpty()) return
            shooter.scheduler.run(ctx.plugin, {
                ctx.award(shooter, Healer, 5.0, cooldownMs = 2_000)
                val ticks = (ctx.perk(shooter, Healer) * 20).toInt().coerceIn(0, 600)
                if (ticks <= 0) return@run
                for (ally in allies) ally.scheduler.run(ctx.plugin, {
                    val existing = ally.getPotionEffect(PotionEffectType.REGENERATION)
                    if (existing == null || (existing.amplifier == 0 && existing.duration < ticks)) {
                        ally.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, ticks, 0, true, false, true))
                    }
                }, null)
            }, null)
        }
    })
}
