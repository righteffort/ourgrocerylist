package org.righteffort.ourgrocerylist.repository

interface SharingRepository {
    suspend fun addEditor(listId: String, email: String)
}
