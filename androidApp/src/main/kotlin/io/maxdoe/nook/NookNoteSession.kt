package io.maxdoe.nook

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.SqlDelightEncryptedEventStore
import io.maxdoe.nook.data.db.NookDatabase
import io.maxdoe.nook.nostr.KtorRelayTransport
import io.maxdoe.nook.nostr.LocalKeySigner
import io.maxdoe.nook.nostr.RelaySync
import io.maxdoe.nook.nostr.RelaySyncHealth
import io.maxdoe.nook.nostr.RelaySyncStatus
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.createNookHttpClient
import io.maxdoe.nook.notes.EpochSecondsClock
import io.maxdoe.nook.notes.NookEventCodec
import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NookNoteSession(
    credentials: LoginCredentials,
    context: Context,
    signerRequester: Nip55ForegroundRequester,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val driver = AndroidSqliteDriver(NookDatabase.Schema, context.applicationContext, DATABASE_NAME)
    private val client: HttpClient = createNookHttpClient()
    val repository: NoteRepository
    val syncHealth: StateFlow<RelaySyncHealth>
    val syncStatus: StateFlow<RelaySyncStatus>

    private val sync: RelaySync
    private val closeMutex = Mutex()
    private var closed = false

    init {
        val signer: Signer =
            when (credentials) {
                is LoginCredentials.LocalKey -> LocalKeySigner(requireNotNull(NostrKeyPair.fromNsec(credentials.nsec)))
                is LoginCredentials.ExternalSigner ->
                    Nip55Signer(
                        context.applicationContext,
                        credentials.publicKey,
                        credentials.packageName,
                        signerRequester,
                    )
            }
        repository =
            NoteRepository(
                store = SqlDelightEncryptedEventStore(driver),
                codec = NookEventCodec(signer),
                clock = EpochSecondsClock { System.currentTimeMillis() / 1_000L },
            )
        sync = RelaySync(repository, signer.publicKey, KtorRelayTransport(client), scope)
        syncStatus = sync.status
        syncHealth = sync.health
        scope.launch {
            repository.load()
            sync.start()
        }
    }

    suspend fun close() {
        closeMutex.withLock {
            if (closed) return
            closed = true
            sync.stop()
            client.close()
            driver.close()
            scope.cancel()
        }
    }

    private companion object {
        const val DATABASE_NAME = "nook.db"
    }
}
