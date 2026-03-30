package org.righteffort.ourgrocerylist.model

sealed class Command {

    abstract fun reverse(): Command

    data class AddItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = DeleteItem(item)
    }

    data class DeleteItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = AddItem(item)
    }

    data class EditItem(
        val previousSnapshot: ShoppingItem,
        val newFields: ItemFields,
    ) : Command() {
        override fun reverse() = EditItem(
            previousSnapshot = previousSnapshot.copy(fields = newFields),
            newFields = previousSnapshot.fields,
        )
    }

    data class CheckItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = UncheckItem(
            item.copy(fields = item.fields.copy(checked = true)),
        )
    }

    data class UncheckItem(
        val item: ShoppingItem,
    ) : Command() {
        override fun reverse() = CheckItem(
            item.copy(fields = item.fields.copy(checked = false)),
        )
    }
}
