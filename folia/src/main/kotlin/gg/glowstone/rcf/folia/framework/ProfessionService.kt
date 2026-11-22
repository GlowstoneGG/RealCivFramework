package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.professions.ProfessionProgression
import gg.glowstone.rcf.core.professions.SkillLevel
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Player PDC is the source of truth; callers must be on the player's owning region. */
internal class ProfessionService(val catalog: ProfessionCatalog) {
    // Fixed namespace so forks that rename the plugin keep player data.
    private val keys = catalog.professions.associate { it.id to NamespacedKey(NAMESPACE, "profession_xp_${it.id}") }

    // Ranks of online players, readable from any region thread.
    private val ranks = ConcurrentHashMap<UUID, Map<String, SkillLevel>>()

    /** XP stored for professions that are no longer loaded is kept, but ignored until they return. */
    fun load(player: Player): ProfessionProgression = catalog.newProgression().also { progress ->
        for ((id, key) in keys) {
            val value = player.persistentDataContainer.get(key, PersistentDataType.DOUBLE) ?: 0.0
            if (value.isFinite() && value >= 0.0) progress.set(id, value)
        }
    }

    fun cachedRank(player: UUID, profession: Profession): SkillLevel =
        ranks[player]?.get(profession.id) ?: SkillLevel.NOVICE

    /** Callers must be on the player's owning region. */
    fun refresh(player: Player) {
        ranks[player.uniqueId] = ranks(load(player))
    }

    fun forget(player: UUID) {
        ranks.remove(player)
    }

    /** Returns the XP actually applied. Professions that aren't loaded are ignored. */
    fun add(player: Player, profession: Profession, amount: Double, notify: Boolean = true): Double {
        if (amount == 0.0 || profession.id !in keys) return 0.0
        val progress = load(player)
        val before = ranks(progress)
        val applied = progress.add(profession.id, amount)
        if (applied == 0.0) return 0.0
        save(player, progress, profession.id)
        if (notify) {
            player.sendActionBar(Component.text("${profession.displayName} ${if (applied > 0) "+" else ""}${applied.formatXp()} XP", TextColor.color(profession.color)))
        }
        announceRankChanges(player, before, progress)
        return applied
    }

    fun set(player: Player, profession: Profession, amount: Double) {
        if (profession.id !in keys) return
        val progress = load(player)
        val before = ranks(progress)
        progress.set(profession.id, amount)
        save(player, progress, profession.id)
        announceRankChanges(player, before, progress)
    }

    fun clear(player: Player) {
        val progress = load(player)
        val before = ranks(progress)
        for (id in keys.keys) {
            progress.set(id, 0.0)
            save(player, progress, id)
        }
        announceRankChanges(player, before, progress)
    }

    private fun ranks(progress: ProfessionProgression) = keys.keys.associateWith(progress::rank)

    private fun save(player: Player, progress: ProfessionProgression, id: String) {
        player.persistentDataContainer.set(keys.getValue(id), PersistentDataType.DOUBLE, progress.xp(id))
    }

    private fun announceRankChanges(player: Player, before: Map<String, SkillLevel>, progress: ProfessionProgression) {
        ranks[player.uniqueId] = ranks(progress)
        for (profession in catalog.professions) {
            val old = before.getValue(profession.id)
            val current = progress.rank(profession.id)
            if (old == current) continue
            Bukkit.getPluginManager().callEvent(ProfessionRankChangeEvent(player, profession.id, old, current))
            val direction = if (current.ordinal > old.ordinal) "reached" else "changed to"
            player.sendMessage(Component.text("${profession.displayName} $direction ${current.displayName} (${current.ordinal})", TextColor.color(profession.color)))
        }
    }

    companion object {
        const val NAMESPACE = "rcf"
    }
}

internal fun Double.formatXp(): String = if (this == toLong().toDouble()) toLong().toString() else "%.1f".format(java.util.Locale.ROOT, this)
