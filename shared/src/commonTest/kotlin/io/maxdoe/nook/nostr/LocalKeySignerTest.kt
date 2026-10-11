package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.NostrKeyPair
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalKeySignerTest {
    @Test
    fun localSigner_signsAndRoundTripsSelfEncryptedContent() {
        runBlocking {
            val signer = LocalKeySigner(NostrKeyPair.generate())
            val event = signer.signEvent(UnsignedNostrEvent(100, 30_078, content = "ciphertext"))

            assertEquals(signer.publicKey, signer.getPublicKey())
            assertTrue(NostrEventSigner.verify(event))

            val ciphertext = signer.nip44Encrypt("private note")
            assertEquals("private note", signer.nip44Decrypt(ciphertext))
            assertEquals(null, signer.nip44Decrypt("not a nip44 payload"))
        }
    }
}
