package io.maxdoe.nook.notes

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

sealed interface NookPayload {
    val updatedAt: Long
    val unknownFields: Map<String, JsonElement>

    fun withUpdatedAt(updatedAt: Long): NookPayload
}

data class NotePayload(
    val title: String,
    val body: String,
    val pinned: Boolean,
    val createdAt: Long,
    override val updatedAt: Long,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : NookPayload {
    override fun withUpdatedAt(updatedAt: Long): NotePayload = copy(updatedAt = updatedAt)
}

data class ChecklistItem(
    val id: String,
    val text: String,
    val done: Boolean,
)

data class ChecklistPayload(
    val title: String,
    val items: List<ChecklistItem>,
    val pinned: Boolean,
    val createdAt: Long,
    override val updatedAt: Long,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : NookPayload {
    override fun withUpdatedAt(updatedAt: Long): ChecklistPayload = copy(updatedAt = updatedAt)
}

data class TombstonePayload(
    override val updatedAt: Long,
    override val unknownFields: Map<String, JsonElement> = emptyMap(),
) : NookPayload {
    override fun withUpdatedAt(updatedAt: Long): TombstonePayload = copy(updatedAt = updatedAt)
}

object NookPayloadJson {
    private const val VERSION = 1
    private val json = Json { explicitNulls = false }

    fun encode(payload: NookPayload): String = json.encodeToString(JsonObject.serializer(), payload.toJsonObject())

    fun decode(value: String): NookPayload? =
        runCatching { decodeObject(json.parseToJsonElement(value).jsonObject) }.getOrNull()

    private fun decodeObject(value: JsonObject): NookPayload {
        require(value.requiredLong("v") == VERSION.toLong()) { "Unsupported payload version." }
        return if (value["deleted"]?.jsonPrimitive?.booleanOrNull == true) {
            TombstonePayload(
                updatedAt = value.requiredLong("updatedAt"),
                unknownFields = value.unknownExcept("v", "deleted", "updatedAt"),
            )
        } else {
            when (value.requiredString("type")) {
                "note" ->
                    NotePayload(
                        title = value.requiredString("title"),
                        body = value.requiredString("body"),
                        pinned = value.requiredBoolean("pinned"),
                        createdAt = value.requiredLong("createdAt"),
                        updatedAt = value.requiredLong("updatedAt"),
                        unknownFields =
                            value.unknownExcept(
                                "v",
                                "type",
                                "title",
                                "body",
                                "pinned",
                                "createdAt",
                                "updatedAt",
                            ),
                    )
                "checklist" ->
                    ChecklistPayload(
                        title = value.requiredString("title"),
                        items = value.requiredItems(),
                        pinned = value.requiredBoolean("pinned"),
                        createdAt = value.requiredLong("createdAt"),
                        updatedAt = value.requiredLong("updatedAt"),
                        unknownFields =
                            value.unknownExcept(
                                "v",
                                "type",
                                "title",
                                "items",
                                "pinned",
                                "createdAt",
                                "updatedAt",
                            ),
                    )
                else -> error("Unknown payload type.")
            }
        }
    }

    private fun NookPayload.toJsonObject(): JsonObject =
        buildJsonObject {
            unknownFields.forEach { (key, value) -> put(key, value) }
            put("v", VERSION)
            when (this@toJsonObject) {
                is NotePayload -> {
                    put("type", "note")
                    put("title", title)
                    put("body", body)
                    put("pinned", pinned)
                    put("createdAt", createdAt)
                    put("updatedAt", updatedAt)
                }
                is ChecklistPayload -> {
                    put("type", "checklist")
                    put("title", title)
                    put(
                        "items",
                        buildJsonArray {
                            items.forEach { item ->
                                add(
                                    buildJsonObject {
                                        put("id", item.id)
                                        put("text", item.text)
                                        put("done", item.done)
                                    },
                                )
                            }
                        },
                    )
                    put("pinned", pinned)
                    put("createdAt", createdAt)
                    put("updatedAt", updatedAt)
                }
                is TombstonePayload -> {
                    put("deleted", true)
                    put("updatedAt", updatedAt)
                }
            }
        }

    private fun JsonObject.requiredItems(): List<ChecklistItem> =
        getValue("items").jsonArray.map { element ->
            val item = element.jsonObject
            ChecklistItem(
                id = item.requiredString("id"),
                text = item.requiredString("text"),
                done = item.requiredBoolean("done"),
            )
        }

    private fun JsonObject.requiredString(name: String): String = getValue(name).jsonPrimitive.content

    private fun JsonObject.requiredLong(name: String): Long = getValue(name).jsonPrimitive.long

    private fun JsonObject.requiredBoolean(name: String): Boolean =
        getValue(name).jsonPrimitive.booleanOrNull ?: error("$name must be boolean.")

    private fun JsonObject.unknownExcept(vararg keys: String): Map<String, JsonElement> = filterKeys { it !in keys }
}
