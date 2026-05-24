package com.xaeryx.translatorrep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xaeryx.translatorrep.firebase.FirebaseSmokeTest
import com.xaeryx.translatorrep.pairing.AnonymousAuthRepository
import com.xaeryx.translatorrep.pairing.AuthState
import com.xaeryx.translatorrep.pairing.PairingCodeAllocator
import com.xaeryx.translatorrep.pairing.PairingCodeGenerator
import com.xaeryx.translatorrep.pairing.PairingCoordinator
import com.xaeryx.translatorrep.pairing.PairingFirestoreRepository
import com.xaeryx.translatorrep.pairing.PairingStatus
import com.xaeryx.translatorrep.pairing.PairingViewModel
import com.xaeryx.translatorrep.pairing.ui.PairedEmptyScreen
import com.xaeryx.translatorrep.ui.components.GlassIntensity
import com.xaeryx.translatorrep.ui.components.MonochromeGlassPanel
import com.xaeryx.translatorrep.ui.theme.TextPrimary
import com.xaeryx.translatorrep.ui.theme.TextSecondary
import com.xaeryx.translatorrep.ui.theme.TranslatorRepTheme
import kotlinx.coroutines.launch

private const val FIREBASE_SMOKE_EXTRA = "firebase-smoke"

/**
 * Host activity. Story 1.8 turns this into the anonymous-sign-in gate: it observes
 * [AnonymousAuthRepository.state] (sign-in is kicked off in
 * [TranslatorRepApplication.onCreate]) and renders a branded loading surface until a
 * stable UID is established — never a login/signup form (FR-1). On a returning launch the
 * cached Firebase session resolves to [AuthState.SignedIn] immediately, so the loading
 * surface is effectively invisible.
 *
 * Story 1.9 replaces the [AuthState.SignedIn] branch with the real Paired-Empty home
 * (pairing-code display + partner-code input).
 *
 * Story 1.4 AC-5 smoke-test trigger still lives here behind a debug + intent-extra gate
 * (see [maybeTriggerFirebaseSmokeTest]).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Edge-to-edge per UX spec — In-Call surfaces use the full screen.
        enableEdgeToEdge()
        maybeTriggerFirebaseSmokeTest()

        val authRepository = (application as TranslatorRepApplication).authRepository

        setContent {
            TranslatorRepTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (val state = authRepository.state.collectAsStateWithLifecycle().value) {
                        // Story 1.11: once signed in, route on persistent pairing status —
                        // Paired (mirror/listener) → Paired home; Unpaired → Paired-Empty
                        // home; Unknown → brief loading on cold launch. The /pairs listener
                        // also drives the immediate post-pair transition (Story 1.10).
                        is AuthState.SignedIn -> {
                            val app = application as TranslatorRepApplication
                            val pairingRepository = app.pairingStatusRepository
                            LaunchedEffect(state.uid) {
                                pairingRepository.start(state.uid)
                                // Story 1.12: ensure the X25519 identity keypair exists +
                                // its public half is published (ADR-A2). Fire-and-forget.
                                app.identityRepository.start(state.uid)
                            }
                            when (
                                val pairing =
                                    pairingRepository.status.collectAsStateWithLifecycle().value
                            ) {
                                PairingStatus.Unknown -> PairingLoadingGate()
                                PairingStatus.Unpaired -> PairedEmptyRoute(ownerUid = state.uid)
                                is PairingStatus.Paired ->
                                    PairedHomePlaceholder(partnerName = pairing.partnerName)
                            }
                        }
                        else -> AuthGateScreen(
                            authState = state,
                            // Retry is only reachable from AuthState.Failed, so this never
                            // races the Application-scope ensureSignedIn() call.
                            onRetry = { lifecycleScope.launch { authRepository.ensureSignedIn() } },
                        )
                    }
                }
            }
        }
    }

    /**
     * Story 1.4 AC-5: kick off [FirebaseSmokeTest.runOnce] when the activity
     * is started with `--es firebase-smoke true` (debug builds only). Used
     * for the one-shot validation that anon sign-in + Firestore rules work
     * end-to-end against the real Firebase project.
     *
     * Trigger from adb:
     * ```
     * adb shell am start -n com.xaeryx.translatorrep/.MainActivity --es firebase-smoke true
     * ```
     */
    private fun maybeTriggerFirebaseSmokeTest() {
        if (BuildConfig.DEBUG && intent?.hasExtra(FIREBASE_SMOKE_EXTRA) == true) {
            lifecycleScope.launch {
                FirebaseSmokeTest.runOnce()
            }
        }
    }
}

