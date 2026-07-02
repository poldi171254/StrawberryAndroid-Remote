package com.zudiewiener.strawberryremote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BlueButton1, // Blue for dark theme
    secondary = GreenButton1, // Green for dark theme
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = BlueButton2, // Blue for light theme
    secondary = GreenButton2, // Green for light theme
    tertiary = Pink40
)

@Composable
fun StrawberryRemoteAndroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
