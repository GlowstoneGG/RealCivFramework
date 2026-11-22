package gg.glowstone.rcf.folia

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import dev.jorel.commandapi.CommandAPI
import dev.jorel.commandapi.CommandAPIPaperConfig
import gg.glowstone.rcf.folia.framework.ActionBar
import gg.glowstone.rcf.folia.framework.ProfessionCatalog
import gg.glowstone.rcf.folia.framework.ProfessionCommands
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.ModuleConfig
import gg.glowstone.rcf.folia.framework.PlacedBlocks
import gg.glowstone.rcf.folia.framework.ProfessionService
import gg.glowstone.rcf.folia.framework.RecipeConfig
import gg.glowstone.rcf.folia.framework.RecipeGate
import gg.glowstone.rcf.folia.modules.allModules
import gg.glowstone.rivet.Lifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.ContinuationInterceptor

class RealCivFrameworkPlugin : SuspendingJavaPlugin() {
    private lateinit var lifecycle: Lifecycle

    override fun onLoad() {
        CommandAPI.onLoad(CommandAPIPaperConfig(this))
    }

    override suspend fun onEnableAsync() {
        CommandAPI.onEnable()
        // MCCoroutine exposes a CoroutineContext; Rivet needs its dispatcher.
        val dispatcher = globalRegionDispatcher[ContinuationInterceptor] as? CoroutineDispatcher
            ?: error("MCCoroutine did not provide a global region dispatcher")

        lifecycle = Lifecycle(dispatcher = dispatcher, instrumentationName = "rcf")
        dataFolder.mkdirs()
        val modules = ModuleConfig.load(dataFolder.resolve("modules.yml"), allModules, logger::warning)
        val loaded = modules.flatMap { it.professions }
        val catalog = ProfessionCatalog(loaded, RecipeConfig.load(dataFolder.resolve("recipes.yml"), loaded, logger::warning))
        for (module in modules) for (profession in module.reads.filter { catalog[it.id] == null }) {
            logger.warning("Module '${module.name}' reads profession '${profession.id}', which isn't loaded; players count as Novice in it")
        }
        val actionBar = ActionBar(this)
        val professions = ProfessionService(catalog, actionBar)
        val ctx = CivContext(this, professions, PlacedBlocks(), actionBar)
        val recipes = RecipeGate(this, professions, actionBar)
        ProfessionCommands(this, professions).register()
        server.pluginManager.registerEvents(actionBar, this)
        server.pluginManager.registerEvents(ctx, this)
        server.pluginManager.registerEvents(ctx.blocks, this)
        server.pluginManager.registerEvents(recipes, this)
        for (player in server.onlinePlayers) player.scheduler.run(this, {
            professions.refresh(player)
            recipes.sync(player)
        }, null)
        for (module in modules) module.bind(ctx)
        lifecycle.addAll(modules)
        lifecycle.start()
        logger.info("Real Civ Framework enabled with modules: ${modules.joinToString { it.name }}")
    }

    override suspend fun onDisableAsync() {
        CommandAPI.onDisable()
        if (::lifecycle.isInitialized) {
            lifecycle.stop()
        }
    }
}
