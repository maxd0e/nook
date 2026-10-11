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
        background = Color(0xFFFAF8F3),
        onBackground = Color(0xFF1E2927),
        surface = Color.White,
        onSurface = Color(0xFF1E2927),
        surfaceVariant = Color(0xFFF1EDE1),
        onSurfaceVariant = Color(0xFF626B67),
        outline = Color(0xFFE4DED0),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF9AD6CA),
        onPrimary = Color(0xFF10201D),
        background = Color(0xFF111B19),
        onBackground = Color(0xFFEAF0ED),
        surface = Color(0xFF1A2825),
        onSurface = Color(0xFFEAF0ED),
        surfaceVariant = Color(0xFF263632),
        onSurfaceVariant = Color(0xFFB3C0BA),
        outline = Color(0xFF3D4D48),
    )

private val NookTypography =
    Typography(
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
            ),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif),
    )

@Composable
fun NookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = NookTypography,
        content = content,
    )
}
