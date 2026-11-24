package gg.glowstone.rcf.folia.modules.stamina

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.armorweight.ArmorWeight
import gg.glowstone.rcf.folia.modules.carry.Carry
import gg.glowstone.rcf.folia.modules.downed.Downed
import gg.glowstone.rcf.folia.modules.professions.Healer
import gg.glowstone.rcf.folia.modules.professions.Hunter
import gg.glowstone.rcf.folia.modules.spawngrace.SpawnGrace
import gg.glowstone.rcf.folia.modules.survival.Fullness
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A 0–100 stamina pool for bursts: sprinting, melee and blocking. Shown on the XP bar while it isn't full. */
internal object Stamina : Module("stamina") {
    override val reads = listOf(Hunter, Healer)

    private const val MAX = 100.0
    private const val PERIOD = 5L
    private const val SECONDS_PER_TICK = PERIOD / 20.0

    private const val SPRINT_PER_SECOND = 3.0
    private const val SPRINT_JUMP = 1.0
    private const val MELEE_HIT = 4.0
    private const val SHIELD_PER_DAMAGE = 2.0
    private const val SHIELD_MAX = 20.0
    private const val SHIELD_COOLDOWN_TICKS = 100

    // Ignore floating point noise in the horizontal displacement, not slow terrain movement.
    private const val MOVEMENT_EPSILON_SQUARED = 1e-10
    private const val WALK_MIN_MOVE = 0.1

    private const val REGEN_DELAY_TICKS = 20L
    private const val REGEN_STANDING = 6.0
    private const val REGEN_WALKING = 3.0
    private const val RAMP_SECONDS = 5.0
    private const val LOW_FULLNESS = 30.0
    private const val FULLNESS_PER_STAMINA = 1.0 / 100

    private const val WINDED_UNTIL = 25.0
    private const val WINDED_SLOW = -0.30
    private const val WINDED_DAMAGE = 0.5
    private const val REVIVED_AT = 25.0
    private const val RESTORE_BAR_AFTER_TICKS = 60L

    private val windedKey = civKey("winded")

    private class State(var lastLoc: Location) {
        @Volatile var value = MAX
        @Volatile var winded = false
        var tick = 0L
        var lastCostAt = -REGEN_DELAY_TICKS
        var fullSince = 0L
        var shown = false
        var wasDowned = false
    }

    // Each player's entry is only written on that player's region; `winded` is also read from damage events.
    private val states = ConcurrentHashMap<UUID, State>()

    fun isMovingSprint(sprinting: Boolean, insideVehicle: Boolean, winded: Boolean, horizontalDistanceSquared: Double): Boolean =
        sprinting && !insideVehicle && !winded && horizontalDistanceSquared > MOVEMENT_EPSILON_SQUARED

    /** Multiplier on a base cost: heavier armour (sprinting only), Hunter rank and carrying a downed player (Healer rank). */
    fun costMultiplier(armorWeight: Double, hunterRank: Int, carrierHealerRank: Int?): Double =
        (1 + armorWeight / 100) * (1 - 0.08 * hunterRank) * (carrierHealerRank?.let { 2 - 0.2 * it } ?: 1.0)

    /** Regen per second at [base], doubling over [RAMP_SECONDS] of unbroken rest, halved when hungry. */
    fun regenRate(base: Double, restSeconds: Double, lowFullness: Boolean): Double =
        base * (1 + restSeconds.coerceIn(0.0, RAMP_SECONDS) / RAMP_SECONDS) * (if (lowFullness) 0.5 else 1.0)

    private fun Player.counts() = (gameMode == GameMode.SURVIVAL || gameMode == GameMode.ADVENTURE) && !isFlying

    private fun Player.sprintMultiplier() = costMultiplier(
        ArmorWeight.weight(uniqueId),
        ctx.rank(this, Hunter).ordinal,
        if (Carry.isCarrying(uniqueId)) ctx.rank(this, Healer).ordinal else null,
    )

    /** Callers must be on the player's region. */
    private fun spend(player: Player, amount: Double) {
        val state = states[player.uniqueId] ?: return
        if (!player.counts() || SpawnGrace.hasGrace(player.uniqueId) || Downed.isDowned(player.uniqueId)) return
        state.lastCostAt = state.tick
        state.value = (state.value - amount).coerceAtLeast(0.0)
        if (state.value <= 0.0 && !state.winded) setWinded(player, state, true)
    }

    private fun setWinded(player: Player, state: State, winded: Boolean) {
        state.winded = winded
        val speed = player.getAttribute(Attribute.MOVEMENT_SPEED) ?: return
        speed.removeModifier(windedKey)
        if (winded) {
            speed.addTransientModifier(AttributeModifier(windedKey, WINDED_SLOW, AttributeModifier.Operation.MULTIPLY_SCALAR_1))
            ctx.actionBar.show(player, "stamina", Component.text("Winded", NamedTextColor.RED))
        }
    }

    private fun refill(player: Player, state: State, value: Double = MAX) {
        state.value = value
        state.lastCostAt = state.tick - REGEN_DELAY_TICKS
        if (state.winded) setWinded(player, state, false)
    }

