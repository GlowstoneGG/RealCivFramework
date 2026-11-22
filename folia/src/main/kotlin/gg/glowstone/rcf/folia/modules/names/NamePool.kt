package gg.glowstone.rcf.folia.modules.names

import kotlin.random.Random

/**
 * Every valid "first + separator + last" combination from `names.yml`, and which names are taken.
 * Names compare without case, as Minecraft's do. Not thread-safe.
 */
internal class NamePool(
    first: Iterable<String>,
    last: Iterable<String>,
    private val separator: String = "_",
    private val random: Random = Random.Default,
) {
    private val first = first.map(String::trim).filter(String::isNotEmpty).distinct()
    private val last = last.map(String::trim).filter(String::isNotEmpty).distinct()
    private val taken = HashSet<String>()

    private fun combinations() = first.asSequence().flatMap { a -> last.asSequence().map { b -> a + separator + b } }.filter(::isValid)

    val size = combinations().count()

    fun isTaken(name: String) = name.lowercase() in taken

    /** Marks a name taken, whether or not the lists can make it. Returns false if it already was. */
    fun claim(name: String) = taken.add(name.lowercase())

    fun release(name: String) {
        taken.remove(name.lowercase())
    }

    /** Claims and returns a random free name, or null if every combination is taken. */
    fun next(): String? {
        if (first.isEmpty() || last.isEmpty()) return null
        repeat(64) {
            val name = first.random(random) + separator + last.random(random)
            if (isValid(name) && claim(name)) return name
        }
        // Mostly taken: fall back to a scan so a free name is always found.
        return combinations().filterNot(::isTaken).toList().randomOrNull(random)?.also(::claim)
    }

    companion object {
        private val valid = Regex("[A-Za-z0-9_]{3,16}")

        fun isValid(name: String) = valid.matches(name)
    }
}
