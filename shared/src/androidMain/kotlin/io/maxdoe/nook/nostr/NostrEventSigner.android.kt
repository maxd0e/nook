package io.maxdoe.nook.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.crypto.EventHasher
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.crypto.Nip01Crypto
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import io.maxdoe.nook.crypto.NostrKeyPair

actual object NostrEventSigner {
    actual fun sign(
        event: UnsignedNostrEvent,
        keyPair: NostrKeyPair,
    ): SignedNostrEvent {
        val signed =
            NostrSignerSync(KeyPair(keyPair.privateKey())).sign<Event>(
                createdAt = event.createdAt,
                kind = event.kind,
                tags = event.tags.map { it.toTypedArray() }.toTypedArray(),
                content = event.content,
            )
        return SignedNostrEvent(
            signed.id,
            signed.pubKey,
            signed.createdAt,
            signed.kind,
            signed.tags.map {
                it.toList()
            },
            signed.content,
            signed.sig,
        )
    }

    actual fun verify(event: SignedNostrEvent): Boolean =
        runCatching {
            EventHasher.hashId(
                event.publicKey,
                event.createdAt,
                event.kind,
                event.tags
                    .map {
                        it.toTypedArray()
                    }.toTypedArray(),
                event.content,
            ) ==
                event.id &&
                Nip01Crypto.verify(
                    event.signature.hexToByteArray(),
                    event.id.hexToByteArray(),
                    event.publicKey.hexToByteArray(),
                )
        }.getOrDefault(false)
}
