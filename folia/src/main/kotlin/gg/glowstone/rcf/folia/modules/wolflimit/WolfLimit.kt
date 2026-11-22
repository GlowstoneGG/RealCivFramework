package gg.glowstone.rcf.folia.modules.wolflimit

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.modules.professions.Hunter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.entity.Wolf
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityTameEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A player can own as many wolves as their Hunter rank number: none at Novice, five at Grandmaster.
 * Wolves are tracked in `wolves.yml` because owners and their wolves can be anywhere, or offline.
 */
internal object WolfLimit : Module("wolf-limit") {
    override val reads = listOf(Hunter)

    private val owned = ConcurrentHashMap<UUID, MutableSet<UUID>>()
    private val fileLock = Any()
    private lateinit var file: File

    private fun load() {
        val yaml = YamlConfiguration.loadConfiguration(file)
        for (owner in yaml.getKeys(false)) {
            val id = runCatching { UUID.fromString(owner) }.getOrNull() ?: continue
            owned[id] = yaml.getStringList(owner).mapNotNullTo(ConcurrentHashMap.newKeySet()) { runCatching { UUID.fromString(it) }.getOrNull() }
        }
    }

    private fun write() = synchronized(fileLock) {
        val yaml = YamlConfiguration()
        for ((owner, wolves) in owned) if (wolves.isNotEmpty()) yaml.set(owner.toString(), wolves.map(UUID::toString))
        yaml.save(file)
    }

    private fun save() {
        ctx.plugin.server.asyncScheduler.runNow(ctx.plugin) { write() }
    }

    override fun listeners(): List<Listener> {
        file = ctx.plugin.dataFolder.resolve("wolves.yml")
        load()
        return listOf(object : Listener {
            @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
            fun onTame(event: EntityTameEvent) {
                if (event.entity !is Wolf) return
                val owner = event.owner as? Player ?: return
                val limit = ctx.rank(owner, Hunter).ordinal
                val count = owned[owner.uniqueId]?.size ?: 0
                if (count < limit) return
                event.isCancelled = true
                val message = if (limit == 0) "You need Hunter Apprentice to tame a wolf" else "Your Hunter rank lets you keep $limit wolves, and you have $count"
                owner.sendActionBar(Component.text(message, NamedTextColor.RED))
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onTamed(event: EntityTameEvent) {
                if (event.entity !is Wolf) return
                owned.computeIfAbsent(event.owner.uniqueId) { ConcurrentHashMap.newKeySet() } += event.entity.uniqueId
                save()
            }

            @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
            fun onDeath(event: EntityDeathEvent) {
                val wolf = event.entity as? Wolf ?: return
                val owner = wolf.ownerUniqueId ?: return
                if (owned[owner]?.remove(wolf.uniqueId) == true) save()
            }
        })
    }

    override fun stop() {
        if (::file.isInitialized) write()
    }
}
