package org.righteffort.ourgrocerylist

import app.cash.turbine.turbineScope
import com.google.firebase.FirebaseOptions
import junit.framework.TestCase.assertEquals
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
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class FirstMultiUserIntegrationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA =
        TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")
    private val userB =
        TestUser(email = "test2@test.invalid", listName = "User B List", appName = "userB")

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
        Timber.v("DEBUG setup done")

    }

    @After
    fun tearDown() {
        // TODO are any of these async/suspend ?
        Timber.v("DEBUG starting tearDown")
        userA.app.delete()
        userB.app.delete()
        clearEmulatorData()
        Timber.v("DEBUG done with tearDown")
    }

    @Test
    fun `user A adds item and user B sees it`() = runTest {
        turbineScope {
            val turbineA = userA.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)
            val turbineB = userB.viewModel.uiState.testIn(backgroundScope, timeout = 15.seconds)

            userA.viewModel.addList(userA.listName)
            userB.viewModel.addList(userB.listName)

            var stateA = turbineA.awaitItem()
            while (stateA.lists.none { it.name == userA.listName }) {
                stateA = turbineA.awaitItem()
            }
            Timber.v("fancy test A got ${userA.listName} lists=${stateA.lists}")

            var stateB = turbineB.awaitItem()
            while (stateB.lists.none { it.name == userB.listName }) {
                stateB = turbineB.awaitItem()
            }
            Timber.v("fancy test B got ${userB.listName} lists=${stateB.lists}")

            val listId = stateA.lists.first { it.isOwner }.id
            userA.viewModel.selectList(listId)
            userA.viewModel.shareList(userB.email)

//            withTimeout(15.seconds) {
//                userA.viewModel.errors.first { it == "Editor added" }  // TODO it's hard to imagine a worse way to check the state
//            }
//            Timber.v("fancy test saw editor added message, what's next?")

            // Drain until User B sees the shared list as an editor.
            stateB = turbineB.awaitItem()
            while (stateB.lists.none { !it.isOwner }) {
                stateB = turbineB.awaitItem()
            }
            Timber.v("fancy test B sees shared list or its own list or something")
            userB.viewModel.selectList(listId)

            // Drain until User B is observing the shared list with no items.
            // was this, claude thought the other one would be better but something is screwed up
            // while (stateB.lists.none { it.id == listId } || stateB.uncheckedItems.isNotEmpty()) {
            while (stateB.isOwner || stateB.lists.none { it.id == listId }) {
                stateB = turbineB.awaitItem()
            }
            Timber.v("fancy test B sees shared list (empty)")

            // User A adds an item via the ViewModel (same path as the real UI).
            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(name = "Milk", quantity = 2.0, checked = false)
            )

            // User B observes the item appear as unchecked.
            while (stateB.uncheckedItems.isEmpty()) {
                stateB = turbineB.awaitItem()
            }
            Timber.v("fancy test user B observes the new item")
            assertEquals(1, stateB.uncheckedItems.size)
            assertEquals("Milk", stateB.uncheckedItems.single().fields.name)

            // User A checks the item. Use stateB's copy of the item — same ID and fields,
            // and avoids a race where User A's own snapshot hasn't arrived yet.
            userA.viewModel.checkItem(stateB.uncheckedItems.single())

            // User B observes the item move to checked.
            while (stateB.checkedItems.isEmpty()) {
                stateB = turbineB.awaitItem()
            }
            assertEquals(1, stateB.checkedItems.size)
            assertEquals(0, stateB.uncheckedItems.size)
            val item = stateB.checkedItems.single()
            assertEquals("Milk", item.fields.name)
            assertEquals(2.0, item.fields.quantity)
            assertTrue(item.fields.checked)

            turbineA.cancelAndIgnoreRemainingEvents()
            turbineB.cancelAndIgnoreRemainingEvents()
        }
        Timber.v("THROMER test exiting")
    }
}
