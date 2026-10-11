package io.maxdoe.nook.notes

import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NoteRepositoryTest {
    private val keyPair = NostrKeyPair.generate()
    private val codec = NookEventCodec(keyPair)

    @Test
    fun save_marksEncryptedEventDirtyAndListsPinnedFirst() {
        val store = FakeEncryptedEventStore()
        val repository = NoteRepository(store, codec, EpochSecondsClock { 100 })

        repository.save("later", NotePayload("Later", "find me", false, 1, 1))
        repository.save("pinned", NotePayload("Pinned", "other", true, 1, 1))

        assertEquals(listOf("pinned", "later"), repository.notes.value.map(NookListItem::id))
        assertTrue(store.all().all(StoredNostrEvent::dirty))
        assertFalse(store.all().any { it.event.content.contains("find me") })
        assertEquals(listOf("later"), repository.search("find").map(NookListItem::id))
    }

    @Test
    fun newestWinsTiesUseLowestIdAndTombstoneRemovesItem() {
        val store = FakeEncryptedEventStore()
        val repository = NoteRepository(store, codec, EpochSecondsClock { 50 })
        val first = codec.build("same", NotePayload("A", "first", false, 1, 1), 20)
        val second = codec.build("same", NotePayload("B", "second", false, 1, 1), 20)

        repository.receive(first)
        repository.receive(second)

        val expectedTitle = if (first.id < second.id) "A" else "B"
        val resolvedPayload =
            repository.notes
                .value
                .single()
                .payload as NotePayload
        assertEquals(expectedTitle, resolvedPayload.title)

        repository.save("same", TombstonePayload(1))
        assertTrue(repository.notes.value.isEmpty())
        assertEquals(50, store.all().maxOf { it.event.createdAt })
    }

    @Test
    fun noteCommands_createEditPinAndDeleteTheResolvedNote() {
        val store = FakeEncryptedEventStore()
        val repository = NoteRepository(store, codec, EpochSecondsClock { 100 })

        repository.createNote("note", "First", "Draft")
        repository.updateNote("note", "Final", "Body")
        repository.setPinned("note", true)

        val saved =
            repository.notes
                .value
                .single()
                .payload as NotePayload
        assertEquals("Final", saved.title)
        assertEquals("Body", saved.body)
        assertTrue(saved.pinned)
        assertTrue(store.all().all(StoredNostrEvent::dirty))

        repository.delete("note")

        assertTrue(repository.notes.value.isEmpty())
    }

    @Test
    fun checklistCommands_updateItemsAndClearCompletedItems() {
        val store = FakeEncryptedEventStore()
        val repository = NoteRepository(store, codec, EpochSecondsClock { 100 })

        repository.createChecklist(
            noteId = "packing",
            title = "Packing",
            items =
                listOf(
                    ChecklistItem("passport", "Passport", false),
                    ChecklistItem("socks", "Socks", true),
                ),
        )
        repository.updateChecklist(
            noteId = "packing",
            title = "Trip packing",
            items =
                listOf(
                    ChecklistItem("passport", "Passport and tickets", true),
                    ChecklistItem("charger", "Phone charger", false),
                    ChecklistItem("socks", "Socks", true),
                ),
        )

        val updated =
            repository.notes.value
                .single()
                .payload as ChecklistPayload
        assertEquals("Trip packing", updated.title)
        assertEquals(2, updated.items.count(ChecklistItem::done))
        assertEquals(
            listOf("Passport and tickets", "Phone charger", "Socks"),
            updated.items
                .map(ChecklistItem::text),
        )

        repository.clearCompletedChecklistItems("packing")

        val cleared =
            repository.notes.value
                .single()
                .payload as ChecklistPayload
        assertEquals(
            listOf("Phone charger"),
            cleared.items
                .map(ChecklistItem::text),
        )
        assertTrue(store.all().all(StoredNostrEvent::dirty))
    }

    private class FakeEncryptedEventStore : EncryptedEventStore {
        private val events = mutableListOf<StoredNostrEvent>()

        override fun all(): List<StoredNostrEvent> = events.toList()

        override fun insert(event: StoredNostrEvent) {
            if (events.none { it.event.id == event.event.id }) events += event
        }

        override fun markClean(eventId: String) {
            val index = events.indexOfFirst { it.event.id == eventId }
            if (index >= 0) events[index] = events[index].copy(dirty = false)
        }
    }
}
