package gg.glowstone.rcf.folia.modules.downed

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.stamina.Stamina
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.entity.Pose
import org.bukkit.entity.Projectile
import org.bukkit.event.Cancellable
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerPortalEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Lethal damage downs a player instead of killing them. They crawl, can't act, and bleed out after 7 minutes
 * unless revived. Any further damage kills them.
 */
internal object Downed : Module("downed") {
    const val BLEED_OUT_TICKS = 8_400
    private const val MAX_OVERKILL = 10.0
    private const val PERIOD = 2L
    private val ignoredCauses = setOf(DamageCause.VOID, DamageCause.KILL, DamageCause.SUICIDE, DamageCause.WORLD_BORDER)
    private val ticksKey = civKey("downed_ticks")
    private val logoutKey = civKey("downed_logout_at")

    private class State(var ticksLeft: Int, val bar: BossBar) {
        var task: ScheduledTask? = null
        var elapsed = 0
        var last: Location? = null
        var barrier: Location? = null
    }

    private val downed = ConcurrentHashMap<UUID, State>()

    fun isDowned(player: UUID): Boolean = downed.containsKey(player)

    /** Stands a downed player back up. Callers must be on the player's region. */
    fun revive(player: Player, health: Double) {
        if (!end(player)) return
        player.health = health.coerceIn(1.0, player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0)
        player.leaveVehicle()
        ctx.actionBar.show(player, "revive", Component.text("You've been revived", NamedTextColor.GREEN))
    }

    private fun down(player: Player, ticks: Int) {
        val bar = BossBar.bossBar(Component.text("Bleeding out", NamedTextColor.RED), ticks / BLEED_OUT_TICKS.toFloat(), BossBar.Color.PINK, BossBar.Overlay.PROGRESS)
        val state = State(ticks, bar)
        downed[player.uniqueId] = state
        player.showBossBar(bar)
        player.setPose(Pose.SWIMMING, true)
        player.persistentDataContainer.set(ticksKey, PersistentDataType.INTEGER, ticks)
        state.task = player.scheduler.runAtFixedRate(ctx.plugin, { tick(player, state) }, null, 1L, PERIOD)
    }

    private fun tick(player: Player, state: State) {
        val position = player.location
        val carried = player.isInsideVehicle
        val crawling = state.last?.let { it.world == position.world && it.distanceSquared(position) > 0.0025 } == true
        state.last = position
        state.ticksLeft -= (if (crawling && !carried) 2 else 1) * PERIOD.toInt()
        if (state.ticksLeft <= 0) {
            end(player)
            player.health = 0.0
            return
        }
        state.elapsed += PERIOD.toInt()
        updateBarrier(player, state, carried)
        if (state.elapsed % 100 == 0) player.addPotionEffect(PotionEffect(PotionEffectType.DARKNESS, 50, 0, true, false, false))
        if (state.elapsed % 20 == 0) {
            state.bar.progress((state.ticksLeft / BLEED_OUT_TICKS.toFloat()).coerceIn(0f, 1f))
            player.persistentDataContainer.set(ticksKey, PersistentDataType.INTEGER, state.ticksLeft)
        }
    }

    // A client-side barrier at head height keeps the player's own client in the crawl pose.
    private fun updateBarrier(player: Player, state: State, carried: Boolean) {
        val head = player.location.block.getRelative(BlockFace.UP)
        val target = head.location.takeIf { !carried && head.type.isAir }
        if (target == state.barrier) return
        state.barrier?.let { restoreBlock(player, it) }
        target?.let { player.sendBlockChange(it, Material.BARRIER.createBlockData()) }
        state.barrier = target
    }

    // After a teleport the old barrier spot can belong to another region, and only that region may read it.
    private fun restoreBlock(player: Player, at: Location) {
        if (Bukkit.isOwnedByCurrentRegion(at)) return player.sendBlockChange(at, at.block.blockData)
        Bukkit.getRegionScheduler().run(ctx.plugin, at) {
            val data = at.block.blockData
            player.scheduler.run(ctx.plugin, {
                // Skip if they changed world (the client dropped the old one) or a barrier is back on that spot.
                if (player.world == at.world && downed[player.uniqueId]?.barrier != at) player.sendBlockChange(at, data)
            }, null)
        }
    }

