package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.ProfessionProgression
import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.NamespacedKey

/** The fixed set of professions loaded at enable, and the recipe rules from `recipes.yml`. */
internal class ProfessionCatalog(
    professions: List<Profession>,
    recipeRules: Map<NamespacedKey, RecipeRule> = defaultRules(professions),
) {
    val professions: List<Profession> = professions.toList()
    private val byId = professions.associateBy { it.id }
        .also { require(it.size == professions.size) { "Duplicate profession id" } }

    private val rules = recipeRules.toMap()

    private val shares: Map<String, Map<SkillLevel, Double>> = professions.associate { profession ->
        profession.id to SkillLevel.entries.drop(1).associateWith(profession::minimumShare)
    }

    operator fun get(id: String): Profession? = byId[id.lowercase()]

    fun ids(): List<String> = professions.map { it.id }

    fun newProgression() = ProfessionProgression(shares)

    /** The rule for a recipe: its own key first, then, for vanilla recipes, the item it makes. */
    fun rule(recipe: NamespacedKey, result: NamespacedKey?): RecipeRule? =
        rules[recipe] ?: result?.takeIf { recipe.namespace == NamespacedKey.MINECRAFT }?.let(rules::get)

    fun allows(rule: RecipeRule?, progress: ProfessionProgression): Boolean = when (rule) {
        null -> true
        RecipeRule.Blocked -> false
        is RecipeRule.Needs -> rule.professionId !in byId || progress.rank(rule.professionId).ordinal >= rule.rank.ordinal
    }

    /** Recipes and items this profession unlocks on reaching [next] that the player can't craft yet. */
    fun nextUnlocks(progress: ProfessionProgression, professionId: String, next: SkillLevel): List<NamespacedKey> =
        rules.filter { (_, rule) ->
            rule is RecipeRule.Needs && rule.professionId == professionId && !allows(rule, progress) && next.ordinal >= rule.rank.ordinal
        }.keys.toList()

    companion object {
        /** The gates professions declare in code, as `recipes.yml` starts out. */
        fun defaultRules(professions: List<Profession>): Map<NamespacedKey, RecipeRule> = buildMap {
            for (profession in professions) {
                for ((key, rank) in profession.recipes) {
                    require(key !in this) { "Recipe $key has more than one profession requirement" }
                    put(key, RecipeRule.Needs(profession.id, rank))
                }
            }
        }
    }
}
