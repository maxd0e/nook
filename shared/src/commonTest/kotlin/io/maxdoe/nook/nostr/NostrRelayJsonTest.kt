package io.maxdoe.nook.nostr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NostrRelayJsonTest {
    @Test
    fun publishCommand_usesNostrWireFields() {
        val event =
            SignedNostrEvent(
                id = "event-id",
                publicKey = "pubkey",
                createdAt = 42,
                kind = 30_078,
                tags = listOf(listOf("d", "nook:one")),
                content = "ciphertext",
                signature = "signature",
            )

        val encoded = NostrRelayJson.encodeCommand(RelayCommand.Publish(event))

        assertTrue(encoded.startsWith("[\"EVENT\","))
        assertTrue(encoded.contains("\"pubkey\":\"pubkey\""))
        assertTrue(encoded.contains("\"created_at\":42"))
        assertTrue(encoded.contains("\"sig\":\"signature\""))
    }

    @Test
    fun incomingOkAndEvent_areParsed() {
        assertEquals(
            RelayMessage.Published("event-id", true, "stored"),
            NostrRelayJson.parseMessage("[\"OK\",\"event-id\",true,\"stored\"]"),
        )
        assertEquals(
            RelayMessage.EndOfStoredEvents("sub"),
            NostrRelayJson.parseMessage("[\"EOSE\",\"sub\"]"),
        )
    }
}
