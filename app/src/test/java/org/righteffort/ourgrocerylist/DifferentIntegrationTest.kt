package org.righteffort.ourgrocerylist

import android.app.Application
import app.cash.turbine.test
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.ui.ShoppingViewModel
import org.righteffort.ourgrocerylist.ui.UiState
import org.righteffort.ourgrocerylist.util.setUpFirebaseEmulators
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowLooper
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

private const val PROJECT_ID = "ourgrocerylist"
private const val TEST_PASSWORD = "test-password"

class TestApp2 : Application()

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp2::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class DifferentIntegrationTest {

    private class TestUser(val email: String, val listName: String, val appName: String) {
        lateinit var app: FirebaseApp
        lateinit var user: User
        lateinit var viewModel: ShoppingViewModel
    }

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        Dispatchers.setMain(UnconfinedTestDispatcher())
        FirebaseFirestore.setLoggingEnabled(true)
        val defaultOptions = FirebaseOptions.Builder()
            .setProjectId(PROJECT_ID)
            .setApplicationId("1:000000000000:android:0000000000000000")
            .setApiKey("test-api-key")
            .build()
        setupUser(userA, defaultOptions)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        // TODO are any of these async/suspend ?
        println("DEBUG starting tearDown")
        userA.app.delete()
        //clearEmulatorData()
        println("DEBUG done with tearDown")
    }

    @Test
    fun someTest() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList(userA.listName)
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == userA.listName })
            val listId = state.lists.first { it.isOwner }.id
            println("DEBUG drained")
            println("DEBUG A lists ${userA.viewModel.uiState.value.lists}")
            println("DEBUG A got $listId")
            cancelAndIgnoreRemainingEvents()
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
        val functions = FirebaseFunctions.getInstance(testUser.app, "us-west1")
        testUser.user = signIn(testUser.app, testUser.email)
        val userFlow = MutableStateFlow<User?>(testUser.user)
        testUser.viewModel = ShoppingViewModel(
            currentUserFlow = userFlow,
            listRepository = FirestoreListRepository(firestore, userFlow, functions),
            repositoryFactory = { listId ->
                FirestoreShoppingRepository(firestore, listId, clientId = UUID.randomUUID().toString())
            },
        )
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