    /** Clears in-memory and client state. Returns false if the player wasn't downed. */
    private fun end(player: Player, keepSaved: Boolean = false): Boolean {
        val state = downed.remove(player.uniqueId) ?: return false
        state.task?.cancel()
        state.barrier?.let { restoreBlock(player, it) }
        player.hideBossBar(state.bar)
        player.setPose(Pose.STANDING, false)
        if (!keepSaved) {
            player.persistentDataContainer.remove(ticksKey)
            player.persistentDataContainer.remove(logoutKey)
        }
        return true
    }

    private fun Cancellable.cancelIfDowned(player: Player?) {
        if (player != null && isDowned(player.uniqueId)) isCancelled = true
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onDamage(event: EntityDamageEvent) {
            val player = event.entity as? Player ?: return
            if (isDowned(player.uniqueId)) {
                event.isCancelled = true
                Stamina.recordDownedHit(event)
                end(player)
                player.health = 0.0
                return
            }
            if (event.cause in ignoredCauses) return
            val overkill = event.finalDamage - player.health
            if (overkill < 0 || overkill >= MAX_OVERKILL) return
            val inventory = player.inventory
            if (inventory.itemInMainHand.type == Material.TOTEM_OF_UNDYING || inventory.itemInOffHand.type == Material.TOTEM_OF_UNDYING) return
            event.isCancelled = true
            Stamina.recordDownedHit(event)
            player.health = (MAX_OVERKILL - overkill).coerceAtLeast(1.0)
            down(player, BLEED_OUT_TICKS)
            player.sendMessage(Component.text("You are bleeding out. A Healer can revive you.", NamedTextColor.RED))
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onDeath(event: PlayerDeathEvent) {
            end(event.player)
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            val player = event.player
            val pdc = player.persistentDataContainer
            val ticks = pdc.get(ticksKey, PersistentDataType.INTEGER) ?: return
            val left = remainingAfterOffline(ticks, pdc.get(logoutKey, PersistentDataType.LONG), System.currentTimeMillis())
            pdc.remove(logoutKey)
            if (left > 0) {
                down(player, left)
            } else {
                pdc.remove(ticksKey)
                player.scheduler.run(ctx.plugin, { player.health = 0.0 }, null)
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        fun onQuit(event: PlayerQuitEvent) {
            val player = event.player
            val state = downed[player.uniqueId] ?: return
            player.persistentDataContainer.set(ticksKey, PersistentDataType.INTEGER, state.ticksLeft)
            player.persistentDataContainer.set(logoutKey, PersistentDataType.LONG, System.currentTimeMillis())
            end(player, keepSaved = true)
        }

        @EventHandler(priority = EventPriority.LOW)
        fun onInteract(event: PlayerInteractEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onInteractEntity(event: PlayerInteractEntityEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onBreak(event: BlockBreakEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onPlace(event: BlockPlaceEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onAttack(event: EntityDamageByEntityEvent) =
            event.cancelIfDowned(event.damager as? Player ?: (event.damager as? Projectile)?.shooter as? Player)

        @EventHandler(priority = EventPriority.LOW)
        fun onPickup(event: EntityPickupItemEvent) = event.cancelIfDowned(event.entity as? Player)

        @EventHandler(priority = EventPriority.LOW)
        fun onConsume(event: PlayerItemConsumeEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onPortal(event: PlayerPortalEvent) = event.cancelIfDowned(event.player)

        @EventHandler(priority = EventPriority.LOW)
        fun onLaunch(event: ProjectileLaunchEvent) = event.cancelIfDowned(event.entity.shooter as? Player)

        @EventHandler(priority = EventPriority.LOW)
        fun onPearl(event: PlayerTeleportEvent) {
            if (event.cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) event.cancelIfDowned(event.player)
        }
    })
}

/** Bleed-out ticks left after time offline; a missing logout time (crash) counts as no time offline. */
internal fun remainingAfterOffline(ticks: Int, logoutAt: Long?, now: Long): Int {
    val offlineTicks = if (logoutAt == null) 0L else ((now - logoutAt).coerceAtLeast(0) / 50)
    return (ticks - offlineTicks).coerceAtLeast(0).toInt()
}
