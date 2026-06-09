package org.righteffort.ourgrocerylist

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.righteffort.ourgrocerylist.client.EnvironmentChoiceRepository
import java.io.File

class DebugFirebaseEnvironmentTest {

    @TempDir
    lateinit var tempDir: File

    private fun makeEnv(): DebugFirebaseEnvironment {
        val choiceRepo = EnvironmentChoiceRepository(tempDir.resolve("firebase_env_choice"))
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO),
            produceFile = { tempDir.resolve("test.preferences_pb") },
        )
        return DebugFirebaseEnvironment(dataStore, choiceRepo)
    }

    @Test
    fun `configureFirebase with no stored choice does nothing`() {
        // Verifies the no-op path does not throw, and in particular does not attempt to
        // call Firebase SDK before an environment has been chosen.
        makeEnv().configureFirebase()
    }

    @Test
    fun `signOut throws when no environment has been chosen`() = runTest {
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { makeEnv().signOut() }
        }
    }
}
