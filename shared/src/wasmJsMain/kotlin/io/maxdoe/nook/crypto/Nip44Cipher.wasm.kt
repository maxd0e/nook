package io.maxdoe.nook.crypto

actual object Nip44Cipher {
    actual fun encryptToSelf(plaintext: String, keyPair: NostrKeyPair): String {
        require(plaintext.isNotEmpty()) { "NIP-44 v2 does not permit empty plaintext." }
        return NostrToolsBridge.encryptToSelf(plaintext, keyPair.secret())
    }

    actual fun decryptFromSelf(ciphertext: String, keyPair: NostrKeyPair): String? =
        runCatching { NostrToolsBridge.decryptFromSelf(ciphertext, keyPair.secret()) }.getOrNull()
}
