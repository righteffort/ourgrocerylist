package org.righteffort.ourgrocerylist

import app.cash.turbine.test
import com.google.firebase.FirebaseOptions
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
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = IntegrationTestApp::class)
@LooperMode(LooperMode.Mode.INSTRUMENTATION_TEST)
class CsvImportIntegrationTest {
    @get:Rule
    val timberRule = TimberTestRule()

    private val userA = TestUser(email = "test1@test.invalid", listName = "User A List", appName = "userA")

    @Before
    fun setUp() = runTest {
        clearEmulatorData()
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
    fun `csv import creates list with correct item count`() = runTest {
        val csvContent = CsvImportIntegrationTest::class.java.classLoader!!
            .getResourceAsStream("test_grocery_import.csv")!!
            .bufferedReader().readText()

        // Count expected rows independently: non-blank lines after the header.
        val expectedCount = csvContent.lines().drop(1).count { it.isNotBlank() }
	println("expectedCount ${expectedCount}")

        userA.viewModel.uiState.test(timeout = 15.seconds) {
            userA.viewModel.importListFromCsv("Imported", csvContent)
            var state: UiState
            do { state = awaitItem() }
            while (
                state.currentListName != "Imported" ||
                (state.uncheckedItems.size + state.checkedItems.size) != expectedCount
            )

            assertEquals(expectedCount, state.uncheckedItems.size + state.checkedItems.size)
            assertEquals("Imported", state.currentListName)
            assertTrue(state.isOwner)
            assertNull(userA.viewModel.importListDialogState.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
