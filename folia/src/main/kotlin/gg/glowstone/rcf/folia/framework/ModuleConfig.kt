package gg.glowstone.rcf.folia.framework

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/** Reads `modules.yml`, adding any modules it doesn't list yet with their defaults. */
internal object ModuleConfig {
    fun load(file: File, modules: List<Module>, warn: (String) -> Unit): List<Module> {
        val yaml = YamlConfiguration.loadConfiguration(file)
        var changed = !file.exists()
        for (module in modules) {
            val path = "modules.${module.name}"
            if (!yaml.isBoolean(path)) {
                yaml.set(path, module.enabledByDefault)
                changed = true
            }
        }
        val known = modules.map { it.name }.toSet()
        yaml.getConfigurationSection("modules")?.getKeys(false)?.filter { it !in known }?.forEach {
            warn("modules.yml lists unknown module '$it'; ignoring it")
        }
        if (changed) {
            yaml.options().setHeader(listOf("Turn Real Civ Framework modules on or off. Changes apply after a restart."))
            yaml.save(file)
        }
        return resolve(modules) { yaml.getBoolean("modules.${it.name}") }
    }

    /** The enabled modules, failing with a readable message when one needs a module that is off. */
    fun resolve(modules: List<Module>, enabled: (Module) -> Boolean): List<Module> {
        val on = modules.filter(enabled)
        val problems = on.flatMap { module ->
            module.dependsOn.filter { it !in on }.map { "'${module.name}' needs '${it.name}', which is disabled" }
        }
        require(problems.isEmpty()) { "Fix modules.yml: ${problems.joinToString("; ")}" }
        return on
    }
}
