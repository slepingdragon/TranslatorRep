package com.xaeryx.translatorrep.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Theme A — Dark (the v1 default; the only theme in baseline post-reconciliation
// until Theme C image-background lands in Epic 8).
//
// Token names mirror UX spec §"Color System — Two Themes > Theme A".
// Cross-platform parity contract: iOS `Color.swift` defines identical tokens
// under the same names (Theme A is bit-for-bit identical to the eye on both
// platforms per Cross-Platform Parity NFR).
// ---------------------------------------------------------------------------

/** App root background — iOS 26 system dark. */
val SurfaceBase = Color(0xFF0A0A0B)

/** MonochromeGlassPanel fill — 6% white over backdrop blur. */
val SurfaceGlass = Color(0x0FFFFFFF) // 0x0F = 15/255 ≈ 0.06 alpha

/** MonochromeGlassPanel edge — 8% white, 1px. */
val BorderGlass = Color(0x14FFFFFF) // 0x14 = 20/255 ≈ 0.08 alpha

/** High emphasis: titles, partner's target Caption. */
val TextPrimary = Color(0xF2FFFFFF) // 0xF2 = 242/255 ≈ 0.95 alpha

/** Medium: body, settings labels, partner's source Caption. */
val TextSecondary = Color(0xB3FFFFFF) // 0xB3 = 179/255 ≈ 0.70 alpha

/** Speaker's own row (FR-16 styling — peripheral, ~60% opacity). */
val TextPeripheral = Color(0x99FFFFFF) // 0x99 = 153/255 ≈ 0.60 alpha

/** Disabled / low-emphasis. */
val TextTertiary = Color(0x61FFFFFF) // 0x61 = 97/255 ≈ 0.38 alpha

/** TranslationUnavailableMarker, offline indicator. Muted DarkGoldenrod. */
val StateAmber = Color(0xFFB8860B)

/** Mic-active dot, recording-state pulse. Muted red. */
val StateRed = Color(0xFFB85450)

// ---------------------------------------------------------------------------
// Theme C — Custom image background (Epic 8). Token overrides below load
// when ThemePicker selects Image; the user-chosen image replaces SurfaceBase.
// surface-overlay = rgba(0,0,0,0.40-0.55 adaptive) — the BackgroundImageOverlay
// adaptive dark tint. Text + state colors are identical to Theme A
// (white-on-overlay reads as if on dark).
// ---------------------------------------------------------------------------

val SurfaceOverlayMin = Color(0x66000000) // 0.40 alpha — adaptive minimum (default)
val SurfaceOverlayMax = Color(0x8C000000) // 0.55 alpha — adaptive maximum for bright images

/** Theme C: thicker glass fill (8% white) — prevents image bleed-through. */
val SurfaceGlassThemeC = Color(0x14FFFFFF) // 0x14 = 20/255 ≈ 0.08 alpha
