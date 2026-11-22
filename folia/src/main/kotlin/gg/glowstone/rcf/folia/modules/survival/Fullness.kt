package gg.glowstone.rcf.folia.modules.survival

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import io.papermc.paper.datacomponent.DataComponentTypes
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityRegainHealthEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil

/** Replaces vanilla hunger with a 0–100 fullness bar that drains over time and is shown on the food bar. */
internal object Fullness : Module("fullness") {
    private const val MAX = 100.0
    private const val START = 40.0
    private const val DRAIN_PER_SECOND = 0.05
    private const val GAIN_PER_NUTRITION = 5.0
    private const val REMEMBERED_MEALS = 5

    // Vanilla spends 6 exhaustion (1.5 food points) per regenerated HP; same cost on the 0–100 scale.
    private const val COST_PER_REGEN_HP = 7.5

    private val key = civKey("fullness")

    // Each player's entry is only touched on that player's region.
    private val recentMeals = ConcurrentHashMap<UUID, ArrayDeque<Material>>()

    private fun Player.fullness() = persistentDataContainer.get(key, PersistentDataType.DOUBLE) ?: START

    private fun Player.setFullness(value: Double) {
        val fullness = value.coerceIn(0.0, MAX)
        persistentDataContainer.set(key, PersistentDataType.DOUBLE, fullness)
        foodLevel = ceil(fullness / 5).toInt()
        saturation = 0f
    }

    /** Callers must be on the player's region. */
    fun level(player: Player): Double = player.fullness()

    /** Callers must be on the player's region. */
    fun spend(player: Player, amount: Double) = player.setFullness(player.fullness() - amount)

    /** Callers must be on the player's region. The task retires when the player leaves. */
    private fun track(player: Player) {
        player.setFullness(player.fullness())
        player.scheduler.runAtFixedRate(ctx.plugin, { _ ->
            if (player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE) {
                player.setFullness(player.fullness() - DRAIN_PER_SECOND)
            }
        }, null, 20L, 20L)
    }

    /** Fullness gained from a food, 20% less for each time it was eaten in the last few meals (at most 80% less). */
    fun mealGain(nutrition: Int, recentRepeats: Int): Double =
        nutrition * GAIN_PER_NUTRITION * (1.0 - 0.2 * recentRepeats.coerceAtMost(4))

    override fun start() {
        for (player in ctx.plugin.server.onlinePlayers) player.scheduler.run(ctx.plugin, { track(player) }, null)
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler
        fun onJoin(event: PlayerJoinEvent) = track(event.player)

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            recentMeals.remove(event.player.uniqueId)
        }

        @EventHandler(priority = EventPriority.HIGHEST)
        fun onFoodChange(event: FoodLevelChangeEvent) {
            if (event.entity is Player) event.isCancelled = true
        }

        // Only reached when no-natural-regen is off; the exhaustion vanilla would charge is cancelled above.
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onRegen(event: EntityRegainHealthEvent) {
            val player = event.entity as? Player ?: return
            if (event.regainReason == EntityRegainHealthEvent.RegainReason.SATIATED) {
                player.setFullness(player.fullness() - COST_PER_REGEN_HP * event.amount)
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onEat(event: PlayerItemConsumeEvent) {
            val nutrition = event.item.getData(DataComponentTypes.FOOD)?.nutrition() ?: return
            val player = event.player
            val meals = recentMeals.computeIfAbsent(player.uniqueId) { ArrayDeque() }
            val gain = mealGain(nutrition, meals.count { it == event.item.type })
            val fullness = player.fullness()
            if (fullness + gain > MAX) {
                event.isCancelled = true
                player.sendActionBar("You're too full to eat that")
                return
            }
            meals.addLast(event.item.type)
            if (meals.size > REMEMBERED_MEALS) meals.removeFirst()
            player.setFullness(fullness + gain)
        }

        @EventHandler
        fun onRespawn(event: PlayerRespawnEvent) {
            val player = event.player
            // Vanilla refills the food bar after this event, so apply ours a tick later.
            player.scheduler.runDelayed(ctx.plugin, { player.setFullness(maxOf(player.fullness(), START)) }, null, 1L)
        }
    })
}
