package org.righteffort.ourgrocerylist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.client.ClientIdRepository
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirebaseSharingRepository
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import org.righteffort.ourgrocerylist.repository.SharingRepository

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

class OurGroceryListApp : Application() {

    // replay=1 so the ViewModel sees this error even if it subscribes after emission.
    // internal so MainActivity can emit errors from the auth/init coroutine it owns.
    internal val _initErrors = MutableSharedFlow<String>(replay = 1)
    val initErrors: SharedFlow<String> = _initErrors.asSharedFlow()

    // Set by MainActivity after auth succeeds. Drives observeLists() in FirestoreListRepository.
    internal val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Requires `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done`
            // 127.0.0.1 because some devices fail to DNS-resolve "localhost".
            Firebase.auth.useEmulator("127.0.0.1", 9099)
            Firebase.firestore.useEmulator("127.0.0.1", 8080)
            Firebase.functions.useEmulator("127.0.0.1", 5001)
        }
    }

    val clientId: String by lazy {
        runBlocking { ClientIdRepository(dataStore).getOrCreate() }
    }

    val listRepository: FirestoreListRepository by lazy {
        FirestoreListRepository(
            firestore = Firebase.firestore,
            currentUserFlow = _currentUser,
            callDeleteList = { listId ->
                Firebase.functions.getHttpsCallable("deleteList")
                    .call(mapOf("listId" to listId))
                    .await()
            },
        )
    }

    val sharingRepository: SharingRepository by lazy {
        FirebaseSharingRepository()
    }
}
