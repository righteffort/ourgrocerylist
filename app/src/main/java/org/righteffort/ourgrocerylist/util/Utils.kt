package org.righteffort.ourgrocerylist.util

import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.google.firebase.functions.functions
import timber.log.Timber
import java.text.NumberFormat
import java.text.ParsePosition
import java.util.Locale

// Requires `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done`
fun setUpFirebaseEmulators(app: FirebaseApp = FirebaseApp.getInstance()) {
    // 127.0.0.1 because some devices fail to DNS-resolve "localhost"
    Firebase.auth(app).useEmulator("127.0.0.1", 9099)
    Timber.v("DEBUG setUpFirebaseEmulators: auth emulator configured")
    Firebase.firestore(app).useEmulator("127.0.0.1", 8080)
    Timber.v("DEBUG setUpFirebaseEmulators: firestore emulator configured")
    Firebase.functions(app, "us-west1").useEmulator("127.0.0.1", 5001)  // TODO hardcoded!
    Timber.v("DEBUG setUpFirebaseEmulators: functions emulator configured")
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
