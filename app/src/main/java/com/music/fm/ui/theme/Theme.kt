package com.music.fm.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** 应用只提供浅色主题（Lumio 蓝田生玉配色）。 */
private val LightColorScheme = lightColorScheme(
    primary = LumioPrimary,
    onPrimary = Color.White,
    primaryContainer = LumioPrimaryLight900,
    onPrimaryContainer = LumioPrimaryDark100,

    secondary = LumioGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6F4EE),
    onSecondaryContainer = Color(0xFF3D9B77),

    tertiary = LumioGreen,

    background = LumioAppBackground,
    onBackground = LumioFont,
    surface = LumioAppBackground,
    onSurface = LumioFont,
    surfaceVariant = LumioKeyBackground,
    onSurfaceVariant = LumioFontLabel,

    surfaceContainerLowest = LumioContentBackground,
    surfaceContainerLow = LumioContentBackground,
    surfaceContainer = LumioContentBackground,
    surfaceContainerHigh = LumioKeyBackground,
    surfaceContainerHighest = Color(0xFFEEEEEE),

    outline = LumioBorder,
    outlineVariant = LumioDivider,
)

@Composable
fun FMusicTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content,
    )
}
