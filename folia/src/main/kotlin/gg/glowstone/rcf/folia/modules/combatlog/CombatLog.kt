package gg.glowstone.rcf.folia.modules.combatlog

import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.combattag.CombatTag
import gg.glowstone.rcf.folia.modules.downed.Downed
import io.papermc.paper.datacomponent.item.ResolvableProfile
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.world.EntitiesLoadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Logging out while combat-tagged leaves your body behind for 15 seconds. If it's killed, it drops a copy of your
 * inventory and you die when you return.
 */
internal object CombatLog : Module("combat-log", dependsOn = listOf(CombatTag)) {
    private const val LINGER_MS = 15_000L
    private val ownerKey = civKey("combat_log_owner")
    private val itemsKey = civKey("combat_log_items")

    private class Body(val entity: Mannequin, @Volatile var expiresAt: Long)

    private val bodies = ConcurrentHashMap<UUID, Body>()
    private lateinit var killed: KilledStore

    override fun start(ctx: CivContext) {
        killed = KilledStore(ctx.plugin.dataFolder.resolve("combat-log-killed.txt"))
    }

    private fun leaveBody(player: Player) {
        val equipment = player.equipment
        val body = player.world.spawn(player.location, Mannequin::class.java) { body ->
            body.setProfile(ResolvableProfile.resolvableProfile(player.playerProfile))
            body.customName(Component.text(player.name))
            body.isCustomNameVisible = true
            body.isPersistent = true
            body.getAttribute(Attribute.MAX_HEALTH)?.baseValue = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            body.health = player.health
            for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND)) {
                body.equipment.setItem(slot, equipment.getItem(slot).clone())
            }
            body.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, player.uniqueId.toString())
            body.persistentDataContainer.set(itemsKey, PersistentDataType.BYTE_ARRAY, ItemStack.serializeItemsAsBytes(player.inventory.contents.filterNotNull().filterNot { it.isEmpty }))
        }
        val tracked = Body(body, System.currentTimeMillis() + LINGER_MS)
        bodies[player.uniqueId] = tracked
        body.scheduler.runAtFixedRate(ctx.plugin, { task ->
            if (bodies[player.uniqueId] !== tracked) {
                task.cancel()
                return@runAtFixedRate
            }
            if (System.currentTimeMillis() < tracked.expiresAt) return@runAtFixedRate
            bodies.remove(player.uniqueId, tracked)
            body.remove()
            task.cancel()
        }, { bodies.remove(player.uniqueId, tracked) }, 20L, 20L)
    }

    /** Callers must be on the player's region. */
    private fun punish(player: Player) {
        if (!killed.remove(player.uniqueId)) return
        player.inventory.clear()
        player.health = 0.0
    }

    private fun Mannequin.owner(): UUID? = persistentDataContainer.get(ownerKey, PersistentDataType.STRING)?.let(UUID::fromString)

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            val player = event.player
            if (Bukkit.isStopping() || !CombatTag.isTagged(player.uniqueId) || Downed.isDowned(player.uniqueId)) return
            leaveBody(player)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val player = event.player
            bodies.remove(player.uniqueId)?.let { body -> body.entity.scheduler.run(ctx.plugin, { body.entity.remove() }, null) }
            player.scheduler.runDelayed(ctx.plugin, { punish(player) }, null, 1L)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onHurt(event: EntityDamageEvent) {
            val owner = (event.entity as? Mannequin)?.owner() ?: return
            bodies[owner]?.expiresAt = System.currentTimeMillis() + LINGER_MS
        }

        @EventHandler
        fun onKilled(event: EntityDeathEvent) {
            val body = event.entity as? Mannequin ?: return
            val owner = body.owner() ?: return
            bodies.remove(owner)
            event.drops.clear()
            body.persistentDataContainer.get(itemsKey, PersistentDataType.BYTE_ARRAY)?.let { bytes ->
                event.drops += ItemStack.deserializeItemsFromBytes(bytes).filterNotNull()
            }
            killed.add(owner)
            Bukkit.getPlayer(owner)?.let { player -> player.scheduler.run(ctx.plugin, { punish(player) }, null) }
        }

        // Bodies left over from a restart have no timer; their owners got off free.
        @EventHandler
        fun onLoad(event: EntitiesLoadEvent) {
            for (entity in event.entities) {
                if (entity is Mannequin && entity.owner() != null && bodies[entity.owner()]?.entity != entity) entity.remove()
            }
        }
    })

    /** Players whose body was killed while they were offline. */
    private class KilledStore(private val file: File) {
        private val players: MutableSet<UUID> = if (file.exists()) file.readLines().mapNotNull { runCatching { UUID.fromString(it.trim()) }.getOrNull() }.toMutableSet() else mutableSetOf()

        @Synchronized
        fun add(player: UUID) {
            if (players.add(player)) save()
        }

        @Synchronized
        fun remove(player: UUID): Boolean = players.remove(player).also { if (it) save() }

        private fun save() {
            file.writeText(players.joinToString("\n"))
        }
    }
}
