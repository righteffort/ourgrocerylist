package org.righteffort.ourgrocerylist

import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.suspendCancellableCoroutine
import org.righteffort.ourgrocerylist.client.EnvironmentChoiceRepository
import org.righteffort.ourgrocerylist.client.FirebaseEnvironmentChoice
import org.righteffort.ourgrocerylist.util.setUpFirebaseEmulators

class DebugFirebaseEnvironment(dataStore: DataStore<Preferences>) : FirebaseEnvironment {

    private val choiceRepository = EnvironmentChoiceRepository(dataStore)
    private val productionEnv = ProductionFirebaseEnvironment()
    private val emulatorAdbReverseEnv = EmulatorFirebaseEnvironment("127.0.0.1", dataStore)
    private val emulatorAvdEnv = EmulatorFirebaseEnvironment("10.0.2.2", dataStore)

    // useEmulator() must only be called once per Firebase instance per process.
    private var emulatorSetupDone = false

    override suspend fun signIn(activity: ComponentActivity) {
        val choice = choiceRepository.get() ?: promptForChoice(activity).also {
            choiceRepository.save(it)
        }
        when (choice) {
            FirebaseEnvironmentChoice.PRODUCTION -> productionEnv.signIn(activity)
            FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE -> {
                ensureEmulatorSetup("127.0.0.1")
                emulatorAdbReverseEnv.signIn(activity)
            }
            FirebaseEnvironmentChoice.EMULATOR_AVD -> {
                ensureEmulatorSetup("10.0.2.2")
                emulatorAvdEnv.signIn(activity)
            }
        }
    }

    override suspend fun signOut() {
        when (choiceRepository.get() ?: FirebaseEnvironmentChoice.PRODUCTION) {
            FirebaseEnvironmentChoice.PRODUCTION -> productionEnv.signOut()
            FirebaseEnvironmentChoice.EMULATOR_ADB_REVERSE -> emulatorAdbReverseEnv.signOut()
            FirebaseEnvironmentChoice.EMULATOR_AVD -> emulatorAvdEnv.signOut()
        }
    }

    private fun ensureEmulatorSetup(host: String) {
        if (!emulatorSetupDone) {
            setUpFirebaseEmulators(host)
            emulatorSetupDone = true
        }
    }

    suspend fun changeEnvironment(activity: ComponentActivity) {
        val newChoice = promptForChoice(activity)
        Firebase.auth.signOut()
        emulatorAdbReverseEnv.clearStoredCredentials()
        emulatorAvdEnv.clearStoredCredentials()
        choiceRepository.save(newChoice)
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
