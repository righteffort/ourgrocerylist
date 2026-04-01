package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ShoppingItem

interface ShoppingRepository {
    fun newItemId(): String
    fun observeItems(): Flow<List<ShoppingItem>>
    fun observeRemotelyModifiedItemIds(): Flow<Set<String>>
    suspend fun apply(command: Command)
    suspend fun ensureListDocument(uid: String)
}