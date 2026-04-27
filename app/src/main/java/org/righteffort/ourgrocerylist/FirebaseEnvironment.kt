package org.righteffort.ourgrocerylist

import androidx.activity.ComponentActivity

interface FirebaseEnvironment {
    // Called synchronously in Application.onCreate(), before the auth state listener fires.
    // Must configure the Firebase SDK (Firestore settings, emulator routing) so that any
    // Firestore access triggered by an already-cached user does not race with configuration.
    fun configureFirebase() {}

    suspend fun signIn(activity: ComponentActivity)
    suspend fun signOut()
}
