package io.maxdoe.nook.notes

import io.maxdoe.nook.crypto.Nip44Cipher
import io.maxdoe.nook.crypto.NostrKeyPair
import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.UnsignedNostrEvent
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NookPayloadEventTest {
    private val keyPair = NostrKeyPair.generate()
    private val codec = NookEventCodec(keyPair)

    @Test
    fun payloads_roundTripAndKeepUnknownFields() {
        val note =
            NotePayload(
                title = "Garden",
                body = "## Spring",
                pinned = true,
                createdAt = 10,
                updatedAt = 11,
                unknownFields = mapOf("future" to JsonPrimitive("kept")),
            )
        val checklist =
            ChecklistPayload(
                title = "Trip",
                items = listOf(ChecklistItem("passport", "Passport", false)),
                pinned = false,
                createdAt = 12,
                updatedAt = 13,
            )

        assertEquals(note, NookPayloadJson.decode(NookPayloadJson.encode(note)))
        assertEquals(checklist, NookPayloadJson.decode(NookPayloadJson.encode(checklist)))
        assertEquals(TombstonePayload(14), NookPayloadJson.decode(NookPayloadJson.encode(TombstonePayload(14))))
        assertTrue(NookPayloadJson.encode(note).contains("\"future\":\"kept\""))
    }

    @Test
    fun builtEvent_isEncryptedNookEventAndParsesBack() {
        val payload = NotePayload("Secret", "private body", false, 10, 11)

        val event = codec.build(noteId = "abc-123", payload = payload, createdAt = 12)
        val parsed = codec.parse(event)

        assertEquals(NookEventCodec.NOOK_KIND, event.kind)
        assertEquals(listOf(listOf("d", "nook:abc-123")), event.tags)
        assertTrue(event.content.isNotEmpty())
        assertTrue(!event.content.contains("private body"))
        assertEquals("abc-123", parsed?.noteId)
        assertEquals(payload, parsed?.payload)
    }

    @Test
    fun foreignOrUndecryptableEvents_areIgnored() {
        val payload = NookPayloadJson.encode(NotePayload("Title", "Body", false, 1, 1))
        val encrypted = Nip44Cipher.encryptToSelf(payload, keyPair)
        val foreign =
            NostrEventSigner.sign(
                UnsignedNostrEvent(1, NookEventCodec.NOOK_KIND, listOf(listOf("d", "else:one")), encrypted),
                keyPair,
            )
        val undecryptable =
            NostrEventSigner.sign(
                UnsignedNostrEvent(1, NookEventCodec.NOOK_KIND, listOf(listOf("d", "nook:one")), "not-ciphertext"),
                keyPair,
            )

        assertNull(codec.parse(foreign))
        assertNull(codec.parse(undecryptable))
    }
}
