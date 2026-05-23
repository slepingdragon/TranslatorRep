package com.xaeryx.translatorrep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.theme.BorderGlass
import com.xaeryx.translatorrep.ui.theme.SurfaceGlass

/**
 * `MonochromeGlassPanel` — UX-DR5 primitive.
 *
 * **Intensity values per UX spec §"Backdrop blur intensity":**
 *   - Thick: 24px blur radius — control rows (Theme A)
 *   - Regular: 16px blur radius — sheets & panels (Theme A)
 *   - Thin: 8px blur radius — Caption-stack subtle wash (Theme A)
 *
 * **CURRENT IMPLEMENTATION (Story 1.1 scaffold — flat translucent card):**
 *
 * This panel currently renders as a translucent surface with a 1px border.
 * The blur intensity values are CARRIED in the API (`GlassIntensity` enum)
 * but NOT yet visually applied — true backdrop blur is deferred to the
 * `haze` library integration in a Phase-5 polish story.
 *
 * **Why no RenderEffect here:** Compose's `Modifier.graphicsLayer { renderEffect = ... }`
 * blurs the panel's own content (titles, body text, controls — making them
 * illegible) rather than blurring what's BEHIND the panel. There is no
 * Compose-stdlib way to do true backdrop blur on Android. The two real
 * options are:
 *   (a) `dev.chrisbanes.haze:haze` library — production-quality, the right answer
 *   (b) `Window.setBackgroundBlurRadius()` API 31+ — window-scoped only, vendor
 *       blur-disable toggle (`Settings.Global.DEVELOPMENT_FORCE_WINDOW_BLUR_DISABLED`)
 *       affects whether it renders, and only works for dialog/popup windows.
 *
 * **Phase 5 polish migration plan:** add `dev.chrisbanes.haze:haze` dependency;
 * replace this panel's body with `Modifier.hazeChild(hazeState, ...)`; add the
 * `Modifier.haze(hazeState)` source modifier to the screen-level scaffold so
 * the Caption stack (and any Theme C custom-image-background content) renders
 * behind the panel with proper blur. Public API surface (`GlassIntensity` enum,
 * `MonochromeGlassPanel(intensity = ..., cornerRadius = ..., content = { ... })`)
 * is stable — no caller changes when haze lands.
 *
 * **Cross-platform parity caveat:** iOS `MonochromeGlassPanel.swift` uses
 * SwiftUI native `.thickMaterial` / `.regularMaterial` / `.ultraThinMaterial`
 * which DO produce true backdrop blur out-of-the-box. The two platforms will
 * look different on this primitive until Android haze lands — acknowledged
 * early-build asymmetry per Architecture Cross-Platform-Parity NFR.
 */
enum class GlassIntensity(val blurRadiusPx: Float) {
    Thick(24f),
    Regular(16f),
    Thin(8f),
}

@Composable
fun MonochromeGlassPanel(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") intensity: GlassIntensity = GlassIntensity.Regular,
    cornerRadius: Dp = 24.dp,
    content: @Composable () -> Unit,
) {
    // `intensity` is intentionally unused at the implementation level until
    // the haze library lands (see file-level KDoc). API is stable.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(SurfaceGlass)
            .border(
                width = 1.dp,
                color = BorderGlass,
                shape = RoundedCornerShape(cornerRadius),
            ),
    ) {
        content()
    }
}
