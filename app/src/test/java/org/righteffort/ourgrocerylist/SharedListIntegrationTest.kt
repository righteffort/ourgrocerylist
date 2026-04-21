package org.righteffort.ourgrocerylist

import android.app.Application
import app.cash.turbine.test
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.ui.ShoppingViewModel
import org.righteffort.ourgrocerylist.util.setUpFirebaseEmulators
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val PROJECT_ID = "ourgrocerylist"
private const val TEST_PASSWORD = "test-password"

class TestApp : Application()

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class SharedListIntegrationTest {

    private class TestUser(val email: String, val listName: String, val appName: String) {
        lateinit var app: FirebaseApp
        lateinit var user: User
        lateinit var viewModel: ShoppingViewModel
    }

//    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")
//    private val userB = TestUser(email = "test2@test.invalid", listName = "User B List", appName = "userB")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        if (false) {
//            val defaultOptions = FirebaseOptions.Builder()
//                .setProjectId(PROJECT_ID)
//                .setApplicationId("1:000000000000:android:0000000000000000")
//                .setApiKey("test-api-key")
//                .build()
//            setupUser(userA, defaultOptions)
//            setupUser(userB, defaultOptions)
//
//            // Share User A's list with User B through the ViewModel (→ Functions emulator).
//            println("DEBUG fetching user A's first list ... assumes there *is* one")
//            val listId = userA.viewModel.uiState.value.lists.first { it.isOwner }.id
//            userA.viewModel.selectList(listId)
//            userA.viewModel.shareList(userB.email)
//
//            println("DEBUG adding editor")
//            withTimeout(15.seconds) {
//                userA.viewModel.errors.first { it == "Editor added" }
//            }
//            // TODO: And what if the timeout expired?
//
//            // Wait for User B to see the shared list as an editor, then select it.
//            withTimeout(15.seconds) {
//                while (userB.viewModel.uiState.value.lists.none { !it.isOwner }) delay(50)
//            }
//            userB.viewModel.selectList(listId)
//            println("DEBUG setup done")
        }
    }

    @After
    fun tearDown() {
        // TODO are any of these async/suspend ?
        println("DEBUG starting tearDown")
//        userA.app.delete()
//        userB.app.delete()
        clearEmulatorData()
        println("DEBUG done with tearDown")
    }

    @Test
    fun notATest() {

    }
    // broken @Test
    fun `user A adds item and user B sees it`() = runBlocking {
//        val listId = userA.viewModel.uiState.value.lists.first { it.isOwner }.id
//
//        userB.viewModel.uiState.test(timeout = 15.seconds) {
//            // Drain until User B is observing the shared list with no items.
//            var state = awaitItem()
//            while (state.lists.none { it.id == listId } || state.uncheckedItems.isNotEmpty()) {
//                state = awaitItem()
//            }
//
//            // User A adds an item via the ViewModel (same path as the real UI).
//            userA.viewModel.openAddDialog("")
//            userA.viewModel.dialogState.value!!.onSave(
//                ItemFields(name = "Milk", quantity = 2.0, checked = false)
//            )
//
//            // User B observes the item appear as unchecked.
//            while (state.uncheckedItems.isEmpty()) { state = awaitItem() }
//            assertEquals(1, state.uncheckedItems.size)
//            assertEquals("Milk", state.uncheckedItems.single().fields.name)
//
//            // User A checks the item.
//            val addedItem = userA.viewModel.uiState.value.uncheckedItems.single()
//            userA.viewModel.checkItem(addedItem)
//
//            // User B observes the item move to checked.
//            while (state.checkedItems.isEmpty()) { state = awaitItem() }
//
//            assertEquals(1, state.checkedItems.size)
//            assertEquals(0, state.uncheckedItems.size)
//            val item = state.checkedItems.single()
//            assertEquals("Milk", item.fields.name)
//            assertEquals(2.0, item.fields.quantity)
//            assertTrue(item.fields.checked)
//
//            cancelAndIgnoreRemainingEvents()
//        }
//        println("THROMER test exiting")
    }

    // --- Helpers ---

    private suspend fun setupUser(testUser: TestUser, options: FirebaseOptions) {
        testUser.app = FirebaseApp.initializeApp(RuntimeEnvironment.getApplication(), options, testUser.appName)
        setUpFirebaseEmulators(testUser.app)
        val firestore = FirebaseFirestore.getInstance(testUser.app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        val functions = FirebaseFunctions.getInstance(testUser.app)
        testUser.user = signIn(testUser.app, testUser.email)
        val userFlow = MutableStateFlow<User?>(testUser.user)
        testUser.viewModel = ShoppingViewModel(
            currentUserFlow = userFlow,
            listRepository = FirestoreListRepository(firestore, userFlow, functions),
            repositoryFactory = { listId ->
                FirestoreShoppingRepository(firestore, listId, clientId = UUID.randomUUID().toString())
            },
        )
        println("THROMER setupUser waiting for the user to have a list?")
        withTimeout(15.seconds) {
            while (testUser.viewModel.uiState.value.lists.none { it.isOwner }) delay(50)
        }
        // TODO: And what if it failed?
        println("THROMER setupUser selectList")
        testUser.viewModel.selectList(testUser.viewModel.uiState.value.lists.first { it.isOwner }.id)
        println("THROMER setupUser renameCurrentList")
        testUser.viewModel.renameCurrentList(testUser.listName)
        println("THROMER setupUser done")
    }

    private suspend fun signIn(app: FirebaseApp, email: String): User {
        val auth = FirebaseAuth.getInstance(app)
        val result = auth.createUserWithEmailAndPassword(email, TEST_PASSWORD)
                .awaitInRobolectric()
        return User(uid = result.user!!.uid, email = email)
    }

    private fun clearEmulatorData() {
        fun delete(url: String) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "DELETE"
                conn.connect()
                val status = conn.responseCode
                check(status == HttpURLConnection.HTTP_OK) {
                    "DELETE $url failed with status $status"
                }
            } finally {
                conn.disconnect()
            }
        }
        delete("http://127.0.0.1:8080/emulator/v1/projects/$PROJECT_ID/databases/(default)/documents")
        delete("http://127.0.0.1:9099/emulator/v1/projects/$PROJECT_ID/accounts")
    }

    /**
     * Awaits a Firebase/Play Services Task in a Robolectric environment by
     * pumping the main looper until the background result is posted.
     */
    suspend fun <T> Task<T>.awaitInRobolectric(): T {
        while (!this.isComplete) {
            ShadowLooper.idleMainLooper()
        }
        // The task is complete; this will unwrap the result or throw the exception immediately.
        return this.await()
    }
}
