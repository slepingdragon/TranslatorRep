package com.xaeryx.translatorrep.call.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.CallSession
import com.xaeryx.translatorrep.call.callSession.RoomState
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * In-Call screen (Story 2.7) — the Audio 40/60 layout (UX-DR16). The upper 40% holds the
 * partner's name, the calm [AudioLevelIndicator] (UX-DR23), and the [AudioCallControlRow]
 * (mute / audio-route / end, UX-DR18) at its bottom edge. The lower 60% is intentionally empty —
 * reserved for the Epic-3 `CaptionStack` to drop in without restructuring.
 *
 * Stateful container: it owns the [CallSession.startCall] collection and the mute state, keeping
 * the layout pieces pure. Back-gesture is suppressed (UX §"Navigation Patterns") so you can't
 * swipe out of a live Call — ending is always an explicit tap. When the room reaches
 * [RoomState.ENDED] (peer/end/teardown) it calls [onEnd] to return to the Paired home.
 */
@Composable
fun InCallScreen(
    callSession: CallSession,
    partnerName: String,
    peerUid: String,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var roomState by remember { mutableStateOf<RoomState?>(null) }
    val muted by callSession.muted.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    BackHandler {
        // Suppress accidental back-exit during a Call — ending is the explicit End control.
    }

    LaunchedEffect(callSession, peerUid) {
        callSession.startCall(CallType.AUDIO, peerUid).collect { roomState = it }
    }
    LaunchedEffect(roomState) {
        if (roomState == RoomState.ENDED) onEnd()
    }

    val active = roomState == RoomState.ACTIVE

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        // ── Upper 40%: partner + presence + controls ──────────────────────────
        Box(
            modifier = Modifier
                .weight(UPPER_WEIGHT)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Text(
                    text = partnerName,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                )
                if (active) {
                    AudioLevelIndicator(active = !muted)
                } else {
                    Text(
                        text = connectingLabel(roomState),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }

            AudioCallControlRow(
                muted = muted,
                onToggleMute = { scope.launch { callSession.toggleMute() } },
                onToggleRoute = { /* earpiece/speaker/Bluetooth routing = Story 2.9 */ },
                onEnd = {
                    scope.launch { callSession.endCall() }
                    onEnd()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            )
        }

        // ── Lower 60%: reserved for the Epic-3 CaptionStack ───────────────────
        Box(
            modifier = Modifier
                .weight(LOWER_WEIGHT)
                .fillMaxWidth(),
        )
    }
}

/** Pre-[RoomState.ACTIVE] status copy — calm, never an "AI listening" / spinner line. */
private fun connectingLabel(state: RoomState?): String = when (state) {
    RoomState.WAITING_FOR_PARTNER -> "Waiting for partner…"
    RoomState.ENDED -> "Call ended"
    null, RoomState.ACTIVE -> "Connecting…"
}

private const val UPPER_WEIGHT = 0.4f
private const val LOWER_WEIGHT = 0.6f
