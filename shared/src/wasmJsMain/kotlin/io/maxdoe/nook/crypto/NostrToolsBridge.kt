@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.maxdoe.nook.crypto

import kotlin.js.JsModule

@JsModule("./nostrBridge.mjs")
external object NostrToolsBridge {
    fun generateNsec(): String

    fun publicKeyFromNsec(nsec: String): String

    fun npubFromNsec(nsec: String): String

    fun normalizeNsec(nsec: String): String

    fun decodeNpub(npub: String): String

    fun encryptToSelf(
        plaintext: String,
        nsec: String,
    ): String

    fun decryptFromSelf(
        ciphertext: String,
        nsec: String,
    ): String

    fun signEvent(
        eventJson: String,
        nsec: String,
    ): String

    fun verifyEvent(eventJson: String): Boolean
}
