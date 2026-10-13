@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package io.maxdoe.nook.web

import io.ktor.client.HttpClient
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.nostr.KtorRelayTransport
import io.maxdoe.nook.nostr.LocalKeySigner
import io.maxdoe.nook.nostr.NookRelays
import io.maxdoe.nook.nostr.RelaySync
import io.maxdoe.nook.nostr.RelaySyncHealth
import io.maxdoe.nook.nostr.RelaySyncStatus
import io.maxdoe.nook.nostr.RelayTransport
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.createNookHttpClient
import io.maxdoe.nook.notes.EpochSecondsClock
import io.maxdoe.nook.notes.NookEventCodec
import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface WebLoginScreen {
    data object Welcome : WebLoginScreen

    data object PasteKey : WebLoginScreen

    data class BackupKey(
        val nsec: String,
        val keyPair: NostrKeyPair,
        val savedConfirmed: Boolean = false,
    ) : WebLoginScreen

    data object SignedIn : WebLoginScreen
}

interface WebNoteSession {
    val workspace: WebWorkspace?

    suspend fun clearAndClose()
}

/** The browser UI's narrow view of a signed-in session. Keys never cross this boundary. */
data class WebWorkspace(
    val repository: NoteRepository,
    val syncStatus: StateFlow<RelaySyncStatus>,
    val syncHealth: StateFlow<RelaySyncHealth>,
)

fun interface WebNoteSessionFactory {
    suspend fun open(signer: Signer): WebNoteSession
}

class WebLoginController(
    private val sessionFactory: WebNoteSessionFactory =
        WebNoteSessionFactory { signer ->
            WebNookNoteSession.open(signer, relayUrls = if (browserE2eMode()) emptyList() else NookRelays.urls)
        },
) {
    var screen: WebLoginScreen = WebLoginScreen.Welcome
        private set

    var error: String? = null
        private set

    private var session: WebNoteSession? = null

    val workspace: WebWorkspace?
        get() = session?.workspace

    fun showPasteKey() {
        error = null
        screen = WebLoginScreen.PasteKey
    }

    fun showWelcome() {
        error = null
        screen = WebLoginScreen.Welcome
    }

    fun createKey() {
        error = null
        val keyPair = NostrKeyPair.generate()
        screen = WebLoginScreen.BackupKey(keyPair.nsec(), keyPair)
    }

    fun setBackupConfirmed(confirmed: Boolean) {
        val backup = screen as? WebLoginScreen.BackupKey ?: return
        screen = backup.copy(savedConfirmed = confirmed)
    }

    suspend fun completeBackup() {
        val backup = screen as? WebLoginScreen.BackupKey ?: return
        if (!backup.savedConfirmed) return
        openSession(LocalKeySigner(backup.keyPair))
    }

    suspend fun signInLocal(value: String) {
        val keyPair = NostrKeyPair.fromNsec(value.trim())
        if (keyPair == null) {
            error = "Enter a valid nsec private key."
            return
        }
        openSession(LocalKeySigner(keyPair))
    }

    suspend fun signInWithExtension() {
        val availabilityError = Nip07Signer.availabilityErrorOrNull()
        if (availabilityError != null) {
            error = availabilityError
            return
        }
        try {
            openSession(Nip07Signer.connect())
        } catch (failure: Throwable) {
            error = failure.message ?: "The Nostr extension could not sign in."
        }
    }

    suspend fun logout() {
        session?.clearAndClose()
        session = null
        error = null
        screen = WebLoginScreen.Welcome
    }

    private suspend fun openSession(signer: Signer) {
        try {
            session = sessionFactory.open(signer)
            error = null
            screen = WebLoginScreen.SignedIn
        } catch (failure: Throwable) {
            error = failure.message ?: "Nook could not start this session."
        }
    }
}

private class WebNookNoteSession private constructor(
    private val store: IndexedDbEncryptedEventStore,
    private val repository: NoteRepository,
    private val sync: RelaySync,
    private val client: HttpClient?,
    private val scope: CoroutineScope,
) : WebNoteSession {
    override val workspace = WebWorkspace(repository, sync.status, sync.health)
    private val closeMutex = Mutex()
    private var closed = false

    override suspend fun clearAndClose() {
        closeMutex.withLock {
            if (closed) return
            closed = true
            try {
                sync.stop()
                store.clear()
            } finally {
                client?.close()
                scope.cancel()
            }
        }
    }

    companion object {
        suspend fun open(
            signer: Signer,
            relayUrls: List<String> = NookRelays.urls,
        ): WebNoteSession {
            val scope = MainScope()
            val client = relayUrls.takeIf(List<String>::isNotEmpty)?.let { createNookHttpClient() }
            return try {
                val store = IndexedDbEncryptedEventStore.open(signer.publicKey, loadExisting = !browserE2eMode())
                val repository =
                    NoteRepository(
                        store = store,
                        codec = NookEventCodec(signer),
                        clock = EpochSecondsClock { browserMillis().toLong() / 1_000L },
                    )
                val transport =
                    client?.let(::KtorRelayTransport) ?: RelayTransport {
                        error("Relay transport is disabled for this session.")
                    }
                val sync = RelaySync(repository, signer.publicKey, transport, scope, relayUrls)
                scope.launch {
                    repository.load()
                    sync.start()
                }
                WebNookNoteSession(store, repository, sync, client, scope)
            } catch (error: Throwable) {
                client?.close()
                scope.cancel()
                throw error
            }
        }
    }
}

private fun browserMillis(): Double = js("Date.now()")

private fun browserE2eMode(): Boolean = js("globalThis.__NOOK_E2E__ === true")
