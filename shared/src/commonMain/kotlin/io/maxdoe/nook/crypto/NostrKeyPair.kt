package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec

/** A Nostr secp256k1 key pair whose private bytes stay internal to the shared module. */
class NostrKeyPair private constructor(
    private val delegate: KeyPair,
) {
    val publicKeyHex: String = delegate.pubKey.toHexKey()

    val npub: String
        get() = delegate.pubKey.toNpub()

    fun nsec(): String = requirePrivateKey().toNsec()

    internal fun privateKeyBytes(): ByteArray = requirePrivateKey().copyOf()

    internal fun publicKeyBytes(): ByteArray = delegate.pubKey.copyOf()

    private fun requirePrivateKey(): ByteArray = checkNotNull(delegate.privKey) { "A private key is required." }

    companion object {
        fun generate(): NostrKeyPair = NostrKeyPair(KeyPair())

        fun fromNsec(value: String): NostrKeyPair? =
            decode(value, "nsec")?.let { privateKey ->
                if (privateKey.size != KEY_SIZE_BYTES) {
                    null
                } else {
                    runCatching { NostrKeyPair(KeyPair(privKey = privateKey)) }.getOrNull()
                }
            }

        fun decodeNpub(value: String): String? =
            decode(value, "npub")
                ?.takeIf { it.size == KEY_SIZE_BYTES }
                ?.toHexKey()

        private fun decode(
            value: String,
            expectedPrefix: String,
        ): ByteArray? =
            runCatching {
                require(value == value.lowercase()) { "Mixed-case Bech32 input is not supported." }
                require(value.startsWith("$expectedPrefix" + "1")) { "Unexpected Bech32 prefix." }
                value.bechToBytes()
            }.getOrNull()

        private const val KEY_SIZE_BYTES = 32
    }
}

internal fun String.hexKeyBytes(): ByteArray = hexToByteArray()
