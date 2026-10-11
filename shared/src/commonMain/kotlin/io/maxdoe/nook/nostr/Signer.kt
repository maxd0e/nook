package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.Nip44Cipher
import io.maxdoe.nook.crypto.NostrKeyPair

/**
 * The only cryptographic capability required by Nook. Implementations may keep
 * the private key locally or delegate every operation to an external signer.
 */
interface Signer {
    val publicKey: String

    suspend fun getPublicKey(): String = publicKey

    suspend fun signEvent(event: UnsignedNostrEvent): SignedNostrEvent

    suspend fun nip44Encrypt(
        plaintext: String,
        peerPublicKey: String = publicKey,
    ): String

    suspend fun nip44Decrypt(
        ciphertext: String,
        peerPublicKey: String = publicKey,
    ): String?
}

/** A request the user can retry after an external signer declines or cannot complete it. */
class SignerRequestException(
    message: String,
) : IllegalStateException(message)

class LocalKeySigner(
    private val keyPair: NostrKeyPair,
) : Signer {
    override val publicKey: String = keyPair.publicKeyHex

    override suspend fun signEvent(event: UnsignedNostrEvent): SignedNostrEvent = NostrEventSigner.sign(event, keyPair)

    override suspend fun nip44Encrypt(
        plaintext: String,
        peerPublicKey: String,
    ): String {
        require(peerPublicKey == publicKey) { "Local Nook encryption only supports self encryption." }
        return Nip44Cipher.encryptToSelf(plaintext, keyPair)
    }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        peerPublicKey: String,
    ): String? {
        if (peerPublicKey != publicKey) return null
        return Nip44Cipher.decryptFromSelf(ciphertext, keyPair)
    }
}
