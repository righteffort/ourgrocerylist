package org.righteffort.ourgrocerylist.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.model.ShoppingItem

class CsvExporterTest {

    private fun item(name: String, quantity: Double = 1.0, checked: Boolean = false) =
        ShoppingItem(id = "id", fields = ItemFields(name = name, quantity = quantity, checked = checked))

    // --- Structure ---

    @Test
    fun `empty list produces header row only`() {
        val csv = CsvExporter.export(emptyList())
        val lines = csv.trim().lines()
        assertEquals(1, lines.size)
        assertEquals("name,quantity,checked", lines[0])
    }

    @Test
    fun `output has one data row per item`() {
        val items = listOf(item("Apples"), item("Bread"), item("Milk"))
        val csv = CsvExporter.export(items)
        // header + 3 data rows (trim to ignore trailing newline)
        assertEquals(4, csv.trim().lines().size)
    }

    // --- Round-trips through CsvImporter ---

    @Test
    fun `single item round-trips`() {
        val items = listOf(item("Apples", 3.0, false))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals(1, parsed.size)
        assertEquals("Apples", parsed[0].name)
        assertEquals(3.0, parsed[0].quantity)
        assertFalse(parsed[0].checked)
    }

    @Test
    fun `checked item round-trips`() {
        val items = listOf(item("Milk", 2.0, true))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertTrue(parsed[0].checked)
    }

    @Test
    fun `multiple items all present`() {
        val items = listOf(item("Apples", 3.0, false), item("Bread", 1.0, true), item("Milk", 2.0, false))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals(3, parsed.size)
        assertEquals(setOf("Apples", "Bread", "Milk"), parsed.map { it.name }.toSet())
    }

    @Test
    fun `fractional quantity round-trips`() {
        val items = listOf(item("Olive oil", 0.5))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals(0.5, parsed[0].quantity)
    }

    // --- Special characters ---

    @Test
    fun `name with comma is quoted and round-trips`() {
        val items = listOf(item("Apples, Red Delicious"))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals("Apples, Red Delicious", parsed[0].name)
    }

    @Test
    fun `name with double-quote character is escaped and round-trips`() {
        val items = listOf(item("Bob's \"special\" sauce"))
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals("Bob's \"special\" sauce", parsed[0].name)
    }

    // --- Full round-trip ---

    @Test
    fun `full round-trip preserves all fields`() {
        val items = listOf(
            item("Apples", 3.0, false),
            item("Bread, Whole Wheat", 1.0, true),
            item("Olive oil", 0.5, false),
        )
        val parsed = CsvImporter.parse(CsvExporter.export(items))
        assertEquals(items.size, parsed.size)
        for ((original, roundTripped) in items.zip(parsed)) {
            assertEquals(original.fields.name, roundTripped.name)
            assertEquals(original.fields.quantity, roundTripped.quantity)
            assertEquals(original.fields.checked, roundTripped.checked)
        }
    }
}
