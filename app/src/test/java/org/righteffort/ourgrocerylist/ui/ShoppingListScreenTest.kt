package org.righteffort.ourgrocerylist.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ShoppingListScreenTest {

    // --- suggestedListName ---

    @Test
    fun `strips extension from simple filename`() {
        assertEquals("Groceries", suggestedListName("Groceries.csv"))
    }

    @Test
    fun `strips extension case-insensitively`() {
        assertEquals("Xyz", suggestedListName("Xyz.CSV"))
    }

    @Test
    fun `strips only the last extension`() {
        assertEquals("archive.tar", suggestedListName("archive.tar.gz"))
    }

    @Test
    fun `returns filename unchanged when no extension`() {
        assertEquals("MyList", suggestedListName("MyList"))
    }

    @Test
    fun `returns empty string for empty filename`() {
        assertEquals("", suggestedListName(""))
    }
}
