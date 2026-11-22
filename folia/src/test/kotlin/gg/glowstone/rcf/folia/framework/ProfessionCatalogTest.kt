package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.ProfessionProgression
import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.NamespacedKey
import org.bukkit.configuration.file.YamlConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfessionCatalogTest {
    private class Gated(id: String, override val recipes: Map<NamespacedKey, SkillLevel>) :
        Profession(id, id, 0, List(SkillLevel.entries.size) { 0.0 }, "")

    private val bow = minecraftKey("bow")
    private val crossbow = minecraftKey("crossbow")

    private fun ProfessionCatalog.canCraft(key: NamespacedKey, progress: ProfessionProgression, result: NamespacedKey? = null) =
        allows(rule(key, result), progress)

    @Test
    fun `recipes unlock at their rank and nextUnlocks lists only the upcoming ones`() {
        val catalog = ProfessionCatalog(listOf(Gated("hunter", mapOf(bow to SkillLevel.APPRENTICE, crossbow to SkillLevel.JOURNEYMAN))))
        val progress = catalog.newProgression()
        assertFalse(catalog.canCraft(bow, progress))
        assertTrue(catalog.canCraft(minecraftKey("bread"), progress))
        assertEquals(listOf(bow), catalog.nextUnlocks(progress, "hunter", SkillLevel.APPRENTICE))

        progress.set("hunter", ProfessionProgression.threshold(SkillLevel.APPRENTICE))
        assertTrue(catalog.canCraft(bow, progress))
        assertFalse(catalog.canCraft(crossbow, progress))
        assertEquals(listOf(crossbow), catalog.nextUnlocks(progress, "hunter", SkillLevel.JOURNEYMAN))
    }

    @Test
    fun `a recipe can only be gated by one profession`() {
        assertFailsWith<IllegalArgumentException> {
            ProfessionCatalog(listOf(Gated("a", mapOf(bow to SkillLevel.APPRENTICE)), Gated("b", mapOf(bow to SkillLevel.MASTER))))
        }
    }

    @Test
    fun `an item id gates vanilla recipes that make it, but not other plugins' recipes`() {
        val rules = mapOf(minecraftKey("white_bed") to RecipeRule.Blocked)
        val catalog = ProfessionCatalog(listOf(Gated("hunter", emptyMap())), rules)
        val progress = catalog.newProgression()
        assertFalse(catalog.canCraft(minecraftKey("dye_white_bed"), progress, result = minecraftKey("white_bed")))
        assertTrue(catalog.canCraft(NamespacedKey("other", "white_bed_alt"), progress, result = minecraftKey("white_bed")))
        assertNull(catalog.rule(minecraftKey("bread"), minecraftKey("bread")))
    }

    @Test
    fun `recipes yml parses ranks and blocked entries and skips what it can't read`() {
        val yaml = YamlConfiguration().apply {
            loadFromString(
                """
                blocked: [minecraft:anvil]
                professions:
                  hunter:
                    apprentice: [bow, minecraft:fishing_rod]
                    legendary: [crossbow]
                  wizard:
                    apprentice: [stick]
                """.trimIndent(),
            )
        }
        val warnings = mutableListOf<String>()
        val rules = RecipeConfig.parse(yaml, setOf("hunter"), warnings::add)
        assertEquals(
            mapOf(
                minecraftKey("anvil") to RecipeRule.Blocked,
                bow to RecipeRule.Needs("hunter", SkillLevel.APPRENTICE),
                minecraftKey("fishing_rod") to RecipeRule.Needs("hunter", SkillLevel.APPRENTICE),
            ),
            rules,
        )
        assertEquals(2, warnings.size)
    }
}
