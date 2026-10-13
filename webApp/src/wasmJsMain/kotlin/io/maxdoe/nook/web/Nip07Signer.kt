@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.maxdoe.nook.web

import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.SignedNostrEvent
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.SignerRequestException
import io.maxdoe.nook.nostr.UnsignedNostrEvent
import kotlinx.coroutines.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise

class Nip07Signer private constructor(
    override val publicKey: String,
) : Signer {
    override suspend fun signEvent(event: UnsignedNostrEvent): SignedNostrEvent =
        runBridge { Nip07Bridge.signEvent(event.toBridgeJson()).await<JsString>().toString() }
            .let(json::parseToJsonElement)
            .jsonObject
            .toSignedEvent()
            .also { signed -> validateSignedEvent(event, signed, publicKey) }

    override suspend fun nip44Encrypt(
        plaintext: String,
        peerPublicKey: String,
    ): String = runBridge { Nip07Bridge.encrypt(peerPublicKey, plaintext).await<JsString>().toString() }

    override suspend fun nip44Decrypt(
        ciphertext: String,
        peerPublicKey: String,
    ): String? = runCatching { Nip07Bridge.decrypt(peerPublicKey, ciphertext).await<JsString>().toString() }.getOrNull()

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun availabilityErrorOrNull(): String? = Nip07Bridge.capabilityError().takeIf(String::isNotBlank)

        suspend fun connect(): Nip07Signer {
            availabilityErrorOrNull()?.let { throw SignerRequestException(it) }
            val publicKey = runBridge { Nip07Bridge.getPublicKey().await<JsString>().toString() }
            require(publicKey.matches(Regex("[0-9a-fA-F]{64}"))) { "The extension returned an invalid public key." }
            return Nip07Signer(publicKey.lowercase())
        }

        private suspend fun <T> runBridge(block: suspend () -> T): T =
            try {
                block()
            } catch (error: Throwable) {
                throw SignerRequestException(error.message ?: "The Nostr extension could not complete the request.")
            }
    }
}

internal fun validateSignedEvent(
    requested: UnsignedNostrEvent,
    signed: SignedNostrEvent,
    publicKey: String,
) {
    check(signed.publicKey == publicKey && NostrEventSigner.verify(signed)) {
        "Signer returned an invalid signature."
    }
    check(
        signed.createdAt == requested.createdAt &&
            signed.kind == requested.kind &&
            signed.tags == requested.tags &&
            signed.content == requested.content,
    ) {
        "Signer changed the requested event."
    }
}

@JsModule("./nip07Bridge.mjs")
private external object Nip07Bridge {
    fun capabilityError(): String

    fun getPublicKey(): Promise<JsString>

    fun signEvent(eventJson: String): Promise<JsString>

    fun encrypt(
        publicKey: String,
        plaintext: String,
    ): Promise<JsString>

    fun decrypt(
        publicKey: String,
        ciphertext: String,
    ): Promise<JsString>
}

private fun UnsignedNostrEvent.toBridgeJson(): String =
    JsonObject(
        mapOf(
            "createdAt" to JsonPrimitive(createdAt),
            "kind" to JsonPrimitive(kind),
            "tags" to JsonArray(tags.map { JsonArray(it.map(::JsonPrimitive)) }),
            "content" to JsonPrimitive(content),
        ),
    ).toString()

private fun JsonObject.toSignedEvent(): SignedNostrEvent =
    SignedNostrEvent(
        id = requireNotNull(this["id"]?.jsonPrimitive?.content),
        publicKey = requireNotNull(this["publicKey"]?.jsonPrimitive?.content),
        createdAt = requireNotNull(this["createdAt"]?.jsonPrimitive?.longOrNull),
        kind = requireNotNull(this["kind"]?.jsonPrimitive?.intOrNull),
        tags = requireNotNull(this["tags"]?.jsonArray).map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
        content = requireNotNull(this["content"]?.jsonPrimitive?.content),
        signature = requireNotNull(this["signature"]?.jsonPrimitive?.content),
    )
