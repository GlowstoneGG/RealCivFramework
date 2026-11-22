package gg.glowstone.rcf.folia.modules.hunterranged

import kotlin.math.exp
import kotlin.math.pow

/** Arrow damage by distance: weak up close, a peak inside a "golden" window that widens with Hunter level, falling off beyond it. */
internal object RangedCurve {
    private const val PEAK = 15.0

    fun damage(crossbow: Boolean, level: Int, distance: Double): Double {
        val min = if (crossbow) 5.0 else 4.0
        val nonGoldenMax = 8.0 + level
        val start = if (crossbow) 55 - 0.6 * level else 44 - 1.6 * level
        val end = if (crossbow) 57 + 1.8 * level else 46 + 1.2 * level
        return when {
            distance < start -> min + (nonGoldenMax - min) * (distance / start).pow(3)
            distance <= end -> PEAK
            else -> min + (nonGoldenMax - min) * exp(-((distance - end) / 18).pow(2.25))
        }
    }
}
