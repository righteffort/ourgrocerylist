package org.righteffort.ourgrocerylist.ui

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.BuildConfig
import org.righteffort.ourgrocerylist.OurGroceryListApp
import org.righteffort.ourgrocerylist.R
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
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            val username = getEmulatorUsername(app)
            val email = "$username@test.invalid"

            try {
                Firebase.auth.createUserWithEmailAndPassword(email, "password").await()
                val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                    displayName = username
                }
                Firebase.auth.currentUser?.updateProfile(profileUpdates)?.await()
            } catch (_: FirebaseAuthUserCollisionException) {
                // User already exists on the emulator — proceed to sign in.
            }
            Firebase.auth.signInWithEmailAndPassword(email, "password").await()
        } else {
            val currentUser = Firebase.auth.currentUser
            if (currentUser == null) {
                signInWithGoogle()
            } else {
                try {
                    currentUser.getIdToken(true).await()  // TODO: this seems contrary to the docs
                } catch (_: FirebaseAuthInvalidUserException) {
		    // TODO: the comment below doesn't seem to match the statement  `true` above -- getIdToken(true) forces a refresh
                    // Cached credentials are stale or revoked — sign out and re-authenticate.
                    Firebase.auth.signOut()
                    signInWithGoogle()
                }
            }
        }
        val currentUser = Firebase.auth.currentUser
            ?: throw IllegalStateException("No authenticated user after sign-in")
        val email = currentUser.email
            ?: throw IllegalStateException("Authenticated user has no email address")
        app._currentUser.value = User(uid = currentUser.uid, email = email)
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

    private suspend fun getEmulatorUsername(app: OurGroceryListApp): String {
        val repo = app.emulatorUsernameRepository!!
        repo.get()?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val editText = EditText(this).apply {
                setText("test")
                inputType = InputType.TYPE_CLASS_TEXT
            }

            AlertDialog.Builder(this)
                .setTitle("Create/Use Test User")
                .setMessage("Username (email will be username@test.invalid):")
                .setView(editText)
                .setPositiveButton("Create") { _, _ ->
                    val username = editText.text.toString()
                    lifecycleScope.launch { repo.save(username) }
                    continuation.resumeWith(Result.success(username))
                }
                .setCancelable(false)
                .show()
        }
    }
}
