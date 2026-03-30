package org.righteffort.ourgrocerylist.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ItemFieldsFingerprintTest {

    private val base = ItemFields(name = "Apples", quantity = 2.0, checked = false)

    @Test
    fun `same fields produce the same fingerprint`() {
        assertEquals(base.fingerprint, ItemFields(name = "Apples", quantity = 2.0, checked = false).fingerprint)
    }

    @Test
    fun `fingerprint is a 64-character lowercase hex string`() {
        assertTrue(base.fingerprint.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `changing name changes fingerprint`() {
        assertNotEquals(base.fingerprint, base.copy(name = "Oranges").fingerprint)
    }

    @Test
    fun `changing quantity changes fingerprint`() {
        assertNotEquals(base.fingerprint, base.copy(quantity = 3.0).fingerprint)
    }

    @Test
    fun `changing checked changes fingerprint`() {
        assertNotEquals(base.fingerprint, base.copy(checked = true).fingerprint)
    }
}
