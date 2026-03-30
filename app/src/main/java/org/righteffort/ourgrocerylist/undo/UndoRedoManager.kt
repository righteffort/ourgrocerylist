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

    private fun updateState() {
        _state.value = UndoRedoState(
            undoAvailable = undoStack.isNotEmpty(),
            redoAvailable = redoStack.isNotEmpty(),
        )
    }
}
