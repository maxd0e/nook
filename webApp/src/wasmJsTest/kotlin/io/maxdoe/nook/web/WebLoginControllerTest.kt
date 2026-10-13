package io.maxdoe.nook.web

import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.UnsignedNostrEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebLoginControllerTest {
    @Test
    fun signedEventValidation_rejectsExtensionChangingRequestedContent() {
        val keyPair = NostrKeyPair.generate()
        val requested =
            UnsignedNostrEvent(
                createdAt = 100,
                kind = 30_078,
                tags = listOf(listOf("d", "nook-note")),
                content = "ciphertext",
            )
        val altered = requested.copy(content = "replacement ciphertext")
        val signedAlteredEvent = NostrEventSigner.sign(altered, keyPair)

        val failure =
            assertFailsWith<IllegalStateException> {
                validateSignedEvent(requested, signedAlteredEvent, keyPair.publicKeyHex)
            }

        assertEquals("Signer changed the requested event.", failure.message)
    }

    @Test
    fun invalidNsec_remainsOnPasteScreenAndDoesNotOpenSession() =
        runTest {
            val sessions = RecordingSessionFactory()
            val controller = WebLoginController(sessions)

            controller.showPasteKey()
            controller.signInLocal("not-a-key")

            assertIs<WebLoginScreen.PasteKey>(controller.screen)
            assertEquals("Enter a valid nsec private key.", controller.error)
            assertEquals(0, sessions.opened)
        }

    @Test
    fun generatedKey_requiresConfirmationAndIsDiscardedWhenReturningToWelcome() =
        runTest {
            val sessions = RecordingSessionFactory()
            val controller = WebLoginController(sessions)

            controller.createKey()
            val backup = assertIs<WebLoginScreen.BackupKey>(controller.screen)
            controller.completeBackup()

            assertEquals(0, sessions.opened)
            controller.showWelcome()

            assertEquals(WebLoginScreen.Welcome, controller.screen)
            assertNull(controller.error)
        }

    @Test
    fun confirmedGeneratedKey_opensMemoryOnlySession() =
        runTest {
            val sessions = RecordingSessionFactory()
            val controller = WebLoginController(sessions)

            controller.createKey()
            controller.setBackupConfirmed(true)
            controller.completeBackup()

            assertEquals(WebLoginScreen.SignedIn, controller.screen)
            assertEquals(1, sessions.opened)
            assertNull(controller.error)
        }

    @Test
    fun logout_clearsSessionAndReturnsToWelcome() =
        runTest {
            val sessions = RecordingSessionFactory()
            val controller = WebLoginController(sessions)

            controller.signInLocal(NostrKeyPair.generate().nsec())
            controller.logout()

            assertEquals(WebLoginScreen.Welcome, controller.screen)
            assertTrue(sessions.session.cleared)
        }

    private class RecordingSessionFactory : WebNoteSessionFactory {
        var opened = 0
        val session = RecordingSession()

        override suspend fun open(signer: Signer): WebNoteSession {
            opened += 1
            return session
        }
    }

    private class RecordingSession : WebNoteSession {
        var cleared = false

        override suspend fun clearAndClose() {
            cleared = true
        }
    }
}
