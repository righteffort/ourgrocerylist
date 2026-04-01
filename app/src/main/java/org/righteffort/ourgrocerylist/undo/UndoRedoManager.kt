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

class UndoRedoManager(private val repository: ShoppingRepository) {

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    private val _state = MutableStateFlow(UndoRedoState())
    val state: StateFlow<UndoRedoState> = _state.asStateFlow()

    suspend fun execute(command: Command) {
        repository.apply(command)
        undoStack.addLast(command)
        redoStack.clear()
        updateState()
    }

    suspend fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        repository.apply(command.reverse())
        redoStack.addLast(command)
        updateState()
    }

    suspend fun redo() {
        val command = redoStack.removeLastOrNull() ?: return
        repository.apply(command)
        undoStack.addLast(command)
        updateState()
    }

    // Called by the ViewModel when a remote write to itemId is detected.
    // Scans each stack from newest to oldest; the first entry referencing itemId
    // and everything older than it are discarded. Entries newer are preserved.
    fun pruneForRemoteWrite(itemId: String) {
        pruneStack(undoStack, itemId)
        pruneStack(redoStack, itemId)
        updateState()
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
