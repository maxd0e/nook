package io.maxdoe.nook

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.SqlDelightEncryptedEventStore
import io.maxdoe.nook.data.db.NookDatabase
import io.maxdoe.nook.nostr.KtorRelayTransport
import io.maxdoe.nook.nostr.RelaySync
import io.maxdoe.nook.nostr.createNookHttpClient
import io.maxdoe.nook.notes.EpochSecondsClock
import io.maxdoe.nook.notes.NookEventCodec
import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NookNoteSession(
    nsec: String,
    context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val driver = AndroidSqliteDriver(NookDatabase.Schema, context.applicationContext, DATABASE_NAME)
    private val client: HttpClient = createNookHttpClient()
    val repository: NoteRepository

    private val sync: RelaySync
    private var closed = false

    init {
        val keyPair = requireNotNull(NostrKeyPair.fromNsec(nsec))
        repository =
            NoteRepository(
                store = SqlDelightEncryptedEventStore(driver),
                codec = NookEventCodec(keyPair),
                clock = EpochSecondsClock { System.currentTimeMillis() / 1_000L },
            )
        repository.load()
        sync = RelaySync(repository, keyPair.publicKeyHex, KtorRelayTransport(client), scope)
        sync.start()
    }

    fun close() {
        if (closed) return
        closed = true
        scope.launch {
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
