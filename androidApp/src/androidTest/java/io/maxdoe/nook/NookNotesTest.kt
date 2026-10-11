package io.maxdoe.nook

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import io.maxdoe.nook.notes.ChecklistItem
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

    @Test
    fun checklistCanBeSelectedFromTheAddMenuAndShowsItsProgress() {
        val repository =
            repository().also {
                it.createChecklist(
                    noteId = "packing",
                    title = "Packing",
                    items =
                        listOf(
                            ChecklistItem("passport", "Passport", false),
                            ChecklistItem("socks", "Socks", true),
                        ),
                )
            }

        composeRule.setContent { NookTheme { NookNotes(repository, onLogout = {}) } }

        composeRule.onNodeWithText("1 of 2 done").assertIsDisplayed()
        composeRule.onNodeWithTag("new_note").performClick()
        composeRule.onNodeWithText("Checklist").performClick()
        composeRule.onNodeWithTag("checklist_editor").assertIsDisplayed()
    }

    @Test
    fun checkingAnItemMovesItToTheCompletedSection() {
        val repository =
            repository().also {
                it.createChecklist(
                    "packing",
                    "Packing",
                    listOf(ChecklistItem("passport", "Passport", false)),
                )
            }

        composeRule.setContent { NookTheme { NookNotes(repository, onLogout = {}) } }

        composeRule.onNodeWithTag("note_packing").performClick()
        composeRule.onNodeWithTag("checklist_toggle_passport").performClick()
        composeRule.onNodeWithText("COMPLETED 1").assertIsDisplayed()
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
