package com.ridesniper.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RideSniperColorScheme = darkColorScheme(
    primary = AccentBlue,
    background = BgBlack,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    secondary = TextSecondary
)

@Composable
fun RideSniperTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RideSniperColorScheme,
        typography = RideSniperTypography,
        content = content
    )
}
