package gg.glowstone.rcf.folia.modules.reinforcement

import com.destroystokyo.paper.event.block.BlockDestroyEvent
import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import gg.glowstone.rcf.folia.modules.professions.Builder
import gg.glowstone.rcf.folia.modules.professions.Miner
import gg.glowstone.rcf.folia.modules.professions.roll
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Particle
import org.bukkit.block.PistonMoveReaction
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.Directional
import org.bukkit.block.data.type.Bed
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.Gate
import org.bukkit.block.data.type.TrapDoor
import org.bukkit.entity.FallingBlock
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockDamageAbortEvent
import org.bukkit.event.block.BlockDamageEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPistonEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.entity.EntityBreakDoorEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

/** Builders reinforce blocks with planks, copper or iron so they break slower and resist explosions. */
internal object Reinforcement : Module("reinforcement") {
    override val reads = listOf(Builder, Miner)

    private const val PARTICLE_RADIUS_CHUNKS = 3
    private const val NUGGET_CHANCE = 0.5
    private val breakKey = civKey("reinforcement_break")
    private val fallingKey = civKey("reinforcement_tier")

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onReinforce(event: PlayerInteractEvent) {
            if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
            val player = event.player
            val tier = Tier.forItem(player.inventory.itemInMainHand.type) ?: return
            val block = event.clickedBlock ?: return
            if (!reinforceable(block)) return
            val existing = ReinforcementStore.tier(block)
            if (existing != null && existing >= tier) {
                ctx.actionBar.show(player, "reinforcement", Component.text("Already reinforced (${existing.label()})"))
                return
            }
            if (ctx.rank(player, Builder) < tier.requiredRank) {
                ctx.actionBar.show(player, "reinforcement", Component.text("${tier.label()} reinforcement needs Builder ${tier.requiredRank.displayName}"))
                return
            }
            val creative = player.gameMode == GameMode.CREATIVE
            if (tier == Tier.WOODEN) {
                val plank = player.inventory.contents.firstOrNull { it != null && it.type.name.endsWith("_PLANKS") }
                if (plank == null && !creative) {
                    ctx.actionBar.show(player, "reinforcement", Component.text("Wooden reinforcement uses a plank"))
                    return
                }
                if (!creative) plank!!.amount -= 1
            } else if (!creative) {
                player.inventory.itemInMainHand.amount -= 1
            }
            event.isCancelled = true
            for (part in halves(block)) ReinforcementStore.put(part, tier)
            val xp = when (tier) {
                Tier.IRON -> 15.0
                Tier.COPPER -> 5.0
                Tier.WOODEN -> if (ctx.rank(player, Builder) == SkillLevel.NOVICE) 4.0 else 1.0
            }
            ctx.award(player, Builder, xp)
            ctx.actionBar.show(player, "reinforcement", Component.text("Reinforced (${tier.label()})"))
        }

        @EventHandler(ignoreCancelled = true)
        fun onStartBreaking(event: BlockDamageEvent) {
            val tier = ReinforcementStore.tier(event.block) ?: return
            if (!canBreak(event.player, tier)) {
                event.isCancelled = true
                denyBreak(event.player, tier)
                return
            }
            event.instaBreak = false
            val attribute = event.player.getAttribute(Attribute.BLOCK_BREAK_SPEED) ?: return
            attribute.removeModifier(breakKey)
            attribute.addTransientModifier(
                AttributeModifier(breakKey, 1.0 / tier.breakDivisor - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY),
            )
        }

