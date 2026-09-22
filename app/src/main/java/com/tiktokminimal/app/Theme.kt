
package com.tiktokminimal.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PureBlackScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF161616),
    onSurfaceVariant = Color(0xFFBDBDBD)
)

@Composable
fun TikTokMinimalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PureBlackScheme,
        content = content
    )
}
