package org.righteffort.ourgrocerylist.repository

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.appFunctions

class FirebaseSharingRepository(
    private val ownerEmail: () -> String?,
    private val callAddEditor: suspend (listId: String, email: String) -> Unit,
) : SharingRepository {

    constructor(
        ownerEmail: () -> String? = { Firebase.auth.currentUser?.email },
        functions: FirebaseFunctions = Firebase.appFunctions,
    ) : this(
        ownerEmail = ownerEmail,
        callAddEditor = { listId, email ->
            functions.getHttpsCallable("addEditor")
                .call(mapOf("listId" to listId, "editorEmail" to email))
                .await()
        },
    )

    override suspend fun addEditor(listId: String, email: String) {
        val currentOwnerEmail = ownerEmail()
        if (currentOwnerEmail != null && email.equals(currentOwnerEmail, ignoreCase = true)) {
            throw IllegalArgumentException("The list owner cannot be added as an editor")
        }
        callAddEditor(listId, email)
    }
}
