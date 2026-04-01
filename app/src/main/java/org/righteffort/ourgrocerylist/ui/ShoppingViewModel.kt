package org.righteffort.ourgrocerylist.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.repository.ShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager

private val ITEM_COMPARATOR = compareBy<ShoppingItem> { it.fields.name.lowercase() }
private const val TAG = "ShoppingViewModel"

class ShoppingViewModel(
    private val repository: ShoppingRepository,
    private val undoRedoManager: UndoRedoManager,
    appErrors: Flow<String> = emptyFlow(),
) : ViewModel() {

    private val _dialogState = MutableStateFlow<ItemDialogState?>(null)
    val dialogState: StateFlow<ItemDialogState?> = _dialogState.asStateFlow()

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _fatalError = MutableStateFlow<String?>(null)
    val fatalError: StateFlow<String?> = _fatalError.asStateFlow()

    init {
        viewModelScope.launch {
            appErrors.collect { message ->
                Log.e(TAG, "Fatal app init error: $message")
                _fatalError.value = message
            }
        }
        viewModelScope.launch {
            repository.observeRemotelyModifiedItemIds()
                .catch { e -> logAndEmitFatalError("Failed to observe remote changes", e) }
                .collect { itemIds -> itemIds.forEach { undoRedoManager.pruneForRemoteWrite(it) } }
        }
    }

    val uiState: StateFlow<UiState> = combine(
        repository.observeItems()
            .catch { e ->
                logAndEmitFatalError("Failed to observe items", e)
                emit(emptyList())
            },
        undoRedoManager.state,
    ) { items, undoRedoState ->
        val (checked, unchecked) = items.partition { it.fields.checked }
        UiState(
            uncheckedItems = unchecked.sortedWith(ITEM_COMPARATOR),
            checkedItems = checked.sortedWith(ITEM_COMPARATOR),
            undoAvailable = undoRedoState.undoAvailable,
            redoAvailable = undoRedoState.redoAvailable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState())

    fun addItem(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val item = ShoppingItem(
            id = repository.newItemId(),
            fields = ItemFields(name = trimmed),
        )
        applyCommand(Command.AddItem(item))
    }

    fun deleteItem(item: ShoppingItem) {
        applyCommand(Command.DeleteItem(item))
    }

    fun editItem(previousSnapshot: ShoppingItem, newFields: ItemFields) {
        applyCommand(Command.EditItem(previousSnapshot, newFields))
    }

    fun checkItem(item: ShoppingItem) {
        applyCommand(Command.CheckItem(item))
    }

    fun uncheckItem(item: ShoppingItem) {
        applyCommand(Command.UncheckItem(item))
    }

    fun undo() {
        viewModelScope.launch {
            try {
                undoRedoManager.undo()
            } catch (e: Exception) {
                logAndEmitError("Undo failed", e)
            }
        }
    }

    fun redo() {
        viewModelScope.launch {
            try {
                undoRedoManager.redo()
            } catch (e: Exception) {
                logAndEmitError("Redo failed", e)
            }
        }
    }

    fun openEditDialog(item: ShoppingItem) {
        _dialogState.value = ItemDialogState(
            title = "Edit item",
            initialFields = item.fields,
            showDelete = true,
            onSave = { newFields ->
                editItem(item, newFields)
                dismissDialog()
            },
            onDelete = {
                deleteItem(item)
                dismissDialog()
            },
            onCancel = { dismissDialog() },
        )
    }

    fun openAddDialog(initialName: String) {
        _dialogState.value = ItemDialogState(
            title = "Add item",
            initialFields = ItemFields(name = initialName),
            showDelete = false,
            onSave = { newFields ->
                val item = ShoppingItem(
                    id = repository.newItemId(),
                    fields = newFields.copy(name = newFields.name.trim()),
                )
                applyCommand(Command.AddItem(item))
                dismissDialog()
            },
            onDelete = null,
            onCancel = { dismissDialog() },
        )
    }

    fun dismissDialog() {
        _dialogState.value = null
    }

    private fun applyCommand(command: Command) {
        viewModelScope.launch {
            try {
                undoRedoManager.execute(command)
            } catch (e: Exception) {
                logAndEmitError("Command failed: ${command::class.simpleName}", e)
            }
        }
    }

    private fun logAndEmitError(message: String, e: Throwable) {
        Log.e(TAG, message, e)
        _errors.tryEmit(e.message ?: message)
    }

    private fun logAndEmitFatalError(message: String, e: Throwable) {
        Log.e(TAG, message, e)
        _fatalError.value = e.message ?: message
    }
}
