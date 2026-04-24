package org.righteffort.ourgrocerylist

import app.cash.turbine.turbineScope
import com.google.firebase.FirebaseOptions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.rules.TimberTestRule
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class MultiUserPropagationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")
    private val userB = TestUser(email = "test2@test.invalid", listName = "User B List", appName = "userB")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
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
        userA.app.delete()
        userB.app.delete()
        clearEmulatorData()
    }

    @Test
    fun `null test`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            setupSharedList(userA, turbineA, userB, turbineB)
            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editor sees pre-existing items`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            userA.viewModel.addList(userA.listName)
            userB.viewModel.addList(userB.listName)

            var stateA = turbineA.awaitItem()
            while (stateA.lists.none { it.name == userA.listName } || stateA.currentListName != userA.listName) {
                stateA = turbineA.awaitItem()
            }

            var stateB = turbineB.awaitItem()
            while (stateB.lists.none { it.name == userB.listName } || stateB.currentListName != userB.listName) {
                stateB = turbineB.awaitItem()
            }

            // User A adds 3 items before sharing.
            for (name in listOf("Apples", "Bread", "Milk")) {
                userA.viewModel.openAddDialog("")
                userA.viewModel.dialogState.value!!.onSave(ItemFields(name = name, quantity = 1.0, checked = false))
            }
            while (stateA.uncheckedItems.size < 3) { stateA = turbineA.awaitItem() }

            // Now A shares with B.
            val listId = stateA.lists.first { it.isOwner }.id
            userA.viewModel.selectList(listId)
            userA.viewModel.shareList(userB.email)

            while (stateB.lists.none { !it.isOwner }) { stateB = turbineB.awaitItem() }
            userB.viewModel.selectList(listId)
            while (stateB.lists.none { it.id == listId } || stateB.uncheckedItems.size < 3) {
                stateB = turbineB.awaitItem()
            }

            assertEquals(3, stateB.uncheckedItems.size)
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Apples" })
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Bread" })
            assertTrue(stateB.uncheckedItems.any { it.fields.name == "Milk" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `A renames shared list, B sees new name`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            val listId = setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.renameCurrentList("Renamed List")

            var stateB = turbineB.awaitItem()
            while (stateB.lists.none { it.id == listId && it.name == "Renamed List" }) {
                stateB = turbineB.awaitItem()
            }

            assertEquals("Renamed List", stateB.lists.first { it.id == listId }.name)
            assertEquals("Renamed List", stateB.currentListName)

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `A renames item, B sees updated name`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "OldName", quantity = 1.0, checked = false))

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "OldName" }) {
                stateB = turbineB.awaitItem()
            }

            val snapshot = stateB.uncheckedItems.first { it.fields.name == "OldName" }
            userA.viewModel.editItem(snapshot, snapshot.fields.copy(name = "NewName"))

            while (stateB.uncheckedItems.none { it.fields.name == "NewName" }) {
                stateB = turbineB.awaitItem()
            }

            assertTrue(stateB.uncheckedItems.any { it.fields.name == "NewName" })
            assertFalse(stateB.uncheckedItems.any { it.fields.name == "OldName" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B deletes item A added, A sees deletion`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Eggs", quantity = 1.0, checked = false))

            var stateB = turbineB.awaitItem()
            while (stateB.uncheckedItems.none { it.fields.name == "Eggs" }) {
                stateB = turbineB.awaitItem()
            }

            userB.viewModel.deleteItem(stateB.uncheckedItems.first { it.fields.name == "Eggs" })

            var stateA = turbineA.awaitItem()
            while (stateA.uncheckedItems.any { it.fields.name == "Eggs" }) {
                stateA = turbineA.awaitItem()
            }

            assertFalse(stateA.uncheckedItems.any { it.fields.name == "Eggs" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `B unchecks item A checked, A sees it back in uncheckedItems`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(ItemFields(name = "Butter", quantity = 1.0, checked = false))

            var stateA = turbineA.awaitItem()
            while (stateA.uncheckedItems.none { it.fields.name == "Butter" }) {
                stateA = turbineA.awaitItem()
            }

            userA.viewModel.checkItem(stateA.uncheckedItems.first { it.fields.name == "Butter" })

            var stateB = turbineB.awaitItem()
            while (stateB.checkedItems.none { it.fields.name == "Butter" }) {
                stateB = turbineB.awaitItem()
            }

            userB.viewModel.uncheckItem(stateB.checkedItems.first { it.fields.name == "Butter" })

            while (stateA.uncheckedItems.none { it.fields.name == "Butter" } || stateA.checkedItems.isNotEmpty()) {
                stateA = turbineA.awaitItem()
            }

            assertTrue(stateA.uncheckedItems.any { it.fields.name == "Butter" })
            assertTrue(stateA.checkedItems.isEmpty())

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `A's private list invisible to B`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            userA.viewModel.addList(userA.listName)
            userB.viewModel.addList(userB.listName)

            var stateA = turbineA.awaitItem()
            while (stateA.lists.none { it.name == userA.listName } || stateA.currentListName != userA.listName) {
                stateA = turbineA.awaitItem()
            }

            var stateB = turbineB.awaitItem()
            while (stateB.lists.none { it.name == userB.listName } || stateB.currentListName != userB.listName) {
                stateB = turbineB.awaitItem()
            }

            userA.viewModel.addList("Private")
            while (stateA.lists.none { it.name == "Private" } || stateA.currentListName != "Private") {
                stateA = turbineA.awaitItem()
            }

            assertFalse(stateB.lists.any { it.name == "Private" })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isOwner flag correct for both users`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            val listId = setupSharedList(userA, turbineA, userB, turbineB)

            assertTrue(userA.viewModel.uiState.value.isOwner)
            assertFalse(userB.viewModel.uiState.value.isOwner)
            assertEquals(listId, userA.viewModel.uiState.value.lists.first { it.isOwner }.id)
            assertEquals(listId, userB.viewModel.uiState.value.lists.first { !it.isOwner }.id)

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `A deletes shared list, B no longer sees it`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            val listId = setupSharedList(userA, turbineA, userB, turbineB)

            userA.viewModel.deleteCurrentList()

            var stateB = turbineB.awaitItem()
            while (stateB.lists.any { it.id == listId }) {
                stateB = turbineB.awaitItem()
            }

            assertFalse(stateB.lists.any { it.id == listId })

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
    }
}
