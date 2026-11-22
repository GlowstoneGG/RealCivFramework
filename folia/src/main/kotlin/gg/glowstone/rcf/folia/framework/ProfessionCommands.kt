package gg.glowstone.rcf.folia.framework

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.DoubleArgument
import dev.jorel.commandapi.arguments.EntitySelectorArgument
import dev.jorel.commandapi.arguments.MultiLiteralArgument
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import gg.glowstone.rcf.core.professions.ProfessionProgression
import gg.glowstone.rcf.core.professions.SkillLevel
import gg.glowstone.rcf.folia.RealCivFrameworkPlugin
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.entity.Player

internal class ProfessionCommands(private val plugin: RealCivFrameworkPlugin, private val professions: ProfessionService) {
    private val catalog get() = professions.catalog

    fun register() {
        CommandAPICommand("profession")
            .withAliases("prof", "class")
            .withOptionalArguments(professionArgument())
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val selected = (args.get("profession") as String?)?.let(catalog::get)
                player.scheduler.run(plugin, { show(player, selected) }, null)
            })
            .register()

        CommandAPICommand("professionxp")
            .withAliases("classxp")
            .withPermission("rcf.professionxp")
            .withSubcommand(CommandAPICommand("list")
                .withArguments(EntitySelectorArgument.OnePlayer("player"))
                .executes(CommandExecutor { sender, args ->
                    val target = args.get("player") as Player
                    onTarget(target) {
                        val progress = professions.load(target)
                        sender.sendMessage("${target.name}'s profession XP:")
                        for (profession in catalog.professions) {
                            sender.sendMessage("${profession.displayName}: ${progress.xp(profession.id).formatXp()} (${progress.rank(profession.id).displayName})")
                        }
                    }
                }))
            .withSubcommand(CommandAPICommand("clear")
                .withArguments(EntitySelectorArgument.OnePlayer("player"))
                .executes(CommandExecutor { sender, args ->
                    val target = args.get("player") as Player
                    onTarget(target) {
                        professions.clear(target)
                        sender.sendMessage("Cleared ${target.name}'s profession XP.")
                    }
                }))
            .withSubcommand(CommandAPICommand("add")
                .withArguments(EntitySelectorArgument.OnePlayer("player"), professionArgument(), DoubleArgument("amount"))
                .executes(CommandExecutor { sender, args ->
                    val target = args.get("player") as Player
                    val profession = catalog[args.get("profession") as String]!!
                    val amount = args.get("amount") as Double
                    onTarget(target) {
                        professions.add(target, profession, amount, notify = false)
                        sender.sendMessage("${target.name}'s ${profession.displayName}: ${professions.load(target).xp(profession.id).formatXp()} XP")
                    }
                }))
            .withSubcommand(CommandAPICommand("set")
                .withArguments(EntitySelectorArgument.OnePlayer("player"), professionArgument(), DoubleArgument("amount", 0.0))
                .executes(CommandExecutor { sender, args ->
                    val target = args.get("player") as Player
                    val profession = catalog[args.get("profession") as String]!!
                    val amount = args.get("amount") as Double
                    onTarget(target) {
                        professions.set(target, profession, amount)
                        sender.sendMessage("${target.name}'s ${profession.displayName}: ${amount.formatXp()} XP")
                    }
                }))
            .register()
    }

    private fun professionArgument() = MultiLiteralArgument("profession", *catalog.ids().toTypedArray())

    private fun onTarget(target: Player, action: () -> Unit) {
        target.scheduler.run(plugin, { action() }, null)
    }

    private fun show(player: Player, selected: Profession?) {
        val progress = professions.load(player)
        player.sendMessage(Component.text("Your professions — ${progress.totalXp().formatXp()} total XP"))
        for (profession in if (selected == null) catalog.professions else listOf(selected)) {
            val rank = progress.rank(profession.id)
            val next = SkillLevel.entries.getOrNull(rank.ordinal + 1)
            val suffix = if (next == null) "max rank" else "${ProfessionProgression.threshold(next).formatXp()} XP for ${next.displayName}"
            player.sendMessage(Component.text("${profession.displayName}: ${rank.displayName} (${rank.ordinal}) · ${progress.xp(profession.id).formatXp()} XP · $suffix", TextColor.color(profession.color)))
            if (selected != null) {
                player.sendMessage(Component.text("Perk: ${profession.describePerk(rank)}"))
                if (next != null) {
                    val unlocks = catalog.nextUnlocks(progress, profession.id, next)
                    if (unlocks.isNotEmpty()) player.sendMessage(Component.text("Next rank recipes: ${unlocks.joinToString()}"))
                }
            }
        }
    }
}
