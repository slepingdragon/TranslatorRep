package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TextTertiary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

/**
 * Paired home (Story 1.13 adds the Settings entry + Unpair). The real Paired home — partner
 * styling + the audio/video Call button — is Story 2.2; this remains a thin placeholder body
 * with a top-right Settings gear (UX-DR35) that opens the Settings sheet.
 *
 * [onUnpair] is fire-and-forget (the caller deletes `/pairs` + clears local state via
 * `PairingStatusRepository.unpair`, which flips app status to Unpaired → re-routes to the
 * Paired-Empty home).
 */
@Composable
fun PairedHomeScreen(
    partnerName: String,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MonochromeGlassPanel(intensity = GlassIntensity.Regular) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Paired with $partnerName",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                    )
                    Text(
                        text = "Calling arrives next.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                    )
                }
            }
        }

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

/**
 * Settings bottom sheet (UX-DR35). Contains the two-tap-confirm "Unpair from {partner}" action
 * plus a placeholder section for future Settings items (Epic 8).
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
                // First tap: arm the confirmation (two-tap unpair).
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
                    // Second tap: confirm.
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

            // Placeholder for Epic 8 settings (theme, display name, privacy, transcript history).
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
        PairedHomeScreen(partnerName = "Ayu", onUnpair = {})
    }
}
