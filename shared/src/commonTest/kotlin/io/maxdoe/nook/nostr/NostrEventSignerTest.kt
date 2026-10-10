package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.NostrKeyPair
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NostrEventSignerTest {
    @Test
    fun signedEvent_hasAValidIdAndSignature() {
        val event =
            NostrEventSigner.sign(
                event =
                    UnsignedNostrEvent(
                        createdAt = 1_790_000_000,
                        kind = 30_078,
                        tags = listOf(listOf("d", "nook:test")),
                        content = "ciphertext",
                    ),
                keyPair = NostrKeyPair.generate(),
            )

        assertTrue(NostrEventSigner.verify(event))
        assertFalse(NostrEventSigner.verify(event.copy(content = "changed")))
        assertFalse(NostrEventSigner.verify(event.copy(signature = event.signature.reversed())))
    }
}
