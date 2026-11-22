package gg.glowstone.rcf.folia.modules.professions

import gg.glowstone.rcf.folia.framework.Module

/** The default ruleset's seven professions. */
internal object Professions : Module("professions") {
    override val professions = listOf(Farmer, Builder, Miner, Healer, Librarian, Hunter, Blacksmith)
}
