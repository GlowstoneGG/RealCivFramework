package gg.glowstone.rcf.core.professions

enum class SkillLevel(val displayName: String) {
    NOVICE("Novice"),
    APPRENTICE("Apprentice"),
    JOURNEYMAN("Journeyman"),
    EXPERT("Expert"),
    MASTER("Master"),
    GRANDMASTER("Grandmaster"),
    ;

    companion object {
        fun of(index: Int): SkillLevel = entries[index.coerceIn(0, entries.lastIndex)]
    }
}
