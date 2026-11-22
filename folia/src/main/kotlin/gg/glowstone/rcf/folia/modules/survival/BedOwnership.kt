package gg.glowstone.rcf.folia.modules.survival

import gg.glowstone.rcf.folia.framework.Module
import gg.glowstone.rcf.folia.framework.civKey
import org.bukkit.Bukkit
import org.bukkit.block.Block
import org.bukkit.block.TileState
import org.bukkit.block.data.type.Bed
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.player.PlayerBedEnterEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import java.util.UUID

/** Sleeping in a bed claims it. Nobody else can use it, and each player owns one bed at a time. */
internal object BedOwnership : Module("bed-ownership") {
    private val ownerKey = civKey("bed_owner")
    private val playerBedKey = civKey("bed")

    private fun Block.bedHead(): Block? {
        val data = blockData as? Bed ?: return null
        return if (data.part == Bed.Part.HEAD) this else getRelative(data.facing)
    }

    private fun Block.owner(): UUID? = (state as? TileState)?.persistentDataContainer
        ?.get(ownerKey, PersistentDataType.STRING)?.let(UUID::fromString)

    private fun Block.setOwner(owner: UUID?) {
        val state = state as? TileState ?: return
        if (owner == null) state.persistentDataContainer.remove(ownerKey)
        else state.persistentDataContainer.set(ownerKey, PersistentDataType.STRING, owner.toString())
        state.update()
    }

    /** Releases the player's previous bed if its chunk is loaded. */
    private fun releaseOld(player: Player) {
        val parts = player.persistentDataContainer.get(playerBedKey, PersistentDataType.STRING)?.split(';') ?: return
        val world = Bukkit.getWorld(UUID.fromString(parts[0])) ?: return
        val (x, y, z) = parts.drop(1).map(String::toInt)
        val owner = player.uniqueId
        // shortcut: a bed in an unloaded chunk stays claimed by its old owner, upgrade to a claim registry if that bothers players
        Bukkit.getRegionScheduler().run(ctx.plugin, world, x shr 4, z shr 4) { _ ->
            if (!world.isChunkLoaded(x shr 4, z shr 4)) return@run
            val bed = world.getBlockAt(x, y, z)
            if (bed.owner() == owner) bed.setOwner(null)
        }
    }

    override fun listeners() = listOf(object : Listener {
        @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
        fun onInteract(event: PlayerInteractEvent) {
            if (event.action != Action.RIGHT_CLICK_BLOCK || event.hand != EquipmentSlot.HAND) return
            val head = event.clickedBlock?.bedHead() ?: return
            val owner = head.owner() ?: return
            val player = event.player
            if (owner != player.uniqueId) {
                // Cancelling the click also stops others setting their spawn here.
                event.isCancelled = true
                player.sendActionBar("This bed belongs to someone else")
            } else if (player.isSneaking) {
                event.isCancelled = true
                head.setOwner(null)
                player.persistentDataContainer.remove(playerBedKey)
                player.setRespawnLocation(null)
                player.sendActionBar("You gave up this bed")
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onSleep(event: PlayerBedEnterEvent) {
            if (event.bedEnterResult != PlayerBedEnterEvent.BedEnterResult.OK) return
            val head = event.bed.bedHead() ?: return
            val player = event.player
            if (head.owner() == player.uniqueId) return
            releaseOld(player)
            head.setOwner(player.uniqueId)
            player.persistentDataContainer.set(playerBedKey, PersistentDataType.STRING, "${head.world.uid};${head.x};${head.y};${head.z}")
            player.sendActionBar("You claimed this bed")
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        fun onBreak(event: BlockBreakEvent) {
            val owner = event.block.bedHead()?.owner() ?: return
            if (owner == event.player.uniqueId) return
            val online = Bukkit.getPlayer(owner) ?: return
            online.scheduler.run(ctx.plugin, { online.sendMessage("Your bed was broken.") }, null)
        }
    })
}
