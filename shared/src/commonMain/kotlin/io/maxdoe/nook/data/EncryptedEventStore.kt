package io.maxdoe.nook.data

import app.cash.sqldelight.db.SqlDriver
import io.maxdoe.nook.data.db.NookDatabase
import io.maxdoe.nook.nostr.SignedNostrEvent
import kotlinx.serialization.json.Json

data class StoredNostrEvent(
    val event: SignedNostrEvent,
    val dTag: String,
    val dirty: Boolean,
)

interface EncryptedEventStore {
    fun all(): List<StoredNostrEvent>

    fun insert(event: StoredNostrEvent)

    fun markClean(eventId: String)
}

class SqlDelightEncryptedEventStore(
    driver: SqlDriver,
) : EncryptedEventStore {
    private val database = NookDatabase(driver)
    private val queries = database.nookEventQueries
    private val json = Json

    override fun all(): List<StoredNostrEvent> = queries.selectAll(::toStoredEvent).executeAsList()

    override fun insert(event: StoredNostrEvent) {
        val source = event.event
        queries.insertEvent(
            id = source.id,
            public_key = source.publicKey,
            created_at = source.createdAt,
            kind = source.kind.toLong(),
            tags = json.encodeToString(source.tags),
            content = source.content,
            signature = source.signature,
            d_tag = event.dTag,
            dirty = if (event.dirty) 1 else 0,
        )
    }

    override fun markClean(eventId: String) {
        queries.markClean(eventId)
    }

    private fun toStoredEvent(
        id: String,
        publicKey: String,
        createdAt: Long,
        kind: Long,
        tags: String,
        content: String,
        signature: String,
        dTag: String,
        dirty: Long,
    ): StoredNostrEvent =
        StoredNostrEvent(
            event =
                SignedNostrEvent(
                    id = id,
                    publicKey = publicKey,
                    createdAt = createdAt,
                    kind = kind.toInt(),
                    tags = json.decodeFromString(tags),
                    content = content,
                    signature = signature,
                ),
            dTag = dTag,
            dirty = dirty != 0L,
        )
}
