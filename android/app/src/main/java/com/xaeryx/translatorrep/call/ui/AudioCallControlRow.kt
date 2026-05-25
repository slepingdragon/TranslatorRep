package com.xaeryx.translatorrep.call.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.call.AudioRoute
import com.xaeryx.translatorrep.ui.theme.BorderGlass
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.SurfaceGlass
import com.xaeryx.translatorrep.ui.theme.TextPrimary

/**
 * In-call control row (Story 2.7, UX-DR18): mute, audio-routing toggle, and end-Call — three
 * circular controls sitting at the bottom edge of the upper 40%. Mute + end are wired; the
 * routing toggle's behaviour (earpiece/speaker/Bluetooth) lands in Story 2.9, so [onToggleRoute]
 * is currently a caller-supplied no-op but the control is present so the layout is final.
 */
@Composable
fun AudioCallControlRow(
    muted: Boolean,
    route: AudioRoute,
    onToggleMute: () -> Unit,
    onToggleRoute: () -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleControl(
            icon = if (muted) CallIcons.MicOff else CallIcons.Mic,
            description = if (muted) "Unmute microphone" else "Mute microphone",
            onClick = onToggleMute,
            filled = muted,
        )
        CircleControl(
            icon = CallIcons.VolumeUp,
            description = "Audio output: ${routeLabel(route)}",
            onClick = onToggleRoute,
            // Highlighted whenever output is routed away from the private earpiece (louder route).
            filled = route != AudioRoute.EARPIECE,
        )
        CircleControl(
            icon = CallIcons.CallEnd,
            description = "End call",
            onClick = onEnd,
            filled = false,
            destructive = true,
        )
    }
}

/**
 * One circular control. Default = monochrome glass with a text-primary glyph. [filled] inverts it
 * to a solid surface (the active/toggled look, e.g. muted). [destructive] paints it state-red with
 * a white glyph (end-Call).
 */
@Composable
private fun CircleControl(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    filled: Boolean,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val background = when {
        destructive -> StateRed
        filled -> TextPrimary
        else -> SurfaceGlass
    }
    val tint = when {
        destructive -> Color.White
        filled -> Color.Black
        else -> TextPrimary
    }
    Box(
        modifier = modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(background)
            .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

private fun routeLabel(route: AudioRoute): String = when (route) {
    AudioRoute.EARPIECE -> "Earpiece"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth"
    AudioRoute.WIRED_HEADSET -> "Wired headset"
}
