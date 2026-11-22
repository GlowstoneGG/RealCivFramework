package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

internal sealed interface RecipeRule {
    /** Nobody can craft it. */
    data object Blocked : RecipeRule

    data class Needs(val professionId: String, val rank: SkillLevel) : RecipeRule
}

/**
 * Reads `recipes.yml`: which rank of which profession unlocks each recipe, and which recipes nobody can craft.
 * Entries are recipe keys or item ids; an item id covers every vanilla recipe that makes that item.
 * Professions missing from the file are added with the gates their code declares.
 */
internal object RecipeConfig {
    private val header = listOf(
        "Recipe gates. Under each profession, list recipe keys or item ids per rank (apprentice ... grandmaster).",
        "An item id such as minecraft:bow gates every vanilla recipe that makes it. 'blocked' recipes can't be crafted by anyone.",
        "Professions missing here are added with their defaults; set one to {} to gate nothing. Changes apply after a restart.",
    )

    fun load(file: File, professions: List<Profession>, warn: (String) -> Unit): Map<NamespacedKey, RecipeRule> {
        val yaml = YamlConfiguration.loadConfiguration(file)
        var changed = !file.exists()
        if (!yaml.isList("blocked")) {
            yaml.set("blocked", emptyList<String>())
            changed = true
        }
        for (profession in professions) {
            val path = "professions.${profession.id}"
            if (yaml.isConfigurationSection(path)) continue
            val section = yaml.createSection(path)
            for ((rank, keys) in profession.recipes.entries.groupBy({ it.value }, { it.key.asString() })) {
                section.set(rank.name.lowercase(), keys)
            }
            changed = true
        }
        if (changed) {
            yaml.options().setHeader(header)
            yaml.save(file)
        }
        return parse(yaml, professions.map { it.id }.toSet(), warn)
    }

    fun parse(yaml: ConfigurationSection, professionIds: Set<String>, warn: (String) -> Unit): Map<NamespacedKey, RecipeRule> {
        val rules = mutableMapOf<NamespacedKey, RecipeRule>()
        fun put(raw: String, rule: RecipeRule) {
            val key = NamespacedKey.fromString(raw.trim().lowercase())
            if (key == null) {
                warn("recipes.yml: '$raw' isn't a valid recipe key or item id; ignoring it")
                return
            }
            require(key !in rules) { "recipes.yml lists $key more than once" }
            rules[key] = rule
        }
        for (raw in yaml.getStringList("blocked")) put(raw, RecipeRule.Blocked)
        val professions = yaml.getConfigurationSection("professions") ?: return rules
        for (id in professions.getKeys(false)) {
            if (id !in professionIds) {
                warn("recipes.yml lists profession '$id', which isn't loaded; ignoring it")
                continue
            }
            val ranks = professions.getConfigurationSection(id) ?: continue
            for (rankName in ranks.getKeys(false)) {
                val rank = SkillLevel.entries.firstOrNull { it.name.equals(rankName, ignoreCase = true) }
                if (rank == null) {
                    warn("recipes.yml: '$rankName' under $id isn't a rank; ignoring it")
                    continue
                }
                for (raw in ranks.getStringList(rankName)) put(raw, RecipeRule.Needs(id, rank))
            }
        }
        return rules
    }
}
