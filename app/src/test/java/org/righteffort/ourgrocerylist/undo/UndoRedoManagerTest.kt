package org.righteffort.ourgrocerylist.undo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UndoRedoManagerTest {

    private lateinit var repository: FakeShoppingRepository
    private lateinit var manager: UndoRedoManager

    @BeforeEach
    fun setUp() {
        repository = FakeShoppingRepository()
        manager = UndoRedoManager(repository)
    }

    // Helpers

    private suspend fun currentItems() = repository.observeItems().first()
    private suspend fun currentNames() = currentItems().map { it.fields.name }
    private suspend fun currentChecked(name: String) =
        currentItems().first { it.fields.name == name }.fields.checked

    private fun item(name: String) = ShoppingItem(id = name, fields = ItemFields(name = name))

    // --- execute / undo / redo ---

    @Test
    fun `execute adds item, undo removes it, redo re-adds it`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Bread")))
        assertEquals(listOf("Bread"), currentNames())

        manager.undo()
        assertTrue(currentNames().isEmpty())

        manager.redo()
        assertEquals(listOf("Bread"), currentNames())
    }

    @Test
    fun `undo and redo of DeleteItem`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))
        manager.execute(Command.DeleteItem(bread))
        assertTrue(currentNames().isEmpty())

        manager.undo()
        assertEquals(listOf("Bread"), currentNames())

        manager.redo()
        assertTrue(currentNames().isEmpty())
    }

    @Test
    fun `undo and redo of EditItem`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))
        manager.execute(Command.EditItem(bread, ItemFields(name = "Milk")))
        assertEquals(listOf("Milk"), currentNames())

        manager.undo()
        assertEquals(listOf("Bread"), currentNames())

        manager.redo()
        assertEquals(listOf("Milk"), currentNames())
    }

    @Test
    fun `undo and redo of CheckItem`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Bread")))
        val bread = currentItems().single()
        manager.execute(Command.CheckItem(bread))
        assertTrue(currentChecked("Bread"))

        manager.undo()
        assertFalse(currentChecked("Bread"))

        manager.redo()
        assertTrue(currentChecked("Bread"))
    }

    @Test
    fun `undo and redo of UncheckItem`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Bread")))
        val bread = currentItems().single()
        manager.execute(Command.CheckItem(bread))
        val checkedBread = currentItems().single()
        manager.execute(Command.UncheckItem(checkedBread))
        assertFalse(currentChecked("Bread"))

        manager.undo()
        assertTrue(currentChecked("Bread"))

        manager.redo()
        assertFalse(currentChecked("Bread"))
    }

    @Test
    fun `execute after undo clears redo stack`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Bread")))
        manager.undo()
        assertTrue(manager.state.value.redoAvailable)

        manager.execute(Command.AddItem(item("Milk")))
        assertFalse(manager.state.value.redoAvailable)

        // Redo should be a no-op — Bread must not re-appear
        manager.redo()
        assertEquals(listOf("Milk"), currentNames())
    }

    @Test
    fun `undo on empty stack is no-op`() = runTest(UnconfinedTestDispatcher()) {
        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
        assertFalse(manager.state.value.redoAvailable)
        assertTrue(currentNames().isEmpty())
    }

    @Test
    fun `redo on empty stack is no-op`() = runTest(UnconfinedTestDispatcher()) {
        manager.redo()
        assertFalse(manager.state.value.undoAvailable)
        assertFalse(manager.state.value.redoAvailable)
        assertTrue(currentNames().isEmpty())
    }

    @Test
    fun `undoAvailable and redoAvailable reflect stack state`() = runTest(UnconfinedTestDispatcher()) {
        assertFalse(manager.state.value.undoAvailable)
        assertFalse(manager.state.value.redoAvailable)

        manager.execute(Command.AddItem(item("Bread")))
        assertTrue(manager.state.value.undoAvailable)
        assertFalse(manager.state.value.redoAvailable)

        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
        assertTrue(manager.state.value.redoAvailable)

        manager.redo()
        assertTrue(manager.state.value.undoAvailable)
        assertFalse(manager.state.value.redoAvailable)
    }

    @Test
    fun `multiple sequential undos unwind in reverse order`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Apples")))
        manager.execute(Command.AddItem(item("Bread")))
        manager.execute(Command.AddItem(item("Milk")))
        assertEquals(setOf("Apples", "Bread", "Milk"), currentNames().toSet())

        manager.undo()
        assertEquals(setOf("Apples", "Bread"), currentNames().toSet())

        manager.undo()
        assertEquals(setOf("Apples"), currentNames().toSet())

        manager.undo()
        assertTrue(currentNames().isEmpty())
    }

    // --- pruneForRemoteWrite ---

    @Test
    fun `pruneForRemoteWrite removes matching entry and everything older from undo stack`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))       // undo[0] — references bread
        manager.execute(Command.AddItem(item("Milk"))) // undo[1]

        manager.pruneForRemoteWrite(bread.id)

        // Only AddItem(milk) remains on the undo stack; undoing it leaves bread
        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
        assertEquals(listOf("Bread"), currentNames())
    }

    @Test
    fun `pruneForRemoteWrite preserves entries newer than the pruned entry`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))          // undo[0] — references bread
        manager.execute(Command.AddItem(item("Milk")))   // undo[1]
        manager.execute(Command.AddItem(item("Eggs")))   // undo[2]

        manager.pruneForRemoteWrite(bread.id)

        // undo[1] and undo[2] (milk, eggs) survive; two undos exhaust the stack
        manager.undo()
        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
        assertEquals(listOf("Bread"), currentNames())
    }

    @Test
    fun `pruneForRemoteWrite with multiple references truncates at the newest one`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))           // undo[0] — references bread
        manager.execute(Command.AddItem(item("Milk")))    // undo[1]
        manager.execute(Command.CheckItem(bread))         // undo[2] — references bread (newer)
        manager.execute(Command.AddItem(item("Eggs")))    // undo[3]

        manager.pruneForRemoteWrite(bread.id)

        // Cut at undo[2] (newest bread reference); only undo[3] (eggs) survives
        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
    }

    @Test
    fun `pruneForRemoteWrite also prunes redo stack`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        manager.execute(Command.AddItem(bread))
        manager.execute(Command.AddItem(item("Milk")))
        manager.undo() // redo: [AddItem(milk)]
        manager.undo() // redo: [AddItem(milk), AddItem(bread)]
        assertTrue(manager.state.value.redoAvailable)

        manager.pruneForRemoteWrite(bread.id)

        // AddItem(bread) is the newest redo entry referencing bread; both entries removed
        assertFalse(manager.state.value.redoAvailable)
    }

    @Test
    fun `pruneForRemoteWrite is no-op when item not referenced in stacks`() = runTest(UnconfinedTestDispatcher()) {
        manager.execute(Command.AddItem(item("Bread")))
        assertTrue(manager.state.value.undoAvailable)

        manager.pruneForRemoteWrite("unknown-id")

        assertTrue(manager.state.value.undoAvailable)
    }

    // --- persistence ---

    @TempDir
    lateinit var tempDir: File

    private fun makeStackRepo(): UndoRedoStackRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempDir.resolve("test.preferences_pb") },
        )
        return UndoRedoStackRepository(dataStore, "list-1")
    }

    @Test
    fun `initialize restores undo availability from persisted stacks`() = runTest(UnconfinedTestDispatcher()) {
        val stackRepo = makeStackRepo()
        val manager1 = UndoRedoManager(FakeShoppingRepository(), stackRepo)
        manager1.initialize()
        manager1.execute(Command.AddItem(item("Bread")))

        val manager2 = UndoRedoManager(FakeShoppingRepository(), stackRepo)
        assertFalse(manager2.state.value.undoAvailable)
        manager2.initialize()
        assertTrue(manager2.state.value.undoAvailable)
    }

    @Test
    fun `initialize restores both undo and redo stacks`() = runTest(UnconfinedTestDispatcher()) {
        val stackRepo = makeStackRepo()
        val manager1 = UndoRedoManager(FakeShoppingRepository(), stackRepo)
        manager1.initialize()
        manager1.execute(Command.AddItem(item("Bread")))
        manager1.execute(Command.AddItem(item("Milk")))
        manager1.undo()

        val manager2 = UndoRedoManager(FakeShoppingRepository(), stackRepo)
        manager2.initialize()
        assertTrue(manager2.state.value.undoAvailable)
        assertTrue(manager2.state.value.redoAvailable)
    }

    @Test
    fun `no stack repository leaves behavior unchanged`() = runTest(UnconfinedTestDispatcher()) {
        val manager = UndoRedoManager(FakeShoppingRepository())
        manager.initialize()
        manager.execute(Command.AddItem(item("Bread")))
        assertTrue(manager.state.value.undoAvailable)
        manager.undo()
        assertFalse(manager.state.value.undoAvailable)
    }
}
