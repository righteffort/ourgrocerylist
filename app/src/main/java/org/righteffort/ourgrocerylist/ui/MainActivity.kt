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
import org.righteffort.ourgrocerylist.model.User
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
                ShoppingListScreen(viewModel)
            }
        }
    }

    private suspend fun signInAndInitialize(app: OurGroceryListApp) {
        app.firebaseEnvironment.signIn(this)
        val currentUser = Firebase.auth.currentUser
            ?: throw IllegalStateException("No authenticated user after sign-in")
        val email = currentUser.email
            ?: throw IllegalStateException("Authenticated user has no email address")
        app._currentUser.value = User(uid = currentUser.uid, email = email)
    }
}
