package gg.glowstone.rcf.folia.modules.names

import dev.jorel.commandapi.CommandAPICommand
import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.arguments.EntitySelectorArgument
import dev.jorel.commandapi.arguments.MultiLiteralArgument
import dev.jorel.commandapi.arguments.StringArgument
import dev.jorel.commandapi.executors.CommandExecutor
import dev.jorel.commandapi.executors.PlayerCommandExecutor
import gg.glowstone.rcf.folia.framework.CivContext
import gg.glowstone.rcf.folia.framework.Module
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Statistic
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerJoinEvent
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Every player plays under a name generated from `names.yml` instead of their account name. New players are offered two more,
 * and have 10 minutes of play to pick one before their name becomes permanent. The name replaces the account name
 * in the login profile, so chat, the tab list, nametags and death messages all show it.
 */
internal object Names : Module("names", enabledByDefault = false) {
    private const val WINDOW_TICKS = 10 * 60 * 20
    private const val CHECK_TICKS = 60L * 20
    private const val EXTRA_OFFERS = 2

    private data class Identity(
        val account: String,
        val name: String,
        val locked: Boolean,
        val offers: List<String>,
        /** Playtime in ticks when the offer was first shown, so the window only runs while the player is online. */
        val offeredAt: Int?,
    ) {
        val choices get() = listOf(name) + offers
    }

    private sealed interface Change {
        data class Done(val identity: Identity, val renamed: Boolean) : Change
        data class Refused(val reason: String) : Change
    }

    private val identities = ConcurrentHashMap<UUID, Identity>()
    private val lock = Any() // guards pool and every identity change
    private val fileLock = Any()
    private lateinit var pool: NamePool
    private lateinit var dataFile: File

    private fun load(ctx: CivContext) {
        val namesFile = ctx.plugin.dataFolder.resolve("names.yml")
        if (!namesFile.exists()) ctx.plugin.saveResource("names.yml", false)
        val names = YamlConfiguration.loadConfiguration(namesFile)
        pool = NamePool(names.getStringList("first"), names.getStringList("last"), names.getString("separator", "_")!!)
        if (pool.size == 0) ctx.plugin.logger.severe("names.yml makes no valid names; new players can't join")

        dataFile = ctx.plugin.dataFolder.resolve("identities.yml")
        val yaml = YamlConfiguration.loadConfiguration(dataFile)
        for (key in yaml.getKeys(false)) {
            val id = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
            val section = yaml.getConfigurationSection(key) ?: continue
            val name = section.getString("name") ?: continue
            val identity = Identity(
                account = section.getString("account").orEmpty(),
                name = name,
                locked = section.getBoolean("locked"),
                offers = section.getStringList("offers"),
                offeredAt = if (section.isInt("offered-at")) section.getInt("offered-at") else null,
            )
            identities[id] = identity
            identity.choices.forEach(pool::claim)
        }
        ctx.plugin.logger.info("Names: ${pool.size} possible, ${identities.size} players named")
    }

    private fun write() = synchronized(fileLock) {
        val yaml = YamlConfiguration()
        for ((id, identity) in identities) {
            val section = yaml.createSection(id.toString())
            section.set("account", identity.account)
            section.set("name", identity.name)
            section.set("locked", identity.locked)
            if (identity.offers.isNotEmpty()) section.set("offers", identity.offers)
            identity.offeredAt?.let { section.set("offered-at", it) }
        }
        yaml.save(dataFile)
    }

    private fun save() {
        ctx.plugin.server.asyncScheduler.runNow(ctx.plugin) { write() }
    }

    /** The player's identity, creating one with a fresh name and offers. Null when the list has run out. */
    private fun assign(id: UUID, account: String): Identity? = synchronized(lock) {
        identities[id]?.let { existing ->
            if (existing.account == account) return existing
            return existing.copy(account = account).also { identities[id] = it; save() }
        }
        val name = pool.next() ?: return null
        val offers = List(EXTRA_OFFERS) { pool.next() }.filterNotNull()
        Identity(account, name, locked = false, offers, offeredAt = null).also { identities[id] = it; save() }
    }

