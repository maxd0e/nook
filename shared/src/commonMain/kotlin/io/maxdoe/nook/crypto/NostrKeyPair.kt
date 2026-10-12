package io.maxdoe.nook.crypto

/** A Nostr secp256k1 key pair whose private bytes stay internal to the platform crypto implementation. */
expect class NostrKeyPair {
    val publicKeyHex: String
    val npub: String

    fun nsec(): String

    companion object {
        fun generate(): NostrKeyPair

        fun fromNsec(value: String): NostrKeyPair?

        fun decodeNpub(value: String): String?
    }
}
