package org.righteffort.ourgrocerylist

import android.app.Application
import app.cash.turbine.turbineScope
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
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
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val TEST_PASSWORD = "test-password"

private data class GoogleServicesConfig(
    val projectNumber: String,
    val projectId: String,
    val storageBucket: String,
    val appId: String,
    val apiKey: String,
)

private val googleServices: GoogleServicesConfig by lazy {
    val root = JSONObject(File("google-services.json").readText())
    val info = root.getJSONObject("project_info")
    val client = root.getJSONArray("client").getJSONObject(0)
    GoogleServicesConfig(
        projectNumber = info.getString("project_number"),
        projectId = info.getString("project_id"),
        storageBucket = info.getString("storage_bucket"),
        appId = client.getJSONObject("client_info").getString("mobilesdk_app_id"),
        apiKey = client.getJSONArray("api_key").getJSONObject(0).getString("current_key"),
    )
}

class TestApp : Application()

// @OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class SharedListIntegrationTest {

    private class TestUser(val email: String, val listName: String, val appName: String) {
        lateinit var app: FirebaseApp
        lateinit var user: User
        lateinit var viewModel: ShoppingViewModel
    }

    private val userA =
        TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")
    private val userB =
        TestUser(email = "test2@test.invalid", listName = "User B List", appName = "userB")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        val defaultOptions = FirebaseOptions.Builder()
            .setProjectId(googleServices.projectId) //  PROJECT_ID)
            .setApplicationId(googleServices.appId) // "1:000000000000:android:0000000000000000")
            .setApiKey(googleServices.apiKey)
            .build()
        setupUser(userA, defaultOptions)
        setupUser(userB, defaultOptions)
        println("DEBUG setup done")

    }

    @After
    fun tearDown() {
        // TODO are any of these async/suspend ?
        println("DEBUG starting tearDown")
        userA.app.delete()
        userB.app.delete()
        // clearEmulatorData()
        println("DEBUG done with tearDown")
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
            println("fancy test A got ${userA.listName} lists=${stateA.lists}")

            var stateB = turbineB.awaitItem()
            while (stateB.lists.none { it.name == userB.listName }) {
                stateB = turbineB.awaitItem()
            }
            println("fancy test B got ${userB.listName} lists=${stateB.lists}")

            val listId = stateA.lists.first { it.isOwner }.id
            userA.viewModel.selectList(listId)
            userA.viewModel.shareList(userB.email)

//            withTimeout(15.seconds) {
//                userA.viewModel.errors.first { it == "Editor added" }  // TODO it's hard to imagine a worse way to check the state
//            }
//            println("fancy test saw editor added message, what's next?")

            // Drain until User B sees the shared list as an editor.
            stateB = turbineB.awaitItem()
            while (stateB.lists.none { !it.isOwner }) {
                stateB = turbineB.awaitItem()
            }
            println("fancy test B sees shared list or its own list or something")
            userB.viewModel.selectList(listId)

            // Drain until User B is observing the shared list with no items.
            while (stateB.lists.none { it.id == listId } || stateB.uncheckedItems.isNotEmpty()) {
                stateB = turbineB.awaitItem()
            }
            println("fancy test B sees shared list (empty)")


            // User A adds an item via the ViewModel (same path as the real UI).
            userA.viewModel.openAddDialog("")
            userA.viewModel.dialogState.value!!.onSave(
                ItemFields(name = "Milk", quantity = 2.0, checked = false)
            )

            // User B observes the item appear as unchecked.
            while (stateB.uncheckedItems.isEmpty()) {
                stateB = turbineB.awaitItem()
            }
            println("fancy test user B observes the new item")
            assertEquals(1, stateB.uncheckedItems.size)
            assertEquals("Milk", stateB.uncheckedItems.single().fields.name)

            // User A checks the item.
            val addedItem = userA.viewModel.uiState.value.uncheckedItems.single()
            userA.viewModel.checkItem(addedItem)

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
        println("THROMER test exiting")
    }

    // --- Helpers ---

    private suspend fun setupUser(testUser: TestUser, options: FirebaseOptions) {
        testUser.app = FirebaseApp.initializeApp(
            RuntimeEnvironment.getApplication(),
            options,
            testUser.appName
        )
        setUpFirebaseEmulators(testUser.app)
        val firestore = FirebaseFirestore.getInstance(testUser.app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        val functions = FirebaseFunctions.getInstance(testUser.app, "us-west1")
        testUser.user = signIn(testUser.app, testUser.email)
        val userFlow = MutableStateFlow<User?>(testUser.user)
        testUser.viewModel = ShoppingViewModel(
            currentUserFlow = userFlow,
            listRepository = FirestoreListRepository(firestore, userFlow, functions),
            repositoryFactory = { listId ->
                FirestoreShoppingRepository(
                    firestore,
                    listId,
                    clientId = UUID.randomUUID().toString()
                )
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
        delete("http://127.0.0.1:8080/emulator/v1/projects/${googleServices.projectId}/databases/(default)/documents")
        delete("http://127.0.0.1:9099/emulator/v1/projects/${googleServices.projectId}/accounts")
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
