# Modules

Every module can be switched off in `plugins/RealCivFramework/modules.yml`. All are on by default unless noted.

## professions

The seven default professions: Farmer, Builder, Miner, Healer, Librarian, Hunter and Blacksmith. Players check theirs with `/profession`. Most other modules read these ranks; with this module off, everyone counts as Novice.

Hunter is the fighter. Killing any mob gives Hunter XP equal to its max health (1 second cooldown). Kills drop 20% more loot per rank, up to double at Grandmaster.

Miner XP comes from breaking ores and stone. Blocks a player placed, and stone that lava and water form (stone generators), give none.

### Recipe gates

`plugins/RealCivFramework/recipes.yml` sets which profession rank unlocks each recipe, and which recipes nobody can craft. It is created on first start from the gates each profession declares; by default Blacksmith Apprentice unlocks the anvil, Hunter Apprentice the fishing rod and bow, and Hunter Journeyman the crossbow.

```yaml
blocked:
  - minecraft:beacon           # nobody can craft this
professions:
  hunter:
    apprentice: [minecraft:fishing_rod, minecraft:bow]
    journeyman: [minecraft:crossbow]
  blacksmith:
    apprentice: [minecraft:anvil]
```

- Entries can be recipe keys or item ids. An item id covers every vanilla recipe that makes that item, so `minecraft:white_bed` also covers dyeing a bed white. Other plugins' recipes are only matched by their own recipe key.
- Locked recipes are hidden from the recipe book and leave the crafting result empty, with a note on the action bar naming the rank needed. They appear in the book as soon as the player qualifies, and disappear again if they lose the rank.
- A recipe can only be listed once. Professions missing from the file are added with their defaults; set one to `{}` to gate nothing. Changes apply after a restart.

## death-penalty

Off by default. A real death wipes all of a player's profession XP.

## reinforcement

Right-click a block with a reinforcement item to make it harder to break.

| Tier | Item | Needs | Break speed | Survives block explosions | Builder XP |
|---|---|---|---|---|---|
| Wooden | Stick (uses one plank from your inventory) | Nothing | ÷1.5 | 10% | 4 as a Novice, then 1 |
| Copper | Copper ingot (used up) | Builder Apprentice | ÷5 | 75% | 5 |
| Iron | Iron ingot (used up) | Builder Journeyman | ÷9 | 95% | 15 |

- Wooden reinforcement wears off after 40 minutes. Copper and iron are permanent.
- You can upgrade a block to a higher tier, but not reapply the same or a lower one.
- Solid blocks, doors, trapdoors, fence gates and beds can be reinforced. Doors and beds are reinforced as a whole.
- Breaking a reinforced block needs a pickaxe. Copper and iron give back a nugget half the time.
- Breaking copper reinforcement needs Builder or Miner Apprentice, and iron needs Builder or Miner Journeyman: the same rank as placing it. Lower ranks can't start breaking it. Creative mode skips this.
- Creeper, TNT and other entity explosions can't destroy reinforced blocks. Bed and respawn-anchor explosions roll the survival chance above; a block that survives loses its reinforcement and drops a nugget.
- Reinforced blocks don't burn, zombies can't break reinforced doors, a reinforced door stays put when the block under it is removed, pistons can't break one, and copper and iron reinforcement moves with pistons and falling blocks.
- Hold a stick, copper ingot or iron ingot to see reinforced blocks within three chunks as coloured particles: brown for wooden, orange for copper, grey for iron.

## fullness

Replaces vanilla hunger with fullness from 0 to 100, shown on the food bar (each drumstick is 10 fullness). New players start at 40, and it drains 0.05 per second, so a full bar lasts about 33 minutes. Food adds 5 fullness per point of nutrition, but each time the same food appears in your last 5 meals it gives 20% less (at most 80% less). You can't eat something that would take you over 100. Dying resets you to 40 and forgets your recent meals. At 0 you starve as in vanilla. If `no-natural-regen` is off, vanilla healing from a full bar costs 7.5 fullness per HP.

## no-natural-regen

Players no longer heal from a full food bar or from peaceful difficulty. Healing comes from food effects, potions, Healers and campfire rest. World gamerules are not changed.

## bed-ownership

Sleeping in a bed claims it. Nobody else can sleep in it or set their spawn there. You own one bed at a time: claiming a new one releases the old one (if its chunk is loaded). Sneak and right-click your bed to give it up, which also clears your respawn point. If someone breaks your bed, you're told.

## bed-cooldown

Respawning at your bed or respawn anchor puts it on a 10-minute cooldown. If you die again before it ends, you respawn at world spawn instead, and you're told how long until it's ready. The cooldown counts real time, including while you're offline. A respawn at world spawn doesn't restart it.

