package org.righteffort.ourgrocerylist.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.functions
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.appFunctions

class FirebaseSharingRepository(
    private val ownerEmail: () -> String? = { Firebase.auth.currentUser?.email },
    private val callAddEditor: suspend (listId: String, email: String) -> Unit = { lId, e ->
        Firebase.appFunctions.getHttpsCallable("addEditor")
            .call(mapOf("listId" to lId, "editorEmail" to e))
            .await()
    },
) : SharingRepository {

    override suspend fun addEditor(listId: String, email: String) {
        val currentOwnerEmail = ownerEmail()
        if (currentOwnerEmail != null && email.equals(currentOwnerEmail, ignoreCase = true)) {
            throw IllegalArgumentException("The list owner cannot be added as an editor")
        }
        callAddEditor(listId, email)
    }
}
