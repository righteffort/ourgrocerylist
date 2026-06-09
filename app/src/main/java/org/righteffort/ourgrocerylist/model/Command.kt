package org.righteffort.ourgrocerylist.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Command {

    abstract fun reverse(): Command

    // Returns true if this command references the given item ID —
    // used by UndoRedoManager to prune stacks on remote writes.
    fun referencesItem(itemId: String): Boolean = when (this) {
        is AddItem -> item.id == itemId
        is DeleteItem -> item.id == itemId
        is EditItem -> previousSnapshot.id == itemId
        is CheckItem -> item.id == itemId
        is UncheckItem -> item.id == itemId
    }

    @Serializable
    @SerialName("add")
    data class AddItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = DeleteItem(item)
    }

    @Serializable
    @SerialName("delete")
    data class DeleteItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = AddItem(item)
    }

    @Serializable
    @SerialName("edit")
    data class EditItem(
        val previousSnapshot: ShoppingItem,
        val newFields: ItemFields,
    ) : Command() {
        override fun reverse() = EditItem(
            previousSnapshot = previousSnapshot.copy(fields = newFields),
            newFields = previousSnapshot.fields,
        )
    }

    @Serializable
    @SerialName("check")
    data class CheckItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = UncheckItem(
            item.copy(fields = item.fields.copy(checked = true)),
        )
    }

    @Serializable
    @SerialName("uncheck")
    data class UncheckItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = CheckItem(
            item.copy(fields = item.fields.copy(checked = false)),
        )
    }
}
