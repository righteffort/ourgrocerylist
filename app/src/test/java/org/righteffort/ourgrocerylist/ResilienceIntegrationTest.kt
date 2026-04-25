package org.righteffort.ourgrocerylist

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.firebase.FirebaseOptions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class ResilienceIntegrationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA =
        TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")
    private val userB =
        TestUser(email = "test2@test.invalid", listName = "User B List", appName = "userB")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        // DebugProbes.install()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // FirebaseFirestore.setLoggingEnabled(true)
        val defaultOptions = FirebaseOptions.Builder()
            .setProjectId(googleServices.projectId)
            .setApplicationId(googleServices.appId)
            .setApiKey(googleServices.apiKey)
            .build()
        setupUser(userA, defaultOptions)
        setupUser(userB, defaultOptions)
    }

    @After
    fun tearDown() {
        // DebugProbes.uninstall()
        Dispatchers.resetMain()
        userA.app.delete()
        userB.app.delete()
        clearEmulatorData()
    }

    // ---- Single-user tests ----

    @Test
    fun `items written offline appear after reconnect`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Groceries")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "Groceries" } || state.currentListName != "Groceries")

            userA.disableNetwork()

            for (name in listOf("Eggs", "Milk", "Bread")) {
                userA.viewModel.openAddDialog("")
                userA.viewModel.dialogState.value!!.onSave(
                    ItemFields(
                        name = name,
                        quantity = 1.0,
                        checked = false
                    )
                )
            }
            while (state.uncheckedItems.size < 3) {
                state = awaitItem()
            }

            assertTrue(state.uncheckedItems.any { it.fields.name == "Eggs" })
            assertTrue(state.uncheckedItems.any { it.fields.name == "Milk" })
            assertTrue(state.uncheckedItems.any { it.fields.name == "Bread" })

            userA.enableNetwork()
            userA.waitForPendingWrites()

            assertTrue(state.uncheckedItems.any { it.fields.name == "Eggs" })
            assertTrue(state.uncheckedItems.any { it.fields.name == "Milk" })
            assertTrue(state.uncheckedItems.any { it.fields.name == "Bread" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undo of offline write is consistent after reconnect`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Groceries")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "Groceries" } || state.currentListName != "Groceries")

            userA.disableNetwork()

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Eggs",
                    quantity = 1.0,
                    checked = false
                )
            )
            while (state.uncheckedItems.none { it.fields.name == "Eggs" }) {
                state = awaitItem()
            }

            userA.viewModel.undo()
            while (state.uncheckedItems.any { it.fields.name == "Eggs" }) {
                state = awaitItem()
            }

            userA.enableNetwork()
            userA.waitForPendingWrites()

            assertFalse(state.uncheckedItems.any { it.fields.name == "Eggs" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `check-then-undo offline leaves item unchecked after reconnect`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Groceries")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "Groceries" } || state.currentListName != "Groceries")

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Milk",
                    quantity = 1.0,
                    checked = false
                )
            )
            while (state.uncheckedItems.none { it.fields.name == "Milk" }) {
                state = awaitItem()
            }
            userA.waitForPendingWrites()

            userA.disableNetwork()

            userA.viewModel.checkItem(state.uncheckedItems.first { it.fields.name == "Milk" })
            while (state.checkedItems.none { it.fields.name == "Milk" }) {
                state = awaitItem()
            }

            userA.viewModel.undo()
            while (state.uncheckedItems.none { it.fields.name == "Milk" }) {
                state = awaitItem()
            }

            userA.enableNetwork()
            userA.waitForPendingWrites()

            assertTrue(state.uncheckedItems.any { it.fields.name == "Milk" })
            assertTrue(state.checkedItems.isEmpty())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `item listener recovers after network interruption`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("Groceries")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "Groceries" } || state.currentListName != "Groceries")

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Apples",
                    quantity = 1.0,
                    checked = false
                )
            )
            while (state.uncheckedItems.none { it.fields.name == "Apples" }) {
                state = awaitItem()
            }
            userA.waitForPendingWrites()

            userA.disableNetwork()
            userA.enableNetwork()

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Oranges",
                    quantity = 1.0,
                    checked = false
                )
            )

            while (state.uncheckedItems.none { it.fields.name == "Oranges" } ||
                state.uncheckedItems.none { it.fields.name == "Apples" }) {
                state = awaitItem()
            }

            assertTrue(state.uncheckedItems.any { it.fields.name == "Apples" })
            assertTrue(state.uncheckedItems.any { it.fields.name == "Oranges" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `list creation PERMISSION_DENIED race item add is responsive`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            // Do not wait for server confirmation — proceed as soon as the optimistic write
            // fires observeLists, which is what triggers the PERMISSION_DENIED race window.
            userA.viewModel.addList("New List")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "New List" })

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Instant",
                    quantity = 1.0,
                    checked = false
                )
            )

            while (state.uncheckedItems.none { it.fields.name == "Instant" }) {
                state = awaitItem()
            }

            userA.waitForPendingWrites()
            assertTrue(state.uncheckedItems.any { it.fields.name == "Instant" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `multiple rapid list creations items isolated`() = runTest {  // TODO: flaky
//        launch {
//            delay(2000)
//            DebugProbes.dumpCoroutines(System.err)
//        }
        userA.viewModel.uiState
            .onEach { Timber.v("userB uiState emission: $it") }
            .test(timeout = 10.seconds) {
                userA.viewModel.addList("L1")
                userA.viewModel.addList("L2")
                userA.viewModel.addList("L3")

                var state: UiState
                Timber.v("DEBUG rapid-lists: starting drain")
                do {
                    state = awaitItem()
                    Timber.v("DEBUG rapid-lists drain: currentListName=${state.currentListName} lists=${state.lists.map { it.name }}")
                } while (
                    state.lists.none { it.name == "L1" } ||
                    state.lists.none { it.name == "L2" } ||
                    state.lists.none { it.name == "L3" } ||
                    state.currentListName != "L3"
                )

                val l1Id = state.lists.first { it.name == "L1" }.id
                val l2Id = state.lists.first { it.name == "L2" }.id

                userA.viewModel.openAddDialog("")
                userA.viewModel.dialogState.value!!.onSave(
                    ItemFields(
                        name = "ItemC",
                        quantity = 1.0,
                        checked = false
                    )
                )
                while (state.uncheckedItems.none { it.fields.name == "ItemC" }) {
                    state = awaitItem()
                }
                assertEquals(1, state.uncheckedItems.size)

                Timber.v("DEBUG rapid-lists after add itemC to L3 state=$state")
                userA.viewModel.selectList(l2Id)
                while (state.currentListName != "L2") {
                    state = awaitItem()
                    Timber.v("DEBUG rapid-lists after switch to L2 state=$state")
                }  // TODO: flaky sometimes can time out here

                userA.viewModel.openAddDialog("")
                userA.viewModel.dialogState.value!!.onSave(
                    ItemFields(
                        name = "ItemB",
                        quantity = 1.0,
                        checked = false
                    )
                )
                while (state.uncheckedItems.none { it.fields.name == "ItemB" }) {
                    state = awaitItem()
                }
                assertEquals(1, state.uncheckedItems.size)

                userA.viewModel.selectList(l1Id)
                while (state.currentListName != "L1") {
                    state = awaitItem()
                }

                userA.viewModel.openAddDialog("")
                userA.viewModel.dialogState.value!!.onSave(
                    ItemFields(
                        name = "ItemA",
                        quantity = 1.0,
                        checked = false
                    )
                )
                while (state.uncheckedItems.none { it.fields.name == "ItemA" }) {
                    state = awaitItem()
                }
                assertEquals(1, state.uncheckedItems.size)
                assertEquals("ItemA", state.uncheckedItems.single().fields.name)

                cancelAndIgnoreRemainingEvents()
            }
    }

    @Test
    fun `listener for non-current list stays warm switch shows cached items`() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList("List A")
            var state: UiState
            do {
                state = awaitItem()
            } while (state.lists.none { it.name == "List A" } || state.currentListName != "List A")
            val listAId = state.lists.first { it.name == "List A" }.id

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Apples",
                    quantity = 1.0,
                    checked = false
                )
            )
            while (state.uncheckedItems.none { it.fields.name == "Apples" }) {
                state = awaitItem()
            }
            userA.waitForPendingWrites()

            userA.viewModel.addList("List B")
            while (state.lists.none { it.name == "List B" } || state.currentListName != "List B") {
                state = awaitItem()
            }

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
            userA.waitForPendingWrites()

            userA.disableNetwork()

            userA.viewModel.selectList(listAId)
            // Drain only for currentListName — "Apples" must already be present in the same
            // state, served from the warm cache kept by observeRemotelyModifiedItemIds.
            while (state.currentListName != "List A") {
                state = awaitItem()
            }

            assertTrue(state.uncheckedItems.any { it.fields.name == "Apples" })

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- Two-user tests ----

    @Test
    fun `remote write prunes undo stack owner cannot undo past it`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Eggs",
                    quantity = 1.0,
                    checked = false
                )
            )

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "Eggs" }) {
                stateB = turbineB.awaitItem()
            }

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Milk",
                    quantity = 1.0,
                    checked = false
                )
            )

            while (stateB.uncheckedItems.none { it.fields.name == "Milk" }) {
                stateB = turbineB.awaitItem()
            }

            // B edits "Eggs" → "Bread"; this remote write to that item ID prunes A's undo entry for it.
            val eggsItem = stateB.uncheckedItems.first { it.fields.name == "Eggs" }
            userB.viewModel.editItem(eggsItem, eggsItem.fields.copy(name = "Bread"))

            var stateA = turbineA.awaitItem()
            while (stateA.uncheckedItems.none { it.fields.name == "Bread" }) {
                stateA = turbineA.awaitItem()
            }

            // User A undoes — the Milk add (most recent valid entry) is reversed.
            userA.viewModel.undo()
            while (stateA.uncheckedItems.any { it.fields.name == "Milk" }) {
                stateA = turbineA.awaitItem()
            }
            assertFalse(stateA.uncheckedItems.any { it.fields.name == "Milk" })

            // User A undoes again — the Eggs add entry was pruned, so this is a no-op.
            userA.viewModel.undo()
            assertTrue(stateA.uncheckedItems.any { it.fields.name == "Bread" })
            assertFalse(stateA.undoAvailable)

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remote delete of item prunes undo no resurrection`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Ghost",
                    quantity = 1.0,
                    checked = false
                )
            )

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "Ghost" }) {
                stateB = turbineB.awaitItem()
            }

            userB.viewModel.deleteItem(stateB.uncheckedItems.first { it.fields.name == "Ghost" })

            var stateA = turbineA.awaitItem()
            while (stateA.uncheckedItems.any { it.fields.name == "Ghost" }) {
                stateA = turbineA.awaitItem()
            }

            // A's undo entry for the Ghost add was pruned by B's remote delete; undo is a no-op.
            userA.viewModel.undo()

            assertFalse(stateA.uncheckedItems.any { it.fields.name == "Ghost" })
            assertFalse(stateA.checkedItems.any { it.fields.name == "Ghost" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `writes by both users while one is offline converge`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.disableNetwork()

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Offline-A",
                    quantity = 1.0,
                    checked = false
                )
            )

            userB.viewModel.openAddDialog("")
            userB.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Online-B",
                    quantity = 1.0,
                    checked = false
                )
            )

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "Online-B" }) {
                stateB = turbineB.awaitItem()
            }

            userA.enableNetwork()
            userA.waitForPendingWrites()

            var stateA = turbineA.awaitItem()
            while (stateA.uncheckedItems.none { it.fields.name == "Offline-A" } ||
                stateA.uncheckedItems.none { it.fields.name == "Online-B" }) {
                stateA = turbineA.awaitItem()
            }
            assertTrue(stateA.uncheckedItems.any { it.fields.name == "Offline-A" })
            assertTrue(stateA.uncheckedItems.any { it.fields.name == "Online-B" })

            while (stateB.uncheckedItems.none { it.fields.name == "Offline-A" } ||
                stateB.uncheckedItems.none { it.fields.name == "Online-B" }) {
                stateB = turbineB.awaitItem()
            }
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Offline-A" })
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Online-B" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B observes A offline writes after A reconnects`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.disableNetwork()

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Secret",
                    quantity = 1.0,
                    checked = false
                )
            )
            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(
                    name = "Hidden",
                    quantity = 1.0,
                    checked = false
                )
            )

            // A is offline — B must not see these items yet.
            assertFalse(userB.viewModel.uiState.value.uncheckedItems.any { it.fields.name == "Secret" })
            assertFalse(userB.viewModel.uiState.value.uncheckedItems.any { it.fields.name == "Hidden" })

            userA.enableNetwork()
            userA.waitForPendingWrites()

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "Secret" } ||
                stateB.uncheckedItems.none { it.fields.name == "Hidden" }) {
                stateB = turbineB.awaitItem()
            }
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Secret" })
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Hidden" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }
}
