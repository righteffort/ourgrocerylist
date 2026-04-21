package org.righteffort.ourgrocerylist.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User
import com.google.firebase.Firebase
import com.google.firebase.functions.FirebaseFunctions
import org.righteffort.ourgrocerylist.appFunctions

class FirestoreListRepository(
    private val firestore: FirebaseFirestore,
    private val currentUserFlow: StateFlow<User?>,
    private val callEmailToUid: suspend (String) -> String,
) : ListRepository {
    constructor(
        firestore: FirebaseFirestore,
        currentUserFlow: StateFlow<User?>,
        functions: FirebaseFunctions = Firebase.appFunctions,
    ) : this(
        firestore = firestore,
        currentUserFlow = currentUserFlow,
        callEmailToUid = { email ->
            functions.getHttpsCallable("emailToUid")
                .call(mapOf("email" to email))
                .await()
                .data as String
        },
    )


    // Two parallel Firestore queries: lists owned by the user + lists where user is an editor.
    // They are merged client-side and sorted: owned first (alphabetically), then editor (alphabetically).
    // Firestore evaluates security rules per result document, so both queries are safe.
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeLists(): Flow<List<ListMetadata>> = currentUserFlow
        .flatMapLatest { user ->
            // Cancel the old callbackFlow immediately on sign-out (user == null) so Firestore
            // snapshot listeners are removed before the auth token is revoked, preventing
            // PERMISSION_DENIED from lingering listeners reaching logAndEmitFatalError.
            // Emit an empty list (not emptyFlow) so the ViewModel's collect block runs its
            // cleanup code — cancelling all observationJobs in listResources — before
            // Firestore revokes the auth token and fires PERMISSION_DENIED on them.
            if (user == null) return@flatMapLatest flowOf(emptyList())
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
                        // println("DEBUG FLR lists ownedListener callback error=$error on: ${Thread.currentThread().name}")
                        if (error != null) { close(error); return@addSnapshotListener }
                        ownedDocs = snapshot?.documents ?: emptyList()
                        // println("DEBUG FLR lists ownedListener ownedDocs=$ownedDocs")
                        // println("DEBUG FLR first doc ${if (ownedDocs.isEmpty()) "nope" else ownedDocs.first().data}")
                        sendCombined()
                    }

                val editorListener = firestore.collection("lists")
                    .whereNotEqualTo("editors.${user.uid}", null)
                    .addSnapshotListener { snapshot, error ->
                        // println("DEBUG FLR lists editorListener error=$error callback on: ${Thread.currentThread().name}")
                        if (error != null) { close(error); return@addSnapshotListener }
                        editorDocs = snapshot?.documents ?: emptyList()
                        // println("DEBUG FLR lists ownedListener editorDocs=$editorDocs")
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
        firestore.document("lists/$listId").delete()
    }

    override suspend fun addEditor(listId: String, email: String) {
        // println("addEditor $listId $email calling emailToUid")
        val uid = callEmailToUid(email)
        // println("addEditor $listId $email called emailToUid")

        val ref = firestore.document("lists/$listId")
        firestore.runTransaction { transaction ->
            val doc = transaction.get(ref)
            val data = checkNotNull(doc.data) { "List $listId not found" }
            val ownerEmail = (data["owner"] as? Map<*, *>)?.get("email") as? String
                ?: error("List $listId has malformed owner field")
            if (email.equals(ownerEmail, ignoreCase = true)) {
                throw IllegalArgumentException("The list owner cannot be added as an editor")
            }
            val editors = data["editors"] as? Map<*, *>
                ?: error("List $listId has malformed editors field")
            if (editors.containsKey(uid)) {
                throw IllegalArgumentException("$email is already an editor of this list")
            }
            transaction.update(ref, "editors.$uid", mapOf("email" to email))
            null
        }.await()
    }
}

private fun DocumentSnapshot.toListMetadata(isOwner: Boolean): ListMetadata? {
    val name = data?.get("name") as? String ?: return null
    return ListMetadata(id = id, name = name, isOwner = isOwner)
}
