package io.maxdoe.nook.notes

import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.nostr.LocalKeySigner
import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.SignedNostrEvent
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.UnsignedNostrEvent

data class ParsedNookEvent(
    val noteId: String,
    val event: SignedNostrEvent,
    val payload: NookPayload,
)

class NookEventCodec(
    private val signer: Signer,
) {
    constructor(keyPair: NostrKeyPair) : this(LocalKeySigner(keyPair))

    suspend fun build(
        noteId: String,
        payload: NookPayload,
        createdAt: Long,
    ): SignedNostrEvent {
        require(noteId.isNotBlank()) { "A Nook note ID is required." }
        return signer
            .signEvent(
                UnsignedNostrEvent(
                    createdAt = createdAt,
                    kind = NOOK_KIND,
                    tags = listOf(listOf(D_TAG, "$NOOK_PREFIX$noteId")),
                    content = signer.nip44Encrypt(NookPayloadJson.encode(payload)),
                ),
            ).also { signed ->
                require(signed.publicKey == signer.publicKey && NostrEventSigner.verify(signed)) {
                    "Signer returned an invalid event."
                }
            }
    }

    suspend fun parse(event: SignedNostrEvent): ParsedNookEvent? {
        val signedEvent =
            event.takeIf {
                it.kind == NOOK_KIND &&
                    it.publicKey == signer.publicKey &&
                    NostrEventSigner.verify(it)
            } ?: return null
        val dValue =
            signedEvent.tags
                .firstOrNull { it.firstOrNull() == D_TAG }
                ?.getOrNull(1)
        return dValue?.let { value ->
            value
                .takeIf { it.startsWith(NOOK_PREFIX) && it.length > NOOK_PREFIX.length }
                ?.let { taggedNoteId ->
                    runCatching { signer.nip44Decrypt(signedEvent.content) }
                        .getOrNull()
                        ?.let(NookPayloadJson::decode)
                        ?.let { payload ->
                            ParsedNookEvent(
                                noteId = taggedNoteId.removePrefix(NOOK_PREFIX),
                                event = signedEvent,
                                payload = payload,
                            )
                        }
                }
        }
    }

    companion object {
        const val NOOK_KIND = 30_078
        const val NOOK_PREFIX = "nook:"
        private const val D_TAG = "d"
    }
}

internal fun ParsedNookEvent.isNewerThan(other: ParsedNookEvent): Boolean =
    event.createdAt > other.event.createdAt ||
        (event.createdAt == other.event.createdAt && event.id < other.event.id)
