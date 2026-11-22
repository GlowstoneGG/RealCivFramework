package gg.glowstone.rcf.folia.framework

import gg.glowstone.rcf.folia.modules.downed.remainingAfterOffline
import kotlin.test.Test
import kotlin.test.assertEquals

class DownedOfflineTest {
    @Test
    fun `offline time drains bleed-out and a missing logout time drains nothing`() {
        assertEquals(1_000, remainingAfterOffline(1_200, logoutAt = 0, now = 10_000))
        assertEquals(0, remainingAfterOffline(1_200, logoutAt = 0, now = 1_000_000))
        assertEquals(1_200, remainingAfterOffline(1_200, logoutAt = null, now = 1_000_000))
        assertEquals(1_200, remainingAfterOffline(1_200, logoutAt = 5_000, now = 1_000))
    }
}
