package org.righteffort.ourgrocerylist.undo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.righteffort.ourgrocerylist.model.Command
import timber.log.Timber

class UndoRedoStackRepository(
    private val dataStore: DataStore<Preferences>,
    private val listId: String,
) {
    private val undoKey = stringPreferencesKey("undo_stack_$listId")
    private val redoKey = stringPreferencesKey("redo_stack_$listId")

    suspend fun load(): Pair<List<Command>, List<Command>> {
        val prefs = dataStore.data.first()
        return deserialize(prefs[undoKey], "undo") to deserialize(prefs[redoKey], "redo")
    }

    suspend fun save(undoStack: List<Command>, redoStack: List<Command>) {
        dataStore.edit { prefs ->
            prefs[undoKey] = Json.encodeToString(undoStack)
            prefs[redoKey] = Json.encodeToString(redoStack)
        }
    }

    private fun deserialize(json: String?, stackName: String): List<Command> {
        if (json == null) return emptyList()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            Timber.w(e, "Failed to deserialize $stackName stack for list $listId, discarding")
            emptyList()
        }
    }
}
