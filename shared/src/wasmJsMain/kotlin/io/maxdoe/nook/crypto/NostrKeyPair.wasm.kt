package io.maxdoe.nook.crypto

actual class NostrKeyPair private constructor(
    private val nsecValue: String,
) {
    actual val publicKeyHex: String = NostrToolsBridge.publicKeyFromNsec(nsecValue)
    actual val npub: String = NostrToolsBridge.npubFromNsec(nsecValue)

    actual fun nsec(): String = nsecValue

    internal fun secret(): String = nsecValue

    actual companion object {
        actual fun generate(): NostrKeyPair = NostrKeyPair(NostrToolsBridge.generateNsec())

        actual fun fromNsec(value: String): NostrKeyPair? =
            runCatching { NostrKeyPair(NostrToolsBridge.normalizeNsec(value)) }.getOrNull()

        actual fun decodeNpub(value: String): String? = runCatching { NostrToolsBridge.decodeNpub(value) }.getOrNull()
    }
}
