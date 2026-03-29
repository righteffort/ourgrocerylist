package org.righteffort.ourgrocerylist.model

sealed class Command {

    data class AddItem(
        val item: ShoppingItem,
    ) : Command()

    data class DeleteItem(
        val item: ShoppingItem,
    ) : Command()

    data class EditItem(
        val previousSnapshot: ShoppingItem,
	val newSnapshot: ShoppingItem,
    ) : Command()

    data class CheckItem(
        val item: ShoppingItem,
    ) : Command()

    data class UncheckItem(
        val item: ShoppingItem,
    ) : Command()
}