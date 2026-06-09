package org.righteffort.ourgrocerylist.client

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ClientIdRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun makeRepository(): ClientIdRepository {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher()),
            produceFile = { tempDir.resolve("test.preferences_pb") },
        )
        return ClientIdRepository(dataStore)
    }

    @Test
    fun `generates a non-null id on first call`() = runTest(UnconfinedTestDispatcher()) {
        val id = makeRepository().getOrCreate()
        assertNotNull(id)
    }

    @Test
    fun `returns the same id on repeated calls`() = runTest(UnconfinedTestDispatcher()) {
        val repo = makeRepository()
        assertEquals(repo.getOrCreate(), repo.getOrCreate())
    }
}
