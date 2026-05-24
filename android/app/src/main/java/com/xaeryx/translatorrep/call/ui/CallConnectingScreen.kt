package com.xaeryx.translatorrep.call.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.call.CallType
import com.xaeryx.translatorrep.call.callSession.CallSession
import com.xaeryx.translatorrep.call.callSession.RoomState
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Minimal in-call shell (Story 2.2). Tapping "Call" on the Paired home lands here: it starts
 * the Call via [CallSession.startCall] and reflects the [RoomState]. The real In-Call screen —
 * Audio 40/60 layout, captions, controls — is Story 2.7, and the actual LiveKit connection is
 * Story 2.3 (until then `startCall` emits nothing, so this stays "Connecting…").
 */
@Composable
fun CallConnectingScreen(
    callSession: CallSession,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var roomState by remember { mutableStateOf<RoomState?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(callSession) {
        callSession.startCall(CallType.AUDIO).collect { roomState = it }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            CircularProgressIndicator(color = TextPrimary)
            Text(
                text = when (roomState) {
                    RoomState.ACTIVE -> "Connected"
                    RoomState.WAITING_FOR_PARTNER -> "Waiting for partner…"
                    RoomState.ENDED -> "Call ended"
                    null -> "Connecting…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            TextButton(
                onClick = {
                    scope.launch { callSession.endCall() }
                    onEnd()
                },
            ) {
                Text("End")
            }
        }
    }
}
