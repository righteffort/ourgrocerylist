package org.righteffort.ourgrocerylist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import org.righteffort.ourgrocerylist.model.ShoppingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(viewModel: ShoppingViewModel) {
    val fatalError by viewModel.fatalError.collectAsState()
    if (fatalError != null) {
        FatalErrorScreen(fatalError!!)
        return
    }

    val state by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    var addFieldText by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    dialogState?.let { ItemDialog(it) }

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
            BottomBar(state, onUndo = { viewModel.undo() }, onRedo = { viewModel.redo() })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                onOpenDialog = {
                    viewModel.openAddDialog(addFieldText)
                    addFieldText = ""
                },
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(state.uncheckedItems, key = { _, item -> item.id }) { index, item ->
                    ItemRow(
                        item = item,
                        isAlternate = index % 2 == 1,
                        onCheckedChange = { viewModel.checkItem(item) },
                        onClick = { viewModel.openEditDialog(item) },
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
                        onClick = { viewModel.openEditDialog(item) },
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
    onOpenDialog: () -> Unit,
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
                    IconButton(onClick = onOpenDialog) {
                        Text("✏")
                    }
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
            checked = item.fields.checked,
            onCheckedChange = { onCheckedChange() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
            ),
        )
        Text(
            text = item.fields.name,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            textDecoration = if (item.fields.checked) TextDecoration.LineThrough else null,
        )
        if (item.fields.quantity != 1.0) {
            Text(
                text = formatQuantity(item.fields.quantity),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BottomBar(state: UiState, onUndo: () -> Unit, onRedo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        TextButton(
            onClick = onUndo,
            enabled = state.undoAvailable,
        ) {
            Text("↩ Undo")
        }
        TextButton(
            onClick = onRedo,
            enabled = state.redoAvailable,
        ) {
            Text("↪ Redo")
        }
    }
}

@Composable
private fun FatalErrorScreen(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Something went wrong",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                modifier = Modifier.padding(top = 16.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

private fun formatQuantity(quantity: Double): String {
    return "×${formatQuantityNumber(quantity)}"
}
