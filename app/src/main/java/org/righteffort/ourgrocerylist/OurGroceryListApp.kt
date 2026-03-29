package org.righteffort.ourgrocerylist

import android.app.Application
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository
import org.righteffort.ourgrocerylist.repository.ShoppingRepository

class OurGroceryListApp : Application() {
    val repository: ShoppingRepository by lazy { FakeShoppingRepository() }
}
