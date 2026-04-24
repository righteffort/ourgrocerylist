package org.righteffort.ourgrocerylist.undo

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.repository.ShoppingRepository

data class UndoRedoState(
    val undoAvailable: Boolean = false,
    val redoAvailable: Boolean = false,
)

class UndoRedoManager(
    private val repository: ShoppingRepository,
    private val stackRepository: UndoRedoStackRepository? = null,
) {

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    private val _state = MutableStateFlow(UndoRedoState())
    val state: StateFlow<UndoRedoState> = _state.asStateFlow()

    // Loads persisted stacks from DataStore. Called once from the ViewModel's observation job
    // before processing remote changes, so stacks are restored before any pruning occurs.
    suspend fun initialize() {
        stackRepository?.load()?.let { (undo, redo) ->
            undoStack.addAll(undo)
            redoStack.addAll(redo)
            updateState()
        }
    }

    suspend fun execute(command: Command) {
        repository.apply(command)
        undoStack.addLast(command)
        redoStack.clear()
        updateState()
        stackRepository?.save(undoStack.toList(), redoStack.toList())
    }

    suspend fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        repository.apply(command.reverse())
        redoStack.addLast(command)
        updateState()
        stackRepository?.save(undoStack.toList(), redoStack.toList())
    }

    suspend fun redo() {
        val command = redoStack.removeLastOrNull() ?: return
        repository.apply(command)
        undoStack.addLast(command)
        updateState()
        stackRepository?.save(undoStack.toList(), redoStack.toList())
    }

    // Called by the ViewModel when a remote write to itemId is detected.
    // Scans each stack from newest to oldest; the first entry referencing itemId
    // and everything older than it are discarded. Entries newer are preserved.
    suspend fun pruneForRemoteWrite(itemId: String) {
        pruneStack(undoStack, itemId)
        pruneStack(redoStack, itemId)
        updateState()
        stackRepository?.save(undoStack.toList(), redoStack.toList())
    }

    private fun pruneStack(stack: ArrayDeque<Command>, itemId: String) {
        // Stack is ordered oldest-first. indexOfLast finds the newest reference.
        val cutIndex = stack.indexOfLast { it.referencesItem(itemId) }
        if (cutIndex >= 0) {
            repeat(cutIndex + 1) { stack.removeFirst() }
        }
    }

    private fun updateState() {
        _state.value = UndoRedoState(
            undoAvailable = undoStack.isNotEmpty(),
            redoAvailable = redoStack.isNotEmpty(),
        )
    }
}
