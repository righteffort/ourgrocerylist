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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.righteffort.ourgrocerylist.repository.FakeShoppingRepository

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: ShoppingViewModel
    private lateinit var collectScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        viewModel = ShoppingViewModel(FakeShoppingRepository())
        // Hold an active subscriber so WhileSubscribed keeps the upstream flow alive.
        collectScope = CoroutineScope(testDispatcher)
        collectScope.launch { viewModel.uiState.collect {} }
    }

    @AfterEach
    fun tearDown() {
        collectScope.cancel()
        Dispatchers.resetMain()
    }

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

    @Test
    fun `deleteItem removes item from the list`() {
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.deleteItem(item)
        assertTrue(viewModel.uiState.value.uncheckedItems.isEmpty())
    }

    @Test
    fun `item has default quantity of 1_0`() {
        viewModel.addItem("Bread")
        assertEquals(1.0, viewModel.uiState.value.uncheckedItems.single().fields.quantity)
    }

    @Test
    fun `editItem updates quantity on item in uiState`() {
        viewModel.addItem("Bread")
        val item = viewModel.uiState.value.uncheckedItems.single()
        viewModel.editItem(item, item.fields.copy(quantity = 2.5))
        assertEquals(2.5, viewModel.uiState.value.uncheckedItems.single().fields.quantity)
    }
}
