package org.righteffort.ourgrocerylist.util

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

fun formatQuantityNumber(quantity: Double): String =
    NumberFormat.getNumberInstance(Locale.getDefault()).apply {
        maximumFractionDigits = 3
        minimumFractionDigits = 0
        isGroupingUsed = false
    }.format(quantity)

fun isValidQuantityText(text: String): Boolean = text.toDoubleOrNullLocale()?.let { it > 0 } ?: false

fun String.toDoubleOrNullLocale(): Double? {
    val trimmed = trim()
    if (trimmed.isEmpty()) return null
    val nf = NumberFormat.getNumberInstance(Locale.getDefault())
    val pos = ParsePosition(0)
    val number = nf.parse(trimmed, pos) ?: return null
    return if (pos.index == trimmed.length) number.toDouble() else null
}
