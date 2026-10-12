package io.maxdoe.nook.crypto

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
    fun uppercaseBech32Keys_areAccepted() {
        val generated = NostrKeyPair.generate()

        val restored = assertNotNull(NostrKeyPair.fromNsec(generated.nsec().uppercase()))

        assertEquals(generated.publicKeyHex, restored.publicKeyHex)
        assertEquals(generated.publicKeyHex, NostrKeyPair.decodeNpub(generated.npub.uppercase()))
    }

    @Test
    fun malformedBech32Key_isRejected() {
        assertNull(NostrKeyPair.fromNsec("npub1qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"))
        assertNull(NostrKeyPair.fromNsec("nsec1invalid"))
        assertNull(NostrKeyPair.decodeNpub("NPUB1INVALID"))
        assertNull(NostrKeyPair.fromNsec("N" + NostrKeyPair.generate().nsec().drop(1)))
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
