package com.xaeryx.translatorrep.call.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * In-call control glyphs (Story 2.7) as [ImageVector]s built from the standard Material Symbols
 * 24dp path data. We inline the four paths we need rather than add the `material-icons-extended`
 * dependency — the project ships only `material-icons-core` (which lacks Mic/MicOff/CallEnd/
 * VolumeUp) and the extended set is a large method-count/APK cost for four icons. The fill color
 * here is irrelevant: callers render these through `Icon(..., tint = …)`, which recolors them.
 */
internal object CallIcons {
    val Mic: ImageVector by lazy { build("Mic", MIC) }
    val MicOff: ImageVector by lazy { build("MicOff", MIC_OFF) }
    val CallEnd: ImageVector by lazy { build("CallEnd", CALL_END) }
    val VolumeUp: ImageVector by lazy { build("VolumeUp", VOLUME_UP) }

    private fun build(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = VIEWPORT,
            viewportHeight = VIEWPORT,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.White),
        ).build()

    private const val VIEWPORT = 24f

    private const val MIC =
        "M12,14c1.66,0 2.99,-1.34 2.99,-3L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3z" +
            "M17.3,11c0,3 -2.54,5.1 -5.3,5.1S6.7,14 6.7,11L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
            "c3.28,-0.48 6,-3.3 6,-6.72h-1.7z"

    private const val MIC_OFF =
        "M19,11h-1.7c0,0.74 -0.16,1.43 -0.43,2.05l1.23,1.23c0.56,-0.98 0.9,-2.09 0.9,-3.28z" +
            "M14.98,11.17c0,-0.06 0.02,-0.11 0.02,-0.17L15,5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v0.18l5.98,5.99z" +
            "M4.27,3L3,4.27l6,6L9,11c0,1.66 1.34,3 3,3 0.22,0 0.44,-0.03 0.65,-0.08l1.66,1.66" +
            "c-0.71,0.33 -1.5,0.52 -2.31,0.52 -2.76,0 -5.3,-2.1 -5.3,-5.1L5,11c0,3.41 2.72,6.23 6,6.72L11,21h2v-3.28" +
            "c0.91,-0.13 1.77,-0.45 2.54,-0.9L19.73,21 21,19.73 4.27,3z"

    private const val CALL_END =
        "M12,9c-1.6,0 -3.15,0.25 -4.6,0.72v3.1c0,0.39 -0.23,0.74 -0.56,0.9 -0.98,0.49 -1.87,1.12 -2.66,1.85" +
            " -0.18,0.18 -0.43,0.28 -0.7,0.28 -0.28,0 -0.53,-0.11 -0.71,-0.29L0.29,13.08" +
            "c-0.18,-0.17 -0.29,-0.42 -0.29,-0.7 0,-0.28 0.11,-0.53 0.29,-0.71C3.34,8.78 7.46,7 12,7" +
            "s8.66,1.78 11.71,4.67c0.18,0.18 0.29,0.43 0.29,0.71 0,0.28 -0.11,0.53 -0.29,0.71l-2.48,2.48" +
            "c-0.18,0.18 -0.43,0.29 -0.71,0.29 -0.27,0 -0.52,-0.11 -0.7,-0.28 -0.79,-0.74 -1.69,-1.36 -2.67,-1.85" +
            " -0.33,-0.16 -0.56,-0.5 -0.56,-0.9v-3.1C15.15,9.25 13.6,9 12,9z"

    private const val VOLUME_UP =
        "M3,9v6h4l5,5L12,4L7,9L3,9z" +
            "M16.5,12c0,-1.77 -1.02,-3.29 -2.5,-4.03v8.05c1.48,-0.73 2.5,-2.25 2.5,-4.02z" +
            "M14,3.23v2.06c2.89,0.86 5,3.54 5,6.71s-2.11,5.85 -5,6.71v2.06c4.01,-0.91 7,-4.49 7,-8.77" +
            "s-2.99,-7.86 -7,-8.77z"
}
