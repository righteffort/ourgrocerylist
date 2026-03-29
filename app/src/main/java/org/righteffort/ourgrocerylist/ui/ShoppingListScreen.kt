package org.righteffort.ourgrocerylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.righteffort.ourgrocerylist.model.ShoppingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel) {
    val state by viewModel.uiState.collectAsState()
    var addFieldText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("List") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            BottomBar(state)
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            AddItemField(
                text = addFieldText,
                onTextChange = { addFieldText = it },
                onCommit = {
                    viewModel.addItem(addFieldText)
                    addFieldText = ""
                },
                onClear = { addFieldText = "" },
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.uncheckedItems, key = { _, item -> item.id }) { index, item ->
                    ItemRow(
                        item = item,
                        isAlternate = index % 2 == 1,
                        onCheckedChange = { viewModel.checkItem(item) },
                        onClick = { /* edit dialog — phase 2 */ },
                    )
                }

                if (state.uncheckedItems.isNotEmpty() && state.checkedItems.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier
                                .width(40.dp)
                                .padding(vertical = 8.dp),
                            thickness = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                itemsIndexed(state.checkedItems, key = { _, item -> item.id }) { index, item ->
                    ItemRow(
                        item = item,
                        isAlternate = index % 2 == 1,
                        onCheckedChange = { viewModel.uncheckItem(item) },
                        onClick = { /* edit dialog — phase 2 */ },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddItemField(
    text: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text("Add item") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit() }),
        trailingIcon = {
            if (text.isNotEmpty()) {
                Row {
                    IconButton(onClick = onClear) {
                        Text("✕")
                    }
                    IconButton(onClick = onCommit) {
                        Text("✓")
                    }
                }
            }
        },
    )
}

@Composable
private fun ItemRow(
    item: ShoppingItem,
    isAlternate: Boolean,
    onCheckedChange: () -> Unit,
    onClick: () -> Unit,
) {
    val backgroundColor = if (isAlternate) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.checked,
            onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            text = item.name,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            textDecoration = if (item.checked) TextDecoration.LineThrough else null,
        )
        if (item.quantity != 1.0) {
            Text(
                text = formatQuantity(item.quantity),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomBar(state: UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextButton(
            onClick = { /* undo — phase 3 */ },
            enabled = state.undoAvailable,
        ) {
            Text("↩ Undo")
        }
        TextButton(
            onClick = { /* redo — phase 3 */ },
            enabled = state.redoAvailable,
        ) {
            Text("↪ Redo")
        }
    }
}

private fun formatQuantity(quantity: Double): String {
    return if (quantity == quantity.toLong().toDouble()) {
        "×${quantity.toLong()}"
    } else {
        "×${"%.3f".format(quantity).trimEnd('0').trimEnd('.')}"
    }
}
