package org.righteffort.ourgrocerylist

import android.text.InputType
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.client.EmulatorUsernameRepository

class EmulatorFirebaseEnvironment(
    private val host: String,
    dataStore: DataStore<Preferences>,
) : FirebaseEnvironment {

    private val usernameRepository = EmulatorUsernameRepository(dataStore)

    override suspend fun signIn(activity: ComponentActivity) {
        val username = getOrPromptUsername(activity)
        val email = "$username@test.invalid"
        try {
            Firebase.auth.createUserWithEmailAndPassword(email, "password").await()
            val profileUpdates = userProfileChangeRequest { displayName = username }
            Firebase.auth.currentUser?.updateProfile(profileUpdates)?.await()  // TODO(claude): Don't silently ignore nulls
        } catch (_: FirebaseAuthUserCollisionException) {
            // User already exists on the emulator — proceed to sign in.
        }
        Firebase.auth.signInWithEmailAndPassword(email, "password").await()
    }

    override suspend fun signOut() {
        usernameRepository.clear()
        Firebase.auth.signOut()
    }

    suspend fun clearStoredCredentials() {
        usernameRepository.clear()
    }

    private suspend fun getOrPromptUsername(activity: ComponentActivity): String {
        usernameRepository.get()?.let { return it }

        return suspendCancellableCoroutine { continuation ->
            val editText = EditText(activity).apply {
                setText("test")
                inputType = InputType.TYPE_CLASS_TEXT
            }
            AlertDialog.Builder(activity)
                .setTitle("Create/Use Test User")
                .setMessage("Emulator: $host\nUsername (email will be username@test.invalid):")
                .setView(editText)
                .setPositiveButton("Create/Use") { _, _ ->
                    val username = editText.text.toString()
                    activity.lifecycleScope.launch { usernameRepository.save(username) }
                    continuation.resumeWith(Result.success(username))
                }
                .setCancelable(false)
                .show()
        }
    }
}
