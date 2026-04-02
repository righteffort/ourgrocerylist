package org.righteffort.ourgrocerylist.repository

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class FirebaseSharingRepositoryTest {

    private fun repo(
        ownerEmail: String?,
        calls: MutableList<Pair<String, String>> = mutableListOf(),
    ) = FirebaseSharingRepository(
        ownerEmail = { ownerEmail },
        callAddEditor = { listId, email -> calls.add(listId to email) },
    )

    @Test
    fun `addEditor with owner's email throws IllegalArgumentException`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo("owner@example.com").addEditor("list-1", "owner@example.com") }
        }
    }

    @Test
    fun `addEditor owner email check is case-insensitive`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repo("Owner@Example.COM").addEditor("list-1", "owner@example.com") }
        }
    }

    @Test
    fun `addEditor with different email calls through with correct listId and email`() {
        val calls = mutableListOf<Pair<String, String>>()
        runBlocking { repo("owner@example.com", calls).addEditor("list-1", "editor@example.com") }
        assertEquals(listOf("list-1" to "editor@example.com"), calls)
    }

    @Test
    fun `addEditor with null owner email does not throw`() {
        val calls = mutableListOf<Pair<String, String>>()
        runBlocking { repo(ownerEmail = null, calls).addEditor("list-1", "anyone@example.com") }
        assertEquals(listOf("list-1" to "anyone@example.com"), calls)
    }
}
