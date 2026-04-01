package org.righteffort.ourgrocerylist.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.model.User

class FirestoreShoppingRepository(
    private val firestore: FirebaseFirestore,
    private val listId: String,
    private val clientId: String,
) : ShoppingRepository {

    // Completed by ensureListDocument after the list document is guaranteed to exist.
    // observeItems and observeRemotelyModifiedItemIds await this before attaching
    // Firestore listeners so that security rules can always read the list document.
    private val _ready = CompletableDeferred<Unit>()

    private val collection get() = firestore.collection("lists/$listId/items")

    override fun newItemId(): String = collection.document().id

    override fun observeItems(): Flow<List<ShoppingItem>> = callbackFlow {
        _ready.await()
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { it.toShoppingItem() } ?: emptyList()
            trySend(items)
        }
        awaitClose { listener.remove() }
    }

    // Emits the IDs of items modified or deleted by other clients. ADDED is excluded
    // because the initial snapshot reports all existing documents as ADDED regardless
    // of authorship, which would incorrectly trigger pruning for our own past writes.
    override fun observeRemotelyModifiedItemIds(): Flow<Set<String>> = callbackFlow {
        _ready.await()
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val remoteIds = snapshot?.documentChanges
                ?.filter { change ->
                    (change.type == DocumentChange.Type.MODIFIED ||
                        change.type == DocumentChange.Type.REMOVED) &&
                        change.document.getString("clientId") != clientId
                }
                ?.map { it.document.id }
                ?.toSet()
                ?: emptySet()
            if (remoteIds.isNotEmpty()) trySend(remoteIds)
        }
        awaitClose { listener.remove() }
    }

    // Creates the list document with owner/editors if it does not already exist.
    override suspend fun ensureListDocument(user: User) {
        val listRef = firestore.document("lists/$listId")
        firestore.runTransaction { transaction ->
            if (!transaction.get(listRef).exists()) {
                transaction.set(
                    listRef,
                    mapOf(
                        "owner" to mapOf("uid" to user.uid, "email" to user.email),
                        "editors" to emptyMap<String, Any>(),
                    ),
                )
            }
        }.await()
        _ready.complete(Unit)
    }

    override suspend fun apply(command: Command) {
        when (command) {
            is Command.AddItem -> writeItem(command.item.id, command.item.fields)
            is Command.DeleteItem -> collection.document(command.item.id).delete().await()
            is Command.EditItem -> writeItem(command.previousSnapshot.id, command.newFields)
            is Command.CheckItem -> writeItem(command.item.id, command.item.fields.copy(checked = true))
            is Command.UncheckItem -> writeItem(command.item.id, command.item.fields.copy(checked = false))
        }
    }

    private suspend fun writeItem(id: String, fields: ItemFields) {
        collection.document(id).set(itemToFirestoreData(fields, clientId)).await()
    }

    private fun DocumentSnapshot.toShoppingItem(): ShoppingItem? =
        data?.let { shoppingItemFromFirestoreData(id, it) }
}

internal fun itemToFirestoreData(fields: ItemFields, clientId: String): Map<String, Any> = mapOf(
    "fields" to mapOf(
        "name" to fields.name,
        "quantity" to fields.quantity,
        "checked" to fields.checked,
    ),
    "fingerprint" to fields.fingerprint,
    "clientId" to clientId,
)

internal fun shoppingItemFromFirestoreData(id: String, data: Map<String, Any?>): ShoppingItem? {
    val fieldsMap = data["fields"] as? Map<*, *> ?: return null
    val name = fieldsMap["name"] as? String ?: return null
    val quantity = (fieldsMap["quantity"] as? Number)?.toDouble() ?: 1.0
    val checked = fieldsMap["checked"] as? Boolean ?: false
    return ShoppingItem(
        id = id,
        fields = ItemFields(name = name, quantity = quantity, checked = checked),
    )
}
