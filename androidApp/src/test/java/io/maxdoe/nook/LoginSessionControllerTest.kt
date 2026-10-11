package io.maxdoe.nook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginSessionControllerTest {
    @Test
    fun signIn_validNsec_persistsAndShowsSignedIn() {
        val store = FakeLoginSessionStore()
        val controller = LoginSessionController(store, FakeNsecKeyCodec)

        controller.signIn(FakeNsecKeyCodec.validNsec)

        assertEquals(LoginScreen.SignedIn, controller.screen)
        assertTrue(store.hasValidKey())
        assertNull(controller.pasteError)
    }

    @Test
    fun signIn_invalidNsec_showsErrorWithoutPersisting() {
        val store = FakeLoginSessionStore()
        val controller = LoginSessionController(store, FakeNsecKeyCodec)

        controller.signIn("nsec1not-a-key")

        assertEquals(LoginScreen.Welcome, controller.screen)
        assertEquals("Enter a valid nsec private key.", controller.pasteError)
        assertFalse(store.hasValidKey())
    }

    @Test
    fun createKey_cannotCompleteUntilBackupIsConfirmed() {
        val store = FakeLoginSessionStore()
        val controller = LoginSessionController(store, FakeNsecKeyCodec)
        controller.createKey()

        controller.completeBackup()
        assertTrue(controller.screen is LoginScreen.BackupKey)
        assertFalse(store.hasValidKey())

        controller.setBackupConfirmed(true)
        controller.completeBackup()
        assertEquals(LoginScreen.SignedIn, controller.screen)
        assertTrue(store.hasValidKey())
    }

    @Test
    fun logout_clearsStoredKeyAndLocalData() {
        val store = FakeLoginSessionStore().also { it.save(FakeNsecKeyCodec.validNsec) }
        val controller = LoginSessionController(store, FakeNsecKeyCodec)

        controller.logout()

        assertEquals(LoginScreen.Welcome, controller.screen)
        assertFalse(store.hasValidKey())
        assertTrue(store.cleared)
    }

    private class FakeLoginSessionStore : LoginSessionStore {
        private var nsec: String? = null
        var cleared = false
            private set

        override fun hasValidKey(): Boolean = nsec == FakeNsecKeyCodec.validNsec

        override fun save(nsec: String) {
            this.nsec = nsec
        }

        override fun clear() {
            nsec = null
            cleared = true
        }
    }

    private object FakeNsecKeyCodec : NsecKeyCodec {
        const val validNsec = "nsec1valid"

        override fun create(): String = validNsec

        override fun normalizeOrNull(value: String): String? = validNsec.takeIf { it == value }
    }
}
