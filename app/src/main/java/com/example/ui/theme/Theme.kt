package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElyzarethPrimary,
    secondary = ElyzarethSecondary,
    tertiary = ElyzarethTertiary,
    background = ElyzarethBackground,
    surface = ElyzarethSurface,
    onPrimary = Color(0xFF140033),
    onSecondary = Color(0xFF002A30),
    onTertiary = Color(0xFF350020),
    onBackground = ElyzarethOnSurface,
    onSurface = ElyzarethOnSurface,
    surfaceVariant = ElyzarethSurfaceVariant
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Music player defaults to dark immersive stage theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
