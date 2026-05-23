package com.xaeryx.translatorrep.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Story 1.1 AC: overrides Material 3 `darkColorScheme` with Theme A tokens.
 * Material You dynamic color is NEVER invoked — `dynamicDarkColorScheme` is
 * not called from anywhere in this app (UX-DR2).
 *
 * Cross-platform parity contract: iOS `TranslatorRepStyle.swift` produces
 * identical surface/text/state tokens.
 */
private val ThemeAMonochromeGlass = darkColorScheme(
    background = SurfaceBase,
    surface = SurfaceBase,
    surfaceVariant = SurfaceGlass,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    primary = TextPrimary,         // Primary text used for primary-action emphasis
    onPrimary = SurfaceBase,
    secondary = TextSecondary,
    onSecondary = SurfaceBase,
    tertiary = TextTertiary,
    onTertiary = SurfaceBase,
    error = StateAmber,            // State A amber per UX — translation-failure marker
    onError = SurfaceBase,
    outline = BorderGlass,
    outlineVariant = BorderGlass,
)

@Composable
fun TranslatorRepTheme(
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = ThemeAMonochromeGlass,
        typography = TranslatorRepTypography,
        content = content,
    )
}
