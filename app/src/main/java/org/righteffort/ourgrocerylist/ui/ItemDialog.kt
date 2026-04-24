package org.righteffort.ourgrocerylist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.righteffort.ourgrocerylist.model.ItemFields
import org.righteffort.ourgrocerylist.util.formatQuantityNumber
import org.righteffort.ourgrocerylist.util.isValidQuantityText
import org.righteffort.ourgrocerylist.util.toDoubleOrNullLocale

@Composable
fun ItemDialog(state: ItemDialogState) {
    var fields by remember { mutableStateOf(state.initialFields) }
    // TODO: Is there a tidier way to do this?
    var quantityText by remember { mutableStateOf(formatQuantityField(state.initialFields.quantity)) }
    val focusRequester = remember { FocusRequester() }

    AlertDialog(
        onDismissRequest = state.onCancel,
        title = { Text(state.title) },
        text = {
            Column {
                Text("Name")
                OutlinedTextField(
                    value = fields.name,
                    onValueChange = { fields = fields.copy(name = it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    singleLine = true,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Quantity")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = {
                            val newQuantity = fields.quantity - 1.0
                            fields = fields.copy(quantity = newQuantity)
                            quantityText = formatQuantityField(newQuantity)
                        },
                        enabled = fields.quantity > 1.0,
                    ) {
                        Text("\u2212")
                    }
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { text ->
                            quantityText = text
                            text.toDoubleOrNullLocale()?.let {
                                if (it > 0) fields = fields.copy(quantity = it)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    IconButton(
                        onClick = {
                            val newQuantity = fields.quantity + 1.0
                            fields = fields.copy(quantity = newQuantity)
                            quantityText = formatQuantityField(newQuantity)
                        },
                    ) {
                        Text("+")
                    }
                }

                if (state.showDelete) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(
                        onClick = { state.onDelete?.invoke() },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                    ) {
                        Text("\uD83D\uDDD1 Delete item")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { state.onSave(fields) },
                enabled = fields.name.isNotBlank() && isValidQuantityText(quantityText),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = state.onCancel) {
                Text("Cancel")
            }
        },
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

private fun formatQuantityField(quantity: Double): String = formatQuantityNumber(quantity)
