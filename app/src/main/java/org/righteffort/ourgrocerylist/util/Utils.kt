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

// For physical devices: `for p in 8080 9099 5001 ; do adb reverse tcp:$p tcp:$p ; done`, host="127.0.0.1"
// For AVD: host="10.0.2.2" (no adb reverse needed)
fun setUpFirebaseEmulators(host: String, app: FirebaseApp = FirebaseApp.getInstance()) {
    Firebase.auth(app).useEmulator(host, 9099)
    Timber.v("DEBUG setUpFirebaseEmulators: auth emulator configured at $host")
    Firebase.firestore(app).useEmulator(host, 8080)
    Timber.v("DEBUG setUpFirebaseEmulators: firestore emulator configured at $host")
    Firebase.functions(app, "us-west1").useEmulator(host, 5001)  // TODO hardcoded!
    Timber.v("DEBUG setUpFirebaseEmulators: functions emulator configured at $host")
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
