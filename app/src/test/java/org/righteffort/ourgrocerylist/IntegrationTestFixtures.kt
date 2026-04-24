package org.righteffort.ourgrocerylist

import android.app.Application
import app.cash.turbine.ReceiveTurbine
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.ui.ShoppingViewModel
import org.righteffort.ourgrocerylist.ui.UiState
import org.righteffort.ourgrocerylist.util.setUpFirebaseEmulators
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal class IntegrationTestApp : Application()

internal const val TEST_PASSWORD = "test-password"

internal data class GoogleServicesConfig(
    val projectNumber: String,
    val projectId: String,
    val storageBucket: String,
    val appId: String,
    val apiKey: String,
)

internal val googleServices: GoogleServicesConfig by lazy {
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

internal class TestUser(val email: String, val listName: String, val appName: String) {
    lateinit var app: FirebaseApp
    lateinit var firestore: FirebaseFirestore
    lateinit var user: User
    lateinit var viewModel: ShoppingViewModel
}

internal suspend fun setupUser(testUser: TestUser, options: FirebaseOptions) {
    // Optional: Verbose Firestore SDK logging.
    // ShadowLog.stream = System.out  // pipe logs to stdout
    // FirebaseFirestore.setLoggingEnabled(true)
    testUser.app = FirebaseApp.initializeApp(
        RuntimeEnvironment.getApplication(),
        options,
        testUser.appName,
    )
    setUpFirebaseEmulators("127.0.0.1", testUser.app)
    val firestore = Firebase.firestore(testUser.app)
    testUser.firestore = firestore
    firestore.firestoreSettings = firestoreSettings {
        setLocalCacheSettings(persistentCacheSettings {})
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
                clientId = UUID.randomUUID().toString(),
            )
        },
    )
}

internal suspend fun signIn(app: FirebaseApp, email: String): User {
    val auth = FirebaseAuth.getInstance(app)
    val result = auth.createUserWithEmailAndPassword(email, TEST_PASSWORD)
        .awaitInRobolectric()
    return User(uid = result.user!!.uid, email = email)
}

internal fun clearEmulatorData() {
    fun delete(url: String) {
        var lastStatus = -1
        repeat(4) { attempt ->
            if (attempt > 0) Thread.sleep(50L * (1 shl (attempt - 1)))
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "DELETE"
                conn.connect()
                lastStatus = conn.responseCode
                if (lastStatus == HttpURLConnection.HTTP_OK) return
                check(lastStatus in 400..599) { "DELETE $url failed with status $lastStatus" }
            } finally {
                conn.disconnect()
            }
        }
        error("DELETE $url failed with status $lastStatus after 4 attempts")
    }

    val projectId = googleServices.projectId
    delete("http://127.0.0.1:8080/emulator/v1/projects/$projectId/databases/(default)/documents")
    delete("http://127.0.0.1:9099/emulator/v1/projects/$projectId/accounts")
}

/**
 * Awaits a Firebase/Play Services Task in a Robolectric environment by
 * pumping the main looper until the background result is posted.
 */
internal suspend fun <T> Task<T>.awaitInRobolectric(): T {
    while (!this.isComplete) {
        ShadowLooper.idleMainLooper()
    }
    // The task is complete; this will unwrap the result or throw the exception immediately.
    return this.await()
}

internal suspend fun TestUser.disableNetwork() =
    firestore.disableNetwork().awaitInRobolectric()

internal suspend fun TestUser.enableNetwork() =
    firestore.enableNetwork().awaitInRobolectric()

internal suspend fun TestUser.waitForPendingWrites() =
    firestore.waitForPendingWrites().awaitInRobolectric()

/**
 * Sets up a shared list scenario: A and B each create their own list, A shares theirs with B,
 * and B selects the shared list. Returns the shared list's ID.
 *
 * Drains both turbines to reach a stable state (B on the shared list, no items).
 */
internal suspend fun setupSharedList(
    userA: TestUser,
    turbineA: ReceiveTurbine<UiState>,
    userB: TestUser,
    turbineB: ReceiveTurbine<UiState>,
): String {
    userA.viewModel.addList(userA.listName)
    userB.viewModel.addList(userB.listName)

    var stateA: UiState
    do { stateA = turbineA.awaitItem() }
    while (stateA.lists.none { it.name == userA.listName } || stateA.currentListName != userA.listName)

    var stateB: UiState
    do { stateB = turbineB.awaitItem() }
    while (stateB.lists.none { it.name == userB.listName } || stateB.currentListName != userB.listName)

    val listId = stateA.lists.first { it.name == userA.listName }.id
    userA.viewModel.selectList(listId)
    userA.viewModel.shareList(userB.email)

    while (stateB.lists.none { !it.isOwner }) { stateB = turbineB.awaitItem() }

    userB.viewModel.selectList(listId)
    while (
        stateB.lists.none { it.id == listId } ||
        stateB.currentListName != (stateB.lists.firstOrNull { it.id == listId }?.name ?: "") ||
        stateB.uncheckedItems.isNotEmpty()
    ) {
        stateB = turbineB.awaitItem()
    }

    return listId
}