        @EventHandler
        fun onStopBreaking(event: BlockDamageAbortEvent) = clearSlowdown(event.player)

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onBreakAttempt(event: BlockBreakEvent) {
            clearSlowdown(event.player)
            val tier = ReinforcementStore.tier(event.block) ?: return
            if (!canBreak(event.player, tier)) {
                event.isCancelled = true
                denyBreak(event.player, tier)
            } else if (!event.player.inventory.itemInMainHand.type.name.endsWith("_PICKAXE")) {
                event.isCancelled = true
                ctx.actionBar.show(event.player, "reinforcement", Component.text("This block is reinforced; you need a pickaxe"))
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBreak(event: BlockBreakEvent) {
            val tier = strip(event.block) ?: return
            if (roll(NUGGET_CHANCE)) dropNugget(event.block, tier)
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onEntityExplode(event: EntityExplodeEvent) {
            event.blockList().removeIf { ReinforcementStore.tier(it) != null }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onBlockExplode(event: BlockExplodeEvent) {
            event.blockList().removeIf { block ->
                val tier = strip(block) ?: return@removeIf false
                roll(tier.explosionSurvival).also { survived -> if (survived) dropNugget(block, tier) }
            }
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onBurn(event: BlockBurnEvent) {
            if (ReinforcementStore.tier(event.block) != null) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onDoorBreak(event: EntityBreakDoorEvent) {
            if (ReinforcementStore.tier(event.block) != null) event.isCancelled = true
        }

        // Physics destroys a door whose support is gone; a reinforced one stays put instead.
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onDestroy(event: BlockDestroyEvent) {
            val block = event.block
            if (ReinforcementStore.tier(block) == null) return
            event.isCancelled = true
            // The player who broke the support already predicted the door gone; show it again.
            Bukkit.getRegionScheduler().runDelayed(ctx.plugin, block.location, { _ ->
                for (part in halves(block)) {
                    for (viewer in part.location.getNearbyPlayers(16.0)) viewer.sendBlockChange(part.location, part.blockData)
                }
            }, 1L)
        }

        // Pistons break doors instead of moving them.
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onPistonBreak(event: BlockPistonExtendEvent) {
            if (event.blocks.any(::pistonBreaksReinforced)) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onPistonBreak(event: BlockPistonRetractEvent) {
            if (event.blocks.any(::pistonBreaksReinforced)) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onPistonExtend(event: BlockPistonExtendEvent) = move(event, event.blocks, pushing = true)

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onPistonRetract(event: BlockPistonRetractEvent) = move(event, event.blocks, pushing = false)

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onFall(event: EntityChangeBlockEvent) {
            val falling = event.entity as? FallingBlock ?: return
            if (event.to.isAir) {
                val entry = ReinforcementStore.remove(event.block) ?: return
                if (entry.spot.tier != Tier.WOODEN) {
                    falling.persistentDataContainer.set(fallingKey, PersistentDataType.INTEGER, entry.spot.tier.ordinal)
                }
            } else {
                val ordinal = falling.persistentDataContainer.get(fallingKey, PersistentDataType.INTEGER) ?: return
                ReinforcementStore.put(event.block, Tier.entries[ordinal])
            }
        }

        @EventHandler
        fun onJoin(event: PlayerJoinEvent) {
            clearSlowdown(event.player)
            showParticles(event.player)
        }

        @EventHandler
        fun onQuit(event: PlayerQuitEvent) = clearSlowdown(event.player)
    })

    override fun start() {
        for (player in Bukkit.getOnlinePlayers()) showParticles(player)
    }

    private fun Tier.label() = name.lowercase()

    /** Breaking a tier needs the Builder or Miner rank that placing it needs, so fresh characters can't break into vaults. */
    private fun canBreak(player: Player, tier: Tier) = player.gameMode == GameMode.CREATIVE ||
        ctx.rank(player, Builder) >= tier.requiredRank || ctx.rank(player, Miner) >= tier.requiredRank

    private fun denyBreak(player: Player, tier: Tier) = ctx.actionBar.show(
        player, "reinforcement",
        Component.text("Breaking ${tier.label()} reinforcement needs Builder or Miner ${tier.requiredRank.displayName}"),
    )

    private fun reinforceable(block: Block): Boolean {
        val data = block.blockData
        return block.type.isSolid || data is Door || data is TrapDoor || data is Gate || data is Bed
    }

    /** Doors and beds are two blocks; both halves share one reinforcement. */
    private fun halves(block: Block): List<Block> {
        val other = when (val data = block.blockData) {
            is Door -> block.getRelative(if (data.half == Bisected.Half.TOP) BlockFace.DOWN else BlockFace.UP)
            is Bed -> block.getRelative(if (data.part == Bed.Part.HEAD) data.facing.oppositeFace else data.facing)
            else -> null
        }
        return listOfNotNull(block, other)
    }

    /** Removes the reinforcement from a block and its other half, returning the tier it had. */
    private fun strip(block: Block): Tier? {
        val tier = ReinforcementStore.tier(block) ?: return null
        for (part in halves(block)) ReinforcementStore.remove(part)
        return tier
    }

    private fun dropNugget(block: Block, tier: Tier) {
        val nugget = tier.nugget ?: return
        block.world.dropItemNaturally(block.location.add(0.5, 0.5, 0.5), ItemStack.of(nugget))
    }

    private fun clearSlowdown(player: Player) {
        player.getAttribute(Attribute.BLOCK_BREAK_SPEED)?.removeModifier(breakKey)
    }

    private fun pistonBreaksReinforced(block: Block) =
        block.pistonMoveReaction == PistonMoveReaction.BREAK && ReinforcementStore.tier(block) != null

    private fun move(event: BlockPistonEvent, blocks: List<Block>, pushing: Boolean) {
        val facing = (event.block.blockData as? Directional)?.facing ?: return
        val direction = if (pushing) facing else facing.oppositeFace
        val moved = blocks.mapNotNull { block -> ReinforcementStore.remove(block)?.let { block to it } }
        for ((block, entry) in moved) ReinforcementStore.put(block.getRelative(direction), entry.spot.tier, entry.createdAt)
    }

    private fun showParticles(player: Player) {
        player.scheduler.runAtFixedRate(ctx.plugin, { _ ->
            if (Tier.forItem(player.inventory.itemInMainHand.type) == null) return@runAtFixedRate
            val world = player.world
            val centerX = player.location.blockX shr 4
            val centerZ = player.location.blockZ shr 4
            for (chunkX in centerX - PARTICLE_RADIUS_CHUNKS..centerX + PARTICLE_RADIUS_CHUNKS) {
                for (chunkZ in centerZ - PARTICLE_RADIUS_CHUNKS..centerZ + PARTICLE_RADIUS_CHUNKS) {
                    if (!Bukkit.isOwnedByCurrentRegion(world, chunkX, chunkZ) || !world.isChunkLoaded(chunkX, chunkZ)) continue
                    for (entry in ReinforcementStore.entries(world.getChunkAt(chunkX, chunkZ))) {
                        val spot = entry.spot
                        player.spawnParticle(
                            Particle.DUST,
                            (chunkX shl 4) + spot.localX + 0.5, spot.y + 0.5, (chunkZ shl 4) + spot.localZ + 0.5,
                            1, Particle.DustOptions(spot.tier.color, 1f),
                        )
                    }
                }
            }
        }, null, 10L, 10L)
    }
}
