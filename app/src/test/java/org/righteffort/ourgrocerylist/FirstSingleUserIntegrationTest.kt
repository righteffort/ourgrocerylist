package org.righteffort.ourgrocerylist

import app.cash.turbine.test
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
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
class FirstSingleUserIntegrationTest {
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
        // TODO are any of these async/suspend ?
        Timber.v("DEBUG starting tearDown")
        userA.app.delete()
        clearEmulatorData()
        Timber.v("DEBUG done with tearDown")
    }

    @Test
    fun someTest() = runTest {
        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.addList(userA.listName)
            var state: UiState
            do { state = awaitItem() } while (state.lists.none { it.name == userA.listName })
            val listId = state.lists.first { it.name == userA.listName }.id

            Timber.v("DEBUG A lists ${userA.viewModel.uiState.value.lists}")
            Timber.v("DEBUG A got $listId")
            cancelAndIgnoreRemainingEvents()
        }
        Timber.v("THROMER test exiting")
    }
}
