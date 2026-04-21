package org.righteffort.ourgrocerylist

import android.app.Application
import android.os.Looper
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.ContinuationInterceptor
import kotlin.time.Duration.Companion.seconds

private const val PROJECT_ID = "ourgrocerylist"
private const val TEST_PASSWORD = "test-password"

class TestApp2 : Application()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
class DifferentIntegrationTest {
    private val testDispatcher = StandardTestDispatcher()

    private class TestUser(val email: String, val listName: String, val appName: String) {
        lateinit var app: FirebaseApp
        lateinit var user: User
        lateinit var viewModel: ShoppingViewModel
    }

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")

    @Before
    fun setUp() {
        clearEmulatorData()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // TODO are any of these async/suspend ?
        println("DEBUG starting tearDown")
        userA.app.delete()
        userB.app.delete()
        //clearEmulatorData()
        println("DEBUG done with tearDown")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun someTest() = runBlocking(/*testDispatcher*/) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FirebaseFirestore.setLoggingEnabled(true)
        val defaultOptions = FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("test-api-key")
            .build()
        setupUser(userA, defaultOptions)

//        val ourDispatcher = coroutineContext[ContinuationInterceptor];
//        val ourScheduler1 = (ourDispatcher as TestDispatcher).scheduler;
//        val ourScheduler2 = coroutineContext[TestCoroutineScheduler];
//        println(
//            "DEBUG ourDispatcher: $ourDispatcher (${
//                "%x".format(
//                    System.identityHashCode(
//                        ourDispatcher
//                    )
//                )
//            })"
//        )
//        println(
//            "DEBUG ourScheduler1:  $ourScheduler1 (${
//                "%x".format(
//                    System.identityHashCode(
//                        ourScheduler1
//                    )
//                )
//            })"
//        )
//        println(
//            "DEBUG ourScheduler2:  $ourScheduler2 (${
//                "%x".format(
//                    System.identityHashCode(
//                        ourScheduler2
//                    )
//                )
//            })"
//        )
        //println("DEBUG ourDispatcher class: ${ourDispatcher!!::class.java.name}")
//        val vmDispatcher = userA.viewModel.viewModelScope.coroutineContext[ContinuationInterceptor]
//        println("vm dispatcher: $vmDispatcher (${"%x".format(System.identityHashCode(vmDispatcher))})")
//        println("vm dispatcher class: ${vmDispatcher!!::class.java.name}");
////        (vmDispatcher as? TestDispatcher)?.let {
////            println("vm scheduler: ${it.scheduler} (${"%x".format(System.identityHashCode(it.scheduler))})")
////        }
//        val vmD = userA.viewModel.viewModelScope.coroutineContext[ContinuationInterceptor]
//        println("vm dispatcher: $vmD")
//
//        val vmScheduler = (vmD as? TestDispatcher)?.scheduler
//        println("vm scheduler: $vmScheduler (${System.identityHashCode(vmScheduler)})")
//        // println("test scheduler: $testScheduler (${System.identityHashCode(testScheduler)})")
//        // println("same scheduler? ${vmScheduler === testScheduler}")
//        println("vmD is TestDispatcher: ${vmD is TestDispatcher}")
//
//
//
//
//        println("DEBUG someTest dispatcher main hash=${Dispatchers.Main.hashCode()} ${Dispatchers.Main}")
//        println("DEBUG Main Dispatcher?: ${Dispatchers.Main}")
        // println("DEBUG testScheduler=${this.testScheduler.hashCode()} ${this.testScheduler.toString()}");
        // I wish this block was in setup

        // Share User A's list with User B through the ViewModel (→ Functions emulator).
        userA.viewModel.uiState.test {
            println("DEBUG add userA list")
            userA.viewModel.addList(userA.listName)
            println("DEBUG draining")
            val timeoutMs = 5000L
            val startTime = System.currentTimeMillis()
            while (userA.viewModel.uiState.value.lists.size == 0) {
                if (System.currentTimeMillis() - startTime > timeoutMs) {
                    // throw AssertionError("Timed out waiting for Firestore emulator callback")
                    println("so sad, time is up")
                    break;
                }


                // A. Give the real background gRPC threads a slice of real time to execute
                Thread.sleep(50)
                shadowOf(Looper.getMainLooper()).idle()  // WTF is this?
                // advanceUntilIdle()
                runUiThreadTasksIncludingDelayedTasks()  // WTF is this?
                //println("DEBUG A lists ${userA.viewModel.uiState.value.lists}")

            }
            println("broke, trying Dispatchers.Default")
            withContext(Dispatchers.Default) {
                withTimeout(20000) {
                    userA.viewModel.uiState.first { it.lists.isNotEmpty() }
                    // println("huh")
                }
            }
            println("time is up")

            println("DEBUG drained")
            println("DEBUG A lists ${userA.viewModel.uiState.value.lists}")
            val listId =
                userA.viewModel.uiState.value.lists.first { it.isOwner }.id  // TODO can addList return the list and its id?
            println("DEBUG A got $listId")
//	    println("DEBUG A selectList")
//            userA.viewModel.selectList(listId)
//	    println("DEBUG A shareList")
//            userA.viewModel.shareList(userB.email)
//
//            println("DEBUG adding editor")
//            // withTimeout(15.seconds) {
//	          // 	userA.viewModel.errors.first { it == "Editor added" }  // what a horrifying way to verify!
//            // }
//
//            // Wait for User B to see the shared list as an editor, then select it.
//            withTimeout(15.seconds) {
//		while (userB.viewModel.uiState.value.lists.none { !it.isOwner }) delay(50)
//            }
//            userB.viewModel.selectList(listId)
//
//
//
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
        }
        println("THROMER test exiting")
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
//        println("THROMER setupUser waiting for the user to have a list?")
//        withTimeout(15.seconds) {
//            while (testUser.viewModel.uiState.value.lists.none { it.isOwner }) delay(50)
//        }
//        // TODO: And what if it failed?
//        println("THROMER setupUser selectList")
//        testUser.viewModel.selectList(testUser.viewModel.uiState.value.lists.first { it.isOwner }.id)
//        println("THROMER setupUser renameCurrentList")
//        testUser.viewModel.renameCurrentList(testUser.listName)
//        println("THROMER setupUser done")
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
