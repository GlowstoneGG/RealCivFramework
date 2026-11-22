package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.core.blocks.PlacedBits
import gg.glowstone.rcf.core.blocks.RecentBlockActions
import org.bukkit.ExplosionResult
import org.bukkit.block.Block
import org.bukkit.block.PistonMoveReaction
import org.bukkit.block.data.Directional
import org.bukkit.entity.FallingBlock
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockBurnEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockMultiPlaceEvent
import org.bukkit.event.block.BlockPistonEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityChangeBlockEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/**
 * Which blocks players placed, kept in chunk PDC for the block's lifetime (one bit per block, per 16-high section).
 * Callers must own the block's region.
 */
internal class PlacedBlocks(private val recent: RecentBlockActions = RecentBlockActions()) : Listener {
    private val fallingKey = civKey("placed_falling")

    /** True for a player-placed block, and for 10 seconds after one broke so drop handlers still see it. */
    fun isPlaced(block: Block) = get(block) || recent.wasPlacedBreak(block.position())

    /** False if this player was already rewarded for placing at this position within the last 24 hours. */
    fun allowBuilderAward(player: UUID, block: Block) = recent.allowBuilderAward(player, block.position())

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val blocks = (event as? BlockMultiPlaceEvent)?.replacedBlockStates?.map { it.block } ?: listOf(event.block)
        for (block in blocks) occupy(block, true)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (set(event.block, false)) recent.markPlacedBreak(event.block.position())
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) = explode(event.blockList(), event.explosionResult)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) = explode(event.blockList(), event.explosionResult)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBurn(event: BlockBurnEvent) {
        set(event.block, false)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonExtend(event: BlockPistonExtendEvent) = move(event, event.blocks, pushing = true)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPistonRetract(event: BlockPistonRetractEvent) = move(event, event.blocks, pushing = false)

    /** Falling blocks carry the bit on the entity; any other entity change (endermen, withers, trampling) clears it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityChangeBlock(event: EntityChangeBlockEvent) {
        val falling = event.entity as? FallingBlock
        when {
            falling == null -> set(event.block, false)
            event.to.isAir -> if (set(event.block, false)) falling.persistentDataContainer.set(fallingKey, PersistentDataType.BOOLEAN, true)
            else -> occupy(event.block, falling.persistentDataContainer.has(fallingKey))
        }
    }

    /** Wind charges and mob-griefing-off explosions list blocks they leave standing; only destroyed blocks lose the bit. */
    private fun explode(blocks: List<Block>, result: ExplosionResult) {
        if (result != ExplosionResult.DESTROY && result != ExplosionResult.DESTROY_WITH_DECAY) return
        for (block in blocks) set(block, false)
    }

    /** The piston's list also holds blocks it breaks (torches, leaves); they lose the bit instead of carrying it. */
    private fun move(event: BlockPistonEvent, blocks: List<Block>, pushing: Boolean) {
        val facing = (event.block.blockData as? Directional)?.facing ?: return
        val direction = if (pushing) facing else facing.oppositeFace
        val moved = blocks.filter { it.pistonMoveReaction != PistonMoveReaction.BREAK }
        val placed = blocks.associateWith { set(it, false) }
        for (block in moved) occupy(block.getRelative(direction), placed.getValue(block))
    }

    /** A new block took this position: drop any note about the block that broke there, then set its bit. */
    private fun occupy(block: Block, placed: Boolean) {
        recent.forgetPlacedBreak(block.position())
        set(block, placed)
    }

    private fun key(block: Block) = civKey("placed_${PlacedBits.section(block.y)}")

    private fun get(block: Block) = PlacedBits.get(
        block.chunk.persistentDataContainer.get(key(block), PersistentDataType.LONG_ARRAY),
        PlacedBits.index(block.x, block.y, block.z),
    )

    /** Sets the bit and returns what it was before. */
    private fun set(block: Block, placed: Boolean): Boolean {
        val pdc = block.chunk.persistentDataContainer
        val key = key(block)
        val words = pdc.get(key, PersistentDataType.LONG_ARRAY)
        val index = PlacedBits.index(block.x, block.y, block.z)
        val was = PlacedBits.get(words, index)
        if (was == placed) return was
        val next = PlacedBits.set(words, index, placed)
        if (next == null) pdc.remove(key) else pdc.set(key, PersistentDataType.LONG_ARRAY, next)
        return was
    }
}
