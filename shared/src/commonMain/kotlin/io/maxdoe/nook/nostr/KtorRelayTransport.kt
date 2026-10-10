package io.maxdoe.nook.nostr

import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class KtorRelayTransport(
    private val client: HttpClient,
) : RelayTransport {
    override suspend fun connect(url: String): RelayConnection =
        KtorRelayConnection(client.webSocketSession(urlString = url))
}

class KtorRelayConnection(
    private val session: WebSocketSession,
) : RelayConnection {
    override val messages: Flow<RelayMessage> =
        flow {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val message = NostrRelayJson.parseMessage(frame.readText())
                    if (message != null) emit(message)
                }
            }
        }

    override suspend fun send(command: RelayCommand) {
        session.send(Frame.Text(NostrRelayJson.encodeCommand(command)))
    }

    override suspend fun close() {
        session.close()
    }
}

internal object NostrRelayJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeCommand(command: RelayCommand): String =
        when (command) {
            is RelayCommand.Publish ->
                json.encodeToString(
                    JsonArray.serializer(),
                    buildJsonArray {
                        add(JsonPrimitive("EVENT"))
                        add(command.event.toJson())
                    },
                )
            is RelayCommand.Subscribe ->
                json.encodeToString(
                    JsonArray.serializer(),
                    buildJsonArray {
                        add(JsonPrimitive("REQ"))
                        add(JsonPrimitive(command.subscriptionId))
                        add(
                            buildJsonObject {
                                put("kinds", JsonArray(command.filter.kinds.map(::JsonPrimitive)))
                                put("authors", JsonArray(command.filter.authors.map(::JsonPrimitive)))
                            },
                        )
                    },
                )
        }

    fun parseMessage(value: String): RelayMessage? =
        runCatching {
            val parts = json.parseToJsonElement(value).jsonArray
            when (parts.stringAt(0)) {
                "EVENT" -> {
                    val subscriptionId = parts.stringAt(1) ?: return null
                    val event = parts.getOrNull(2)?.toEvent() ?: return null
                    RelayMessage.Event(subscriptionId, event)
                }
                "EOSE" -> RelayMessage.EndOfStoredEvents(parts.stringAt(1) ?: return null)
                "OK" ->
                    RelayMessage.Published(
                        eventId = parts.stringAt(1) ?: return null,
                        accepted = parts.getOrNull(2)?.jsonPrimitive?.booleanOrNull ?: false,
                        message = parts.stringAt(3).orEmpty(),
                    )
                "NOTICE" -> RelayMessage.Notice(parts.stringAt(1).orEmpty())
                "CLOSED" -> {
                    val subscriptionId = parts.stringAt(1) ?: return null
                    RelayMessage.Closed(subscriptionId, parts.stringAt(2).orEmpty())
                }
                else -> null
            }
        }.getOrNull()

    private fun SignedNostrEvent.toJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            put("pubkey", publicKey)
            put("created_at", createdAt)
            put("kind", kind)
            put("tags", JsonArray(tags.map { tag -> JsonArray(tag.map(::JsonPrimitive)) }))
            put("content", content)
            put("sig", signature)
        }

    private fun JsonElement.toEvent(): SignedNostrEvent? =
        runCatching {
            val event = jsonObject
            SignedNostrEvent(
                id = requireNotNull(event.string("id")),
                publicKey = requireNotNull(event.string("pubkey")),
                createdAt = requireNotNull(event["created_at"]?.jsonPrimitive?.longOrNull),
                kind = requireNotNull(event["kind"]?.jsonPrimitive?.intOrNull),
                tags = requireNotNull(event["tags"]?.jsonArray?.toTags()),
                content = requireNotNull(event.string("content")),
                signature = requireNotNull(event.string("sig")),
            )
        }.getOrNull()

    private fun JsonArray.toTags(): List<List<String>> =
        map { tag -> tag.jsonArray.mapNotNull { it.jsonPrimitive.contentOrNull } }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun JsonArray.stringAt(index: Int): String? = getOrNull(index)?.jsonPrimitive?.contentOrNull
}
