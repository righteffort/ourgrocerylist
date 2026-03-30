package org.righteffort.ourgrocerylist.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID

private val CLIENT_ID_KEY = stringPreferencesKey("client_id")

class ClientIdRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun getOrCreate(): String {
        val existing = dataStore.data.first()[CLIENT_ID_KEY]
        if (existing != null) return existing
        val newId = UUID.randomUUID().toString()
        dataStore.edit { it[CLIENT_ID_KEY] = newId }
        return newId
    }
}
