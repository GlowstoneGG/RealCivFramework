package gg.glowstone.rcf.core.professions

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ProfessionProgressionTest {
    @Test
    fun `thresholds are cumulative and max rank stays capped`() {
        val progress = ProfessionProgression()
        val threshold = ProfessionProgression.threshold(SkillLevel.APPRENTICE)
        progress.set("miner", threshold - 1)
        assertEquals(SkillLevel.NOVICE, progress.rank("miner"))
        progress.add("miner", 1.0)
        assertEquals(SkillLevel.APPRENTICE, progress.rank("miner"))
        progress.set("miner", ProfessionProgression.threshold(SkillLevel.GRANDMASTER) + 1000)
        assertEquals(SkillLevel.GRANDMASTER, progress.rank("miner"))
    }

    @Test
    fun `share requirement can change a rank when another class gains XP`() {
        val progress = ProfessionProgression(mapOf("farmer" to mapOf(SkillLevel.APPRENTICE to 0.5)))
        val threshold = ProfessionProgression.threshold(SkillLevel.APPRENTICE)
        progress.set("farmer", threshold)
        assertEquals(SkillLevel.APPRENTICE, progress.rank("farmer"))
        progress.set("miner", threshold * 2)
        assertEquals(SkillLevel.NOVICE, progress.rank("farmer"))
        progress.set("miner", 0.0)
        assertEquals(SkillLevel.APPRENTICE, progress.rank("farmer"))
    }

    @Test
    fun `XP cannot become negative or nonfinite`() {
        val progress = ProfessionProgression()
        progress.add("healer", 10.0)
        assertEquals(-10.0, progress.add("healer", -100.0))
        assertEquals(0.0, progress.xp("healer"))
        assertFailsWith<IllegalArgumentException> { progress.add("healer", Double.NaN) }
        assertFailsWith<IllegalArgumentException> { progress.set("healer", Double.POSITIVE_INFINITY) }
    }
}
