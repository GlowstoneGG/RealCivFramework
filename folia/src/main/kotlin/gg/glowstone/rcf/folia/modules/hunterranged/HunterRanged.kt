package gg.glowstone.rcf.folia.modules.hunterranged

import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.professions.Hunter
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.persistence.PersistentDataType
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.sqrt

/** Player arrows deal damage by distance travelled; Hunters shoot faster arrows and sometimes keep them. */
internal object HunterRanged : Module("hunter-ranged") {
    override val reads = listOf(Hunter)

    // [originX, originY, originZ, crossbow (0/1), hunterLevel, force, powerLevel]
    private val shotKey = civKey("hunter_shot")
    private val shotType = PersistentDataType.LIST.doubles()

    private fun conserveChance(level: Int) = when (level) {
        2 -> 0.10
        3, 4 -> 0.25
        5 -> 0.50
        else -> 0.0
    }

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onShoot(event: EntityShootBowEvent) {
            val player = event.entity as? Player ?: return
            val bow = event.bow ?: return
            if (bow.type != Material.BOW && bow.type != Material.CROSSBOW) return
            val arrow = event.projectile as? AbstractArrow ?: return
            val crossbow = bow.type == Material.CROSSBOW
            val level = ctx.rank(player, Hunter).ordinal
            val force = if (crossbow) 1.0 else event.force.toDouble()
            val origin = player.location
            arrow.persistentDataContainer.set(
                shotKey, shotType,
                listOf(origin.x, origin.y, origin.z, if (crossbow) 1.0 else 0.0, level.toDouble(), force, bow.getEnchantmentLevel(Enchantment.POWER).toDouble()),
            )
            arrow.velocity = arrow.velocity.multiply(1 + 0.12 * level)
            if (!crossbow && ThreadLocalRandom.current().nextDouble() < conserveChance(level)) {
                event.setConsumeItem(false)
                // The arrow wasn't paid for, so it can't be picked back up.
                arrow.pickupStatus = AbstractArrow.PickupStatus.CREATIVE_ONLY
                player.updateInventory()
            }
        }

        // Before downed (HIGH), which judges lethality on the final arrow damage.
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        fun onHit(event: EntityDamageByEntityEvent) {
            val arrow = event.damager as? AbstractArrow ?: return
            val shot = arrow.persistentDataContainer.get(shotKey, shotType)?.takeIf { it.size == 7 } ?: return
            val hit = arrow.location
            val distance = sqrt((hit.x - shot[0]).let { it * it } + (hit.y - shot[1]).let { it * it } + (hit.z - shot[2]).let { it * it })
            event.damage = RangedCurve.damage(shot[3] == 1.0, shot[4].toInt(), distance) * shot[5] * (1 + 0.2 * shot[6])
        }
    })
}
