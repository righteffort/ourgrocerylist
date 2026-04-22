package org.righteffort.ourgrocerylist

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import org.righteffort.ourgrocerylist.client.ClientIdRepository
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import timber.log.Timber

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

// TODO: This probably belongs somewhere else.
val Firebase.appFunctions: FirebaseFunctions
    get() = this.functions("us-west1")  // TODO: Don't hardcode us-west1!

class OurGroceryListApp : Application() {

    // replay=1 so the ViewModel sees this error even if it subscribes after emission.
    // internal so MainActivity can emit errors from the auth/init coroutine it owns.
    internal val internalInitErrors = MutableSharedFlow<String>(replay = 1)
    val initErrors: SharedFlow<String> = internalInitErrors.asSharedFlow()

    // Drives observeLists() in FirestoreListRepository.
    internal val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    lateinit var firebaseEnvironment: FirebaseEnvironment

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            Timber.plant(object : Timber.DebugTree() {
                override fun isLoggable(tag: String?, priority: Int) =
                    priority != android.util.Log.VERBOSE
            })
        }
        super.onCreate()
        Firebase.firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
        firebaseEnvironment = if (BuildConfig.USE_FIREBASE_EMULATOR) {
            // Requires `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done` for emulator.
            EmulatorFirebaseEnvironment(dataStore)
        } else {
            ProductionFirebaseEnvironment()
        }
        Firebase.auth.addIdTokenListener { firebaseAuth: FirebaseAuth ->
            val fbUser = firebaseAuth.currentUser
            val newUser = if (fbUser?.email != null) {
                User(uid = fbUser.uid, email = fbUser.email!!)
            } else {
                null
            }
            _currentUser.value = newUser
        }
    }

    val clientId: String by lazy {
        runBlocking { ClientIdRepository(dataStore).getOrCreate() }
    }

    val listRepository: FirestoreListRepository by lazy {
        FirestoreListRepository(
            firestore = Firebase.firestore,
            currentUserFlow = _currentUser,
        )
    }

}
