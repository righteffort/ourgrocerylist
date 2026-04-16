package org.righteffort.ourgrocerylist.util

import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions

import com.google.firebase.Firebase
import org.righteffort.ourgrocerylist.appFunctions

// Requires `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done`
fun setUpFirebaseEmulators() {
    // 127.0.0.1 because some devices fail to DNS-resolve "localhost"
    Firebase.auth.useEmulator("127.0.0.1", 9099)
    Firebase.firestore.useEmulator("127.0.0.1", 8080)
    Firebase.appFunctions.useEmulator("127.0.0.1", 5001)
    // TODO: check that someone is listening
}

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
