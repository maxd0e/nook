package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.data.EncryptedEventStore
import io.maxdoe.nook.data.StoredNostrEvent
import io.maxdoe.nook.notes.EpochSecondsClock
import io.maxdoe.nook.notes.NookEventCodec
import io.maxdoe.nook.notes.NotePayload
import io.maxdoe.nook.notes.NoteRepository
import io.maxdoe.nook.notes.TombstonePayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RelaySyncTest {
    private val keyPair = NostrKeyPair.generate()
    private val codec = NookEventCodec(keyPair)

    @Test
    fun initialSync_downloadsAndStoresEvents() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport()
            val sync = sync(repository, relay)
            val event = codec.build("remote", NotePayload("Remote", "Downloaded", false, 1, 1), 1)

            waitUntil { relay.connections.isNotEmpty() }
            relay.connections.single().emit(RelayMessage.Event("nook-fake", event))

            waitUntil {
                repository.repository.notes.value
                    .singleOrNull()
                    ?.id == "remote"
            }
            assertFalse(repository.repository.dirtyEvents().any { it.id == event.id })
            val subscription =
                relay.connections
                    .single()
                    .sent
                    .first() as RelayCommand.Subscribe
            assertEquals(NostrFilter(listOf(30_078), listOf(keyPair.publicKeyHex)), subscription.filter)
            sync.stop()
        }

    @Test
    fun edit_isPublishedAndReceivedBySecondDevice() =
        runTest {
            val firstRepository = repository()
            val secondRepository = repository()
            val relay = FakeRelayTransport()
            val sync = sync(firstRepository, relay)

            val saved = firstRepository.repository.save("shared", NotePayload("Shared", "From first", false, 1, 1))
            waitUntil {
                relay.connections
                    .singleOrNull()
                    ?.publishedEvents()
                    ?.contains(saved) == true
            }
            relay.connections.single().emit(RelayMessage.Event("other-device", saved))

            assertTrue(secondRepository.repository.receive(saved))
            assertEquals(
                "Shared",
                (
                    secondRepository.repository.notes.value
                        .single()
                        .payload as NotePayload
                ).title,
            )
            sync.stop()
        }

    @Test
    fun dirtyOfflineEdit_isSentAfterReconnect() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport(available = false)
            val saved = repository.repository.save("offline", NotePayload("Offline", "Queued", false, 1, 1))
            val sync = sync(repository, relay)

            delay(20)
            assertTrue(relay.connections.isEmpty())
            assertTrue(repository.repository.dirtyEvents().any { it.id == saved.id })
            relay.available = true

            waitUntil {
                relay.connections
                    .singleOrNull()
                    ?.publishedEvents()
                    ?.contains(saved) == true
            }
            sync.stop()
        }

    @Test
    fun status_movesFromOfflineToSyncingThenSyncedAfterRelayCatchUp() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport(available = false)
            val sync = sync(repository, relay)

            assertEquals(RelaySyncStatus.Offline, sync.status.value)
            relay.available = true
            waitUntil { relay.connections.isNotEmpty() }
            waitUntil { sync.status.value == RelaySyncStatus.Syncing }

            relay.connections.single().emit(RelayMessage.EndOfStoredEvents("nook-fake"))
            waitUntil { sync.status.value == RelaySyncStatus.Synced }
            sync.stop()
        }

    @Test
    fun failedConnection_exposesSafeFailureAndRecoveryClearsIt() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport(available = false)
            val sync = sync(repository, relay)

            waitUntil {
                sync.health.value.relays
                    .singleOrNull()
                    ?.failure == RelayFailureReason.Network
            }
            assertEquals(
                RelayConnectionStatus.Failed,
                sync.health.value.relays
                    .single()
                    .status,
            )

            relay.available = true
            waitUntil {
                sync.health.value.relays
                    .singleOrNull()
                    ?.status == RelayConnectionStatus.Connected
            }
            assertEquals(
                null,
                sync.health.value.relays
                    .single()
                    .failure,
            )
            sync.stop()
        }

    @Test
    fun closedSubscription_clearsSyncedStateAndReconnects() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport()
            val sync = sync(repository, relay)

            waitUntil { relay.connections.isNotEmpty() }
            relay.connections.single().emit(RelayMessage.EndOfStoredEvents("nook-fake"))
            waitUntil { sync.status.value == RelaySyncStatus.Synced }

            relay.connections.single().emit(RelayMessage.Closed("nook-fake", "subscription closed"))
            waitUntil { relay.connections.size == 2 }
            assertEquals(RelaySyncStatus.Syncing, sync.status.value)
            assertTrue(relay.connections.first().closed)
            sync.stop()
        }

    @Test
    fun tombstoneRemovesNoteOnOtherDevice() =
        runTest {
            val firstRepository = repository()
            val secondRepository = repository()
            val relay = FakeRelayTransport()
            val sync = sync(firstRepository, relay)
            val original = firstRepository.repository.save("deleted", NotePayload("Delete", "Me", false, 1, 1))
            secondRepository.repository.receive(original)
            waitUntil { relay.connections.isNotEmpty() }

            val tombstone = firstRepository.repository.save("deleted", TombstonePayload(2))
            waitUntil {
                relay.connections
                    .single()
                    .publishedEvents()
                    .contains(tombstone)
            }
            secondRepository.repository.receive(tombstone)

            assertTrue(
                secondRepository.repository.notes.value
                    .isEmpty(),
            )
            sync.stop()
        }

    @Test
    fun duplicateIncomingEvent_isHandledOnceAndSuccessfulAcknowledgementCleansIt() =
        runTest {
            val repository = repository()
            val relay = FakeRelayTransport()
            val sync = sync(repository, relay)
            val incoming = codec.build("once", NotePayload("Only", "Once", false, 1, 1), 1)
            waitUntil { relay.connections.isNotEmpty() }

            relay.connections.single().emit(RelayMessage.Event("nook-fake", incoming))
            relay.connections.single().emit(RelayMessage.Event("nook-fake", incoming))
            waitUntil { repository.repository.notes.value.size == 1 }
            assertEquals(1, repository.storeSize())

            val local = repository.repository.save("local", NotePayload("Local", "Dirty", false, 1, 1))
            waitUntil {
                relay.connections
                    .single()
                    .publishedEvents()
                    .contains(local)
            }
            relay.connections.single().emit(RelayMessage.Published(local.id, accepted = true, "stored"))
            waitUntil { repository.repository.dirtyEvents().none { it.id == local.id } }
            sync.stop()
        }

    @Test
    fun stop_waitsForRelayCleanupBeforeItAllowsRestart() =
        runTest {
            val repository = repository()
            val closeGate = Channel<Unit>(1)
            val relay = FakeRelayTransport(delayedFirstClose = closeGate)
            val sync = sync(repository, relay)
            waitUntil { relay.connections.isNotEmpty() }

            val stopping = async { sync.stop() }
            relay.closeStarted.receive()
            assertFalse(stopping.isCompleted)

            closeGate.send(Unit)
            stopping.await()
            sync.start()
            waitUntil { relay.connections.size == 2 }
            assertFalse(relay.connections.last().closed)
            sync.stop()
        }

    private fun repository(): TestRepository {
        val store = FakeEncryptedEventStore()
        return TestRepository(NoteRepository(store, codec, EpochSecondsClock { 100 }), store)
    }

    private suspend fun sync(
        testRepository: TestRepository,
        relay: FakeRelayTransport,
    ): RelaySync =
        RelaySync(
            repository = testRepository.repository,
            publicKey = keyPair.publicKeyHex,
            transport = relay,
            scope = CoroutineScope(coroutineContext),
            relayUrls = listOf("wss://fake"),
            debounceMillis = 5,
            reconnectDelayMillis = 5,
        ).also(RelaySync::start)

    private suspend fun waitUntil(predicate: () -> Boolean) {
        repeat(100) {
            if (predicate()) return
            delay(5)
            yield()
        }
        assertTrue(predicate(), "Timed out waiting for relay state")
    }

    private data class TestRepository(
        val repository: NoteRepository,
        private val store: FakeEncryptedEventStore,
    ) {
        fun storeSize(): Int = store.all().size
    }

    private class FakeEncryptedEventStore : EncryptedEventStore {
        private val events = mutableListOf<StoredNostrEvent>()

        override fun all(): List<StoredNostrEvent> = events.toList()

        override suspend fun insert(event: StoredNostrEvent) {
            if (events.none { it.event.id == event.event.id }) events += event
        }

        override suspend fun markClean(eventId: String) {
            val index = events.indexOfFirst { it.event.id == eventId }
            if (index >= 0) events[index] = events[index].copy(dirty = false)
        }
    }

    private class FakeRelayTransport(
        var available: Boolean = true,
        private val delayedFirstClose: Channel<Unit>? = null,
    ) : RelayTransport {
        val connections = mutableListOf<FakeRelayConnection>()
        val closeStarted = Channel<Unit>(1)

        override suspend fun connect(url: String): RelayConnection {
            if (!available) throw IllegalStateException("offline")
            return FakeRelayConnection(
                closeGate = if (connections.isEmpty()) delayedFirstClose else null,
                closeStarted = if (connections.isEmpty()) closeStarted else null,
            ).also(connections::add)
        }
    }

    private class FakeRelayConnection(
        private val closeGate: Channel<Unit>? = null,
        private val closeStarted: Channel<Unit>? = null,
    ) : RelayConnection {
        private val incoming = Channel<RelayMessage>(Channel.UNLIMITED)
        val sent = mutableListOf<RelayCommand>()
        var closed = false

        override val messages: Flow<RelayMessage> = incoming.receiveAsFlow()

        override suspend fun send(command: RelayCommand) {
            sent += command
        }

        override suspend fun close() {
            closeStarted?.trySend(Unit)
            closeGate?.receive()
            closed = true
            incoming.close()
        }

        suspend fun emit(message: RelayMessage) {
            incoming.send(message)
        }

        fun publishedEvents(): List<SignedNostrEvent> =
            sent.filterIsInstance<RelayCommand.Publish>().map(RelayCommand.Publish::event)
    }
}
