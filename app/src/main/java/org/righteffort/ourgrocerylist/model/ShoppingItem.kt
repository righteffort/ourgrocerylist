package org.righteffort.ourgrocerylist.model

// User-editable fields
data class ItemFields(
    val name: String,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
)

data class ShoppingItem(
    val id: String,
    val version: Int = 0,
    val fields: ItemFields,
)