/**
 * Story 1.9 Paired-Empty home route. Builds [PairingViewModel] with the signed-in UID and a
 * Firestore-backed allocator, then renders [PairedEmptyScreen]. Manual construction (no DI
 * framework) — consistent with [TranslatorRepApplication.authRepository].
 */
@Composable
private fun PairedEmptyRoute(ownerUid: String) {
    val pairingViewModel: PairingViewModel = viewModel(
        factory = remember(ownerUid) {
            // One repository instance serves both seams (CodeStore + PairStore).
            val repository = PairingFirestoreRepository()
            PairingViewModel.Factory(
                ownerUid = ownerUid,
                allocator = PairingCodeAllocator(
                    generator = PairingCodeGenerator(),
                    codeStore = repository,
                ),
                coordinator = PairingCoordinator(codeStore = repository, pairStore = repository),
            )
        },
    )
    // No explicit onPaired navigation: creating /pairs makes the app-wide
    // PairingStatusRepository listener fire (Firestore echoes the local write), flipping
    // status to Paired, which re-routes MainActivity to PairedHomePlaceholder.
    PairedEmptyScreen(viewModel = pairingViewModel)
}

/** Brief loading gate while pairing status resolves on cold launch (Story 1.11). */
@Composable
private fun PairingLoadingGate() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = TextPrimary)
    }
}

/**
 * Minimal Paired-home placeholder (Stories 1.10/1.11). The real Paired home — partner display
 * name styling + the audio/video Call button — is Story 2.2.
 */
@Composable
private fun PairedHomePlaceholder(partnerName: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MonochromeGlassPanel(
            intensity = GlassIntensity.Regular,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
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
}

/**
 * Renders the current [AuthState] inside the Theme-A monochrome-glass panel. The
 * "TranslatorRep" wordmark is always present; the body below it reflects the state.
 * No state shows a login/signup form (FR-1). The [AuthState.SignedIn] branch is a fallback
 * (previews); in the running app MainActivity routes SignedIn to [PairedEmptyRoute].
 */
@Composable
private fun AuthGateScreen(authState: AuthState, onRetry: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        MonochromeGlassPanel(
            intensity = GlassIntensity.Regular,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "TranslatorRep",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                )
                when (authState) {
                    AuthState.SigningIn -> {
                        CircularProgressIndicator(color = TextPrimary)
                        Text(
                            text = "Getting things ready…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }

                    is AuthState.SignedIn -> {
                        // Placeholder home — Story 1.9 replaces this with the
                        // Paired-Empty home (pairing-code display + partner input).
                        Text(
                            text = "You're all set.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    }

                    is AuthState.Failed -> {
                        Text(
                            text = "Couldn't connect. Check your internet and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                        Button(onClick = onRetry) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun AuthGateSigningInPreview() {
    TranslatorRepTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            AuthGateScreen(authState = AuthState.SigningIn, onRetry = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun AuthGateSignedInPreview() {
    TranslatorRepTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            AuthGateScreen(authState = AuthState.SignedIn(uid = "preview-uid"), onRetry = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0A0B)
@Composable
private fun AuthGateFailedPreview() {
    TranslatorRepTheme {
        Box(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
            AuthGateScreen(authState = AuthState.Failed(reason = "UnknownHostException"), onRetry = {})
        }
    }
}
