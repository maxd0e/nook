package io.maxdoe.nook.nostr

import io.maxdoe.nook.crypto.NostrKeyPair

data class UnsignedNostrEvent(
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>> = emptyList(),
    val content: String = "",
)

data class SignedNostrEvent(
    val id: String,
    val publicKey: String,
    val createdAt: Long,
    val kind: Int,
    val tags: List<List<String>>,
    val content: String,
    val signature: String,
)

expect object NostrEventSigner {
    fun sign(event: UnsignedNostrEvent, keyPair: NostrKeyPair): SignedNostrEvent

    fun verify(event: SignedNostrEvent): Boolean
}
