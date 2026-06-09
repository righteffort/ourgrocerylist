package org.righteffort.ourgrocerylist.ui

import org.righteffort.ourgrocerylist.model.ItemFields

data class ItemDialogState(
    val title: String,
    val initialFields: ItemFields,
    val showDelete: Boolean,
    val onSave: (ItemFields) -> Unit,
    // Null when the dialog is opened for a new item that does not yet exist.
    val onDelete: (() -> Unit)?,
    val onCancel: () -> Unit,
)
