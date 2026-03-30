package org.righteffort.ourgrocerylist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.runBlocking
import org.righteffort.ourgrocerylist.client.ClientIdRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class OurGroceryListApp : Application() {

    override fun onCreate() {
        super.onCreate()
        @Suppress("KotlinConstantConditions")
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Requires `adb reverse tcp:8080 tcp:8080`.
            Firebase.firestore.useEmulator("127.0.0.1", 8080)  // localhost may not resolve.
        }
    }

    val clientId: String by lazy {
        runBlocking { ClientIdRepository(dataStore).getOrCreate() }
    }

    val repository: ShoppingRepository by lazy {
        FirestoreShoppingRepository(
            firestore = Firebase.firestore,
            listId = "default",
            clientId = clientId,
        )
    }

    val undoRedoManager: UndoRedoManager by lazy { UndoRedoManager(repository) }
}
