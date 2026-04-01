package org.righteffort.ourgrocerylist.repository

interface SharingRepository {
    suspend fun addEditor(email: String)
}
