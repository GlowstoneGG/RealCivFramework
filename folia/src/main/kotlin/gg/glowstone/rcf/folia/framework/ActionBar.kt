package gg.glowstone.rcf.folia.framework

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shares each player's action bar between modules. A slot holds one line until its TTL passes or it is set again,
 * and the live slots are joined into one line, status slots first. The line is redrawn on the player's own scheduler
 * from their first message until they quit, and nothing is sent while they have no live slots.
 */
internal class ActionBar(private val plugin: Plugin) : Listener {
    private class Slot(val text: Component, val status: Boolean, val expiresAt: Long)

    private val slots = ConcurrentHashMap<UUID, ConcurrentHashMap<String, Slot>>()
    private val ticking = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Shows [text] in [slot] for [ttlMs]. Safe from any thread. Messages in different slots show side by side, so give
     * each kind of message its own slot. [status] is for lines about the player's ongoing state; they sit leftmost.
     */
    fun show(player: Player, slot: String, text: Component, status: Boolean = false, ttlMs: Long = if (status) STATUS_MS else NOTICE_MS) {
        slots.computeIfAbsent(player.uniqueId) { ConcurrentHashMap() }[slot] = Slot(text, status, System.currentTimeMillis() + ttlMs)
        // The player's scheduler retires the task when they quit.
        if (ticking.add(player.uniqueId)) {
            val task = player.scheduler.runAtFixedRate(plugin, { draw(player) }, { ticking.remove(player.uniqueId) }, 1L, PERIOD_TICKS)
            if (task == null) ticking.remove(player.uniqueId)
        }
    }

    private fun draw(player: Player) {
        val live = slots[player.uniqueId] ?: return
        val now = System.currentTimeMillis()
        live.values.removeIf { it.expiresAt <= now }
        // Sorted by slot name after status, so lines hold their place between redraws.
        val lines = live.entries.sortedWith(compareBy({ !it.value.status }, { it.key })).map { it.value.text }
        if (lines.isNotEmpty()) player.sendActionBar(Component.join(JoinConfiguration.separator(SEPARATOR), lines))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        slots.remove(event.player.uniqueId)
    }

    private companion object {
        const val PERIOD_TICKS = 4L
        const val NOTICE_MS = 2_000L
        const val STATUS_MS = 4_000L
        val SEPARATOR = Component.text(" │ ", NamedTextColor.DARK_GRAY)
    }
}
