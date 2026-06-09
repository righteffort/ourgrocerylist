package org.righteffort.ourgrocerylist

import androidx.activity.ComponentActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.tasks.await

class ProductionFirebaseEnvironment : FirebaseEnvironment {

    override fun configureFirebase() {
        Firebase.firestore.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
    }

    override suspend fun signIn(activity: ComponentActivity) {
        val currentUser = Firebase.auth.currentUser
        if (currentUser == null) {
            signInWithGoogle(activity)
        } else {
            try {
                currentUser.getIdToken(true).await()  // TODO: this seems contrary to the docs
            } catch (_: FirebaseAuthInvalidUserException) {
		// TODO: the comment below doesn't seem to match the statement  `true` above -- getIdToken(true) forces a refresh
		// Cached credentials are stale or revoked — sign out and re-authenticate.
                Firebase.auth.signOut()
                signInWithGoogle(activity)
            }
        }
    }

    override suspend fun signOut() {
        Firebase.auth.signOut()
    }

    private suspend fun signInWithGoogle(activity: ComponentActivity) {
        val credentialManager = CredentialManager.create(activity)

        // Try returning users first (accounts previously authorized with this app).
        // Fall back to the full account chooser if none are found.
        val credential = try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(activity.getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(true)
                        .build()
                )
                .build()
            credentialManager.getCredential(activity, request).credential
        } catch (e: NoCredentialException) {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(
                    GetGoogleIdOption.Builder()
                        .setServerClientId(activity.getString(R.string.default_web_client_id))
                        .setFilterByAuthorizedAccounts(false)
                        .build()
                )
                .build()
            credentialManager.getCredential(activity, request).credential
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
