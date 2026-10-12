package io.maxdoe.nook.crypto

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import com.vitorpamplona.quartz.nip19Bech32.toNpub
import com.vitorpamplona.quartz.nip19Bech32.toNsec

actual class NostrKeyPair private constructor(
    internal val delegate: KeyPair,
) {
    actual val publicKeyHex: String = delegate.pubKey.toHexKey()
    actual val npub: String get() = delegate.pubKey.toNpub()

    actual fun nsec(): String = privateKey().toNsec()

    internal fun privateKey(): ByteArray = checkNotNull(delegate.privKey) { "A private key is required." }.copyOf()

    internal fun publicKey(): ByteArray = delegate.pubKey.copyOf()

    actual companion object {
        actual fun generate(): NostrKeyPair = NostrKeyPair(KeyPair())

        actual fun fromNsec(value: String): NostrKeyPair? =
            decode(value, "nsec")?.takeIf { it.size == KEY_SIZE_BYTES }?.let { privateKey ->
                runCatching { NostrKeyPair(KeyPair(privKey = privateKey)) }.getOrNull()
            }

        actual fun decodeNpub(value: String): String? =
            decode(value, "npub")?.takeIf { it.size == KEY_SIZE_BYTES }?.toHexKey()

        private fun decode(value: String, expectedPrefix: String): ByteArray? =
            runCatching {
                require(!value.any(Char::isLowerCase) || !value.any(Char::isUpperCase))
                val normalized = value.lowercase()
                require(normalized.startsWith("$expectedPrefix" + "1"))
                normalized.bechToBytes()
            }.getOrNull()

        private const val KEY_SIZE_BYTES = 32
    }
}
