package org.righteffort.ourgrocerylist.util

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVRecord
import org.righteffort.ourgrocerylist.model.ItemFields
import java.io.IOException
import java.io.StringReader

class CsvParseException(message: String) : Exception(message)

object CsvImporter {
    private val NAME_HEADERS = setOf("name", "description")
    private val QUANTITY_HEADERS = setOf("quantity")
    private val CHECKED_HEADERS = setOf("checked", "done")

    /**
     * Parses CSV content into a list of [ItemFields].
     * Throws [CsvParseException] with a human-readable message on any parse error.
     *
     * - Header row required. Column names are case-insensitive.
     * - "name" column required; "quantity" and "checked" are optional (defaults: 1.0, false).
     * - Blank quantity value → 1.0; blank checked value → false.
     * - Unknown columns are ignored. Blank rows are skipped.
     */
    fun parse(csvContent: String): List<ItemFields> {
        val allRecords = try {
            CSVFormat.RFC4180.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .get()
                .parse(StringReader(csvContent))
                .use { it.records }
        } catch (e: IOException) {
            throw CsvParseException("Could not read CSV: ${e.message}")
        }

        if (allRecords.isEmpty()) throw CsvParseException("CSV file has no header row.")

        val rawHeaders = allRecords[0].map { it.lowercase() }

        for ((fieldName, headers) in listOf(
            "name" to NAME_HEADERS,
            "quantity" to QUANTITY_HEADERS,
            "checked" to CHECKED_HEADERS,
        )) {
            if (rawHeaders.count { it in headers } > 1) {
                throw CsvParseException("Duplicate column: $fieldName")
            }
        }

        val nameIdx = rawHeaders.indexOfFirst { it in NAME_HEADERS }
            .takeIf { it >= 0 } ?: throw CsvParseException("Missing required column: name")
        val quantityIdx = rawHeaders.indexOfFirst { it in QUANTITY_HEADERS }.takeIf { it >= 0 }
        val checkedIdx = rawHeaders.indexOfFirst { it in CHECKED_HEADERS }.takeIf { it >= 0 }

        return allRecords.drop(1).mapIndexedNotNull { dataIndex, record ->
            val rowNumber = dataIndex + 2 // 1-based; header is row 1
            if (record.all { it.isBlank() }) null
            else parseRow(record, rowNumber, nameIdx, quantityIdx, checkedIdx)
        }
    }

    private fun parseRow(
        record: CSVRecord,
        rowNumber: Int,
        nameIdx: Int,
        quantityIdx: Int?,
        checkedIdx: Int?,
    ): ItemFields {
        if (nameIdx >= record.size()) {
            throw CsvParseException("Row $rowNumber: too few columns (name column missing)")
        }
        return ItemFields(
            name = record[nameIdx],
            quantity = resolveQuantity(record, quantityIdx, rowNumber),
            checked = resolveChecked(record, checkedIdx, rowNumber),
        )
    }

    private fun resolveQuantity(record: CSVRecord, colIdx: Int?, rowNumber: Int): Double {
        if (colIdx == null || colIdx >= record.size()) return 1.0
        val raw = record[colIdx]
        if (raw.isBlank()) return 1.0
        val d = raw.toDoubleOrNullLocale()
            ?: throw CsvParseException("Row $rowNumber: invalid quantity \"$raw\"")
        return if (d <= 0.0) 1.0 else d
    }

    private fun resolveChecked(record: CSVRecord, colIdx: Int?, rowNumber: Int): Boolean {
        if (colIdx == null || colIdx >= record.size()) return false
        val raw = record[colIdx]
        if (raw.isBlank()) return false
        return when (raw.lowercase()) {
            "true" -> true
            "false" -> false
            else -> throw CsvParseException(
                "Row $rowNumber: invalid checked value \"$raw\" (expected true or false)"
            )
        }
    }
}
