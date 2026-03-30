package org.righteffort.ourgrocerylist.model

// User-editable fields
data class ItemFields(
    val name: String,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
) {
    // TODO: Replace with cross-platform stable hash (e.g. canonical JSON → SHA-256) for Firestore
    val fingerprint: String get() = "$name|$quantity|$checked"
}

data class ShoppingItem(
    val id: String,
    val fields: ItemFields,
)
