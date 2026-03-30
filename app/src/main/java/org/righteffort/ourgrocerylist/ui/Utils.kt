package org.righteffort.ourgrocerylist.ui

fun formatQuantityNumber(quantity: Double): String =
    "%.3f".format(quantity).trimEnd('0').trimEnd('.')
