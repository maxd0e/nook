package io.maxdoe.nook.nostr

import kotlinx.coroutines.flow.Flow

data class NostrFilter(
    val kinds: List<Int>,
    val authors: List<String>,
)

sealed interface RelayCommand {
    data class Subscribe(
        val subscriptionId: String,
        val filter: NostrFilter,
    ) : RelayCommand

    data class Publish(
        val event: SignedNostrEvent,
    ) : RelayCommand
}

sealed interface RelayMessage {
    data class Event(
        val subscriptionId: String,
        val event: SignedNostrEvent,
    ) : RelayMessage

    data class Published(
        val eventId: String,
        val accepted: Boolean,
        val message: String,
    ) : RelayMessage

    data class EndOfStoredEvents(
        val subscriptionId: String,
    ) : RelayMessage

    data class Notice(
        val message: String,
    ) : RelayMessage

    data class Closed(
        val subscriptionId: String,
        val message: String,
    ) : RelayMessage
}

interface RelayConnection {
    val messages: Flow<RelayMessage>

    suspend fun send(command: RelayCommand)

    suspend fun close()
}

fun interface RelayTransport {
    suspend fun connect(url: String): RelayConnection
}
