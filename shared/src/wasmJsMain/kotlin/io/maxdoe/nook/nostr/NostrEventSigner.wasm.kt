package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.crypto.NostrToolsBridge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

actual object NostrEventSigner {
    private val json = Json { ignoreUnknownKeys = true }

    actual fun sign(
        event: UnsignedNostrEvent,
        keyPair: NostrKeyPair,
    ): SignedNostrEvent =
        NostrToolsBridge
            .signEvent(event.toBridgeJson(), keyPair.nsec())
            .let(json::parseToJsonElement)
            .jsonObject
            .toSignedEvent()

    actual fun verify(event: SignedNostrEvent): Boolean = NostrToolsBridge.verifyEvent(event.toBridgeJson())
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

private fun SignedNostrEvent.toBridgeJson(): String =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "publicKey" to JsonPrimitive(publicKey),
            "createdAt" to JsonPrimitive(createdAt),
            "kind" to JsonPrimitive(kind),
            "tags" to JsonArray(tags.map { JsonArray(it.map(::JsonPrimitive)) }),
            "content" to JsonPrimitive(content),
            "signature" to JsonPrimitive(signature),
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
