package org.righteffort.ourgrocerylist.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.BuildConfig
import org.righteffort.ourgrocerylist.OurGroceryListApp
import org.righteffort.ourgrocerylist.R
import org.righteffort.ourgrocerylist.ui.theme.OurGroceryListTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as OurGroceryListApp
                ShoppingViewModel(app.repository, app.undoRedoManager, app.initErrors)
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
                app._initErrors.emit(e.message ?: "Initialization failed")
            }
        }

        setContent {
            OurGroceryListTheme {
                ShoppingListScreen(viewModel)
            }
        }
    }

    private suspend fun signInAndInitialize(app: OurGroceryListApp) {
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            try {
                Firebase.auth.createUserWithEmailAndPassword("test@example.com", "password").await()
            } catch (e: FirebaseAuthUserCollisionException) {
                // User already exists on the emulator — proceed to sign in.
            }
            Firebase.auth.signInWithEmailAndPassword("test@example.com", "password").await()
        } else {
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                signInWithGoogle()
            } else {
                try {
                    currentUser.getIdToken(true).await()
                } catch (e: Exception) {
                    // Cached credentials are stale or revoked — sign out and re-authenticate.
                    Firebase.auth.signOut()
                    signInWithGoogle()
                }
            }
        }
        val uid = Firebase.auth.currentUser?.uid
            ?: throw IllegalStateException("No authenticated user after sign-in")
        app.repository.ensureListDocument(uid)
    }

    private suspend fun signInWithGoogle() {
        val credentialManager = CredentialManager.create(this)

        // Try returning users first (accounts previously authorized with this app).
        // Fall back to the full account chooser if none are found.
        val credential = try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(true)
                        .build()
                )
                .build()
            credentialManager.getCredential(this, request).credential
        } catch (e: NoCredentialException) {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                )
                .build()
            credentialManager.getCredential(this, request).credential
        }

        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
            Firebase.auth.signInWithCredential(authCredential).await()
        } else {
            throw IllegalStateException("Unexpected credential type: ${credential.type}")
        }
    }
}
