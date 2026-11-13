package com.nephrocare.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

val Blue = Color(0xFF1E6FB8)
val BlueDark = Color(0xFF154F86)
val Green = Color(0xFF2E9E7A)
val Teal = Color(0xFF2C8C99)
val Danger = Color(0xFFC62828)
val Warning = Color(0xFFE68A00)
val Ok = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6F7),
    onPrimaryContainer = Color(0xFF0B2E52),
    secondary = Green,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F0E6),
    onSecondaryContainer = Color(0xFF0B3A2B),
    tertiary = Teal,
    tertiaryContainer = Color(0xFFD2EEF2),
    onTertiaryContainer = Color(0xFF07363D),
    background = Color(0xFFF5F9FD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color.White,
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE7EEF5),
    onSurfaceVariant = Color(0xFF42474E),
    error = Danger,
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF5F1111),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CC7F2),
    onPrimary = Color(0xFF00325A),
    primaryContainer = Color(0xFF154F86),
    onPrimaryContainer = Color(0xFFD6E6F7),
    secondary = Color(0xFF8FD8BC),
    onSecondary = Color(0xFF003827),
    secondaryContainer = Color(0xFF1C5A45),
    onSecondaryContainer = Color(0xFFD3F0E6),
    tertiary = Color(0xFF86D1DC),
    tertiaryContainer = Color(0xFF1B5B64),
    onTertiaryContainer = Color(0xFFD2EEF2),
    background = Color(0xFF0F1720),
    onBackground = Color(0xFFE2E6EA),
    surface = Color(0xFF16202A),
    onSurface = Color(0xFFE2E6EA),
    surfaceVariant = Color(0xFF26323E),
    onSurfaceVariant = Color(0xFFC2C9D1),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF7A1C1C),
    onErrorContainer = Color(0xFFFFDAD6),
)

/** Slightly larger than the Material defaults: many users are older adults. */
private val AppTypography = Typography().let { t ->
    t.copy(
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
        bodyMedium = t.bodyMedium.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodySmall = t.bodySmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = t.labelLarge.copy(fontSize = 16.sp),
        labelMedium = t.labelMedium.copy(fontSize = 14.sp),
    )
}

@Composable
fun NephroTheme(theme: String, textScale: Float, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale)) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            content = content,
        )
    }
}

val QuranTextStyle = TextStyle(fontSize = 26.sp, lineHeight = 50.sp)
