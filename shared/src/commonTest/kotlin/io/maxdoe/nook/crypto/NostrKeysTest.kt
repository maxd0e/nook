package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip44Encryption.Nip44v2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NostrKeysTest {
    @Test
    fun generatedKey_encodesAndDecodesAsNsecAndNpub() {
        val generated = NostrKeyPair.generate()

        val restored = assertNotNull(NostrKeyPair.fromNsec(generated.nsec()))

        assertEquals(generated.publicKeyHex, restored.publicKeyHex)
        assertEquals(generated.npub, restored.npub)
        assertEquals(generated.publicKeyHex, NostrKeyPair.decodeNpub(generated.npub))
    }

    @Test
    fun malformedBech32Key_isRejected() {
        assertNull(NostrKeyPair.fromNsec("npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"))
        assertNull(NostrKeyPair.fromNsec("nsec1invalid"))
        assertNull(NostrKeyPair.decodeNpub("NPUB1INVALID"))
    }

    @Test
    fun nip44OfficialConversationKeyVector_matches() {
        val privateKey = "315e59ff51cb9209768cf7da80791ddcaae56ac9775eb25b6dee1234bc5d2268".hexToByteArray()
        val publicKey = "c2f9d9948dc8c7c38321e4b85c8558872eafa0641cd269db76848a6073e69133".hexToByteArray()

        val conversationKey = Nip44v2().getConversationKey(privateKey, publicKey)

        assertEquals(
            "3dfef0ce2a4d80a25e7a328accf73448ef67096f65f79588e358d9a0eb9013f1",
            conversationKey.toHexKey(),
        )
    }

    @Test
    fun nip44OfficialEncryptionVector_matches() {
        val cipher = Nip44v2()
        val conversationKey =
            "c41c775356fd92eadc63ff5a0dc1da211b268cbea22316767095b2871ea1412d".hexToByteArray()
        val nonce = "0000000000000000000000000000000000000000000000000000000000000001".hexToByteArray()

        val payload = cipher.encryptWithNonce("a", conversationKey, nonce).encodePayload()

        assertEquals(
            "AgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABee0G5VSK0/9YypIObAtD" +
                "KfYEAjD35uVkHyB0F4DwrcNaCXlCWZKaArsGrY6M9wnuTMxWfp1RTN9Xga8no+kF5Vsb",
            payload,
        )
    }

    @Test
    fun nip44_roundTripsLongAndUnicodeText() {
        val keyPair = NostrKeyPair.generate()
        val plaintext = "Nook \uD83C\uDF3F ".repeat(2_000)

        val ciphertext = Nip44Cipher.encryptToSelf(plaintext, keyPair)

        assertEquals(plaintext, Nip44Cipher.decryptFromSelf(ciphertext, keyPair))
    }

    @Test
    fun nip44_rejectsEmptyTextBecauseTheProtocolForbidsIt() {
        val keyPair = NostrKeyPair.generate()

        assertFailsWith<IllegalArgumentException> { Nip44Cipher.encryptToSelf("", keyPair) }
    }

    @Test
    fun tamperedNip44Ciphertext_failsSafely() {
        val keyPair = NostrKeyPair.generate()
        val ciphertext = Nip44Cipher.encryptToSelf("private note", keyPair)
        val tampered = ciphertext.dropLast(1) + if (ciphertext.last() == 'A') 'B' else 'A'

        assertNull(Nip44Cipher.decryptFromSelf(tampered, keyPair))
    }
}