## campfire-rest

While you sleep within 4 blocks (2 vertically) of a lit campfire, you heal 1 HP every 45 seconds, up to 5 HP per Minecraft day.

## downed

Lethal damage downs a player instead of killing them, unless the hit overshoots their health by 10 or more, or they hold a totem of undying. Downed players crawl, can't interact, fight, build, eat, pick up items, use portals or throw ender pearls, and see darkness every few seconds. They bleed out after 7 minutes, twice as fast while crawling. Any further damage kills them. Logging out doesn't help: the timer keeps running while they're offline.

## revive

Needs `downed`. Healers (Apprentice or higher) revive downed players by channelling beside them: 5 seconds at Apprentice, down to 2 seconds at Grandmaster. They must keep sneaking and stay within 2.5 blocks. The revived player stands up with 2 HP per Healer rank, and the Healer earns 20 XP. Healers can also bandage hurt players who are still standing, restoring 4 HP plus 1.5 per rank above Apprentice (7.5 second cooldown, 5 XP).

## carry

Needs `downed`. Anyone can pick up a downed player and carry them. Carrying slows you by 30%, 4 points less per Healer rank. The carried player can't climb off.

## corpses

When a player dies, their drops stay with a corpse at the spot for 15 minutes instead of scattering. Anyone can open it and take the items, one player at a time. When it expires, whatever is left falls to the ground.

## combat-tag

Hitting another player, or being hit by one, tags both for 60 seconds, shown as a red bar. The tag survives logging out.

## combat-log

Needs `combat-tag`. Logging out while tagged leaves your body standing where you were for 15 seconds, wearing your gear (each hit resets the timer). If someone kills it, it drops a copy of your inventory, and you die with an empty inventory when you return. If it survives, you come back as you left.

### Controls

| Action | Gesture |
|---|---|
| Revive a downed player (Healer) | Sneak + right-click them with an empty hand, and keep sneaking |
| Bandage a hurt player (Healer) | Sneak + right-click them with an empty hand |
| Pick up a downed player | Right-click them with an empty hand |
| Put them down | Sneak |
| Loot a corpse | Right-click it |
| Search a player (`pat-down`) | Sneak + right-click them holding a spyglass |

## armor-weight

Armour has weight: material (leather 1, turtle 1.5, chainmail or copper 2, gold 2.5, iron 3, diamond 5, netherite 6) times slot (helmet 5, chestplate 8, leggings 7, boots 4). Each point of weight slows you by 0.1%, so full iron (72) is about 7% slower. The action bar shows your load when it changes: Light, Medium, Heavy or Overloaded. Sprint-jumping more than 3 times in 4 seconds at Medium or heavier gives Slowness I for 2 seconds. With `stamina` on, this Slowness is replaced by heavier armour draining stamina faster.

## stamina

Stamina runs from 0 to 100 and is shown on the XP bar while it isn't full (your level number stays as it is). It's for bursts: sprinting and sprint-swimming cost 3 per second, sprint-jumps 1, landed melee hits 4, and blocking with a shield 2 per point of damage blocked (at most 20). Walking, plain jumps, swimming, mining, building, farming, bows, elytras and riding cost nothing.

- Armour makes sprinting and sprint-jumping cost more: 1% per point of weight (from `armor-weight`). Unarmoured you can sprint for 33 seconds, in full iron about 19, in netherite about 14.
- Stamina comes back 1 second after you last spent any: 6 per second standing still, sneaking or riding, and 3 per second walking, doubling over 5 seconds of unbroken rest. A full refill takes about 11 seconds standing. Every 100 stamina regained costs 1 fullness, and below 30 fullness it comes back at half speed.
- At 0 you're Winded until you're back to 25: 30% slower, melee hits deal half damage, and a block that empties you puts your shield down for 5 seconds. You can always still jump.
- Hunters spend 8% less on sprinting, jumping and melee per rank (40% less at Grandmaster). Carrying a downed player doubles sprint costs, 20% less per Healer rank, and you don't recover while moving.
- You're full on joining and after respawning. Downed players don't recover; when revived they have 25. Creative, spectator, flying and spawn grace don't drain.

## armor-shield

Wearing a full set of armour absorbs the first strong PvP melee hit: a fully charged (85%+) sword, axe or spear hit dealing at least 2 damage is cancelled outright. The shield then recharges for 10 minutes, counting only while you're online. It's ready again after you die.

## hunter-ranged

