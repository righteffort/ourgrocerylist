package org.righteffort.ourgrocerylist.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem

class FirestoreShoppingRepositoryTest {

    // --- shoppingItemFromFirestoreData ---

    @Test
    fun `parses a well-formed document`() {
        val item = shoppingItemFromFirestoreData(
            id = "abc",
            data = mapOf("fields" to mapOf("name" to "Apples", "quantity" to 2.0, "checked" to false)),
        )
        assertEquals(ShoppingItem(id = "abc", fields = ItemFields(name = "Apples", quantity = 2.0, checked = false)), item)
    }

    @Test
    fun `returns null when fields map is missing`() {
        assertNull(shoppingItemFromFirestoreData("abc", emptyMap()))
    }

    @Test
    fun `returns null when name is missing`() {
        assertNull(shoppingItemFromFirestoreData("abc", mapOf("fields" to mapOf("quantity" to 1.0))))
    }

    @Test
    fun `defaults quantity to 1 when absent`() {
        val item = shoppingItemFromFirestoreData(
            id = "abc",
            data = mapOf("fields" to mapOf("name" to "Bread")),
        )
        assertEquals(1.0, item?.fields?.quantity)
    }

    @Test
    fun `defaults checked to false when absent`() {
        val item = shoppingItemFromFirestoreData(
            id = "abc",
            data = mapOf("fields" to mapOf("name" to "Bread")),
        )
        assertEquals(false, item?.fields?.checked)
    }

    @Test
    fun `handles Firestore Long for whole-number quantity`() {
        // Firestore returns Long for numbers without a decimal point
        val item = shoppingItemFromFirestoreData(
            id = "abc",
            data = mapOf("fields" to mapOf("name" to "Bread", "quantity" to 3L)),
        )
        assertEquals(3.0, item?.fields?.quantity)
    }

    // --- itemToFirestoreData ---

    @Test
    fun `write data contains nested fields map`() {
        val fields = ItemFields(name = "Milk", quantity = 1.0, checked = true)
        val data = itemToFirestoreData(fields, clientId = "client-1")

        @Suppress("UNCHECKED_CAST")
        val nested = data["fields"] as Map<String, Any>
        assertEquals("Milk", nested["name"])
        assertEquals(1.0, nested["quantity"])
        assertEquals(true, nested["checked"])
    }

    @Test
    fun `write data contains fingerprint and clientId`() {
        val fields = ItemFields(name = "Milk", quantity = 1.0, checked = false)
        val data = itemToFirestoreData(fields, clientId = "client-1")
        assertEquals(fields.fingerprint, data["fingerprint"])
        assertEquals("client-1", data["clientId"])
    }
}
