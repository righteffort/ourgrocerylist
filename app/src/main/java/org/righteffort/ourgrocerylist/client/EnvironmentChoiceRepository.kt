package org.righteffort.ourgrocerylist.client

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

enum class FirebaseEnvironmentChoice {
    PRODUCTION,
    EMULATOR_ADB_REVERSE,  // 127.0.0.1 — physical device + adb reverse
    EMULATOR_AVD,          // 10.0.2.2  — Android Virtual Device
}

private val ENV_CHOICE_KEY = stringPreferencesKey("firebase_env_choice")

class EnvironmentChoiceRepository(private val dataStore: DataStore<Preferences>) {
    suspend fun get(): FirebaseEnvironmentChoice? {
        val raw = dataStore.data.first()[ENV_CHOICE_KEY] ?: return null
        return FirebaseEnvironmentChoice.valueOf(raw)
    }

    suspend fun save(choice: FirebaseEnvironmentChoice) {
        dataStore.edit { it[ENV_CHOICE_KEY] = choice.name }
    }
}
