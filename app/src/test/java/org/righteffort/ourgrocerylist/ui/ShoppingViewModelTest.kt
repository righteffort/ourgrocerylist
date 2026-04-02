package org.righteffort.ourgrocerylist.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.righteffort.ourgrocerylist.model.ListMetadata
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FakeListRepository
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository
import org.righteffort.ourgrocerylist.repository.SharingRepository

private val TEST_USER = User(uid = "test-uid", email = "test@test.com")
private const val LIST_ID = "list-1"

private class FakeSharingRepository(private val error: Exception? = null) : SharingRepository {
    override suspend fun addEditor(listId: String, email: String) {
        if (error != null) throw error
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ShoppingViewModel
    private lateinit var collectScope: CoroutineScope
    private lateinit var fakeListRepo: FakeListRepository

    private fun makeViewModel(
        sharingRepository: SharingRepository = FakeSharingRepository(),
        initialLists: List<ListMetadata> = listOf(ListMetadata(LIST_ID, "Groceries", isOwner = true)),
    ): ShoppingViewModel {
        fakeListRepo = FakeListRepository(initialLists)
        return ShoppingViewModel(
            currentUserFlow = MutableStateFlow(TEST_USER),
            listRepository = fakeListRepo,
            repositoryFactory = { FakeShoppingRepository() },
            sharingRepository = sharingRepository,
        ).also { vm ->
            collectScope.launch { vm.uiState.collect {} }
        }
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        collectScope = CoroutineScope(testDispatcher)
        viewModel = makeViewModel()
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

    // --- shareList ---

    @Test
    fun `shareList on success dismisses dialog and emits 'Editor added'`() {
        val vm = makeViewModel(sharingRepository = FakeSharingRepository())
        vm.openShareListDialog()
        val messages = mutableListOf<String>()
        collectScope.launch { vm.errors.collect { messages.add(it) } }
        vm.shareList("editor@example.com")
        assertNull(vm.shareListDialogState.value)
        assertEquals(listOf("Editor added"), messages)
    }

    @Test
    fun `shareList on failure keeps dialog open with error message`() {
        val vm = makeViewModel(sharingRepository = FakeSharingRepository(error = Exception("user not found")))
        vm.openShareListDialog()
        vm.shareList("editor@example.com")
        assertEquals("user not found", vm.shareListDialogState.value?.errorMessage)
    }

    @Test
    fun `openShareListDialog shows dialog with no error`() {
        viewModel.openShareListDialog()
        assertEquals(ShareListDialogState(), viewModel.shareListDialogState.value)
    }

    @Test
    fun `dismissShareListDialog hides dialog`() {
        viewModel.openShareListDialog()
        viewModel.dismissShareListDialog()
        assertNull(viewModel.shareListDialogState.value)
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

    // --- list operations ---

    @Test
    fun `uiState reflects current list name and isOwner`() {
        assertEquals("Groceries", viewModel.uiState.value.currentListName)
        assertTrue(viewModel.uiState.value.isOwner)
    }

    @Test
    fun `uiState lists contains all lists`() {
        assertEquals(listOf(ListMetadata(LIST_ID, "Groceries", isOwner = true)), viewModel.uiState.value.lists)
    }

    @Test
    fun `addList creates new list and switches to it`() {
        viewModel.addList("Hardware")
        val lists = viewModel.uiState.value.lists
        assertEquals(2, lists.size)
        assertEquals("Hardware", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `addList with blank name does nothing`() {
        viewModel.addList("  ")
        assertEquals(1, viewModel.uiState.value.lists.size)
    }

    @Test
    fun `selectList switches current list`() {
        viewModel.addList("Hardware")
        val hardwareId = viewModel.uiState.value.lists.first { it.name == "Hardware" }.id
        viewModel.selectList(LIST_ID)
        assertEquals("Groceries", viewModel.uiState.value.currentListName)
        viewModel.selectList(hardwareId)
        assertEquals("Hardware", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `undo stacks are independent per list`() {
        viewModel.addItem("Bread")
        assertTrue(viewModel.uiState.value.undoAvailable)

        viewModel.addList("Hardware")
        // New list: undo stack should be empty
        assertFalse(viewModel.uiState.value.undoAvailable)

        viewModel.selectList(LIST_ID)
        // Back to Groceries: undo should still be available
        assertTrue(viewModel.uiState.value.undoAvailable)
    }

    @Test
    fun `renameCurrentList updates list name`() {
        viewModel.renameCurrentList("Weekly Shop")
        assertEquals("Weekly Shop", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `renameCurrentList with blank name does nothing`() {
        viewModel.renameCurrentList("  ")
        assertEquals("Groceries", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `deleteCurrentList removes list and falls back to another`() {
        viewModel.addList("Hardware")
        assertEquals("Hardware", viewModel.uiState.value.currentListName)
        viewModel.deleteCurrentList()
        assertEquals(1, viewModel.uiState.value.lists.size)
        assertEquals("Groceries", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `deleteCurrentList on last list recreates default Groceries list`() {
        viewModel.deleteCurrentList()
        // FakeListRepository deletes the list, then the ViewModel's init block
        // detects empty list and calls createList("Groceries").
        assertEquals(1, viewModel.uiState.value.lists.size)
        assertEquals("Groceries", viewModel.uiState.value.currentListName)
    }

    @Test
    fun `non-owner list hides isOwner in uiState`() {
        val vm = makeViewModel(
            initialLists = listOf(ListMetadata("shared-1", "Their List", isOwner = false)),
        )
        assertFalse(vm.uiState.value.isOwner)
    }

    @Test
    fun `owned list sorts before editor list`() {
        val vm = makeViewModel(
            initialLists = listOf(
                ListMetadata("a", "Apples", isOwner = false),
                ListMetadata("b", "Bananas", isOwner = true),
            ),
        )
        assertEquals(listOf("Bananas", "Apples"), vm.uiState.value.lists.map { it.name })
        assertEquals("Bananas", vm.uiState.value.currentListName)
    }

    // --- list dialog visibility ---

    @Test
    fun `openAddListDialog and dismissAddListDialog toggle visibility`() {
        assertFalse(viewModel.addListDialogVisible.value)
        viewModel.openAddListDialog()
        assertTrue(viewModel.addListDialogVisible.value)
        viewModel.dismissAddListDialog()
        assertFalse(viewModel.addListDialogVisible.value)
    }

    @Test
    fun `openRenameListDialog and dismissRenameListDialog toggle visibility`() {
        assertFalse(viewModel.renameListDialogVisible.value)
        viewModel.openRenameListDialog()
        assertTrue(viewModel.renameListDialogVisible.value)
        viewModel.dismissRenameListDialog()
        assertFalse(viewModel.renameListDialogVisible.value)
    }

    @Test
    fun `openDeleteListDialog and dismissDeleteListDialog toggle visibility`() {
        assertFalse(viewModel.deleteListDialogVisible.value)
        viewModel.openDeleteListDialog()
        assertTrue(viewModel.deleteListDialogVisible.value)
        viewModel.dismissDeleteListDialog()
        assertFalse(viewModel.deleteListDialogVisible.value)
    }
}
