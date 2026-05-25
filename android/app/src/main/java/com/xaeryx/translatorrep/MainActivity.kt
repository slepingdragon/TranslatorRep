package com.xaeryx.translatorrep

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xaeryx.translatorrep.call.callSession.CallSession
import com.xaeryx.translatorrep.call.livekit.LiveKitRoomManager
import com.xaeryx.translatorrep.call.ui.InCallScreen
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
import com.xaeryx.translatorrep.pairing.ui.PairedHomeScreen
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
                                is PairingStatus.Paired -> PairedRoute(
                                    partnerUid = pairing.partnerUid,
                                    partnerName = pairing.partnerName,
                                    onUnpair = {
                                        pairingRepository.unpair(state.uid, pairing.pairId)
                                    },
                                )
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
    // status to Paired, which re-routes MainActivity to PairedRoute.
    PairedEmptyScreen(viewModel = pairingViewModel)
}

/**
 * Paired destination (Story 2.2): the Paired home with the Call button, toggling into the
 * (scaffold) in-call screen when Call is tapped. The `CallSession` is the orchestration seam
 * (owns the LiveKit room lifecycle; Story 2.3 wires the real connection).
 */
@Composable
private fun PairedRoute(partnerUid: String, partnerName: String, onUnpair: () -> Unit) {
    val context = LocalContext.current
    var inCall by remember { mutableStateOf(false) }
    // CallSession owns the LiveKit room lifecycle (Story 2.2/2.3); UI never touches LiveKit.
    val callSession = remember(context) {
        CallSession(LiveKitRoomManager(context.applicationContext))
    }

    // Story 2.3: mic permission must be granted before a call can publish audio.
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) inCall = true }

    if (inCall) {
        InCallScreen(
            callSession = callSession,
            partnerName = partnerName,
            peerUid = partnerUid,
            onEnd = { inCall = false },
        )
    } else {
        PairedHomeScreen(
            partnerName = partnerName,
            onCall = {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) {
                    inCall = true
                } else {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            // Story 1.13: unpair deletes /pairs + clears local state, flipping status to
            // Unpaired → re-routes to the Paired-Empty home.
            onUnpair = onUnpair,
        )
    }
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
