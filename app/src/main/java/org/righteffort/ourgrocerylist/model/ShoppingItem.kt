package org.righteffort.ourgrocerylist.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.security.MessageDigest

// User-editable fields
@Serializable
data class ItemFields(
    val name: String,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
) {
    // Canonical JSON (keys sorted alphabetically) → SHA-256 hex.
    // Uses the generated serializer so new fields are included automatically.
    val fingerprint: String get() {
        val element = fingerprintJson.encodeToJsonElement(serializer(), this).jsonObject
        val sorted = JsonObject(element.entries.sortedBy { it.key }.associate { it.key to it.value })
        val canonical = fingerprintJson.encodeToString(sorted)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val fingerprintJson = Json.Default
    }
}

@Serializable
data class ShoppingItem(
    val id: String,
    val fields: ItemFields,
)
