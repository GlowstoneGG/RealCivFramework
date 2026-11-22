package gg.glowstone.rcf.core.professions

import kotlin.math.floor
import kotlin.math.pow

/** Cumulative XP per profession id. A rank requires both its XP threshold and its share of all profession XP. */
class ProfessionProgression(
    private val minimumShare: Map<String, Map<SkillLevel, Double>> = emptyMap(),
) {
    private val xp = HashMap<String, Double>()

    init {
        minimumShare.values.forEach { ranks ->
            ranks.values.forEach { require(it.isFinite() && it in 0.0..1.0) }
        }
    }

    fun xp(professionId: String): Double = xp[professionId] ?: 0.0

    fun totalXp(): Double = xp.values.sum()

    fun share(professionId: String): Double = if (totalXp() == 0.0) 0.0 else xp(professionId) / totalXp()

    fun rank(professionId: String): SkillLevel {
        var result = SkillLevel.NOVICE
        for (candidate in SkillLevel.entries.drop(1)) {
            if (xp(professionId) < threshold(candidate) || share(professionId) < (minimumShare[professionId]?.get(candidate) ?: 0.0)) break
            result = candidate
        }
        return result
    }

    /** Returns the actual XP change after clamping at zero. */
    fun add(professionId: String, amount: Double): Double {
        require(amount.isFinite()) { "XP change must be finite" }
        val before = xp(professionId)
        val after = (before + amount).coerceAtLeast(0.0)
        require(after.isFinite()) { "XP total must be finite" }
        xp[professionId] = after
        return after - before
    }

    fun set(professionId: String, amount: Double) {
        require(amount.isFinite() && amount >= 0.0) { "XP must be finite and nonnegative" }
        xp[professionId] = amount
    }

    companion object {
        private val thresholds = List(SkillLevel.entries.size) { index ->
            if (index == 0) 0.0 else floor(1.8 * (25 * index * index + 5 * index + 200 * 2.45.pow(index)) - 300)
        }

        fun threshold(rank: SkillLevel): Double = thresholds[rank.ordinal]
    }
}
