package org.righteffort.ourgrocerylist

import app.cash.turbine.test
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.ui.UiState
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Rule
import org.righteffort.ourgrocerylist.rules.TimberTestRule
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SingleUserListIntegrationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FirebaseFirestore.setLoggingEnabled(true)
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
    fun `list creation appears in uiState`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Groceries")
            var state: UiState
            // Also gate on currentListName: _lists fires the outer combine immediately when
            // updated, but flatMapLatest hasn't emitted yet, so there is a transient state
            // where lists=[Groceries] but currentListName is still "".
            do { state = awaitItem() } while (state.lists.none { it.name == "Groceries" } || state.currentListName != "Groceries")
            assertEquals("Groceries", state.currentListName)
            assertTrue(state.isOwner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `list rename reflected in uiState`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Original")
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == "Original" })

            userA.viewModel.renameCurrentList("Weekend Run")
            while (state.currentListName != "Weekend Run") { state = awaitItem() }

            assertEquals("Weekend Run", state.currentListName)
            assertTrue(state.lists.any { it.name == "Weekend Run" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete list with others present selects another`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Alpha")
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == "Alpha" })
            val alphaId = state.lists.first { it.name == "Alpha" }.id

            // Drain until Beta is visible AND the VM has switched to it, so selectList(alphaId)
            // is a genuine state change (StateFlow deduplicates equal values).
            userA.viewModel.addList("Beta")
            while (state.lists.none { it.name == "Beta" } || state.currentListName != "Beta") { state = awaitItem() }

            // Now on Beta; switch to Alpha and wait for the transition.
            userA.viewModel.selectList(alphaId)
            while (state.currentListName != "Alpha") { state = awaitItem() }

            userA.viewModel.deleteCurrentList()
            // Also gate on currentListName: _lists fires the outer combine immediately when updated,
            // but flatMapLatest hasn't rescheduled yet, so there is a transient state where
            // lists=[Beta] but listId still=alphaId → currentListName="".
            while (state.lists.any { it.name == "Alpha" } || state.currentListName.isEmpty()) { state = awaitItem() }

            assertFalse(state.lists.any { it.name == "Alpha" })
            assertTrue(state.currentListName.isNotEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete only list triggers Groceries auto-creation`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("ToDelete")
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == "ToDelete" })

            userA.viewModel.deleteCurrentList()
            // Drain past transient states: _lists may briefly still show [ToDelete] (stale,
            // before the observeLists collect updates it) or currentListName may be empty
            // (lists=[Groceries] but _currentListId hasn't switched yet). Wait for stable state.
            while (state.currentListName != "Groceries") { state = awaitItem() }

            assertEquals("Groceries", state.currentListName)
            assertTrue(state.uncheckedItems.isEmpty())
            assertTrue(state.isOwner)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching lists changes items`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("List A")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "List A" } || state.currentListName != "List A")
            val listA = state.lists.first { it.name == "List A" }
            Timber.v("created list ${listA.name} ${listA.id}")

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Apples",
                    quantity = 1.0,
                    checked = false
                )
            )
            Timber.v("DEBUG TEST before Apples drain: currentListName=${state.currentListName} uncheckedItems=${state.uncheckedItems.map { it.fields.name }}")
            while (state.uncheckedItems.none { it.fields.name == "Apples" }) {
                state = awaitItem()
                Timber.v("DEBUG TEST Apples drain: currentListName=${state.currentListName} uncheckedItems=${state.uncheckedItems.map { it.fields.name }}")
            }

            // Drain until List B is visible AND the VM has switched to it.
            userA.viewModel.addList("List B")
            while (state.lists.none { it.name == "List B" } || state.currentListName != "List B") {
                state = awaitItem()
            }
            val listB = state.lists.first { it.name == "List B" }
            Timber.v("created list ${listB.name} ${listB.id}")

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Bread",
                    quantity = 1.0,
                    checked = false
                )
            )
            while (state.uncheckedItems.none { it.fields.name == "Bread" }) {
                state = awaitItem()
            }

            assertTrue(state.uncheckedItems.any { it.fields.name == "Bread" })
            assertFalse(state.uncheckedItems.any { it.fields.name == "Apples" })

            // Switch back to List A and verify Apples is there.
            Timber.v("Switching to ${listA.name}")
            userA.viewModel.selectList(listA.id)
            while (state.currentListName != "List A") {
                state = awaitItem()
            }
            while (state.uncheckedItems.none { it.fields.name == "Apples" }) {
                state = awaitItem()
            }
            Timber.v("Switched to ${listA.name}")

            assertTrue(state.uncheckedItems.any { it.fields.name == "Apples" })
            assertFalse(state.uncheckedItems.any { it.fields.name == "Bread" })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `items from different lists do not bleed`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("List A")
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == "List A" })

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Milk", quantity = 1.0, checked = false))
            while (state.uncheckedItems.none { it.fields.name == "Milk" }) { state = awaitItem() }

            // Drain until List B is visible AND the VM has switched to it.
            userA.viewModel.addList("List B")
            while (state.lists.none { it.name == "List B" } || state.currentListName != "List B") { state = awaitItem() }

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Eggs", quantity = 1.0, checked = false))
            while (state.uncheckedItems.none { it.fields.name == "Eggs" }) { state = awaitItem() }

            assertEquals(1, state.uncheckedItems.size)
            assertEquals("Eggs", state.uncheckedItems.single().fields.name)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
