package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip44Encryption.Nip44

object Nip44Cipher {
    fun encryptToSelf(
        plaintext: String,
        keyPair: NostrKeyPair,
    ): String {
        require(plaintext.isNotEmpty()) { "NIP-44 v2 does not permit empty plaintext." }
        return Nip44
            .encrypt(
                msg = plaintext,
                privateKey = keyPair.privateKeyBytes(),
                pubKey = keyPair.publicKeyBytes(),
            ).encodePayload()
    }

    fun decryptFromSelf(
        ciphertext: String,
        keyPair: NostrKeyPair,
    ): String? =
        runCatching {
            Nip44.decrypt(
                payload = ciphertext,
                privateKey = keyPair.privateKeyBytes(),
                pubKey = keyPair.publicKeyBytes(),
            )
        }.getOrNull()
}
