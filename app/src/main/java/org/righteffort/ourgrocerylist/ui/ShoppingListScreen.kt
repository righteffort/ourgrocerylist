package org.righteffort.ourgrocerylist.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.util.formatQuantityNumber

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    viewModel: ShoppingViewModel,
    onSignout: () -> Unit,
    onRestart: () -> Unit,
    onChangeFirebaseEnv: (suspend () -> Unit)? = null,
) {
    val fatalError by viewModel.fatalError.collectAsState()
    if (fatalError != null) {
        FatalErrorScreen(fatalError!!, onRestart)
        return
    }

    val state by viewModel.uiState.collectAsState()
    val dialogState by viewModel.dialogState.collectAsState()
    val shareListDialogState by viewModel.shareListDialogState.collectAsState()
    val addListDialogVisible by viewModel.addListDialogVisible.collectAsState()
    val renameListDialogVisible by viewModel.renameListDialogVisible.collectAsState()
    val deleteListDialogVisible by viewModel.deleteListDialogVisible.collectAsState()
    val importListDialogState by viewModel.importListDialogState.collectAsState()
    var addFieldText by remember { mutableStateOf("") }
    var signOutConfirmDialogVisible by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    if (signOutConfirmDialogVisible) {
        SignoutConfirmDialog(
            onConfirm = {
                signOutConfirmDialogVisible = false
                onSignout()
            },
            onDismiss = { signOutConfirmDialogVisible = false },
        )
    }

    dialogState?.let { ItemDialog(it) }

    shareListDialogState?.let { ds ->
        ShareListDialog(
            errorMessage = ds.errorMessage,
            onConfirm = { email -> viewModel.shareList(email) },
            onDismiss = { viewModel.dismissShareListDialog() },
        )
    }

    if (addListDialogVisible) {
        AddListDialog(
            onConfirm = { name ->
                viewModel.addList(name)
                viewModel.dismissAddListDialog()
            },
            onDismiss = { viewModel.dismissAddListDialog() },
        )
    }

    if (renameListDialogVisible) {
        RenameListDialog(
            currentName = state.currentListName,
            onConfirm = { name ->
                viewModel.renameCurrentList(name)
                viewModel.dismissRenameListDialog()
            },
            onDismiss = { viewModel.dismissRenameListDialog() },
        )
    }

    importListDialogState?.let { ds ->
        ImportListDialog(
            dialogState = ds,
            onImport = { name, csv -> viewModel.importListFromCsv(name, csv) },
            onDismiss = { viewModel.dismissImportListDialog() },
        )
    }

    if (deleteListDialogVisible) {
        DeleteListDialog(
            listName = state.currentListName,
            onConfirm = {
                viewModel.deleteCurrentList()
                viewModel.dismissDeleteListDialog()
            },
            onDismiss = { viewModel.dismissDeleteListDialog() },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    ListNamePill(
                        currentListName = state.currentListName,
                        lists = state.lists,
                        onSelect = { listId -> viewModel.selectList(listId) },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    OverflowMenu(
                        isOwner = state.isOwner,
                        currentUserEmail = state.currentUserEmail,
                        onAddList = { viewModel.openAddListDialog() },
                        onRenameList = { viewModel.openRenameListDialog() },
                        onShareList = { viewModel.openShareListDialog() },
                        onImportList = { viewModel.openImportListDialog() },
                        onDeleteList = { viewModel.openDeleteListDialog() },
                        onSignoutRequest = { signOutConfirmDialogVisible = true },
                        onChangeFirebaseEnv = onChangeFirebaseEnv?.let { handler ->
                            {
                                scope.launch {
                                    handler()
                                    snackbarHostState.showSnackbar("Close and reopen the app to apply")
                                }
                            }
                        },
                    )
                },
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
private fun ListNamePill(
    currentListName: String,
    lists: List<ListMetadata>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(50),
                )
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = currentListName.ifEmpty { "…" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "Switch list",
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            lists.forEach { list ->
                DropdownMenuItem(
                    text = { Text(list.name) },
                    onClick = {
                        expanded = false
                        onSelect(list.id)
                    },
                )
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
private fun OverflowMenu(
    isOwner: Boolean,
    currentUserEmail: String,
    onAddList: () -> Unit,
    onRenameList: () -> Unit,
    onShareList: () -> Unit,
    onImportList: () -> Unit,
    onDeleteList: () -> Unit,
    onSignoutRequest: () -> Unit,
    onChangeFirebaseEnv: (() -> Unit)? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More options",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text("Add list") },
            onClick = {
                expanded = false
                onAddList()
            },
        )
        if (isOwner) {
            DropdownMenuItem(
                text = { Text("Rename list") },
                onClick = {
                    expanded = false
                    onRenameList()
                },
            )
            DropdownMenuItem(
                text = { Text("Share list") },
                onClick = {
                    expanded = false
                    onShareList()
                },
            )
            DropdownMenuItem(
                text = { Text("Import list from CSV") },
                onClick = {
                    expanded = false
                    onImportList()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete list") },
                onClick = {
                    expanded = false
                    onDeleteList()
                },
            )
        }
        HorizontalDivider()
        if (onChangeFirebaseEnv != null) {
            DropdownMenuItem(
                text = { Text("Firebase environment…") },
                onClick = {
                    expanded = false
                    onChangeFirebaseEnv()
                },
            )
        }
        DropdownMenuItem(
            text = { Text("Sign out") },
            onClick = {
                expanded = false
                onSignoutRequest()
            },
        )
        Text(
            text = currentUserEmail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SignoutConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign out?") },
        text = { Text("You will be signed out and returned to the sign-in screen.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Sign out") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun AddListDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("List name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onConfirm(name)
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun RenameListDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("List name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (name.isNotBlank()) onConfirm(name)
                }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DeleteListDialog(listName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete \"$listName\"?") },
        text = {
            Text("Are you sure? The list and all its items will be deleted immediately and permanently.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ShareListDialog(
    errorMessage: String?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share list") },
        text = {
            Column {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (email.isNotBlank()) onConfirm(email.trim())
                    }),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(email.trim()) },
                enabled = email.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FatalErrorScreen(message: String, onRestart: () -> Unit) {
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
            Button(
                onClick = onRestart,
                modifier = Modifier.padding(top = 24.dp),
            ) {
                Text("Restart")
            }
        }
    }
}

@Composable
private fun ImportListDialog(
    dialogState: ImportListDialogState,
    onImport: (name: String, csvContent: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var csvContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dialogState.proposedName) {
        if (dialogState.proposedName != null) {
            name = dialogState.proposedName
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val (displayName, content) = withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    } ?: uri.lastPathSegment ?: uri.toString()
                    displayName to context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.readText()
                }
                selectedFileName = displayName
                if (name.isBlank()) {
                    name = suggestedListName(displayName)
                }
                csvContent = content
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import list from CSV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = { launcher.launch("*/*") }) {
                        Text("Choose file")
                    }
                    Text(
                        text = selectedFileName ?: "No file selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (selectedFileName != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (dialogState.errorMessage != null) {
                    Text(
                        text = dialogState.errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(name, csvContent ?: return@TextButton) },
                enabled = name.isNotBlank() && csvContent != null,
            ) { Text("Import") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

internal fun suggestedListName(fileName: String): String =
    fileName.substringBeforeLast('.', fileName)

private fun formatQuantity(quantity: Double): String {
    return "×${formatQuantityNumber(quantity)}"
}
