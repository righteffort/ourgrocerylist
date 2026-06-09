package org.righteffort.ourgrocerylist.util

import org.apache.commons.csv.CSVFormat
import org.righteffort.ourgrocerylist.model.ShoppingItem
import java.io.StringWriter

object CsvExporter {
    fun export(items: List<ShoppingItem>): String {
        val out = StringWriter()
        CSVFormat.RFC4180.builder()
            .setHeader("name", "quantity", "checked")
            .get()
            .print(out)
            .use { printer ->
                for (item in items) {
                    printer.printRecord(
                        item.fields.name,
                        formatQuantityNumber(item.fields.quantity),
                        item.fields.checked,
                    )
                }
            }
        return out.toString()
    }
}
