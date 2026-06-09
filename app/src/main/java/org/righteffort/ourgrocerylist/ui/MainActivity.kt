package org.righteffort.ourgrocerylist.ui

import android.content.Intent
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import org.righteffort.ourgrocerylist.BuildConfig
import org.righteffort.ourgrocerylist.DebugFirebaseEnvironment
import org.righteffort.ourgrocerylist.OurGroceryListApp
import org.righteffort.ourgrocerylist.client.DataStoreListOrderRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.ui.theme.OurGroceryListTheme
import org.righteffort.ourgrocerylist.undo.UndoRedoStackRepository
import timber.log.Timber

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as OurGroceryListApp
                ShoppingViewModel(
                    currentUserFlow = app.currentUser,
                    listRepository = app.listRepository,
                    repositoryFactory = { ownerUid, listId ->
                        FirestoreShoppingRepository(
                            firestore = Firebase.firestore,
                            ownerUid = ownerUid,
                            listId = listId,
                            clientId = app.clientId,
                        )
                    },
                    appErrors = app.initErrors,
                    stackRepositoryFactory = { listId ->
                        UndoRedoStackRepository(app.preferences, listId)
                    },
                    listOrderRepository = DataStoreListOrderRepository(app.preferences),
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as OurGroceryListApp
        lifecycleScope.launch {
            try {
                signInAndInitialize(app)
            } catch (e: Exception) {
                Timber.e(e, "Auth/list initialization failed")
                app.internalInitErrors.emit(e.message ?: "Initialization failed")
            }
        }

        val onChangeFirebaseEnv: (suspend () -> Unit)? = if (BuildConfig.DEBUG) {
            { (app.firebaseEnvironment as DebugFirebaseEnvironment).changeEnvironment(this) }
        } else null

        setContent {
            OurGroceryListTheme {
                ShoppingListScreen(
                    viewModel,
                    onSignout = ::signOut,
                    onRestart = ::restartApp,
                    onChangeFirebaseEnv = onChangeFirebaseEnv,
                )
            }
        }
    }

    private suspend fun signInAndInitialize(app: OurGroceryListApp) {
        app.firebaseEnvironment.signIn(this)
        // Validate post-conditions. _currentUser is driven by the auth state listener in
        // OurGroceryListApp, which fires after Firestore's internal token-updater — so by the
        // time observeLists() reacts to _currentUser, Firestore already holds the new token.
        val currentUser = Firebase.auth.currentUser
            ?: throw IllegalStateException("No authenticated user after sign-in")
        currentUser.email
            ?: throw IllegalStateException("Authenticated user has no email address")
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)!!
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        Process.killProcess(Process.myPid())
    }

    private fun signOut() {
        val app = application as OurGroceryListApp
        lifecycleScope.launch {
            try {
                app.firebaseEnvironment.signOut()
                // _currentUser is cleared by the auth state listener on signOut().
                signInAndInitialize(app)
            } catch (e: Exception) {
                Timber.e(e, "Re-authentication after sign-out failed")
                app.internalInitErrors.emit(e.message ?: "Re-authentication failed")
            }
        }
    }
}
