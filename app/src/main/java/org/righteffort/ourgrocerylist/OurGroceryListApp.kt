package org.righteffort.ourgrocerylist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import org.righteffort.ourgrocerylist.client.ClientIdRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class OurGroceryListApp : Application() {

    // replay=1 so the ViewModel sees this error even if it subscribes after emission.
    // internal so MainActivity can emit errors from the auth/init coroutine it owns.
    internal val _initErrors = MutableSharedFlow<String>(replay = 1)
    val initErrors: SharedFlow<String> = _initErrors.asSharedFlow()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Requires `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done`
            // Use 127.0.0.1 — some devices fail to DNS-resolve "localhost".
            Firebase.auth.useEmulator("127.0.0.1", 9099)
            Firebase.firestore.useEmulator("127.0.0.1", 8080)
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
