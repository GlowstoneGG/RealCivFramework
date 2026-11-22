package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.folia.RealCivFrameworkPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRecipeDiscoverEvent
import org.bukkit.inventory.Recipe

/** Hides and blocks recipes that `recipes.yml` gates until the player reaches the required rank, or blocks outright. */
internal class RecipeGate(
    private val plugin: RealCivFrameworkPlugin,
    private val professions: ProfessionService,
) : Listener {
    private val catalog get() = professions.catalog

    // Resolved once at enable. Recipes other plugins register later are still checked on craft, by their own key.
    private val managed: Map<NamespacedKey, RecipeRule> = buildMap {
        plugin.server.recipeIterator().forEach { recipe ->
            val key = (recipe as? Keyed)?.key ?: return@forEach
            catalog.rule(key, recipe.resultKey())?.let { put(key, it) }
        }
    }

    private fun Recipe.resultKey() = result.type.takeUnless { it.isAir }?.key

    fun sync(player: Player) {
        val progress = professions.load(player)
        for ((key, rule) in managed) {
            if (catalog.allows(rule, progress)) player.discoverRecipe(key) else player.undiscoverRecipe(key)
        }
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.scheduler.runDelayed(plugin, { sync(player) }, null, 1L)
    }

    @EventHandler
    fun onRankChange(event: ProfessionRankChangeEvent) = sync(event.player)

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDiscover(event: PlayerRecipeDiscoverEvent) {
        val rule = managed[event.recipe] ?: catalog.rule(event.recipe, null)
        if (!catalog.allows(rule, professions.load(event.player))) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPrepare(event: PrepareItemCraftEvent) {
        val player = event.view.player as? Player ?: return
        val recipe = event.recipe ?: return
        val key = (recipe as? Keyed)?.key ?: return
        val rule = catalog.rule(key, recipe.resultKey())
        if (catalog.allows(rule, professions.load(player))) return
        event.inventory.result = null
        player.sendActionBar(Component.text(describe(rule), NamedTextColor.RED))
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        val key = (event.recipe as? Keyed)?.key ?: return
        if (!catalog.allows(catalog.rule(key, event.recipe.resultKey()), professions.load(player))) event.isCancelled = true
    }

    private fun describe(rule: RecipeRule?) = when (rule) {
        is RecipeRule.Needs -> "Needs ${catalog[rule.professionId]?.displayName ?: rule.professionId} ${rule.rank.displayName}"
        else -> "Nobody can craft this here"
    }
}
