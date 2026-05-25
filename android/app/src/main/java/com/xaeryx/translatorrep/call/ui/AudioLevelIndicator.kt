package com.xaeryx.translatorrep.call.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TextTertiary

/**
 * Calm, **anti-evaluative** presence indicator for the In-Call screen (Story 2.7, UX-DR23): a
 * pulsing state-red "mic-active" dot + five monochrome bars that breathe gently while the mic is
 * live. It deliberately does NOT reflect real audio loudness — UX-DR23 forbids a VU-style meter
 * that could make someone self-conscious about how they speak. When [active] is false (muted) the
 * dot goes dim/static and the bars rest flat, so muting reads as a calm, obvious state change.
 */
@Composable
fun AudioLevelIndicator(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "audioLevel")

    Row(
        modifier = modifier.semantics {
            contentDescription = if (active) "Microphone active" else "Microphone muted"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Mic-active dot — pulses red while live, rests dim when muted.
        val dotAlpha by transition.animateFloat(
            initialValue = if (active) 0.45f else 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot",
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (active) StateRed.copy(alpha = dotAlpha) else TextTertiary,
                ),
        )

        // Five bars — staggered gentle breathing when active; flat + dim when muted. Each bar
        // animates UP TO a distinct resting peak (BAR_PEAKS), so if the OS has animations disabled
        // (animator_duration_scale = 0) the value snaps to that peak and we still show an
        // intentional, varied static silhouette instead of a frozen row of uniform max-height bars.
        repeat(BAR_COUNT) { index ->
            val fraction by transition.animateFloat(
                initialValue = MIN_FRACTION,
                targetValue = BAR_PEAKS[index],
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 720, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * STAGGER_MS),
                ),
                label = "bar$index",
            )
            val barHeight = if (active) MAX_BAR_HEIGHT * fraction else REST_BAR_HEIGHT
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (active) TextSecondary else TextTertiary.copy(alpha = 0.5f)),
            )
        }
    }
}

private const val BAR_COUNT = 5
private const val STAGGER_MS = 120
private const val MIN_FRACTION = 0.35f
private const val MAX_BAR_HEIGHT = 24f
private const val REST_BAR_HEIGHT = 6f

/**
 * Per-bar resting peak — a gentle center-weighted silhouette. Doubles as the static fallback when
 * the OS disables animations (the animation snaps to these targets), so the indicator reads as a
 * calm waveform rather than a broken row of equal-height bars.
 */
private val BAR_PEAKS = listOf(0.5f, 0.78f, 1f, 0.72f, 0.55f)
