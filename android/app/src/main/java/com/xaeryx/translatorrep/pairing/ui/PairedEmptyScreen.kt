package com.xaeryx.translatorrep.pairing.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xaeryx.translatorrep.pairing.PairingCodeUiState
import com.xaeryx.translatorrep.pairing.PairingViewModel
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme
import kotlinx.coroutines.launch

/**
 * Paired-Empty home (Story 1.9). D4b "partner-input-first" layout (UX-DR15): the partner-code
 * input is foregrounded at the top (the active task), with the user's own code displayed
 * below the divider (the passive artifact the partner needs).
 *
 * Stateful entry point: collects [PairingViewModel.codeState], owns the clipboard write +
 * "Code copied" snackbar (kept here so [PairingCodeDisplay] stays Context-free), and forwards
 * regenerate / retry to the ViewModel.
 */
@Composable
fun PairedEmptyScreen(
    viewModel: PairingViewModel,
    modifier: Modifier = Modifier,
) {
    val codeState by viewModel.codeState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    PairedEmptyContent(
        codeState = codeState,
        snackbarHostState = snackbarHostState,
        onCopy = { code ->
            clipboard.setText(AnnotatedString(code))
            scope.launch { snackbarHostState.showSnackbar("Code copied") }
        },
        onRegenerate = viewModel::regenerate,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

/**
 * Stateless content — previewable + decoupled from the ViewModel. [onCopy] receives the code
 * so the host can write the clipboard + show the snackbar.
 */
@Composable
private fun PairedEmptyContent(
    codeState: PairingCodeUiState,
    snackbarHostState: SnackbarHostState,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            // D4b: partner-input first (the active task).
            PairingCodeInput(modifier = Modifier.fillMaxWidth())

            HorizontalDivider()

            // Below the divider: the user's own code (the passive artifact).
            when (codeState) {
                PairingCodeUiState.Loading -> OwnCodeLoading()
                is PairingCodeUiState.Ready -> PairingCodeDisplay(
                    code = codeState.code,
                    onCopy = { onCopy(codeState.code) },
                    onRegenerate = onRegenerate,
                    modifier = Modifier.fillMaxWidth(),
                )
                is PairingCodeUiState.Error -> OwnCodeError(onRetry = onRetry)
            }
        }
    }
}

@Composable
private fun OwnCodeLoading() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = "Generating your code…",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
        )
    }
}

@Composable
private fun OwnCodeError(onRetry: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Couldn't create your code. Check your connection.",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
        )
        TextButton(onClick = onRetry) {
            Text("Try again")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairedEmptyReadyPreview() {
    TranslatorRepTheme {
        PairedEmptyContent(
            codeState = PairingCodeUiState.Ready("482917"),
            snackbarHostState = remember { SnackbarHostState() },
            onCopy = {},
            onRegenerate = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun PairedEmptyLoadingPreview() {
    TranslatorRepTheme {
        PairedEmptyContent(
            codeState = PairingCodeUiState.Loading,
            snackbarHostState = remember { SnackbarHostState() },
            onCopy = {},
            onRegenerate = {},
            onRetry = {},
        )
    }
}
