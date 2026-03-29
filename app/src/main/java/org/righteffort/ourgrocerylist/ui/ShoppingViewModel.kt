package org.righteffort.ourgrocerylist.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
        applyCommand(Command.EditItem(previousSnapshot, previousSnapshot.copy(fields = newFields)))
    }

    fun checkItem(item: ShoppingItem) {
        applyCommand(Command.CheckItem(item))
    }

    fun uncheckItem(item: ShoppingItem) {
        applyCommand(Command.UncheckItem(item))
    }

    private fun applyCommand(command: Command) {
        viewModelScope.launch {
            repository.apply(command)
        }
    }
}
