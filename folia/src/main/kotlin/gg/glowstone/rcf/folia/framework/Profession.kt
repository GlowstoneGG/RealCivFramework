package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.NamespacedKey
import org.bukkit.event.Listener
import kotlin.math.roundToInt

/**
 * One profession: its identity, a perk value per rank, recipe gates, and the listeners that award XP and apply perks.
 * Subclass it in `content/` and add it to `DefaultContent.professions`.
 */
internal abstract class Profession(
    /** Stable storage id. Changing it orphans stored XP. */
    val id: String,
    val displayName: String,
    val color: Int,
    private val perks: List<Double>,
    private val perkLabel: String,
) {
    init {
        require(id.matches(Regex("[a-z0-9_]+"))) { "Profession id must be lowercase [a-z0-9_]: $id" }
        require(perks.size == SkillLevel.entries.size) { "$id needs one perk value per rank" }
        require(perks.all { it.isFinite() && it >= 0.0 }) { "$id perks must be finite and nonnegative" }
    }

    /** Vanilla or custom recipes this profession gates, with the rank that unlocks each. */
    open val recipes: Map<NamespacedKey, SkillLevel> = emptyMap()

    /** Share of a player's total XP this profession needs before reaching [rank]. */
    open fun minimumShare(rank: SkillLevel): Double = 0.0

    fun perk(rank: SkillLevel): Double = perks[rank.ordinal]

    open fun describePerk(rank: SkillLevel): String = "${(perk(rank) * 100).roundToInt()}% $perkLabel"

    /** Listeners are registered once at enable. They run on Folia region threads, so use the context's schedulers. */
    open fun listeners(ctx: CivContext): List<Listener> = emptyList()
}

internal fun minecraftKey(value: String): NamespacedKey = NamespacedKey.minecraft(value)
