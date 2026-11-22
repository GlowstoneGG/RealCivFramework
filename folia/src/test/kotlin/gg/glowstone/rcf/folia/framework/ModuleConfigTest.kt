package gg.glowstone.rcf.folia.framework

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ModuleConfigTest {
    private class TestModule(name: String, dependsOn: List<Module> = emptyList()) : Module(name, dependsOn)

    private val downed = TestModule("downed")
    private val revive = TestModule("revive", listOf(downed))

    @Test
    fun `enabled modules keep their order and a disabled dependency fails with its name`() {
        assertEquals(listOf(downed, revive), ModuleConfig.resolve(listOf(downed, revive)) { true })
        assertEquals(listOf(downed), ModuleConfig.resolve(listOf(downed, revive)) { it == downed })
        val error = assertFailsWith<IllegalArgumentException> { ModuleConfig.resolve(listOf(downed, revive)) { it == revive } }
        assertTrue("'revive' needs 'downed'" in error.message!!)
    }
}