    /** Locks [name] as the player's permanent name, giving back their other names. */
    private fun settle(id: UUID, name: String, allowTaken: Boolean = false): Change = synchronized(lock) {
        val current = identities[id] ?: return Change.Refused("That player has no name yet")
        val mine = current.choices.any { it.equals(name, ignoreCase = true) }
        if (!mine && !allowTaken && pool.isTaken(name)) return Change.Refused("$name is already taken")
        current.choices.forEach(pool::release)
        pool.claim(name)
        val updated = current.copy(name = name, locked = true, offers = emptyList())
        identities[id] = updated
        save()
        Change.Done(updated, renamed = name != current.name)
    }

    /** Ticks of play left to choose, or null if the window hasn't started. Callers must be on the player's region. */
    private fun remaining(player: Player, identity: Identity): Int? =
        identity.offeredAt?.let { WINDOW_TICKS - (player.getStatistic(Statistic.PLAY_ONE_MINUTE) - it) }

    private fun minutes(ticks: Int) = (ticks + 1199) / 1200

    private fun kickRenamed(player: Player, name: String) {
        player.scheduler.run(ctx.plugin, {
            player.kick(Component.text("Your name is now $name. Rejoin to play as $name.", NamedTextColor.GOLD))
        }, null)
    }

    private fun showOffers(player: Player, identity: Identity) {
        val left = minutes(remaining(player, identity) ?: WINDOW_TICKS)
        player.sendMessage(Component.text(
            "Choose the name you'll be known by. You have $left minute${if (left == 1) "" else "s"} of play to decide; " +
                "after that, ${identity.name} is yours for good.",
            NamedTextColor.GOLD,
        ))
        var line = Component.text("  ")
        for ((i, choice) in identity.choices.withIndex()) {
            line = line.append(Component.text("[$choice${if (i == 0) " (current)" else ""}]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/name $choice"))
                .hoverEvent(HoverEvent.showText(Component.text("Choose $choice"))))
                .append(Component.text("  "))
        }
        player.sendMessage(line)
    }

    /** Runs every minute while the player is online and hasn't chosen. Returns false once there is nothing left to do. */
    private fun check(player: Player): Boolean {
        val identity = identities[player.uniqueId]?.takeUnless { it.locked } ?: return false
        val left = remaining(player, identity) ?: return true
        if (left <= 0) {
            settle(player.uniqueId, identity.name)
            player.sendMessage(Component.text("Your name is now permanent: ${identity.name}", NamedTextColor.GOLD))
            return false
        }
        if (left <= CHECK_TICKS || left in (4 * CHECK_TICKS + 1)..(5 * CHECK_TICKS)) {
            player.sendMessage(Component.text("${minutes(left)} minute${if (minutes(left) == 1) "" else "s"} left to choose your name.", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.runCommand("/name"))
                .hoverEvent(HoverEvent.showText(Component.text("Show my choices"))))
        }
        return true
    }

    private fun choose(player: Player, choice: String, confirmed: Boolean) {
        val identity = identities[player.uniqueId] ?: return
        if (identity.locked) {
            player.sendMessage(Component.text("Your name is ${identity.name}, and it's permanent.", NamedTextColor.RED))
            return
        }
        val left = remaining(player, identity)
        if (left != null && left <= 0) {
            check(player)
            return
        }
        val name = identity.choices.firstOrNull { it.equals(choice, ignoreCase = true) }
        if (name == null) {
            player.sendMessage(Component.text("$choice isn't one of your choices.", NamedTextColor.RED))
            showOffers(player, identity)
            return
        }
        if (!confirmed) {
            val note = if (name == identity.name) "Keep $name for good?" else "Become $name for good? You'll be disconnected to apply it."
            player.sendMessage(Component.text("$note ", NamedTextColor.GOLD).append(
                Component.text("[Confirm]", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/name $name confirm"))
                    .hoverEvent(HoverEvent.showText(Component.text("This can't be undone")))))
            return
        }
        when (val change = settle(player.uniqueId, name)) {
            is Change.Refused -> player.sendMessage(Component.text(change.reason, NamedTextColor.RED))
            is Change.Done -> if (change.renamed) kickRenamed(player, name)
                else player.sendMessage(Component.text("Your name is now permanent: $name", NamedTextColor.GOLD))
        }
    }

    private fun registerCommands() {
        CommandAPICommand("name")
            .withOptionalArguments(
                StringArgument("choice").replaceSuggestions(ArgumentSuggestions.strings { info ->
                    (info.sender as? Player)?.let { identities[it.uniqueId] }?.takeUnless { it.locked }?.choices?.toTypedArray() ?: emptyArray()
                }),
                MultiLiteralArgument("confirm", "confirm"),
            )
            .executesPlayer(PlayerCommandExecutor { player, args ->
                val choice = args.get("choice") as String?
                val confirmed = args.get("confirm") != null
                player.scheduler.run(ctx.plugin, {
                    val identity = identities[player.uniqueId]
                    when {
                        identity == null -> player.sendMessage(Component.text("You don't have a name yet. Rejoin to get one.", NamedTextColor.RED))
                        choice != null -> choose(player, choice, confirmed)
                        identity.locked -> player.sendMessage(Component.text("Your name is ${identity.name}, and it's permanent.", NamedTextColor.GOLD))
                        else -> showOffers(player, identity)
                    }
                }, null)
            })
            .register()

        CommandAPICommand("setname")
            .withPermission("rcf.names.admin")
            .withArguments(EntitySelectorArgument.OnePlayer("player"), StringArgument("name"))
            .executes(CommandExecutor { sender, args ->
                val target = args.get("player") as Player
                val name = (args.get("name") as String).replace(' ', '_').filter { it.isLetterOrDigit() || it == '_' }
                if (!NamePool.isValid(name)) {
                    sender.sendMessage("Names are 3 to 16 letters, digits or underscores.")
                    return@CommandExecutor
                }
                when (val change = settle(target.uniqueId, name)) {
                    is Change.Refused -> sender.sendMessage(change.reason)
                    is Change.Done -> {
                        sender.sendMessage("${target.name} is now $name.")
                        if (change.renamed) kickRenamed(target, name)
                    }
                }
            })
            .register()

        CommandAPICommand("rerollname")
            .withPermission("rcf.names.admin")
            .withArguments(EntitySelectorArgument.OnePlayer("player"))
            .executes(CommandExecutor { sender, args ->
                val target = args.get("player") as Player
                // Held across both steps so nobody else can take the name in between.
                val change = synchronized(lock) {
                    pool.next()?.let { name ->
                        pool.release(name)
                        settle(target.uniqueId, name)
                    }
                }
                when (change) {
                    null -> sender.sendMessage("names.yml has no unused names left.")
                    is Change.Refused -> sender.sendMessage(change.reason)
                    is Change.Done -> {
                        sender.sendMessage("${target.name} is now ${change.identity.name}.")
                        kickRenamed(target, change.identity.name)
                    }
                }
            })
            .register()

        CommandAPICommand("realname")
            .withPermission("rcf.names.admin")
            .withArguments(EntitySelectorArgument.OnePlayer("player"))
            .executes(CommandExecutor { sender, args ->
                val target = args.get("player") as Player
                val account = identities[target.uniqueId]?.account?.ifEmpty { null } ?: "unknown"
                sender.sendMessage(Component.text("${target.name} is account $account (${target.uniqueId})")
                    .clickEvent(ClickEvent.copyToClipboard(target.uniqueId.toString()))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy the UUID"))))
            })
            .register()
    }

    override fun listeners(ctx: CivContext): List<Listener> {
        load(ctx)
        return listOf(object : Listener {
            @EventHandler(priority = EventPriority.HIGH)
            fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
                if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
                val identity = assign(event.uniqueId, event.name)
                if (identity == null) {
                    ctx.plugin.logger.severe("names.yml has no unused names left; refusing ${event.name}")
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.text("This server has run out of names for new players. Please tell an admin."))
                    return
                }
                event.playerProfile = ctx.plugin.server.createProfileExact(event.uniqueId, identity.name)
                    .apply { setProperties(event.playerProfile.properties) }
            }

            @EventHandler
            fun onJoin(event: PlayerJoinEvent) {
                val player = event.player
                var identity = identities[player.uniqueId]?.takeUnless { it.locked } ?: return
                if (identity.offeredAt == null) {
                    val playtime = player.getStatistic(Statistic.PLAY_ONE_MINUTE)
                    identity = synchronized(lock) {
                        identities.computeIfPresent(player.uniqueId) { _, it -> it.copy(offeredAt = it.offeredAt ?: playtime) }
                    } ?: return
                    save()
                }
                showOffers(player, identity)
                player.scheduler.runAtFixedRate(ctx.plugin, { task -> if (!check(player)) task.cancel() }, null, CHECK_TICKS, CHECK_TICKS)
            }
        })
    }

    override fun start(ctx: CivContext) = registerCommands()

    override fun stop() {
        if (::dataFile.isInitialized) write()
    }
}
