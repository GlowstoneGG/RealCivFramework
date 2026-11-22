package gg.glowstone.rcf.folia.modules.rankgates

import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GateRulesTest {
    private fun rules(text: String, warnings: MutableList<String> = mutableListOf()) =
        GateRules.parse(YamlConfiguration().apply { loadFromString(text.trimIndent()) }, warnings::add)

    @Test
    fun `stations list every profession that opens them, and bad entries are skipped with a warning`() {
        val warnings = mutableListOf<String>()
        val rules = rules(
            """
            mining:
              journeyman: [diamond_ore, not_a_block]
            stations:
              blacksmith:
                apprentice: [minecraft:anvil]
              builder:
                legendary: [anvil]
                master: [anvil]
            """,
            warnings,
        )
        assertEquals(SkillLevel.JOURNEYMAN, rules.miningRank(Material.DIAMOND_ORE))
        assertNull(rules.miningRank(Material.STONE))
        assertEquals(
            listOf(GateRules.Requirement("blacksmith", SkillLevel.APPRENTICE), GateRules.Requirement("builder", SkillLevel.MASTER)),
            rules.station(Material.ANVIL),
        )
        assertEquals(2, warnings.size)
    }

    @Test
    fun `enchant limits follow rank, grandmaster-only enchantments need Grandmaster, unlisted ranks are unlimited`() {
        val rules = rules(
            """
            enchanting:
              max-level: {novice: 0, journeyman: 1}
              grandmaster-only: [mending]
            """,
        )
        val sharpness = NamespacedKey.minecraft("sharpness")
        val mending = NamespacedKey.minecraft("mending")
        assertEquals(0, rules.maxLevel(SkillLevel.NOVICE, sharpness))
        assertEquals(1, rules.maxLevel(SkillLevel.JOURNEYMAN, sharpness))
        assertEquals(Int.MAX_VALUE, rules.maxLevel(SkillLevel.EXPERT, sharpness))
        assertEquals(0, rules.maxLevel(SkillLevel.MASTER, mending))
        assertEquals(Int.MAX_VALUE, rules.maxLevel(SkillLevel.GRANDMASTER, mending))
    }
}
