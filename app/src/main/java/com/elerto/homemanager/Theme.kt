package com.elerto.homemanager

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F6F78),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBFEAEE),
    onPrimaryContainer = Color(0xFF00363B),
    secondary = Color(0xFF9A5B00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDDB3),
    onSecondaryContainer = Color(0xFF3A2000),
    tertiary = Color(0xFF55627C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E3FF),
    onTertiaryContainer = Color(0xFF111C32),
    background = Color(0xFFF5FAFA),
    onBackground = Color(0xFF161D1E),
    surface = Color(0xFFF5FAFA),
    onSurface = Color(0xFF161D1E),
    surfaceVariant = Color(0xFFDAE4E5),
    onSurfaceVariant = Color(0xFF3F4949),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBFC8C9),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F5),
    surfaceContainer = Color(0xFFE9EFEF),
    surfaceContainerHigh = Color(0xFFE3E9E9),
    surfaceContainerHighest = Color(0xFFDEE4E4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF86D3DC),
    onPrimary = Color(0xFF00363B),
    primaryContainer = Color(0xFF004F56),
    onPrimaryContainer = Color(0xFFBFEAEE),
    secondary = Color(0xFFFFB95C),
    onSecondary = Color(0xFF472A00),
    secondaryContainer = Color(0xFF663E00),
    onSecondaryContainer = Color(0xFFFFDDB3),
    tertiary = Color(0xFFBDC7E3),
    onTertiary = Color(0xFF273148),
    tertiaryContainer = Color(0xFF3E4860),
    onTertiaryContainer = Color(0xFFD9E3FF),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E4),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E4),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBFC8C9),
    outline = Color(0xFF899393),
    outlineVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1E),
    surfaceContainer = Color(0xFF1A2122),
    surfaceContainerHigh = Color(0xFF242B2C),
    surfaceContainerHighest = Color(0xFF2F3637),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun HomeInventoryTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, shapes = AppShapes, content = content)
}
