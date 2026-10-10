package io.maxdoe.nook.notes

import io.maxdoe.nook.crypto.Nip44Cipher
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.SignedNostrEvent
import io.maxdoe.nook.nostr.UnsignedNostrEvent

data class ParsedNookEvent(
    val noteId: String,
    val event: SignedNostrEvent,
    val payload: NookPayload,
)

class NookEventCodec(
    private val keyPair: NostrKeyPair,
) {
    fun build(
        noteId: String,
        payload: NookPayload,
        createdAt: Long,
    ): SignedNostrEvent {
        require(noteId.isNotBlank()) { "A Nook note ID is required." }
        return NostrEventSigner.sign(
            event =
                UnsignedNostrEvent(
                    createdAt = createdAt,
                    kind = NOOK_KIND,
                    tags = listOf(listOf(D_TAG, "$NOOK_PREFIX$noteId")),
                    content = Nip44Cipher.encryptToSelf(NookPayloadJson.encode(payload), keyPair),
                ),
            keyPair = keyPair,
        )
    }

    fun parse(event: SignedNostrEvent): ParsedNookEvent? =
        event
            .takeIf { it.kind == NOOK_KIND && NostrEventSigner.verify(it) }
            ?.let { signedEvent ->
                val dValue =
                    signedEvent.tags
                        .firstOrNull { it.firstOrNull() == D_TAG }
                        ?.getOrNull(1)
                dValue?.let { value ->
                    value
                        .takeIf { it.startsWith(NOOK_PREFIX) && it.length > NOOK_PREFIX.length }
                        ?.let { taggedNoteId ->
                            Nip44Cipher
                                .decryptFromSelf(signedEvent.content, keyPair)
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
