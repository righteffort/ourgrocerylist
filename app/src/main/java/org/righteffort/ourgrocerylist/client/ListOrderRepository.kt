package org.righteffort.ourgrocerylist.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.first

private const val KEY_PREFIX = "list_order_"

interface ListOrderRepository {
    suspend fun loadAll(): Map<String, Long>
    suspend fun save(listId: String, timestamp: Long)
    suspend fun remove(listId: String)
}

class DataStoreListOrderRepository(
    private val dataStore: DataStore<Preferences>,
) : ListOrderRepository {

    override suspend fun loadAll(): Map<String, Long> =
        dataStore.data.first().asMap()
            .filterKeys { it.name.startsWith(KEY_PREFIX) }
            .mapKeys { (key, _) -> key.name.removePrefix(KEY_PREFIX) }
            .mapValues { (_, v) -> v as Long }

    override suspend fun save(listId: String, timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("$KEY_PREFIX$listId")] = timestamp
        }
    }

    override suspend fun remove(listId: String) {
        dataStore.edit { prefs ->
            prefs.remove(longPreferencesKey("$KEY_PREFIX$listId"))
        }
    }
}
