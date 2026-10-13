@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.maxdoe.nook.web

import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import io.maxdoe.nook.nostr.SignedNostrEvent
import kotlinx.coroutines.await
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.js.JsModule
import kotlin.js.JsString
import kotlin.js.Promise

/** Browser persistence for relay ciphertext only. Plaintext projections stay in NoteRepository memory. */
class IndexedDbEncryptedEventStore private constructor(
    private val publicKey: String,
    private val events: MutableList<StoredNostrEvent>,
) : EncryptedEventStore {
    override fun all(): List<StoredNostrEvent> = events.toList()

    override suspend fun insert(event: StoredNostrEvent) {
        require(event.event.publicKey == publicKey) { "Cannot store an event for a different account." }
        if (events.none { it.event.id == event.event.id }) {
            IndexedDbBridge.put(json.encodeToString(PersistedEvent.from(event))).await<JsString>()
            events += event
        }
    }

    override suspend fun markClean(eventId: String) {
        val index = events.indexOfFirst { it.event.id == eventId }
        if (index >= 0) {
            IndexedDbBridge.markClean(eventId).await<JsString>()
            events[index] = events[index].copy(dirty = false)
        }
    }

    suspend fun clear() {
        events.clear()
        IndexedDbBridge.clear(publicKey).await<JsString>()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        suspend fun open(publicKey: String): IndexedDbEncryptedEventStore {
            val saved =
                json.decodeFromString<List<PersistedEvent>>(
                    IndexedDbBridge.readAll(publicKey).await<JsString>().toString(),
                )
            return IndexedDbEncryptedEventStore(publicKey, saved.map(PersistedEvent::toStored).toMutableList())
        }
    }
}

@JsModule("./indexedDbBridge.mjs")
private external object IndexedDbBridge {
    fun readAll(publicKey: String): Promise<JsString>

    fun put(value: String): Promise<JsString>

    fun markClean(eventId: String): Promise<JsString>

    fun clear(publicKey: String): Promise<JsString>
}

@Serializable
private data class PersistedEvent(
    val id: String,
    val publicKey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val signature: String,
    val dTag: String,
    val dirty: Boolean,
) {
    fun toStored(): StoredNostrEvent =
        StoredNostrEvent(
            SignedNostrEvent(id, publicKey, createdAt, kind, tags, content, signature),
            dTag,
            dirty,
        )

    companion object {
        fun from(value: StoredNostrEvent): PersistedEvent =
            value.event.let { event ->
                PersistedEvent(
                    event.id,
                    event.publicKey,
                    event.createdAt,
                    event.kind,
                    event.tags,
                    event.content,
                    event.signature,
                    value.dTag,
                    value.dirty,
                )
            }
    }
}
