package gg.glowstone.rcf.folia.framework

import gg.glowstone.rivet.Feature
import gg.glowstone.rivet.Phase
import org.bukkit.event.Listener

/**
 * An optional gameplay system, switched on or off in `modules.yml`. Each module is a Rivet [Feature]:
 * it starts after the modules it [dependsOn], and stops in reverse order.
 *
 * Add new modules to `modules/Modules.kt`.
 */
internal abstract class Module(
    name: String,
    val dependsOn: List<Module> = emptyList(),
    val enabledByDefault: Boolean = true,
) : Feature(name) {
    /** Professions this module adds to the catalog. */
    open val professions: List<Profession> = emptyList()

    /** Professions this module reads ranks from. If one isn't loaded, players count as Novice in it. */
    open val reads: List<Profession> = emptyList()

    lateinit var ctx: CivContext
        private set

    /** True once this module is switched on in `modules.yml` and bound. Lets other modules read it softly. */
    val enabled: Boolean get() = ::ctx.isInitialized

    open fun listeners(): List<Listener> = emptyList()

    /** Runs once after listeners are registered, on the global region thread. */
    open fun start() {}

    /** Runs on plugin disable, in reverse start order. */
    open fun stop() {}

    @Suppress("unused")
    val enable: Phase by phase(after = dependsOn.map { it.enable }) {
        val ctx = ctx
        val manager = ctx.plugin.server.pluginManager
        for (listener in professions.flatMap { it.listeners(ctx) } + listeners()) manager.registerEvents(listener, ctx.plugin)
        start()
    }

    init {
        onDisable { stop() }
    }

    fun bind(ctx: CivContext) {
        this.ctx = ctx
    }
}
