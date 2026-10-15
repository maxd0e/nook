package io.maxdoe.nook

import android.content.Context
import android.net.Uri
import io.maxdoe.nook.nostr.NostrEventSigner
import io.maxdoe.nook.nostr.SignedNostrEvent
import io.maxdoe.nook.nostr.Signer
import io.maxdoe.nook.nostr.SignerRequestException
import io.maxdoe.nook.nostr.UnsignedNostrEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Android NIP-55 backend. Foreground requests are deliberately injected so UI owns activities. */
interface Nip55ForegroundRequester {
    suspend fun request(request: Nip55Request): Nip55Result
}

data class Nip55Request(
    val type: String,
    val payload: String = "",
    val peerPublicKey: String? = null,
    val currentUser: String? = null,
    val packageName: String? = null,
    val permissions: String? = null,
)

sealed interface Nip55Result {
    data class Success(
        val result: String,
        val signedEvent: String? = null,
        val packageName: String? = null,
    ) : Nip55Result

    data object Rejected : Nip55Result

    data object Unavailable : Nip55Result

    data class Failed(
        val message: String,
    ) : Nip55Result
}

class Nip55Signer(
    private val context: Context,
    override val publicKey: String,
    private val signerPackage: String,
    private val foregroundRequester: Nip55ForegroundRequester,
) : Signer {
    override suspend fun signEvent(event: UnsignedNostrEvent): SignedNostrEvent {
        val payload = event.toJson()
        val result = request("sign_event", payload, null)
        val signed =
            (result.signedEvent ?: result.result).toSignedEvent()
                ?: throw SignerRequestException("Signer returned a malformed event.")
        check(
            signed.publicKey == publicKey && NostrEventSigner.verify(signed),
        ) { "Signer returned an invalid signature." }
        check(
            signed.createdAt == event.createdAt &&
                signed.kind == event.kind &&
                signed.tags == event.tags &&
                signed.content == event.content,
        ) {
            "Signer changed the requested event."
        }
        return signed
    }

    override suspend fun nip44Encrypt(
        plaintext: String,
        peerPublicKey: String,
    ): String = request("nip44_encrypt", plaintext, peerPublicKey).result

    override suspend fun nip44Decrypt(
        ciphertext: String,
        peerPublicKey: String,
    ): String? = runCatching { request("nip44_decrypt", ciphertext, peerPublicKey).result }.getOrNull()

    private suspend fun request(
        type: String,
        payload: String,
        peerPublicKey: String?,
    ): Nip55Result.Success {
        val background = withContext(Dispatchers.IO) { query(type, payload, peerPublicKey) }
        val result =
            when (background) {
                Nip55Result.Rejected -> throw SignerRequestException("Signer request was rejected.")
                Nip55Result.Unavailable ->
                    withContext(Dispatchers.Main.immediate) {
                        foregroundRequester.request(
                            Nip55Request(type, payload, peerPublicKey, publicKey, signerPackage),
                        )
                    }
                else -> background
            }
        return result as? Nip55Result.Success
            ?: throw SignerRequestException(
                (result as? Nip55Result.Failed)?.message ?: "Signer is unavailable.",
            )
    }

    @Suppress("ReturnCount")
    private fun query(
        type: String,
        payload: String,
        peerPublicKey: String?,
    ): Nip55Result {
        val uri = Uri.parse("content://$signerPackage.${type.uppercase()}")
        return runCatching {
            context.contentResolver
                .query(uri, arrayOf(payload, peerPublicKey.orEmpty(), publicKey), null, null, null)
                ?.use { cursor ->
                    if (cursor.columnNames.contains("rejected")) return Nip55Result.Rejected
                    if (!cursor.moveToFirst()) return Nip55Result.Unavailable
                    Nip55Result.Success(
                        result = cursor.getString(cursor.getColumnIndexOrThrow("result")),
                        signedEvent =
                            cursor.columnNames.takeIf { it.contains("event") }?.let {
                                cursor.getString(cursor.getColumnIndexOrThrow("event"))
                            },
                    )
                } ?: Nip55Result.Unavailable
        }.getOrElse { Nip55Result.Unavailable }
    }
}

private fun UnsignedNostrEvent.toJson(): String =
    JsonObject(
        mapOf(
            "created_at" to JsonPrimitive(createdAt),
            "kind" to JsonPrimitive(kind),
            "tags" to JsonArray(tags.map { JsonArray(it.map(::JsonPrimitive)) }),
            "content" to JsonPrimitive(content),
        ),
    ).toString()

private fun String.toSignedEvent(): SignedNostrEvent? =
    runCatching {
        val value = Json.parseToJsonElement(this).jsonObject
        SignedNostrEvent(
            id = value.getValue("id").jsonPrimitive.content,
            publicKey = value.getValue("pubkey").jsonPrimitive.content,
            createdAt = value.getValue("created_at").jsonPrimitive.long,
            kind = value.getValue("kind").jsonPrimitive.int,
            tags = value.getValue("tags").jsonArray.map { tag -> tag.jsonArray.map { it.jsonPrimitive.content } },
            content = value.getValue("content").jsonPrimitive.content,
            signature = value.getValue("sig").jsonPrimitive.content,
        )
    }.getOrNull()
