package io.maxdoe.nook

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF1E4D4A),
        onPrimary = Color.White,
        background = Color(0xFFFBF8F2),
        onBackground = Color(0xFF1B2422),
        surface = Color.White,
        onSurface = Color(0xFF1B2422),
        surfaceVariant = Color(0xFFF0EADC),
        onSurfaceVariant = Color(0xFF5E6862),
        outline = Color(0xFFE6E0D2),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7FC4B8),
        onPrimary = Color(0xFF101917),
        background = Color(0xFF101917),
        onBackground = Color(0xFFE8EFEC),
        surface = Color(0xFF17211F),
        onSurface = Color(0xFFE8EFEC),
        onSurfaceVariant = Color(0xFF9BA8A3),
    )

private val NookTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    )

@Composable
fun NookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = NookTypography,
        content = content,
    )
}
