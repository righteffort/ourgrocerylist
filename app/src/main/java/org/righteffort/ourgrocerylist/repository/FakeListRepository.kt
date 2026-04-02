package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User
import java.util.UUID

class FakeListRepository(
    initialLists: List<ListMetadata> = emptyList(),
) : ListRepository {

    private val _lists = MutableStateFlow(initialLists)

    override fun observeLists(): Flow<List<ListMetadata>> = _lists.asStateFlow()

    override suspend fun createList(owner: User, name: String): String {
        val id = UUID.randomUUID().toString()
        _lists.value = _lists.value + ListMetadata(id = id, name = name, isOwner = true)
        return id
    }

    override suspend fun renameList(listId: String, name: String) {
        _lists.value = _lists.value.map { if (it.id == listId) it.copy(name = name) else it }
    }

    override suspend fun deleteList(listId: String) {
        _lists.value = _lists.value.filter { it.id != listId }
    }
}
