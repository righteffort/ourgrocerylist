package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ShoppingItem

interface ShoppingRepository {
    fun observeItems(): Flow<List<ShoppingItem>>
    suspend fun apply(command: Command)
}