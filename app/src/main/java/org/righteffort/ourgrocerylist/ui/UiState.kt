package org.righteffort.ourgrocerylist.ui

import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.ShoppingItem

data class UiState(
    val uncheckedItems: List<ShoppingItem> = emptyList(),
    val checkedItems: List<ShoppingItem> = emptyList(),
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
    val currentListName: String = "",
    val lists: List<ListMetadata> = emptyList(),
    val isOwner: Boolean = false,
)
