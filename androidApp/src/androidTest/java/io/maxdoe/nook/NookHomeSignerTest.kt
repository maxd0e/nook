package io.maxdoe.nook

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NookHomeSignerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun signerAction_requestsPublicKeyDirectlyFromWelcome() {
        val requester = RecordingSignerRequester()
        composeRule.setContent {
            NookTheme {
                NookHome(
                    sessionStore = FakeLoginSessionStore(),
                    signerRequester = requester,
                )
            }
        }

        composeRule.onNodeWithText("Sign in with a signer app").performClick()

        composeRule.waitUntil { requester.request != null }
        assertEquals("get_public_key", requester.request?.type)
        assertEquals(
            "[{\"type\":\"get_public_key\"},{\"type\":\"sign_event\",\"kind\":30078},{\"type\":\"nip44_encrypt\"},{\"type\":\"nip44_decrypt\"}]",
            requester.request?.permissions,
        )
    }

    private class RecordingSignerRequester : Nip55ForegroundRequester {
        var request: Nip55Request? = null

        override suspend fun request(request: Nip55Request): Nip55Result {
            this.request = request
            return Nip55Result.Unavailable
        }
    }

    private class FakeLoginSessionStore : LoginSessionStore {
        override fun hasValidKey(): Boolean = false

        override fun nsecOrNull(): String? = null

        override fun save(nsec: String) = Unit

        override fun clear() = Unit
    }
}
