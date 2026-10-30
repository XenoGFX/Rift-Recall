package com.riftrecall.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFC8AA6E)
val Teal = Color(0xFF0AC8B9)
val Good = Color(0xFF4CD39A)
val Bad = Color(0xFFFF6B6B)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1405),
    secondary = Teal,
    onSecondary = Color(0xFF00201E),
    background = Color(0xFF010A13),
    onBackground = Color(0xFFE8ECF4),
    surface = Color(0xFF0A1428),
    onSurface = Color(0xFFE8ECF4),
    surfaceVariant = Color(0xFF1B2538),
    onSurfaceVariant = Color(0xFFB4BCCB),
    error = Bad,
)

@Composable
fun RiftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
