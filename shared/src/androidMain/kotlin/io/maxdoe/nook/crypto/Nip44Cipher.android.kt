package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip44Encryption.Nip44

actual object Nip44Cipher {
    actual fun encryptToSelf(plaintext: String, keyPair: NostrKeyPair): String {
        require(plaintext.isNotEmpty()) { "NIP-44 v2 does not permit empty plaintext." }
        return Nip44.encrypt(plaintext, keyPair.privateKey(), keyPair.publicKey()).encodePayload()
    }

    actual fun decryptFromSelf(ciphertext: String, keyPair: NostrKeyPair): String? =
        runCatching { Nip44.decrypt(ciphertext, keyPair.privateKey(), keyPair.publicKey()) }.getOrNull()
}
