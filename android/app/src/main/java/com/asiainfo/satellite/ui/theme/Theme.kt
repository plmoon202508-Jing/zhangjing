package com.asiainfo.satellite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HoloColors = darkColorScheme(
    primary = Cyan,
    secondary = Violet,
    tertiary = Magenta,
    background = Bg0,
    surface = Bg1,
    onPrimary = Bg0,
    onBackground = TextMain,
    onSurface = TextMain
)

@Composable
fun AsiaInfoSatelliteTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HoloColors,
        typography = Typography(),
        content = content
    )
}
