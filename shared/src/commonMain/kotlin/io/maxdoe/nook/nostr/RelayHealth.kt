package io.maxdoe.nook.nostr

enum class RelayConnectionStatus {
    Connecting,
    Connected,
    Failed,
}

enum class RelayFailureReason {
    Network,
    SecureConnection,
    RelayRejected,
    Unknown,
}

data class RelayConnectionHealth(
    val url: String,
    val status: RelayConnectionStatus,
    val failure: RelayFailureReason? = null,
)

data class RelaySyncHealth(
    val relays: List<RelayConnectionHealth> = emptyList(),
) {
    val failure: RelayFailureReason?
        get() = relays.firstNotNullOfOrNull(RelayConnectionHealth::failure)
}
