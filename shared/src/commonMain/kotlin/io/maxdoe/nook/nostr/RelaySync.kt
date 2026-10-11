package io.maxdoe.nook.nostr

import io.maxdoe.nook.notes.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class RelaySyncStatus {
    Offline,
    Syncing,
    Synced,
}

class RelaySync(
    private val repository: NoteRepository,
    private val publicKey: String,
    private val transport: RelayTransport,
    private val scope: CoroutineScope,
    private val relayUrls: List<String> = NookRelays.urls,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val reconnectDelayMillis: Long = DEFAULT_RECONNECT_DELAY_MILLIS,
) {
    private val connections = mutableMapOf<String, RelayConnection>()
    private val caughtUpRelays = mutableSetOf<String>()
    private val connectionMutex = Mutex()
    private val mutableStatus = MutableStateFlow(RelaySyncStatus.Offline)
    private var rootJob: Job? = null
    private var publishJob: Job? = null

    val status: StateFlow<RelaySyncStatus> = mutableStatus.asStateFlow()

    fun start() {
        if (rootJob != null) return
        rootJob =
            scope.launch {
                relayUrls.distinct().forEach { url -> launch { runRelay(url) } }
                launch {
                    repository.notes.collect { schedulePublish() }
                }
            }
    }

    suspend fun stop() {
        rootJob?.cancelAndJoin()
        rootJob = null
        publishJob?.cancelAndJoin()
        publishJob = null
        val openConnections =
            connectionMutex.withLock {
                connections.values.toList().also {
                    connections.clear()
                    caughtUpRelays.clear()
                    updateStatusLocked()
                }
            }
        openConnections.forEach { it.close() }
    }

    private suspend fun runRelay(url: String) {
        while (currentCoroutineContext().isActive) {
            runCatching {
                val connection = transport.connect(url)
                connectionMutex.withLock {
                    connections[url] = connection
                    caughtUpRelays.remove(url)
                    updateStatusLocked()
                }
                connection.send(
                    RelayCommand.Subscribe(
                        subscriptionId = subscriptionId(url),
                        filter = NostrFilter(kinds = listOf(NOOK_KIND), authors = listOf(publicKey)),
                    ),
                )
                publishDirtyTo(connection)
                connection.messages.collect { message -> handleMessage(url, message) }
            }
            removeConnection(url)
            if (currentCoroutineContext().isActive) delay(reconnectDelayMillis)
        }
    }

    private fun schedulePublish() {
        updateStatus()
        publishJob?.cancel()
        publishJob =
            scope.launch {
                delay(debounceMillis)
                publishDirtyToOpenRelays()
            }
    }

    private suspend fun publishDirtyToOpenRelays() {
        val openConnections = connectionMutex.withLock { connections.values.toList() }
        openConnections.forEach { connection -> publishDirtyTo(connection) }
    }

    private suspend fun publishDirtyTo(connection: RelayConnection) {
        repository.dirtyEvents().forEach { event -> connection.send(RelayCommand.Publish(event)) }
    }

    private fun handleMessage(
        url: String,
        message: RelayMessage,
    ) {
        when (message) {
            is RelayMessage.Event -> {
                repository.receive(message.event)
                updateStatus()
            }
            is RelayMessage.Published -> {
                if (message.accepted) repository.markPublished(message.eventId)
                updateStatus()
            }
            is RelayMessage.EndOfStoredEvents ->
                scope.launch {
                    connectionMutex.withLock {
                        caughtUpRelays += url
                        updateStatusLocked()
                    }
                }
            is RelayMessage.Closed,
            is RelayMessage.Notice,
            -> Unit
        }
    }

    private suspend fun removeConnection(url: String) {
        val disconnected =
            connectionMutex.withLock {
                caughtUpRelays.remove(url)
                connections.remove(url).also { updateStatusLocked() }
            }
        disconnected?.close()
    }

    private fun updateStatus() {
        scope.launch {
            connectionMutex.withLock { updateStatusLocked() }
        }
    }

    private fun updateStatusLocked() {
        mutableStatus.value =
            when {
                connections.isEmpty() -> RelaySyncStatus.Offline
                repository.dirtyEvents().isNotEmpty() || caughtUpRelays.isEmpty() -> RelaySyncStatus.Syncing
                else -> RelaySyncStatus.Synced
            }
    }

    private fun subscriptionId(url: String): String = "nook-${url.hashCode().toUInt().toString(16)}"

    private companion object {
        const val NOOK_KIND = 30_078
        const val DEFAULT_DEBOUNCE_MILLIS = 1_000L
        const val DEFAULT_RECONNECT_DELAY_MILLIS = 2_000L
    }
}
