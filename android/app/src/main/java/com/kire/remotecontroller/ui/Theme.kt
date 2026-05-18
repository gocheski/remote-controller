package com.kire.remotecontroller.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color.White,
    secondary = Color(0xFF6A1B9A),
    onSecondary = Color.White,
    background = Color(0xFFF5F5F5),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    secondary = Color(0xFFCE93D8),
    onSecondary = Color(0xFF3E1A4D),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
)

/** YouTube brand red — same in light and dark for recognition. */
val YoutubeRed = Color(0xFFFF0000)
val OnYoutubeRed = Color.White

@Composable
fun RemoteTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
