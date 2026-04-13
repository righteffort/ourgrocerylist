package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User

interface ListRepository {
    fun observeLists(): Flow<List<ListMetadata>>
    suspend fun createList(owner: User, name: String): String
    suspend fun renameList(listId: String, name: String)
    suspend fun deleteList(listId: String)
    suspend fun addEditor(listId: String, email: String)
}
