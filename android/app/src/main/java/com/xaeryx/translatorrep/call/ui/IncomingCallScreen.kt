package com.xaeryx.translatorrep.call.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.theme.StateGreen
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary

/**
 * Incoming-call ring screen (Story 2.5/2.6, in-app MVP). Shown to the callee when the partner
 * places a call (the signal arrives via [com.xaeryx.translatorrep.call.signaling.CallSignalRepository]).
 * Calm and centered: "Incoming call" + partner name up top, Decline (red) / Accept (green) at the
 * bottom. Back-gesture is suppressed — the choice is explicit (decline), like the In-Call screen.
 */
@Composable
fun IncomingCallScreen(
    partnerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler {
        // Don't let a back-swipe silently drop the call — Decline is the explicit action.
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Text(
            text = "Incoming call",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = partnerName,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.weight(1f))

        Row(
            horizontalArrangement = Arrangement.spacedBy(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RingAction(
                icon = CallIcons.CallEnd,
                description = "Decline",
                background = StateRed,
                onClick = onDecline,
            )
            RingAction(
                icon = CallIcons.Call,
                description = "Accept",
                background = StateGreen,
                onClick = onAccept,
            )
        }

        Spacer(Modifier.height(72.dp))
    }
}

@Composable
private fun RingAction(
    icon: ImageVector,
    description: String,
    background: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(background, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = Color.White,
            modifier = Modifier.size(30.dp),
        )
    }
}
