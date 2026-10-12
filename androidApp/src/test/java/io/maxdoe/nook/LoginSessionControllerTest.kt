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

        controller.signIn(FakeNsecKeyCodec.VALID_NSEC)

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
    fun cancelBackup_returnsToWelcomeWithoutSavingGeneratedKey() {
        val store = FakeLoginSessionStore()
        val controller = LoginSessionController(store, FakeNsecKeyCodec)
        controller.createKey()

        controller.cancelBackup()

        assertEquals(LoginScreen.Welcome, controller.screen)
        assertFalse(store.hasValidKey())
    }

    @Test
    fun logout_clearsStoredKeyAndLocalData() {
        val store = FakeLoginSessionStore().also { it.save(FakeNsecKeyCodec.VALID_NSEC) }
        val controller = LoginSessionController(store, FakeNsecKeyCodec)

        controller.logout()

        assertEquals(LoginScreen.Welcome, controller.screen)
        assertFalse(store.hasValidKey())
        assertTrue(store.cleared)
    }

    @Test
    fun signerPairing_savesOnlyPublicPairingAndShowsSignedIn() {
        val store = FakeLoginSessionStore()
        val controller = LoginSessionController(store, FakeNsecKeyCodec)
        val publicKey = "a".repeat(64)

        controller.completeSignerPairing(publicKey, "com.example.signer")

        assertEquals(LoginScreen.SignedIn, controller.screen)
        assertEquals(LoginCredentials.ExternalSigner(publicKey, "com.example.signer"), store.credentialsOrNull())
        assertNull(store.nsecOrNull())
    }

    private class FakeLoginSessionStore : LoginSessionStore {
        private var nsec: String? = null
        private var signer: LoginCredentials.ExternalSigner? = null
        var cleared = false
            private set

        override fun hasValidKey(): Boolean = nsec == FakeNsecKeyCodec.VALID_NSEC

        override fun nsecOrNull(): String? = nsec

        override fun credentialsOrNull(): LoginCredentials? = signer ?: nsec?.let(LoginCredentials::LocalKey)

        override fun save(nsec: String) {
            this.nsec = nsec
        }

        override fun saveSigner(
            publicKey: String,
            packageName: String,
        ) {
            nsec = null
            signer = LoginCredentials.ExternalSigner(publicKey, packageName)
        }

        override fun clear() {
            nsec = null
            signer = null
            cleared = true
        }
    }

    private object FakeNsecKeyCodec : NsecKeyCodec {
        const val VALID_NSEC = "nsec1valid"

        override fun create(): String = VALID_NSEC

        override fun normalizeOrNull(value: String): String? = VALID_NSEC.takeIf { it == value }
    }
}