Arrows from players' bows and crossbows deal damage by distance travelled. Up close they're weak (4 for bows, 5 for crossbows), rising to 8 just short of a "golden" window where they hit for 15. Beyond the window damage falls away again. The window is 44–46 blocks for bows and 55–57 for crossbows, and widens with Hunter rank to 36–52 and 52–66 at Grandmaster; the near-window damage also rises by 1 per rank. Damage scales with bow draw and Power (+20% per level).

Hunters' arrows fly 12% faster per rank. From Journeyman, bows sometimes don't use up the arrow: 10% at Journeyman, 25% at Expert and Master, 50% at Grandmaster.

## instinct

Players no longer appear on each other's locator bar. Instead, when a monster breaks or changes a block (zombies breaking doors, endermen, ravagers), it shows on the locator bar of Hunters within 8 blocks (Apprentice), 12 (Journeyman) or 16 (Expert and above) for 15 seconds.

## pat-down

Sneak + right-click a player while holding a spyglass to search them.

- **Downed players** can be searched by anyone, straight away, and you can take items from their inventory.
- **Standing players** can only be searched by Hunters (Apprentice or higher). Keep sneaking, holding the spyglass and within 3 blocks for 5 seconds; then you see a copy of their inventory but can't take anything. They're told they were searched.

Works without the `downed` module; then only the Hunter search applies.

## spawn-grace

New players, and players who die and respawn (at world spawn, a bed or an anchor), get 3 minutes 30 seconds of spawn grace, shown as a pink bar. During it mobs can't target them and damage from mobs is cancelled. Other players can still hurt them, and so can the world: fire, lava, drowning, starving and so on. Falling doesn't end grace, but any of these do:

- being hit by a player, or by a player's projectile, even a snowball
- dealing damage to anything
- taking damage from the world, other than falling
- earning profession XP from gameplay (admin commands don't count)
- dying

The timer counts real time and keeps running while the player is offline. Rejoining before it ends resumes it.

## rank-gates

Profession ranks gate more than recipes. Everything is set in `plugins/RealCivFramework/rank-gates.yml`; empty a section to switch that gate off. Creative mode skips the mining and workstation gates.

- **Mining.** Some blocks drop nothing, not even XP orbs, below a Miner rank. They still break and still pay Miner XP, so players can work their way up. Defaults: gold, redstone and lapis ores need Apprentice, diamond and emerald ores Journeyman, ancient debris Expert.
- **Workstations.** Some blocks only open for a profession rank. A block listed under several professions opens for anyone who meets one of them. Defaults: anvils, smithing tables and grindstones need Blacksmith Apprentice, blast furnaces Blacksmith Journeyman, brewing stands Healer Apprentice, stonecutters Builder Apprentice, smokers Farmer Apprentice.
- **Enchanting.** Librarian rank caps the enchantment level a player can apply, at the enchanting table and with an anvil. Offers above the cap are hidden. Defaults: Novice and Apprentice can't enchant, Journeyman can apply level I, Expert II, Master III, Grandmaster anything. Mending and Infinity need Grandmaster. Repairing or combining gear that already has an enchantment at that level is fine.

## wolf-limit

A player can own as many wolves as their Hunter rank number: none at Novice, one at Apprentice, up to five at Grandmaster. Taming one more fails and the action bar says why. Wolves are counted from when the module is turned on, in `plugins/RealCivFramework/wolves.yml`; a wolf stops counting when it dies.

## names

Off by default. Every player plays under a generated name instead of their account name. The name replaces the account name at login, so chat, the tab list, nametags, death messages and commands all use it. Only holders of `rcf.names.admin` can see the account name, with `/realname`; it also appears in the server log.

- Names are generated from `plugins/RealCivFramework/names.yml`, which holds a list of first names, a list of last names and a separator. Each name is a random combination such as `Alden_Ashford`; combinations over 16 characters, or with anything but letters, digits and underscores, are skipped. The defaults make about 7,800 names. No two players share a name. Changes apply after a restart, and names already given out are kept.
- On their first join, a player gets a random name plus two more to choose from. `/name` shows the choices; clicking one asks for confirmation. Picking a different name disconnects the player so it applies everywhere; they rejoin under it.
- The choice stays open for 10 minutes of play (time offline doesn't count), with reminders at 5 minutes and 1 minute. After that the current name becomes permanent.
- New players can't join if every combination is taken; the server log says so.
- Admins (`rcf.names.admin`): `/setname <player> <name>` sets a permanent name, `/rerollname <player>` gives a new random one, and `/realname <player>` shows the account name and UUID. The first two disconnect the player.

Names are stored in `plugins/RealCivFramework/identities.yml`. Turning the module off gives everyone their account names back; turning it on again restores the generated ones.
