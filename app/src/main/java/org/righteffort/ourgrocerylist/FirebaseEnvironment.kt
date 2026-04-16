package org.righteffort.ourgrocerylist

import androidx.activity.ComponentActivity

interface FirebaseEnvironment {
    suspend fun signIn(activity: ComponentActivity)
    suspend fun signOut()
}
