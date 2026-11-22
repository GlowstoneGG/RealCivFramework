# Extending Real Civ Framework

Real Civ Framework is meant to be forked. The kernel in `folia/.../framework/` handles profession XP and ranks, recipe gating, commands and the module lifecycle. Gameplay lives in modules under `folia/.../modules/`, one package each. [`modules/Modules.kt`](../folia/src/main/kotlin/gg/glowstone/rcf/folia/modules/Modules.kt) lists every module this build knows about. Each server's `plugins/RealCivFramework/modules.yml` decides which of them run.

## Turn modules on or off

`modules.yml` is created on first start with every module at its default. Set a module to `false` and restart. If a module needs another one that's off (for example `revive` needs `downed`), the plugin refuses to start and the error names both. See [modules.md](modules.md) for what each module does.

## Add a module

1. Create a package under `modules/`, for example `modules/sprinting/Sprinting.kt`:

   ```kotlin
   internal object Sprinting : Module("sprinting") {
       override val reads = listOf(Builder)   // professions whose ranks you read (only used for startup warnings)

       override fun listeners(ctx: CivContext) = listOf(object : Listener {
           @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
           fun onSprint(event: PlayerToggleSprintEvent) {
               if (event.isSprinting && ctx.rank(event.player, Builder) >= SkillLevel.MASTER) { /* ... */ }
           }
       })
   }
   ```

2. Add `Sprinting` to `allModules` in `Modules.kt`.
3. If it builds on another module, pass it in: `Module("sprinting", dependsOn = listOf(Fullness))`. Rivet starts it after its dependencies and stops it before them.

A module can also bring its own professions with `override val professions = listOf(...)`. They join the catalog only while the module is on.

## Add a profession

1. Create `modules/professions/Fisher.kt`:

   ```kotlin
   internal object Fisher : Profession(
       id = "fisher",                 // storage key; never change it once players have XP
       displayName = "Fisher",
       color = 0x1F8FBF,
       perks = listOf(0.0, 0.05, 0.10, 0.15, 0.20, 0.25), // one value per rank, Novice → Grandmaster
       perkLabel = "extra fish chance",
   ) {
       override fun listeners(ctx: CivContext) = listOf(object : Listener {
           @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
           fun onFish(event: PlayerFishEvent) {
               if (event.state == PlayerFishEvent.State.CAUGHT_FISH) {
                   ctx.award(event.player, Fisher, 3.0, cooldownMs = 1_000)
               }
           }
       })
   }
   ```

2. Add it to `Professions.professions`, or to the `professions` list of whichever module it belongs to.
3. To gate recipes, override `recipes`. These are the defaults written to `recipes.yml` the first time the profession loads; after that the file decides (see [modules.md](modules.md#recipe-gates)). A recipe can only be gated once, and the plugin refuses to start if it is listed twice.

To remove a profession, take it out of the list. Stored XP stays in player data and comes back if you re-add it. To rebalance, edit the numbers in its file. Override `minimumShare(rank)` to make a rank require that share of the player's total profession XP.

## What `CivContext` gives you

| Member | Use |
|---|---|
| `rank(player, profession)` / `perk(player, profession)` | Cached ranks, safe to read from any thread. Unloaded professions read as Novice. |
| `award(player, profession, amount, cooldownMs, key)` | Grant XP. `key` separates cooldowns, for example ore vs stone. |
| `blocks` | Recently placed block tracking, so place/break loops can't farm XP. |
| `actionBar.show(player, slot, text, status, ttlMs)` | Show a line on the action bar without erasing other modules' lines. Use one slot per kind of message; a new message replaces the old one in its slot. Pass `status = true` for lines about the player's ongoing state, which sit leftmost. Safe from any thread. Never call `player.sendActionBar` directly. |
| `plugin` | For schedulers. |
| `professions` | Direct storage access (`load`, `add`, `set`, `clear`). |
| `civKey(name)` | A `NamespacedKey` under the fixed `rcf` namespace, for PDC data. |

Other plugins and modules can listen for `ProfessionRankChangeEvent`, and for `ProfessionXpGainEvent`, which fires when gameplay awards XP through `award` (admin commands don't fire it).

## Folia threading

Listeners run on the region thread that owns the event, not on a main thread. `award` and anything touching a player's data or inventory must run on that player's region. If the event belongs to another entity's region (a mob death, a splash potion, breeding), hop over with `player.scheduler.run(ctx.plugin, { ... }, null)`. `Hunter.kt` and `Healer.kt` show the pattern. Repeating work goes on the player's or entity's own scheduler, never a global loop over all players.

## Storage

Profession XP is stored in each player's persistent data under `rcf:profession_xp_<id>`. Modules store their own data under the same namespace through `civKey`. The namespace is fixed, so renaming the plugin in a fork keeps player data. Changing a profession's `id` doesn't: XP under the old id is orphaned.
