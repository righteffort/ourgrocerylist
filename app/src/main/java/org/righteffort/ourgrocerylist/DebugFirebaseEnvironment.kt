package org.righteffort.ourgrocerylist

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup
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
import org.righteffort.ourgrocerylist.client.EnvironmentChoiceRepository
import org.righteffort.ourgrocerylist.client.FirebaseEnvironmentChoice
import org.righteffort.ourgrocerylist.util.setUpFirebaseEmulators

class DebugFirebaseEnvironment(
    dataStore: DataStore<Preferences>,
    private val choiceRepo: EnvironmentChoiceRepository,
) : FirebaseEnvironment {

    constructor(context: Context, dataStore: DataStore<Preferences>) : this(
        dataStore = dataStore,
        choiceRepo = EnvironmentChoiceRepository(context),
    )

    private val usernameRepo = EmulatorUsernameRepository(dataStore)
    private val productionEnv = ProductionFirebaseEnvironment()

    // Called from Application.onCreate() before the auth listener. No-op on first run (no stored
    // choice yet) — first run has no cached user, so no Firestore access races with signIn().
    // Note: changing environments requires a full process restart — Firebase routing
    // (useEmulator) cannot be changed after it is first applied.
    override fun configureFirebase() {
        applyConfig(choiceRepo.get() ?: return)
    }

    override suspend fun signIn(activity: ComponentActivity) {
        val choice = choiceRepo.get() ?: promptForChoice(activity).also { newChoice ->
            choiceRepo.save(newChoice)
            applyConfig(newChoice)  // first run only — safe, no cached user yet
        }
        when (choice) {
            FirebaseEnvironmentChoice.PRODUCTION -> productionEnv.signIn(activity)
            FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE -> signInEmulator(activity, "127.0.0.1")
            FirebaseEnvironmentChoice.EMULATOR_AVD -> signInEmulator(activity, "10.0.2.2")
        }
    }

    override suspend fun signOut() {
        val choice = checkNotNull(choiceRepo.get()) {
            "signOut reached without a stored environment choice — sign-in never completed"
        }
        when (choice) {
            FirebaseEnvironmentChoice.PRODUCTION -> {}
            FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE,
            FirebaseEnvironmentChoice.EMULATOR_AVD -> usernameRepo.clear()
        }
        Firebase.auth.signOut()
    }

    suspend fun changeEnvironment(activity: ComponentActivity) {
        Firebase.auth.signOut()
        usernameRepo.clear()  // no-op for production; clears stored test username for emulator
        choiceRepo.save(promptForChoice(activity))
    }

    private fun applyConfig(choice: FirebaseEnvironmentChoice) {
        when (choice) {
            FirebaseEnvironmentChoice.PRODUCTION -> productionEnv.configureFirebase()
            FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE -> setUpFirebaseEmulators("127.0.0.1")
            FirebaseEnvironmentChoice.EMULATOR_AVD -> setUpFirebaseEmulators("10.0.2.2")
        }
    }

    private suspend fun signInEmulator(activity: ComponentActivity, host: String) {
        val username = getOrPromptUsername(activity, host)
        val email = "$username@test.invalid"
        try {
            Firebase.auth.createUserWithEmailAndPassword(email, "password").await()
            Firebase.auth.currentUser?.updateProfile(
                userProfileChangeRequest { displayName = username }
            )?.await()  // TODO(claude): Don't silently ignore nulls
        } catch (_: FirebaseAuthUserCollisionException) {
            // User already exists on the emulator — proceed to sign in.
        }
        Firebase.auth.signInWithEmailAndPassword(email, "password").await()
    }

    private suspend fun getOrPromptUsername(activity: ComponentActivity, host: String): String {
        usernameRepo.get()?.let { return it }
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
                    activity.lifecycleScope.launch { usernameRepo.save(username) }
                    continuation.resumeWith(Result.success(username))
                }
                .setCancelable(false)
                .show()
        }
    }

    private suspend fun promptForChoice(activity: ComponentActivity): FirebaseEnvironmentChoice =
        suspendCancellableCoroutine { continuation ->
            val options = listOf(
                FirebaseEnvironmentChoice.PRODUCTION to "Firebase Production",
                FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE to "Emulator — 127.0.0.1 (physical device + adb reverse)",
                FirebaseEnvironmentChoice.EMULATOR_AVD to "Emulator — 10.0.2.2 (AVD)",
            )
            val radioGroup = RadioGroup(activity).apply {
                options.forEachIndexed { index, (_, label) ->
                    addView(RadioButton(activity).apply {
                        id = index
                        text = label
                    })
                }
                check(0)
            }
            AlertDialog.Builder(activity)
                .setTitle("Select Firebase Environment")
                .setView(radioGroup)
                .setPositiveButton("OK") { _, _ ->
                    continuation.resumeWith(Result.success(options[radioGroup.checkedRadioButtonId].first))
                }
                .setCancelable(false)
                .show()
        }
}
