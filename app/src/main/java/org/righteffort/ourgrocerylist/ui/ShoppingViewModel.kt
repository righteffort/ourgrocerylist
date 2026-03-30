package org.righteffort.ourgrocerylist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import java.util.UUID

private val ITEM_COMPARATOR = compareBy<ShoppingItem> { it.fields.name.lowercase() }

class ShoppingViewModel(
    private val repository: ShoppingRepository,
) : ViewModel() {

    private val _dialogState = MutableStateFlow<ItemDialogState?>(null)
    val dialogState: StateFlow<ItemDialogState?> = _dialogState.asStateFlow()

    val uiState: StateFlow<UiState> = repository.observeItems()
        .map { items ->
            val (checked, unchecked) = items.partition { it.fields.checked }
            UiState(
                uncheckedItems = unchecked.sortedWith(ITEM_COMPARATOR),
                checkedItems = checked.sortedWith(ITEM_COMPARATOR),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val item = ShoppingItem(
            id = UUID.randomUUID().toString(),
            fields = ItemFields(name = trimmed),
        )
        applyCommand(Command.AddItem(item))
    }

    fun deleteItem(item: ShoppingItem) {
        applyCommand(Command.DeleteItem(item))
    }

    fun editItem(previousSnapshot: ShoppingItem, newFields: ItemFields) {
        applyCommand(Command.EditItem(previousSnapshot, newFields))
    }

    fun checkItem(item: ShoppingItem) {
        applyCommand(Command.CheckItem(item))
    }

    fun uncheckItem(item: ShoppingItem) {
        applyCommand(Command.UncheckItem(item))
    }

    fun openEditDialog(item: ShoppingItem) {
        _dialogState.value = ItemDialogState(
            title = "Edit item",
            initialFields = item.fields,
            showDelete = true,
            onSave = { newFields ->
                editItem(item, newFields)
                dismissDialog()
            },
            onDelete = {
                deleteItem(item)
                dismissDialog()
            },
            onCancel = { dismissDialog() },
        )
    }

    fun openAddDialog(initialName: String) {
        _dialogState.value = ItemDialogState(
            title = "Add item",
            initialFields = ItemFields(name = initialName),
            showDelete = false,
            onSave = { newFields ->
                val item = ShoppingItem(
                    id = UUID.randomUUID().toString(),
                    fields = newFields.copy(name = newFields.name.trim()),
                )
                applyCommand(Command.AddItem(item))
                dismissDialog()
            },
            onDelete = null,
            onCancel = { dismissDialog() },
        )
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    private fun applyCommand(command: Command) {
        viewModelScope.launch {
            repository.apply(command)
        }
    }
}
