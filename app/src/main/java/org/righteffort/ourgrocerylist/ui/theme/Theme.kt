package org.righteffort.ourgrocerylist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AccentPurple = Color(0xFF8775B8)

private val LightColorScheme = lightColorScheme(
    primary = AccentPurple,
)

@Composable
fun OurGroceryListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
