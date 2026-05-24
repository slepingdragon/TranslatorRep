package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.StateRed
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextTertiary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme

private const val PLACEHOLDER_DASHES = "—  —  —  —  —  —"

/**
 * `PairingCodeInput` (UX-DR13) — partner's-code entry, foregrounded above the own-code
 * display in the D4b "partner-input-first" layout (UX-DR15). Story 1.10 makes it interactive:
 * a single 6-digit numeric field (native number keypad), `text-primary` digits over a
 * placeholder of dashes, the "Pair" button enabled only at exactly 6 digits, and an inline
 * error below the field. State is hoisted to [PairingViewModel] via the host screen.
 *
 * @param canPair true when exactly 6 digits are entered and no submit is in flight.
 */
@Composable
fun PairingCodeInput(
    code: String,
    onCodeChange: (String) -> Unit,
    onPair: () -> Unit,
    canPair: Boolean,
    submitting: Boolean,
    errorMessage: String?,
    modifier: Modifier = Modifier,
) {
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

            BasicTextField(
                value = code,
                onValueChange = onCodeChange,
                enabled = !submitting,
                singleLine = true,
                textStyle = MaterialTheme.typography.displayLarge.copy(
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(TextPrimary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (canPair) onPair() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp) // UX-DR13 large hit target
                    .semantics { contentDescription = "Partner's 6-digit pairing code" },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (code.isEmpty()) {
                            Text(
                                text = PLACEHOLDER_DASHES,
                                style = MaterialTheme.typography.displayLarge,
                                color = TextTertiary,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = StateRed,
                )
            }

            Button(
                onClick = onPair,
                enabled = canPair,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                if (submitting) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 24.dp))
                } else {
                    Text("Pair")
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairingCodeInputEmptyPreview() {
    TranslatorRepTheme {
        PairingCodeInput(
            code = "",
            onCodeChange = {},
            onPair = {},
            canPair = false,
            submitting = false,
            errorMessage = null,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairingCodeInputErrorPreview() {
    TranslatorRepTheme {
        PairingCodeInput(
            code = "482917",
            onCodeChange = {},
            onPair = {},
            canPair = true,
            submitting = false,
            errorMessage = "Code not found",
            modifier = Modifier.padding(16.dp),
        )
    }
}
