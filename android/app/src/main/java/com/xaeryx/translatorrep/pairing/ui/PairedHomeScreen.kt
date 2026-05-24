package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TextTertiary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

/**
 * Paired home (Story 2.2). Partner display name centered at top; a single prominent "Call"
 * pill centered below it (UX-DR38 — filled glass pill, ≥48dp, full text-primary label). v1
 * places an Audio Call (the two-button `CallTypeSelector` arrives in Epic 6). A top-right
 * Settings gear (UX-DR35, Story 1.13) opens the Settings sheet with two-tap-confirm Unpair.
 *
 * The real In-Call screen is Story 2.7; the actual LiveKit connection is Story 2.3. [onCall]
 * starts the Call via `CallSession.startCall(.audio)` (wired in MainActivity).
 */
@Composable
fun PairedHomeScreen(
    partnerName: String,
    onCall: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Text(
            text = partnerName,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 48.dp),
        )

        CallButton(
            onCall = onCall,
            modifier = Modifier.align(Alignment.Center),
        )

        IconButton(
            onClick = { showSettings = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = TextSecondary,
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            partnerName = partnerName,
            onUnpair = {
                showSettings = false
                onUnpair()
            },
            onDismiss = { showSettings = false },
        )
    }
}

/** UX-DR38 primary action — a filled monochrome-glass pill. */
@Composable
private fun CallButton(onCall: () -> Unit, modifier: Modifier = Modifier) {
    MonochromeGlassPanel(
        intensity = GlassIntensity.Thick,
        cornerRadius = 100.dp,
        modifier = modifier
            .heightIn(min = 56.dp)
            .clickable(onClick = onCall)
            .semantics { contentDescription = "Call" },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 56.dp, vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Call",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )
        }
    }
}

/**
 * Settings bottom sheet (UX-DR35). Two-tap-confirm "Unpair from {partner}" + a placeholder
 * section for future Settings items (Epic 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(
    partnerName: String,
    onUnpair: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirming by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
            )

            if (!confirming) {
                TextButton(
                    onClick = { confirming = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Unpair from $partnerName",
                        color = StateRed,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text(
                    text = "This disconnects you both and can't be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = { confirming = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = onUnpair,
                        colors = ButtonDefaults.buttonColors(containerColor = StateRed),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Unpair")
                    }
                }
            }

            HorizontalDivider()

            Text(
                text = "More settings coming soon.",
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
            )
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairedHomePreview() {
    TranslatorRepTheme {
        PairedHomeScreen(partnerName = "Ayu", onCall = {}, onUnpair = {})
    }
}