    private fun tick(player: Player, state: State) {
        state.tick += PERIOD
        val loc = player.location
        val moved = if (loc.world == state.lastLoc.world) {
            val dx = loc.x - state.lastLoc.x
            val dz = loc.z - state.lastLoc.z
            dx * dx + dz * dz
        } else 0.0
        state.lastLoc = loc
        if (player.isDead) return
        if (!player.counts()) {
            refill(player, state)
        } else if (Downed.isDowned(player.uniqueId)) {
            state.wasDowned = true
        } else {
            if (state.wasDowned) {
                state.wasDowned = false
                refill(player, state, REVIVED_AT)
            }
            val sprinting = isMovingSprint(player.isSprinting, player.isInsideVehicle, state.winded, moved)
            if (sprinting) spend(player, SPRINT_PER_SECOND * SECONDS_PER_TICK * player.sprintMultiplier())
            regen(player, state, sprinting, moved >= WALK_MIN_MOVE * WALK_MIN_MOVE)
            if (state.winded && state.value >= WINDED_UNTIL) setWinded(player, state, false)
        }
        display(player, state)
    }

    private fun regen(player: Player, state: State, sprinting: Boolean, moving: Boolean) {
        val idle = state.tick - state.lastCostAt
        if (state.value >= MAX || idle < REGEN_DELAY_TICKS) return
        if (sprinting || player.isGliding || (moving && Carry.isCarrying(player.uniqueId))) return
        val base = if (moving && !player.isInsideVehicle && !player.isSneaking) REGEN_WALKING else REGEN_STANDING
        val hungry = Fullness.enabled && Fullness.level(player) < LOW_FULLNESS
        val rest = (idle - REGEN_DELAY_TICKS) / 20.0
        val gain = (regenRate(base, rest, hungry) * SECONDS_PER_TICK).coerceAtMost(MAX - state.value)
        state.value += gain
        if (Fullness.enabled) Fullness.spend(player, gain * FULLNESS_PER_STAMINA)
    }

    // The level number stays real, so enchanting and anvils are unaffected; vanilla XP updates are overwritten next tick.
    private fun display(player: Player, state: State) {
        if (state.value < MAX && player.counts()) {
            player.sendExperienceChange((state.value / MAX).toFloat(), player.level)
            state.shown = true
            state.fullSince = state.tick
        } else if (state.shown && (!player.counts() || state.tick - state.fullSince >= RESTORE_BAR_AFTER_TICKS)) {
            player.sendExperienceChange(player.exp, player.level)
            state.shown = false
        }
    }

    /** Callers must be on the player's region. The task retires when the player leaves. */
    private fun track(player: Player) {
        val state = State(player.location)
        states[player.uniqueId] = state
        player.scheduler.runAtFixedRate(ctx.plugin, { task ->
            if (states[player.uniqueId] !== state) task.cancel() else tick(player, state)
        }, { states.remove(player.uniqueId, state) }, PERIOD, PERIOD)
    }

    override fun start() {
        for (player in ctx.plugin.server.onlinePlayers) player.scheduler.run(ctx.plugin, { track(player) }, null)
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler
        fun onJoin(event: PlayerJoinEvent) = track(event.player)

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) {
            states.remove(event.player.uniqueId)
        }

        @EventHandler
        fun onRespawn(event: PlayerRespawnEvent) {
            val player = event.player
            val state = states[player.uniqueId] ?: return
            state.wasDowned = false
            refill(player, state)
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onJump(event: PlayerJumpEvent) {
            val player = event.player
            if (player.isSprinting && states[player.uniqueId]?.winded == false) spend(player, SPRINT_JUMP * player.sprintMultiplier())
        }

        // Runs before Downed's HIGH overkill check, so a winded hit that would overkill may only down.
        @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
        fun onWindedHit(event: EntityDamageByEntityEvent) {
            val attacker = event.damager as? Player ?: return
            if (event.cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK && states[attacker.uniqueId]?.winded == true) {
                event.damage *= WINDED_DAMAGE
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onMelee(event: EntityDamageByEntityEvent) {
            if (event.isCancelled && !Downed.replacedHit(event)) return
            val attacker = event.damager as? Player ?: return
            if (event.cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return
            val cost = MELEE_HIT * (1 - 0.08 * ctx.rank(attacker, Hunter).ordinal)
            if (Bukkit.isOwnedByCurrentRegion(attacker)) spend(attacker, cost)
            else attacker.scheduler.run(ctx.plugin, { spend(attacker, cost) }, null)
        }

        @Suppress("DEPRECATION")
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBlock(event: EntityDamageEvent) {
            val player = event.entity as? Player ?: return
            if (!player.isBlocking || !event.isApplicable(EntityDamageEvent.DamageModifier.BLOCKING)) return
            val blocked = -event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING)
            if (blocked <= 0) return
            spend(player, (SHIELD_PER_DAMAGE * blocked).coerceAtMost(SHIELD_MAX))
            if ((states[player.uniqueId]?.value ?: MAX) <= 0.0) {
                player.setCooldown(Material.SHIELD, SHIELD_COOLDOWN_TICKS)
                player.clearActiveItem()
            }
        }
    })
}
