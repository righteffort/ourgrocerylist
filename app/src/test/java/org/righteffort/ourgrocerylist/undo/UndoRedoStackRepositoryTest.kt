package org.righteffort.ourgrocerylist.undo

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.righteffort.ourgrocerylist.model.Command
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class UndoRedoStackRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun makeRepo(listId: String = "list-1"): UndoRedoStackRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempDir.resolve("test.preferences_pb") },
        )
        return UndoRedoStackRepository(dataStore, listId)
    }

    private fun item(name: String) = ShoppingItem(id = name, fields = ItemFields(name = name))

    @Test
    fun `load returns empty stacks when nothing has been saved`() = runTest(UnconfinedTestDispatcher()) {
        val (undo, redo) = makeRepo().load()
        assertTrue(undo.isEmpty())
        assertTrue(redo.isEmpty())
    }

    @Test
    fun `roundtrips all command types`() = runTest(UnconfinedTestDispatcher()) {
        val bread = item("Bread")
        val milk = item("Milk")
        val undoCommands = listOf(
            Command.AddItem(bread),
            Command.DeleteItem(milk),
            Command.EditItem(bread, ItemFields(name = "Sourdough")),
            Command.CheckItem(bread),
            Command.UncheckItem(bread),
        )
        val redoCommands = listOf(Command.AddItem(milk))
        val repo = makeRepo()

        repo.save(undoCommands, redoCommands)
        val (loadedUndo, loadedRedo) = repo.load()

        assertEquals(undoCommands, loadedUndo)
        assertEquals(redoCommands, loadedRedo)
    }

    @Test
    fun `save overwrites previous stacks`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepo()
        repo.save(listOf(Command.AddItem(item("Bread"))), emptyList())
        repo.save(listOf(Command.AddItem(item("Milk"))), emptyList())

        val (undo, redo) = repo.load()
        assertEquals(1, undo.size)
        assertEquals("Milk", (undo[0] as Command.AddItem).item.fields.name)
        assertTrue(redo.isEmpty())
    }

    @Test
    fun `stacks for different list ids are independent`() = runTest(UnconfinedTestDispatcher()) {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempDir.resolve("test.preferences_pb") },
        )
        val repo1 = UndoRedoStackRepository(dataStore, "list-1")
        val repo2 = UndoRedoStackRepository(dataStore, "list-2")

        repo1.save(listOf(Command.AddItem(item("Bread"))), emptyList())
        repo2.save(listOf(Command.AddItem(item("Milk"))), emptyList())

        val (undo1, _) = repo1.load()
        val (undo2, _) = repo2.load()
        assertEquals("Bread", (undo1[0] as Command.AddItem).item.fields.name)
        assertEquals("Milk", (undo2[0] as Command.AddItem).item.fields.name)
    }
}
