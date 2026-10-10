package io.maxdoe.nook.notes

import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import io.maxdoe.nook.nostr.SignedNostrEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NookListItem(
    val id: String,
    val payload: NookPayload,
    val eventId: String,
    val createdAt: Long,
    val dirty: Boolean,
)

fun interface EpochSecondsClock {
    fun now(): Long
}

class NoteRepository(
    private val store: EncryptedEventStore,
    private val codec: NookEventCodec,
    private val clock: EpochSecondsClock,
) {
    private val mutableNotes = MutableStateFlow<List<NookListItem>>(emptyList())

    val notes: StateFlow<List<NookListItem>> = mutableNotes.asStateFlow()

    fun load() {
        refresh()
    }

    fun receive(event: SignedNostrEvent): Boolean {
        val parsed = codec.parse(event) ?: return false
        store.insert(
            StoredNostrEvent(
                event = event,
                dTag = "${NookEventCodec.NOOK_PREFIX}${parsed.noteId}",
                dirty = false,
            ),
        )
        refresh()
        return true
    }

    fun save(
        noteId: String,
        payload: NookPayload,
    ): SignedNostrEvent {
        val previousCreatedAt =
            parsedEvents().filter { it.noteId == noteId }.maxOfOrNull { it.event.createdAt }
                ?: Long.MIN_VALUE
        val createdAt = maxOf(clock.now(), previousCreatedAt + 1)
        val event = codec.build(noteId, payload.withUpdatedAt(createdAt), createdAt)
        store.insert(
            StoredNostrEvent(
                event = event,
                dTag = "${NookEventCodec.NOOK_PREFIX}$noteId",
                dirty = true,
            ),
        )
        refresh()
        return event
    }

    fun markPublished(eventId: String) {
        store.markClean(eventId)
        refresh()
    }

    /** Returns locally created encrypted events that still need relay delivery. */
    fun dirtyEvents(): List<SignedNostrEvent> = store.all().filter(StoredNostrEvent::dirty).map(StoredNostrEvent::event)

    fun search(query: String): List<NookListItem> {
        val normalizedQuery = query.trim().lowercase()
        if (normalizedQuery.isEmpty()) return notes.value
        return notes.value.filter { item -> item.payload.matches(normalizedQuery) }
    }

    private fun refresh() {
        val storedEvents = store.all()
        val dirtyByEventId = storedEvents.associate { it.event.id to it.dirty }
        val resolved =
            storedEvents
                .mapNotNull { stored -> codec.parse(stored.event) }
                .groupBy(ParsedNookEvent::noteId)
                .mapNotNull { (noteId, candidates) ->
                    val winner =
                        candidates.reduce { selected, candidate ->
                            if (candidate.isNewerThan(selected)) candidate else selected
                        }
                    winner.takeUnless { it.payload is TombstonePayload }?.let {
                        NookListItem(
                            id = noteId,
                            payload = it.payload,
                            eventId = it.event.id,
                            createdAt = it.event.createdAt,
                            dirty = dirtyByEventId[it.event.id] == true,
                        )
                    }
                }.sortedWith(
                    compareByDescending<NookListItem> { it.payload.isPinned() }
                        .thenByDescending { it.createdAt }
                        .thenBy { it.eventId },
                )
        mutableNotes.value = resolved
    }

    private fun parsedEvents(): List<ParsedNookEvent> = store.all().mapNotNull { codec.parse(it.event) }
}

private fun NookPayload.isPinned(): Boolean =
    when (this) {
        is NotePayload -> pinned
        is ChecklistPayload -> pinned
        is TombstonePayload -> false
    }

private fun NookPayload.matches(query: String): Boolean =
    when (this) {
        is NotePayload -> "$title\n$body".lowercase().contains(query)
        is ChecklistPayload ->
            (listOf(title) + items.map(ChecklistItem::text))
                .joinToString("\n")
                .lowercase()
                .contains(query)
        is TombstonePayload -> false
    }
