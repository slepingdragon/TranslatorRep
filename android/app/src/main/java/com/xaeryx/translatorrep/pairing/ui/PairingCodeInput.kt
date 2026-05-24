package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextTertiary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

/**
 * `PairingCodeInput` (UX-DR13) — partner's-code entry, foregrounded above the own-code
 * display in the D4b "partner-input-first" layout (UX-DR15).
 *
 * **Story 1.9 scope:** presentational only — it renders the empty state (placeholder digits +
 * a disabled "Pair" button) so the Paired-Empty home has its correct D4b layout (and the
 * own-code panel sits visually below it, per AC-7). The interactive behavior — numeric
 * keyboard, 1–5/6-digit states, the Firestore code lookup, the "Pair" transition, and the
 * inline errors ("Code not found" / "Code expired" / "That's your own code") — is **Story
 * 1.10**, which replaces this stub with the stateful component.
 */
@Composable
fun PairingCodeInput(modifier: Modifier = Modifier) {
    MonochromeGlassPanel(
        intensity = GlassIntensity.Regular,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Enter your partner's code",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
            )
            // Placeholder digits — the real numeric field lands in Story 1.10.
            Text(
                text = "—  —  —  —  —  —",
                style = MaterialTheme.typography.displayLarge,
                color = TextTertiary,
            )
            Button(
                onClick = {},
                enabled = false, // Enabled at exactly 6 digits in Story 1.10.
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Pair")
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairingCodeInputPreview() {
    TranslatorRepTheme {
        PairingCodeInput(modifier = Modifier.padding(16.dp))
    }
}
