package gg.glowstone.rcf.core.blocks

/** One "player placed" bit per block of a 16×16×16 chunk section, packed into [WORDS] longs. */
object PlacedBits {
    const val WORDS = 64

    /** Section of a world Y, independent of the world's minimum height. */
    fun section(y: Int) = y shr 4

    /** Bit index within the section, from world coordinates (negative ones included). */
    fun index(x: Int, y: Int, z: Int) = (x and 15) or ((z and 15) shl 4) or ((y and 15) shl 8)

    fun get(words: LongArray?, index: Int): Boolean {
        val valid = words?.takeIf { it.size == WORDS } ?: return false
        return (valid[index ushr 6] ushr (index and 63)) and 1L != 0L
    }

    /** Sets a bit, mutating [words] when it is valid. Returns null once no bit is set, so the entry can be dropped. */
    fun set(words: LongArray?, index: Int, value: Boolean): LongArray? {
        val out = words?.takeIf { it.size == WORDS } ?: if (value) LongArray(WORDS) else return null
        val bit = 1L shl (index and 63)
        val word = index ushr 6
        out[word] = if (value) out[word] or bit else out[word] and bit.inv()
        return out.takeIf { array -> array.any { it != 0L } }
    }
}
