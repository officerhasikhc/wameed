package com.wameed.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = WameedGreen80,
    secondary = WameedGreenGrey80,
    tertiary = WameedAccent80
)

private val LightColorScheme = lightColorScheme(
    primary = WameedGreen40,
    secondary = WameedGreenGrey40,
    tertiary = WameedAccent40
)

@Composable
fun WameedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}