package com.xaeryx.translatorrep.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Typography tokens per UX spec §"Typography System (Identical Across Both
 * Themes)". System default font (Roboto on Android, SF Pro Text on iOS).
 *
 * Cross-platform parity contract: iOS exposes identical sizes via
 * `TranslatorRepStyle.swift` — Caption-primary 22pt iOS ↔ 20sp Android,
 * Caption-peripheral 18pt iOS ↔ 16sp Android, etc.
 */
val TranslatorRepTypography = Typography(
    // displayLarge — Pairing Code, large numerals (UX-DR14: 44sp tracking ~10-12pt)
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = (44 * 1.3).sp,
        letterSpacing = 4.sp,        // generous tracking for 6-digit codes
    ),

    // titleLarge — Screen headers (24sp Medium per UX Title role)
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = (24 * 1.3).sp,
    ),

    // bodyLarge — Caption-primary (partner's target Caption text, 20sp Regular,
    // 1.4× line height for Indonesian compound words — UX validated)
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = (20 * 1.4).sp,
    ),

    // bodyMedium — Caption-peripheral (speaker's own row, 16sp Regular)
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (16 * 1.4).sp,
    ),

    // bodySmall — Caption-source (source text alongside target, 14sp Regular)
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = (14 * 1.4).sp,
    ),

    // labelLarge — Body for Settings / privacy summary (17pt iOS ↔ 16sp Android)
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (16 * 1.3).sp,
    ),

    // labelSmall — Footnote (timestamps, status text, 12sp Regular)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = (12 * 1.3).sp,
    ),
)
