package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

/**
 * `PairingCodeDisplay` (UX-DR14) — the passive own-code panel on the Paired-Empty home
 * (Story 1.9). Renders the 6-digit code in `Display` typography (`displayLarge` = 44sp with
 * generous tracking) above a "Share this with your partner" hint in Footnote (`labelSmall`).
 *
 * Interaction: **tap** to copy ([onCopy]); **long-press** to reveal a "Regenerate code"
 * menu ([onRegenerate]). Regeneration is rare — mostly if the code is believed leaked.
 *
 * The snackbar confirmation + actual clipboard write are owned by the host screen
 * ([PairedEmptyScreen]) so this component stays presentational and Context-free.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PairingCodeDisplay(
    code: String,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    MonochromeGlassPanel(
        intensity = GlassIntensity.Regular,
        modifier = modifier,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .combinedClickable(
                        onClick = onCopy,
                        onLongClick = { menuExpanded = true },
                        onLongClickLabel = "Regenerate code",
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
                    // A single accessible node: the digits read as one code, not six.
                    .semantics { contentDescription = "Your pairing code: $code. Tap to copy." },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = code,
                    style = MaterialTheme.typography.displayLarge,
                    color = TextPrimary,
                )
                Text(
                    text = "Share this with your partner",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Regenerate code") },
                    onClick = {
                        menuExpanded = false
                        onRegenerate()
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairingCodeDisplayPreview() {
    TranslatorRepTheme {
        PairingCodeDisplay(code = "482917", onCopy = {}, onRegenerate = {})
    }
}
