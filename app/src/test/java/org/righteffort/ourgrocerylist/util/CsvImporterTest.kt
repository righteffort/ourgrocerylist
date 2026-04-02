package org.righteffort.ourgrocerylist.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CsvImporterTest {

    // --- Normal parsing ---

    @Test
    fun `parses all three columns in standard order`() {
        val csv = "name,quantity,checked\nApples,3.0,true\nBread,2.0,false"
        val items = CsvImporter.parse(csv)
        assertEquals(2, items.size)
        assertEquals("Apples", items[0].name)
        assertEquals(3.0, items[0].quantity)
        assertTrue(items[0].checked)
        assertEquals("Bread", items[1].name)
        assertEquals(2.0, items[1].quantity)
        assertFalse(items[1].checked)
    }

    @Test
    fun `parses columns in non-standard order`() {
        val csv = "checked,name,quantity\ntrue,Milk,4.0"
        val items = CsvImporter.parse(csv)
        assertEquals("Milk", items[0].name)
        assertEquals(4.0, items[0].quantity)
        assertTrue(items[0].checked)
    }

    @Test
    fun `column headers are case-insensitive`() {
        val csv = "NAME,QUANTITY,CHECKED\nEggs,12.0,FALSE"
        val items = CsvImporter.parse(csv)
        assertEquals("Eggs", items[0].name)
        assertEquals(12.0, items[0].quantity)
        assertFalse(items[0].checked)
    }

    @Test
    fun `mixed-case headers are recognised`() {
        val csv = "Name,Quantity,Checked\nButter,1.0,True"
        val items = CsvImporter.parse(csv)
        assertEquals("Butter", items[0].name)
        assertEquals(1.0, items[0].quantity)
        assertTrue(items[0].checked)
    }

    // --- Optional columns and defaults ---

    @Test
    fun `missing quantity column defaults to 1_0`() {
        val csv = "name,checked\nOranges,false"
        val items = CsvImporter.parse(csv)
        assertEquals(1.0, items[0].quantity)
    }

    @Test
    fun `missing checked column defaults to false`() {
        val csv = "name,quantity\nCheese,2.0"
        val items = CsvImporter.parse(csv)
        assertFalse(items[0].checked)
    }

    @Test
    fun `only name column present uses all defaults`() {
        val csv = "name\nYogurt"
        val items = CsvImporter.parse(csv)
        assertEquals("Yogurt", items[0].name)
        assertEquals(1.0, items[0].quantity)
        assertFalse(items[0].checked)
    }

    @Test
    fun `blank quantity field defaults to 1_0`() {
        val csv = "name,quantity,checked\nApples,,false"
        val items = CsvImporter.parse(csv)
        assertEquals(1.0, items[0].quantity)
    }

    @Test
    fun `blank checked field defaults to false`() {
        val csv = "name,quantity,checked\nApples,2.0,"
        val items = CsvImporter.parse(csv)
        assertFalse(items[0].checked)
    }

    // --- Unknown columns and blank rows ---

    @Test
    fun `unknown columns are ignored`() {
        val csv = "name,aisle,quantity,notes\nCarrots,produce,5.0,fresh"
        val items = CsvImporter.parse(csv)
        assertEquals("Carrots", items[0].name)
        assertEquals(5.0, items[0].quantity)
    }

    @Test
    fun `blank rows are skipped`() {
        val csv = "name,quantity\nApples,1.0\n\nBread,2.0\n"
        val items = CsvImporter.parse(csv)
        assertEquals(2, items.size)
        assertEquals("Apples", items[0].name)
        assertEquals("Bread", items[1].name)
    }

    @Test
    fun `all-whitespace rows are skipped`() {
        val csv = "name,quantity\nApples,1.0\n ,  \nBread,2.0"
        val items = CsvImporter.parse(csv)
        assertEquals(2, items.size)
    }

    @Test
    fun `empty data section returns empty list`() {
        val csv = "name,quantity,checked\n"
        val items = CsvImporter.parse(csv)
        assertTrue(items.isEmpty())
    }

    // --- Quoting and special characters ---

    @Test
    fun `quoted field with embedded comma is parsed as single value`() {
        val csv = "name,quantity\n\"Apples, Red Delicious\",3.0"
        val items = CsvImporter.parse(csv)
        assertEquals("Apples, Red Delicious", items[0].name)
        assertEquals(3.0, items[0].quantity)
    }

    @Test
    fun `double-quoted quote character in field`() {
        val csv = "name\n\"Bob's \"\"special\"\" sauce\""
        val items = CsvImporter.parse(csv)
        assertEquals("Bob's \"special\" sauce", items[0].name)
    }

    // --- Non-integer quantity ---

    @Test
    fun `non-integer quantity is preserved`() {
        val csv = "name,quantity\nOlive oil,0.5"
        val items = CsvImporter.parse(csv)
        assertEquals(0.5, items[0].quantity)
    }

    // --- Error cases ---

    @Test
    fun `missing name column throws with message`() {
        val csv = "quantity,checked\n1.0,false"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("name"), "Expected 'name' in: ${ex.message}")
    }

    @Test
    fun `duplicate name column throws with message`() {
        val csv = "name,name,quantity\nApples,Apples,1.0"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("name"), "Expected 'name' in: ${ex.message}")
    }

    @Test
    fun `duplicate quantity column throws with message`() {
        val csv = "name,quantity,quantity\nApples,1.0,2.0"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("quantity"), "Expected 'quantity' in: ${ex.message}")
    }

    @Test
    fun `duplicate checked column throws with message`() {
        val csv = "name,checked,checked\nApples,true,false"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("checked"), "Expected 'checked' in: ${ex.message}")
    }

    @Test
    fun `non-numeric quantity throws with row number`() {
        val csv = "name,quantity\nApples,lots"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("2"), "Expected row 2 in: ${ex.message}")
        assertTrue(ex.message!!.contains("lots"), "Expected offending value in: ${ex.message}")
    }

    @Test
    fun `zero quantity throws with row number`() {
        val csv = "name,quantity\nApples,0"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("2"), "Expected row 2 in: ${ex.message}")
    }

    @Test
    fun `negative quantity throws with row number`() {
        val csv = "name,quantity\nApples,-1.0"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("2"), "Expected row 2 in: ${ex.message}")
    }

    @Test
    fun `invalid checked value throws with row number`() {
        val csv = "name,checked\nApples,yes"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("2"), "Expected row 2 in: ${ex.message}")
        assertTrue(ex.message!!.contains("yes"), "Expected offending value in: ${ex.message}")
    }

    @Test
    fun `error row number accounts for header row`() {
        val csv = "name,quantity\nApples,1.0\nBread,bad"
        val ex = assertThrows<CsvParseException> { CsvImporter.parse(csv) }
        assertTrue(ex.message!!.contains("3"), "Expected row 3 in: ${ex.message}")
    }

    @Test
    fun `empty file throws`() {
        val ex = assertThrows<CsvParseException> { CsvImporter.parse("") }
        assertTrue(ex.message!!.isNotBlank())
    }
}
