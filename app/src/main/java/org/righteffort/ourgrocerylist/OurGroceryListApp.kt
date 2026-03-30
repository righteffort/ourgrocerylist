package org.righteffort.ourgrocerylist

import android.app.Application
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager

class OurGroceryListApp : Application() {
    val repository: ShoppingRepository by lazy { FakeShoppingRepository() }
    val undoRedoManager: UndoRedoManager by lazy { UndoRedoManager(repository) }
}
