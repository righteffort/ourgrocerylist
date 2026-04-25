package org.righteffort.ourgrocerylist.ui

import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.ShoppingItem

data class UiState(
    // These are independent of the currently selected list:
    val currentUserEmail: String = "",
    val lists: List<ListMetadata> = emptyList(),
    // These refer to the currently selected list:
    val uncheckedItems: List<ShoppingItem> = emptyList(),
    val checkedItems: List<ShoppingItem> = emptyList(),
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
    val currentListName: String = "",
    val isOwner: Boolean = false,
)
