package com.xaeryx.translatorrep.call.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.xaeryx.translatorrep.call.AudioRoute
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.CallSession
import com.xaeryx.translatorrep.call.callSession.RoomState
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * In-Call screen (Story 2.7) — the Audio 40/60 layout (UX-DR16). The upper 40% holds the
 * partner's name, the calm [AudioLevelIndicator] (UX-DR23), and the [AudioCallControlRow]
 * (mute / audio-route / end, UX-DR18) at its bottom edge. The lower 60% is intentionally empty —
 * reserved for the Epic-3 `CaptionStack` to drop in without restructuring.
 *
 * Stateful container: it owns the [CallSession.startCall] collection, the mute + audio-route
 * state, and the end-call flow. Back-gesture is suppressed (UX §"Navigation Patterns") so you
 * can't swipe out of a live Call — ending is always an explicit tap. End is immediate for short
 * calls but asks for a two-tap confirm once the call has run past [LONG_CALL_THRESHOLD_MS]
 * (Story 2.8 / FR-9). When the room reaches [RoomState.ENDED] (peer hung up / teardown) it calls
 * [onEnd] to return to the Paired home.
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
    var activeSinceMs by remember { mutableStateOf<Long?>(null) }
    var showEndConfirm by remember { mutableStateOf(false) }
    val muted by callSession.muted.collectAsStateWithLifecycle()
    val route by callSession.audioRoute.collectAsStateWithLifecycle(initialValue = AudioRoute.EARPIECE)
    val scope = rememberCoroutineScope()

    BackHandler {
        // Suppress accidental back-exit during a Call — ending is the explicit End control.
    }

    LaunchedEffect(callSession, peerUid) {
        callSession.startCall(CallType.AUDIO, peerUid).collect { roomState = it }
    }
    LaunchedEffect(roomState) {
        if (roomState == RoomState.ACTIVE && activeSinceMs == null) {
            activeSinceMs = System.currentTimeMillis()
        }
        if (roomState == RoomState.ENDED) onEnd()
    }

    fun endNow() {
        scope.launch { callSession.endCall() }
        onEnd()
    }

    fun onEndTapped() {
        val startedAt = activeSinceMs
        val isLongCall = startedAt != null &&
            System.currentTimeMillis() - startedAt > LONG_CALL_THRESHOLD_MS
        if (isLongCall) showEndConfirm = true else endNow()
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
                route = route,
                onToggleMute = { scope.launch { callSession.toggleMute() } },
                onToggleRoute = { callSession.cycleAudioRoute() },
                onEnd = ::onEndTapped,
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

    if (showEndConfirm) {
        EndCallConfirmSheet(
            onConfirm = {
                showEndConfirm = false
                endNow()
            },
            onDismiss = { showEndConfirm = false },
        )
    }
}

/** Two-tap end confirm for calls past [LONG_CALL_THRESHOLD_MS] (Story 2.8 / UX-DR38). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndCallConfirmSheet(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "End this call?",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text("Cancel")
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = StateRed),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("End")
                }
            }
        }
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

/** Calls longer than this get a two-tap end confirm (FR-9): 5 minutes. */
private const val LONG_CALL_THRESHOLD_MS = 5 * 60 * 1000L
