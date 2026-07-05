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

private val ZenyColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonBlue,
    tertiary = NeonTeal,
    background = CosmicBlack,
    surface = CosmicSurface,
    onBackground = TextWhite,
    onSurface = TextWhite,
    surfaceContainer = CosmicSurfaceElevated,
    onPrimary = CosmicBlack,
    onSecondary = TextWhite,
    onTertiary = CosmicBlack
)

private val LightColorScheme = lightColorScheme(
    primary = NeonBlue,
    secondary = NeonCyan,
    tertiary = NeonTeal,
    background = TextWhite,
    surface = ColorHexHelper.LightGray,
    onBackground = CosmicBlack,
    onSurface = CosmicBlack
)

object ColorHexHelper {
    val LightGray = androidx.compose.ui.graphics.Color(0xFFF0F2F5)
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force premium dark mode for a pristine glowing holographic vibe!
    dynamicColor: Boolean = false, // Use our high-fidelity custom brand palette
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        ZenyColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
