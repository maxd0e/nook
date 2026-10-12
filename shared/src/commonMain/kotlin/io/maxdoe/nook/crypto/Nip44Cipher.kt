package io.maxdoe.nook.crypto

expect object Nip44Cipher {
    fun encryptToSelf(plaintext: String, keyPair: NostrKeyPair): String

    fun decryptFromSelf(ciphertext: String, keyPair: NostrKeyPair): String?
}
