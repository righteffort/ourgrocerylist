package org.righteffort.ourgrocerylist.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.ui.theme.OurGroceryListTheme

@Preview(showBackground = true)
@Composable
private fun ItemRowPreview() {
    OurGroceryListTheme {
        ItemRow(
            item = ShoppingItem("123", ItemFields("item name", 2.0, false)),
            isAlternate = false,
            onToggle = {},
            onEdit = {},
        )
    }
}
