package io.maxdoe.nook.nostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NookRelaysTest {
    @Test
    fun defaultsContainThreeSecureRelayUrls() {
        assertEquals(3, NookRelays.urls.size)
        assertEquals(NookRelays.urls.size, NookRelays.urls.toSet().size)
        assertTrue(NookRelays.urls.all { it.startsWith("wss://") })
    }
}
