package org.righteffort.ourgrocerylist.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.righteffort.ourgrocerylist.OurGroceryListApp
import org.righteffort.ourgrocerylist.ui.theme.OurGroceryListTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ShoppingViewModel by viewModels {
        viewModelFactory {
            initializer {
                val app = application as OurGroceryListApp
                ShoppingViewModel(app.repository, app.undoRedoManager)
            }
        } 
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OurGroceryListTheme {
                ShoppingListScreen(viewModel)
            }
        }
    }
}
