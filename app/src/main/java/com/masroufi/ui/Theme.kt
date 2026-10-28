package com.masroufi.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF1B5E20)

private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF4E6352))
private val DarkColors = darkColorScheme(primary = Color(0xFF8BD68E), secondary = Color(0xFFB5CCB7))

@Composable
fun MasroufiTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

val incomeColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFF81C784) else Color(0xFF2E7D32)

val expenseColor: Color
    @Composable get() = if (isSystemInDarkTheme()) Color(0xFFEF9A9A) else Color(0xFFC62828)

@Composable
fun amountColor(amount: Double): Color = when {
    amount > 0 -> incomeColor
    amount < 0 -> expenseColor
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
