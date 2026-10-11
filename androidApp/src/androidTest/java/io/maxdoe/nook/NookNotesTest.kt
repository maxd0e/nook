package io.maxdoe.nook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import io.maxdoe.nook.notes.EpochSecondsClock
import io.maxdoe.nook.notes.NookEventCodec
import io.maxdoe.nook.notes.NoteRepository
import org.junit.Rule
import org.junit.Test

class NookNotesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun search_filtersTheLiveNoteList() {
        val repository =
            repository().also {
                it.createNote("garden", "Garden ideas", "Plant spring beds")
                it.createNote("travel", "Packing", "Passport")
            }

        composeRule.setContent { NookTheme { NookNotes(repository, onLogout = {}) } }

        composeRule.onNodeWithText("Garden ideas").assertIsDisplayed()
        composeRule.onNodeWithTag("notes_search").performTextInput("passport")
        composeRule.onNodeWithText("Packing").assertIsDisplayed()
    }

    private fun repository(): NoteRepository {
        val keyPair = NostrKeyPair.generate()
        return NoteRepository(
            store = FakeEncryptedEventStore(),
            codec = NookEventCodec(keyPair),
            clock = EpochSecondsClock { 100 },
        )
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
