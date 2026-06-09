package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ShoppingItem
import java.util.UUID

class FakeShoppingRepository : ShoppingRepository {

    private val items = MutableStateFlow<List<ShoppingItem>>(emptyList())

    override fun newItemId(): String = UUID.randomUUID().toString()

    override fun observeItems(): Flow<List<ShoppingItem>> = items.asStateFlow()

    override fun observeRemotelyModifiedItemIds(): Flow<Set<String>> = emptyFlow()

    override suspend fun apply(command: Command) {
        items.value = when (command) {
            is Command.AddItem -> items.value + command.item
            is Command.DeleteItem -> items.value.filter { it.id != command.item.id }
            is Command.EditItem -> items.value.map {
                if (it.id == command.previousSnapshot.id) it.copy(fields = command.newFields) else it
            }
            is Command.CheckItem -> items.value.map {
                if (it.id == command.item.id) it.copy(fields = it.fields.copy(checked = true)) else it
            }
            is Command.UncheckItem -> items.value.map {
                if (it.id == command.item.id) it.copy(fields = it.fields.copy(checked = false)) else it
            }
        }
    }
}
