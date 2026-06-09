package org.righteffort.ourgrocerylist.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

private val EMULATOR_USERNAME_KEY = stringPreferencesKey("emulator_username")

class EmulatorUsernameRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun get(): String? = dataStore.data.first()[EMULATOR_USERNAME_KEY]

    suspend fun save(username: String) {
        dataStore.edit { it[EMULATOR_USERNAME_KEY] = username }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(EMULATOR_USERNAME_KEY) }
    }
}
