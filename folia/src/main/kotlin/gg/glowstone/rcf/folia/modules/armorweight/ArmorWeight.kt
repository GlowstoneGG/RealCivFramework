package gg.glowstone.rcf.folia.modules.armorweight

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent
import com.destroystokyo.paper.event.player.PlayerJumpEvent
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.stamina.Stamina
import net.kyori.adventure.text.Component
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Heavier armour slows you down, and sprint-jumping in it wears you out. */
internal object ArmorWeight : Module("armor-weight") {
    private val key = civKey("armor_weight")
    private val materials = mapOf(
        "LEATHER" to 1.0, "TURTLE" to 1.5, "CHAINMAIL" to 2.0, "COPPER" to 2.0,
        "GOLDEN" to 2.5, "IRON" to 3.0, "DIAMOND" to 5.0, "NETHERITE" to 6.0,
    )
    private val slots = mapOf("HELMET" to 5.0, "CHESTPLATE" to 8.0, "LEGGINGS" to 7.0, "BOOTS" to 4.0)
    private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)

    private enum class Band { LIGHT, MEDIUM, HEAVY, OVERLOADED }

    private val bands = ConcurrentHashMap<UUID, Band>()
    private val jumps = ConcurrentHashMap<UUID, ArrayDeque<Long>>()
    private val weights = ConcurrentHashMap<UUID, Double>()

    /** Total armour weight. Safe from any thread; reads 0 when this module is off. */
    fun weight(player: UUID): Double = weights[player] ?: 0.0

    private fun weight(item: ItemStack?): Double {
        val name = item?.type?.name ?: return 0.0
        val material = materials[name.substringBefore('_')] ?: return 0.0
        return material * (slots[name.substringAfterLast('_')] ?: return 0.0)
    }

    private fun band(total: Double): Band {
        val load = total - 25
        return when {
            load <= 50 -> Band.LIGHT
            load <= 100 -> Band.MEDIUM
            load <= 150 -> Band.HEAVY
            else -> Band.OVERLOADED
        }
    }

    /** Callers must be on the player's owning region. [changed] overrides one slot, since the event may fire before the slot updates. */
    private fun update(player: Player, changed: Pair<EquipmentSlot, ItemStack>? = null) {
        val total = armorSlots.sumOf { slot -> weight(if (changed?.first == slot) changed.second else player.inventory.getItem(slot)) }
        val speed = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
        speed.removeModifier(key)
        if (total > 0) speed.addTransientModifier(AttributeModifier(key, -total / 1000, AttributeModifier.Operation.ADD_SCALAR))
        weights[player.uniqueId] = total
        val band = band(total)
        val previous = bands.put(player.uniqueId, band)
        if (previous != null && previous != band) {
            player.sendActionBar(Component.text("Armour: ${band.name.lowercase().replaceFirstChar(Char::uppercase)}"))
        }
    }

    override fun start(ctx: CivContext) {
        for (player in ctx.plugin.server.onlinePlayers) player.scheduler.run(ctx.plugin, { update(player) }, null)
    }

    override fun listeners(ctx: CivContext) = listOf(object : Listener {
        @EventHandler(priority = EventPriority.MONITOR)
        fun onJoin(event: PlayerJoinEvent) = update(event.player)

        @EventHandler(priority = EventPriority.MONITOR)
        fun onArmor(event: PlayerArmorChangeEvent) = update(event.player, event.slot to event.newItem)

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onJump(event: PlayerJumpEvent) {
            // Stamina charges heavier armour for sprint-jumps instead.
            if (Stamina.enabled) return
            val player = event.player
            if (!player.isSprinting || (bands[player.uniqueId] ?: Band.LIGHT) == Band.LIGHT) return
            val now = System.currentTimeMillis()
            val recent = jumps.computeIfAbsent(player.uniqueId) { ArrayDeque() }
            recent.addLast(now)
            while (recent.isNotEmpty() && now - recent.first() > 4_000) recent.removeFirst()
            if (recent.size > 3) player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 40, 0))
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            bands.remove(event.player.uniqueId)
            jumps.remove(event.player.uniqueId)
            weights.remove(event.player.uniqueId)
        }
    })
}
