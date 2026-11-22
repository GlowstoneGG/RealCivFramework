package gg.glowstone.rcf.folia.modules.rankgates

import gg.glowstone.rcf.core.professions.SkillLevel
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection

/** The rules in `rank-gates.yml`. */
internal class GateRules(
    private val mining: Map<Material, SkillLevel>,
    private val stations: Map<Material, List<Requirement>>,
    private val enchantMax: Map<SkillLevel, Int>,
    private val grandmasterOnly: Set<NamespacedKey>,
) {
    data class Requirement(val professionId: String, val rank: SkillLevel)

    /** Miner rank a block needs to drop anything, or null if it always drops. */
    fun miningRank(block: Material): SkillLevel? = mining[block]

    /** What a station needs, or empty if anyone can use it. Meeting any one requirement is enough. */
    fun station(block: Material): List<Requirement> = stations[block].orEmpty()

    /** Highest level of [enchantment] a Librarian of [rank] can apply. */
    fun maxLevel(rank: SkillLevel, enchantment: NamespacedKey): Int =
        if (enchantment in grandmasterOnly && rank != SkillLevel.GRANDMASTER) 0 else enchantMax[rank] ?: Int.MAX_VALUE

    companion object {
        fun parse(yaml: ConfigurationSection, warn: (String) -> Unit): GateRules {
            fun rank(name: String, where: String) =
                SkillLevel.entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                    ?: null.also { warn("rank-gates.yml: '$name' under $where isn't a rank; ignoring it") }

            fun materials(section: ConfigurationSection, path: String, where: String) = section.getStringList(path).mapNotNull { raw ->
                Material.matchMaterial(raw)
                    ?: null.also { warn("rank-gates.yml: '$raw' under $where isn't a block; ignoring it") }
            }

            val mining = mutableMapOf<Material, SkillLevel>()
            yaml.getConfigurationSection("mining")?.let { section ->
                for (name in section.getKeys(false)) {
                    val level = rank(name, "mining") ?: continue
                    for (block in materials(section, name, "mining")) mining[block] = level
                }
            }

            val stations = mutableMapOf<Material, MutableList<Requirement>>()
            yaml.getConfigurationSection("stations")?.let { professions ->
                for (id in professions.getKeys(false)) {
                    val ranks = professions.getConfigurationSection(id) ?: continue
                    for (name in ranks.getKeys(false)) {
                        val level = rank(name, "stations.$id") ?: continue
                        for (block in materials(ranks, name, "stations.$id")) stations.getOrPut(block, ::mutableListOf) += Requirement(id, level)
                    }
                }
            }

            val enchantMax = mutableMapOf<SkillLevel, Int>()
            yaml.getConfigurationSection("enchanting.max-level")?.let { section ->
                for (name in section.getKeys(false)) enchantMax[rank(name, "enchanting.max-level") ?: continue] = section.getInt(name)
            }
            val grandmasterOnly = yaml.getStringList("enchanting.grandmaster-only").mapNotNull { raw ->
                NamespacedKey.fromString(raw.trim().lowercase())
                    ?: null.also { warn("rank-gates.yml: '$raw' isn't an enchantment id; ignoring it") }
            }.toSet()

            return GateRules(mining, stations, enchantMax, grandmasterOnly)
        }
    }
}
