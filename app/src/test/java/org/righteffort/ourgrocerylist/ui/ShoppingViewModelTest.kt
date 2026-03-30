package org.righteffort.ourgrocerylist.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository
import org.righteffort.ourgrocerylist.undo.UndoRedoManager

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ShoppingViewModel
    private lateinit var collectScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val repository = FakeShoppingRepository()
        viewModel = ShoppingViewModel(repository, UndoRedoManager(repository))
        // Hold an active subscriber so WhileSubscribed keeps the upstream flow alive.
        collectScope = CoroutineScope(testDispatcher)
        collectScope.launch { viewModel.uiState.collect {} }
    }

    @AfterEach
    fun tearDown() {
        collectScope.cancel()
        Dispatchers.resetMain()
    }

    // --- addItem ---

    @Test
    fun `initial state has empty unchecked and checked lists`() {
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
        assertTrue(viewModel.uiState.value.checkedItems.isEmpty())
    }

    @Test
    fun `addItem puts item in uncheckedItems`() {
        viewModel.addItem("Bread")
        assertEquals(listOf("Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
    }

    @Test
    fun `addItem with blank name does nothing`() {
        viewModel.addItem("   ")
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
    }

    @Test
    fun `items are sorted case-insensitively`() {
        viewModel.addItem("Bread")
        viewModel.addItem("banana")
        assertEquals(
            listOf("banana", "Bread"),
            viewModel.uiState.value.uncheckedItems.map { it.fields.name },
        )
    }

    @Test
    fun `numbers sort before letters`() {
        viewModel.addItem("Avocado")
        viewModel.addItem("1% milk")
        assertEquals(
            listOf("1% milk", "Avocado"),
            viewModel.uiState.value.uncheckedItems.map { it.fields.name },
        )
    }

    // --- checkItem / uncheckItem ---

    @Test
    fun `checkItem moves item from unchecked to checked`() {
        viewModel.addItem("Bread")
        viewModel.checkItem(viewModel.uiState.value.uncheckedItems.single())
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
        assertEquals(listOf("Bread"), viewModel.uiState.value.checkedItems.map { it.fields.name })
    }

    @Test
    fun `uncheckItem moves item from checked to unchecked`() {
        viewModel.addItem("Bread")
        viewModel.checkItem(viewModel.uiState.value.uncheckedItems.single())
        viewModel.uncheckItem(viewModel.uiState.value.checkedItems.single())
        assertTrue(viewModel.uiState.value.checkedItems.isEmpty())
        assertEquals(listOf("Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
    }

    @Test
    fun `checked and unchecked sections are each independently alphabetized`() {
        listOf("Milk", "Apples", "Zucchini", "Bread").forEach { viewModel.addItem(it) }
        viewModel.checkItem(viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Milk" })
        viewModel.checkItem(viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Zucchini" })
        assertEquals(listOf("Apples", "Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
        assertEquals(listOf("Milk", "Zucchini"), viewModel.uiState.value.checkedItems.map { it.fields.name })
    }

    // --- deleteItem ---

    @Test
    fun `deleteItem removes item from the list`() {
        viewModel.addItem("Bread")
        viewModel.deleteItem(viewModel.uiState.value.uncheckedItems.single())
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
    }

    @Test
    fun `deleteItem on nonexistent item is a no-op`() {
        viewModel.addItem("Bread")
        viewModel.addItem("Milk")
        val milk = viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Milk" }
        viewModel.deleteItem(milk)
        viewModel.deleteItem(milk) // stale reference, already gone
        assertEquals(listOf("Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
    }

    // --- editItem ---

    @Test
    fun `editItem updates name and item re-sorts`() {
        viewModel.addItem("Milk")
        viewModel.addItem("Apples")
        val milk = viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Milk" }
        viewModel.editItem(milk, milk.fields.copy(name = "Zucchini"))
        assertEquals(listOf("Apples", "Zucchini"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
    }

    @Test
    fun `editItem updates quantity, item stays in same list position`() {
        viewModel.addItem("Apples")
        viewModel.addItem("Milk")
        val milk = viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Milk" }
        viewModel.editItem(milk, milk.fields.copy(quantity = 3.0))
        assertEquals(listOf("Apples", "Milk"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
        assertEquals(3.0, viewModel.uiState.value.uncheckedItems.first { it.fields.name == "Milk" }.fields.quantity)
    }

    @Test
    fun `editItem with blank name applies fields as given`() {
        // The dialog disables Save when name is blank; the ViewModel does not guard this.
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.editItem(item, item.fields.copy(name = ""))
        assertEquals("", viewModel.uiState.value.uncheckedItems.single().fields.name)
    }

    @Test
    fun `item has default quantity of 1_0`() {
        viewModel.addItem("Bread")
        assertEquals(1.0, viewModel.uiState.value.uncheckedItems.single().fields.quantity)
    }

    // --- dialog state ---

    @Test
    fun `openEditDialog sets dialogState with correct title, fields, and showDelete`() {
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.openEditDialog(item)
        val dialog = viewModel.dialogState.value!!
        assertEquals("Edit item", dialog.title)
        assertEquals(item.fields, dialog.initialFields)
        assertTrue(dialog.showDelete)
    }

    @Test
    fun `openAddDialog sets dialogState with correct title, fields, and showDelete`() {
        viewModel.openAddDialog("Bread")
        val dialog = viewModel.dialogState.value!!
        assertEquals("Add item", dialog.title)
        assertEquals(ItemFields(name = "Bread"), dialog.initialFields)
        assertFalse(dialog.showDelete)
    }

    @Test
    fun `dismissDialog sets dialogState to null`() {
        viewModel.openAddDialog("Bread")
        viewModel.dismissDialog()
        assertNull(viewModel.dialogState.value)
    }

    @Test
    fun `edit dialog onSave applies edit and dismisses`() {
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.openEditDialog(item)
        viewModel.dialogState.value!!.onSave(item.fields.copy(name = "Milk"))
        assertEquals(listOf("Milk"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
        assertNull(viewModel.dialogState.value)
    }

    @Test
    fun `add dialog onSave adds item and dismisses`() {
        viewModel.openAddDialog("Bread")
        viewModel.dialogState.value!!.onSave(ItemFields(name = "Bread"))
        assertEquals(listOf("Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
        assertNull(viewModel.dialogState.value)
    }

    @Test
    fun `edit dialog onDelete deletes item and dismisses`() {
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.openEditDialog(item)
        viewModel.dialogState.value!!.onDelete!!.invoke()
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
        assertNull(viewModel.dialogState.value)
    }

    @Test
    fun `openAddDialog onDelete is null`() {
        viewModel.openAddDialog("Bread")
        assertNull(viewModel.dialogState.value!!.onDelete)
    }

    // --- undo/redo ---

    @Test
    fun `undoAvailable is false initially, true after addItem`() {
        assertFalse(viewModel.uiState.value.undoAvailable)
        viewModel.addItem("Bread")
        assertTrue(viewModel.uiState.value.undoAvailable)
    }

    @Test
    fun `undo after addItem removes the item and sets redoAvailable true`() {
        viewModel.addItem("Bread")
        viewModel.undo()
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
        assertFalse(viewModel.uiState.value.undoAvailable)
        assertTrue(viewModel.uiState.value.redoAvailable)
    }

    @Test
    fun `redo after undo re-adds the item`() {
        viewModel.addItem("Bread")
        viewModel.undo()
        viewModel.redo()
        assertEquals(listOf("Bread"), viewModel.uiState.value.uncheckedItems.map { it.fields.name })
        assertFalse(viewModel.uiState.value.redoAvailable)
    }
}
