package org.righteffort.ourgrocerylist.integration

import app.cash.turbine.test
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.User
import org.righteffort.ourgrocerylist.repository.FirebaseSharingRepository
import org.righteffort.ourgrocerylist.repository.FirestoreListRepository
import org.righteffort.ourgrocerylist.repository.FirestoreShoppingRepository
import org.righteffort.ourgrocerylist.repository.SharingRepository
import org.righteffort.ourgrocerylist.ui.ShoppingViewModel
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
class SharedListIntegrationTest {

    @Test
    fun `always fail`() = runBlocking {
        assertEquals(0, 1)
    }

}
