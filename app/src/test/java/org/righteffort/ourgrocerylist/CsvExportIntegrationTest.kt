package org.righteffort.ourgrocerylist

import app.cash.turbine.test
import com.google.firebase.FirebaseOptions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.rules.TimberTestRule
import org.righteffort.ourgrocerylist.ui.UiState
import org.righteffort.ourgrocerylist.util.CsvImporter
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CsvExportIntegrationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val defaultOptions = FirebaseOptions.Builder()
            .setProjectId(googleServices.projectId)
            .setApplicationId(googleServices.appId)
            .setApiKey(googleServices.apiKey)
            .build()
        setupUser(userA, defaultOptions)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        userA.app.delete()
        clearEmulatorData()
    }

    @Test
    fun `export captures all items including checked`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            // applyCommand drops items silently if currentListId is null, so wait for the
            // default list to be auto-selected before adding items.
            var state: UiState
            do { state = awaitItem() } while (state.currentListName.isEmpty())

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Apples", quantity = 3.0, checked = false))
            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Bread", quantity = 1.0, checked = false))

            do { state = awaitItem() } while (state.uncheckedItems.size < 2)

            // Check one item.
            userA.viewModel.checkItem(state.uncheckedItems.first { it.fields.name == "Apples" })
            do { state = awaitItem() } while (state.checkedItems.none { it.fields.name == "Apples" })

            val csv = userA.viewModel.exportCurrentListToCsv()
            val parsed = CsvImporter.parse(csv)

            assertEquals(2, parsed.size)
            val apples = parsed.first { it.name == "Apples" }
            val bread = parsed.first { it.name == "Bread" }
            assertEquals(3.0, apples.quantity)
            assertTrue(apples.checked)
            assertEquals(1.0, bread.quantity)
            assertFalse(bread.checked)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `export of empty list produces importable header-only CSV`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            // Wait for the default list to be selected with no items.
            var state: UiState
            do { state = awaitItem() } while (state.currentListName.isEmpty())

            val csv = userA.viewModel.exportCurrentListToCsv()
            val parsed = CsvImporter.parse(csv)

            assertTrue(parsed.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
