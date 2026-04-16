package org.righteffort.ourgrocerylist.ui

import android.os.Bundle
import android.util.Log
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
import org.righteffort.ourgrocerylist.OurGroceryListApp
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.ui.theme.OurGroceryListTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as OurGroceryListApp
                ShoppingViewModel(
                    currentUserFlow = app.currentUser,
                    listRepository = app.listRepository,
                    repositoryFactory = { listId ->
                        FirestoreShoppingRepository(
                            firestore = Firebase.firestore,
                            listId = listId,
                            clientId = app.clientId,
                        )
                    },
                    appErrors = app.initErrors,
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
                Log.e(TAG, "Auth/list initialization failed", e)
                app.internalInitErrors.emit(e.message ?: "Initialization failed")
            }
        }

        setContent {
            OurGroceryListTheme {
                ShoppingListScreen(viewModel, onSignout = ::signout)
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

    private fun signout() {
        val app = application as OurGroceryListApp
        lifecycleScope.launch {
            try {
                app.firebaseEnvironment.signOut()
                // _currentUser is cleared by the auth state listener on signOut().
                signInAndInitialize(app)
            } catch (e: Exception) {
                Log.e(TAG, "Re-authentication after sign-out failed", e)
                app.internalInitErrors.emit(e.message ?: "Re-authentication failed")
            }
        }
    }
}
