package org.righteffort.ourgrocerylist.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User

class FirestoreListRepository(
    private val firestore: FirebaseFirestore,
    private val currentUserFlow: StateFlow<User?>,
    private val callDeleteList: suspend (listId: String) -> Unit = { listId ->
        // Populated by OurGroceryListApp with the Firebase Functions callable.
        throw NotImplementedError("callDeleteList not configured")
    },
) : ListRepository {

    // Two parallel Firestore queries: lists owned by the user + lists where user is an editor.
    // They are merged client-side and sorted: owned first (alphabetically), then editor (alphabetically).
    // Firestore evaluates security rules per result document, so both queries are safe.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeLists(): Flow<List<ListMetadata>> = currentUserFlow
        .filterNotNull()  // Becomes not-null when auth completes.
        .flatMapLatest { user ->
            callbackFlow {
                var ownedDocs: List<DocumentSnapshot> = emptyList()
                var editorDocs: List<DocumentSnapshot> = emptyList()

                fun sendCombined() {
                    val all = (
                        ownedDocs.mapNotNull { it.toListMetadata(isOwner = true) } +
                        editorDocs.mapNotNull { it.toListMetadata(isOwner = false) }
                    )
                        .distinctBy { it.id }
                        .sortedWith(compareBy({ !it.isOwner }, { it.name.lowercase() }))
                    trySend(all)
                }

                val ownedListener = firestore.collection("lists")
                    .whereEqualTo("owner.uid", user.uid)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) { close(error); return@addSnapshotListener }
                        ownedDocs = snapshot?.documents ?: emptyList()
                        sendCombined()
                    }

                val editorListener = firestore.collection("lists")
                    .whereNotEqualTo("editors.${user.uid}", null)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) { close(error); return@addSnapshotListener }
                        editorDocs = snapshot?.documents ?: emptyList()
                        sendCombined()
                    }

                awaitClose {
                    ownedListener.remove()
                    editorListener.remove()
                }
            }
        }

    override suspend fun createList(owner: User, name: String): String {
        val ref = firestore.collection("lists").document()
        ref.set(
            mapOf(
                "name" to name,
                "owner" to mapOf("uid" to owner.uid, "email" to owner.email),
                "editors" to emptyMap<String, Any>(),
            )
        ).await()
        return ref.id
    }

    override suspend fun renameList(listId: String, name: String) {
        firestore.document("lists/$listId").update("name", name).await()
    }

    override suspend fun deleteList(listId: String) {
        callDeleteList(listId)
    }
}

private fun DocumentSnapshot.toListMetadata(isOwner: Boolean): ListMetadata? {
    val name = data?.get("name") as? String ?: return null
    return ListMetadata(id = id, name = name, isOwner = isOwner)
}
