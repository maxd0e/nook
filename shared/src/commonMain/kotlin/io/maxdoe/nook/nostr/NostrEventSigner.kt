package io.maxdoe.nook.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
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

object NostrEventSigner {
    fun sign(
        event: UnsignedNostrEvent,
        keyPair: NostrKeyPair,
    ): SignedNostrEvent {
        val signer = NostrSignerSync(KeyPair(keyPair.privateKeyBytes()))
        val signed =
            signer.sign<Event>(
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags.map { it.toTypedArray() }.toTypedArray(),
                content = event.content,
            )
        return signed.toNookEvent()
    }

    fun verify(event: SignedNostrEvent): Boolean =
        runCatching {
            EventHasher.hashId(
                pubKey = event.publicKey,
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags.map { it.toTypedArray() }.toTypedArray(),
                content = event.content,
            ) == event.id &&
                Nip01Crypto.verify(
                    signature = event.signature.hexToByteArray(),
                    hash = event.id.hexToByteArray(),
                    pubKey = event.publicKey.hexToByteArray(),
                )
        }.getOrDefault(false)

    private fun Event.toNookEvent(): SignedNostrEvent =
        SignedNostrEvent(
            id = id,
            publicKey = pubKey,
            createdAt = createdAt,
            kind = kind,
            tags = tags.map { it.toList() },
            content = content,
            signature = sig,
        )
}
