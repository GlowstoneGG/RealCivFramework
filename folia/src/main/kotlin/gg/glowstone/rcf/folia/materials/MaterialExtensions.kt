package gg.glowstone.rcf.folia.materials

import gg.glowstone.rcf.core.materials.MaterialType
import org.bukkit.Material

internal fun Material.getMaterialType(): MaterialType {
    val suffix = name.substringAfterLast('_', missingDelimiterValue = "")
    return MaterialType.entries.firstOrNull { it.name == suffix } ?: MaterialType.OTHER
}
