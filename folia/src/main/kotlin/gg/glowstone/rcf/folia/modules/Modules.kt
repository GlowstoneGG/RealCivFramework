package gg.glowstone.rcf.folia.modules

import gg.glowstone.rcf.folia.modules.armorshield.ArmorShield
import gg.glowstone.rcf.folia.modules.armorweight.ArmorWeight
import gg.glowstone.rcf.folia.modules.carry.Carry
import gg.glowstone.rcf.folia.modules.combatlog.CombatLog
import gg.glowstone.rcf.folia.modules.combattag.CombatTag
import gg.glowstone.rcf.folia.modules.corpses.Corpses
import gg.glowstone.rcf.folia.modules.deathpenalty.DeathPenalty
import gg.glowstone.rcf.folia.modules.downed.Downed
import gg.glowstone.rcf.folia.modules.hunterranged.HunterRanged
import gg.glowstone.rcf.folia.modules.instinct.Instinct
import gg.glowstone.rcf.folia.modules.names.Names
import gg.glowstone.rcf.folia.modules.patdown.PatDown
import gg.glowstone.rcf.folia.modules.professions.Professions
import gg.glowstone.rcf.folia.modules.rankgates.RankGates
import gg.glowstone.rcf.folia.modules.reinforcement.Reinforcement
import gg.glowstone.rcf.folia.modules.revive.Revive
import gg.glowstone.rcf.folia.modules.spawngrace.SpawnGrace
import gg.glowstone.rcf.folia.modules.stamina.Stamina
import gg.glowstone.rcf.folia.modules.survival.BedCooldown
import gg.glowstone.rcf.folia.modules.survival.BedOwnership
import gg.glowstone.rcf.folia.modules.survival.CampfireRest
import gg.glowstone.rcf.folia.modules.survival.Fullness
import gg.glowstone.rcf.folia.modules.survival.NoNaturalRegen
import gg.glowstone.rcf.folia.modules.wolflimit.WolfLimit

/** Every module this build knows about. `modules.yml` decides which of them run. Forks add or remove entries here. */
internal val allModules = listOf(
    Professions,
    DeathPenalty,
    Reinforcement,
    Fullness,
    NoNaturalRegen,
    BedOwnership,
    BedCooldown,
    CampfireRest,
    Downed,
    Revive,
    Carry,
    Corpses,
    CombatTag,
    CombatLog,
    ArmorWeight,
    Stamina,
    ArmorShield,
    HunterRanged,
    Instinct,
    PatDown,
    SpawnGrace,
    Names,
    RankGates,
    WolfLimit,
)
