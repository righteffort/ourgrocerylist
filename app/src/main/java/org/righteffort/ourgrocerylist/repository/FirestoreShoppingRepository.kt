package org.righteffort.ourgrocerylist.repository

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import timber.log.Timber

class FirestoreShoppingRepository(
    private val firestore: FirebaseFirestore,
    private val listId: String,
    private val clientId: String,
) : ShoppingRepository {

    private val collection get() = firestore.collection("lists/$listId/items")

    override fun newItemId(): String = collection.document().id

    override fun observeItems(): Flow<List<ShoppingItem>> = callbackFlow {
        Timber.v("RACE_DEBUG FSR listId=$listId observeItems starting listener collection=${collection.path}")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.v("DEBUG FSR listId=$listId observeItems error=$error  collection=${collection.path}")
                close(error)
                return@addSnapshotListener
            }
            val items = snapshot?.documents?.mapNotNull { it.toShoppingItem() } ?: emptyList()
            Timber.v("DEBUG FSR listId=$listId items observe items=$items collection=${collection.path}")
            trySend(items)
        }
        awaitClose {
            Timber.v("DEBUG FSR listId=$listId observeItems awaitClose — listener removed collection=${collection.path}")
            listener.remove()
        }
    }.retryWhen { cause, attempt ->
        // PERMISSION_DENIED is transient when a newly created list hasn't been committed
        // server-side yet but the ownedListener has already fired on the local optimistic
        // write (hasPendingWrites=true). Retry indefinitely to let the server catch up;
        // the coroutine is canceled when the list is removed from listResources.
	// Hopefull the UX is ok if we get here.
        val transient = cause is FirebaseFirestoreException &&
            cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        if (transient) {
            val delayMs = minOf(250L shl minOf(attempt.toInt(), 5), 8_000L)
            Timber.v("DEBUG FSR listId=$listId observeItems PERMISSION_DENIED attempt=$attempt retrying in ${delayMs}ms collection=${collection.path}")
            delay(delayMs)
            Timber.v("DEBUG FSR listId=$listId observeItems delay elapsed, retrying listener attempt=$attempt collection=${collection.path}")
            true
        } else false
    }

    // Purpose: detect changes by other clients so that we can prune.
    // Fringe benefit: keeps cache warm if user switches lists while offline.
    // Emits the IDs of items modified or deleted by other clients. ADDED is excluded
    // because the initial snapshot reports all existing documents as ADDED regardless
    // of authorship, which would incorrectly trigger pruning for our own past writes.
    override fun observeRemotelyModifiedItemIds(): Flow<Set<String>> = callbackFlow {
        Timber.v("RACE_DEBUG FSR listId=$listId observeRemotelyModifiedItemIds starting listener collection=${collection.path}")
        val listener = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Timber.v("DEBUG FSR listId=$listId error non-null oh well collection=${collection.path}")
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
            Timber.v("DEBUG FSR listId=$listId items observe remoteIds=$remoteIds collection=${collection.path}")
        }
        awaitClose {
            Timber.v("DEBUG FSR listId=$listId observeRemotelyModifiedItemIds awaitClose — listener removed collection=${collection.path}")
            listener.remove()
        }
    }.retryWhen { cause, attempt ->
        // Same transient PERMISSION_DENIED race as observeItems — see comment there.
        val transient = cause is FirebaseFirestoreException &&
            cause.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
        if (transient) {
            val delayMs = minOf(250L shl minOf(attempt.toInt(), 5), 8_000L)
            Timber.v("DEBUG FSR listId=$listId observeRemotelyModifiedItemIds PERMISSION_DENIED attempt=$attempt retrying in ${delayMs}ms collection=${collection.path}")
            delay(delayMs)
            Timber.v("DEBUG FSR listId=$listId observeRemotelyModifiedItemIds delay elapsed, retrying listener attempt=$attempt collection=${collection.path}")
            true
        } else false
    }

    override suspend fun apply(command: Command) {
        when (command) {
            is Command.AddItem -> writeItem(command.item.id, command.item.fields)
            is Command.DeleteItem -> collection.document(command.item.id).delete()
            is Command.EditItem -> writeItem(command.previousSnapshot.id, command.newFields)
            is Command.CheckItem -> writeItem(command.item.id, command.item.fields.copy(checked = true))
            is Command.UncheckItem -> writeItem(command.item.id, command.item.fields.copy(checked = false))
        }
    }

    private fun writeItem(id: String, fields: ItemFields) {
        Timber.v("DEBUG FSR writeItem id=$id fields=$fields")
        collection.document(id).set(itemToFirestoreData(fields, clientId))
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
