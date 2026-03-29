package org.righteffort.ourgrocerylist.model

data class ShoppingItem(
    val id: String,
    val name: String,
    val quantity: Double = 1.0,
    val checked: Boolean = false,
    val version: Int = 0,
)
