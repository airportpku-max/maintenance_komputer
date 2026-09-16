package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = GreenLight,
    secondary = Green40,
    tertiary = AccentOrange,
    background = DarkText,
    surface = DarkText,
    onPrimary = PrimaryGreen,
    onBackground = WindowBackground,
    onSurface = WindowBackground
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = OnPrimaryGreen,
    primaryContainer = GreenContainer,
    onPrimaryContainer = OnGreenContainer,
    secondary = Green40,
    tertiary = AccentOrange,
    background = WindowBackground,
    surface = CardBackground,
    onBackground = DarkText,
    onSurface = DarkText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    // Disable dynamic color to maintain our custom branded green design consistency
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
