package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User

interface ListRepository {
    fun observeLists(): Flow<List<ListMetadata>>
    suspend fun createList(owner: User, name: String): String
    suspend fun renameList(list: ListMetadata, name: String)
    suspend fun deleteList(list: ListMetadata)
    suspend fun addEditor(list: ListMetadata, email: String)
}
