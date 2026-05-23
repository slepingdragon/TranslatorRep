---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: complete
completedAt: 2026-05-22
workflowType: architecture
project_name: TranslatorRep
user_name: Bania
date: 2026-05-22
canonical_source_of_truth: |
  The "SCOPE EXPANSION (post-CU, pre-CA — 2026-05-22)" section in
  ~/.claude/projects/c--Users-bania-Desktop-TranslatorRep/memory/project_translatorrep.md
  supersedes specific lines in the file artifacts below. Where they conflict, SCOPE EXPANSION wins.
  CA's deliverables include the reconciled PRD/UX updates (PRD §6.1 privacy, §10.2 v2-deferral,
  §10.3 timeline, §11 success metrics; UX spec component inventory additions).
inputDocuments:
  - _bmad-output/planning-artifacts/briefs/brief-TranslatorRep-2026-05-22/brief.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/prd.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/addendum.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/reconcile-brief.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/reconcile-dr.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/reconcile-tr.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/review-prose.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/review-rubric.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/review-structural.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
  - _bmad-output/planning-artifacts/ux-design-directions.html
  - _bmad-output/planning-artifacts/research/domain-conversational-indonesian-sundanese-linguistics-research-2026-05-22.md
  - _bmad-output/planning-artifacts/research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md
  - "memory:project_translatorrep.md (SCOPE EXPANSION — canonical for v1)"
  - "memory:linguistics_translation_prompt.md (v1 Gemini system prompt; status: TBD given on-device pivot)"
  - "memory:user_bania.md (user profile)"
---

# Architecture Decision Document — TranslatorRep

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together. The SCOPE EXPANSION in memory is the canonical source of truth for v1 — file artifacts below are pre-expansion baseline._

## Step 1 — Initialization Complete

Workflow scaffolded. Inputs loaded. Ready for step 2 (project context analysis).

## Project Context Analysis

### Requirements Overview

**Authoritative requirement set:**
PRD §4 ships 24 FRs (FR-1 to FR-24, FR-25 reserved). The SCOPE EXPANSION in
memory layers six locks on top: video in v1, self-host LiveKit OSS on Oracle
ARM A1, two-button Audio/Video on Paired home, speaker/earpiece toggle, E2EE
via Insertable Streams, on-device translation. CA must surface these as net-new
FRs and revise affected existing FRs as part of its deliverables.

**Functional surface (load-bearing categories):**

- Pairing & Identity (FR-1 to FR-5): anonymous Firebase Auth + 6-digit code +
  paired-forever; minimal compute; depends on Firestore. E2EE adds a key-
  exchange concern on top of the existing pair record.
- Calling (FR-6 to FR-10): native VoIP UX via CallKit/PushKit (iOS) and
  ConnectionService/FCM (Android). Audio quality acceptance test pre-defined.
  Net-new: Audio Call vs Video Call type selection; in-call speaker/earpiece
  toggle; video track lifecycle (FR-additions).
- Translation Pipeline (FR-11 to FR-16): mic tap → VAD → on-device ASR →
  Translation Provider → Data Channel delivery → speaker + peer caption
  render. v1 Plan A binds Translation Provider to an on-device model
  (replacing the Cloud Run + Gemini path described in FR-14). v1 Plan B
  fallback: Vertex AI Gemini paid (~$5/mo) if Week-1 validation fails.
- Captions UI (FR-17 to FR-20): scrollable history, partial captions
  (Android only — iOS Whisper.cpp does not stream true partials; surface as
  AsrProvider.supportsStreamingPartials flag), auto-scroll, failure markers.
  Layout-competes-with-video on the In-Call upper region.
- Settings (FR-21 to FR-24): transcript history opt-in, post-editor toggle
  (status uncertain under on-device translation), display name, privacy
  summary. UX adds: 3-theme picker, custom image background picker,
  Bania-side quality-review entry, her one-tap post-Call reaction.

**Non-Functional Requirements (load-bearing for architecture):**

- NFR-Privacy: WhatsApp-equivalent (or better) — E2EE media client-to-client;
  translation text never leaves device; metadata only on Bania's Oracle VM +
  Firebase. Verification protocol due Week 1 (packet capture + manual e2e
  test + pipeline review).
- NFR-Provider-Abstraction (PRD §6.4): `AsrProvider` and `TranslationProvider`
  interfaces; v1 binds on-device implementations, v2 swaps for Sundanese,
  Plan B escape swaps for cloud Gemini.
- NFR-Cost: $0/month at SFU + ASR + translation + auth + storage. Plan B
  permits $5/mo for the translation line item only.
- NFR-Latency: SM-4 relaxed from <3.5s median to <8s median, p95 <12s. Range
  driven by on-device inference: ~5–8s iOS / ~3–5s Android realistic.
- NFR-Quality: SM-2 relaxed from ≥80% to ≥60% for v1 Plan A. Reverts toward
  ≥80% under Plan B.
- NFR-Battery: SM-5 acknowledged best-effort; potential 50–70%/hour worst
  case with Whisper.cpp + on-device translation per Utterance. May force
  Whisper.cpp small→base downgrade.
- NFR-App-Size: +250 MB to ~1.5 GB acceptable; sideload distribution makes
  this fine. Would block App Store / Play Store submission.
- NFR-Reliability: never silently drop captions (FR-20); offline-degraded ASR;
  graceful video→audio fallback on poor network.
- NFR-Cold-Start: warmup-ping pattern (PRD §6.5) applies to Cloud Run; under
  the on-device + self-host pivot, Cloud Run may be removed entirely. Oracle
  VM is always-on so cold-start moves from "first-call" to "service-restart"
  problem.
- NFR-Observability: Crashlytics opt-in default off; conversation content
  NEVER logged; inter-turn-gap + Call-duration metadata logging (UX-spec NFR).

**Scale & Complexity:**

- Primary domain: cross-platform native mobile real-time communications +
  on-device ML inference + self-hosted WebRTC SFU.
- Complexity level: HIGH in absolute terms; personal-scale operationally
  (2 specific users, sideload distribution, no compliance regime).
- Estimated architectural components: ~14–18 major.

### Technical Constraints & Dependencies

**Platform constraints (PRD §8 + SCOPE EXPANSION):**

- Android minSDK 33 (on-device ASR), target SDK 35
- iOS minOS 17, target iOS 26
- Native-only — no cross-platform framework (rejected upstream in TR Step 2)
- Distribution: APK sideload + TestFlight Ad Hoc; no Play Store / App Store
- LiveKit OSS on Oracle Always-Free ARM A1 (24 GB RAM, 4 OCPU, 10 TB
  egress); Docker `linux/arm64`; TLS + monitoring

**Hard external dependencies:**

- LiveKit SDK ≥v2.0 (Insertable Streams `e2eeOptions` exposed)
- WebRTC stack with AEC/AGC/NS (LiveKit defaults)
- Whisper.cpp small multilingual + Core ML on iOS (no Apple `id-ID` on-device)
- `SpeechRecognizer.createOnDeviceSpeechRecognizer()` (API 33+) for `id-ID` +
  `en-US` on Android — runtime availability probe required
- Firebase Auth (anonymous) + App Check (DeviceCheck / Play Integrity) +
  Firestore (Spark plan)

**Open architecture decisions (deferred from PRD/UX to CA):**

- On-device translation model (NLLB-200 / Gemma 2B / MADLAD class)
- Translation runtime stack (llama.cpp / onnxruntime / Core ML / TFLite)
- Particle-preservation strategy outside Gemini prompt
- E2EE key exchange (Firestore-stored / OOB / ephemeral mint)
- In-Call layout with video tile + Caption stack (3 candidate layouts)
- Theme C × video interaction
- Camera permission flow
- `VideoCallControlRow` composition
- Cloud Run keep-vs-remove
- Sundanese particle preservation under on-device NMT (open even for v1's
  Sundanese lexical insertions per DR §4)

### Cross-Cutting Concerns Identified

1. Provider Abstraction layer (AsrProvider + TranslationProvider) — v1 Plan A,
   v1 Plan B escape, v2 Sundanese all depend on this
2. Privacy posture per data-class (media / translation text / metadata) +
   Week-1 verification gate
3. Real-time latency budget coupled to on-device model choice
4. Battery budget coupled to model size + runtime + per-Utterance cadence
5. Cold-start / warmup pattern (residual if Cloud Run survives)
6. Failure surfacing as first-class UI state (translation / ASR /
   Sundanese / network / video / E2EE)
7. App-attestation surface (whatever remains backend-side)
8. Theme system (3 themes) including image-background overlay logic
9. Native VoIP integration with audio-vs-video signaling
10. Localization-aware typography (Indonesian compound words)

## Starter Template Evaluation

### Primary Technology Domain

Multi-stack native mobile real-time communications. No single starter
template covers the surface; four per-stack starters compose to the v1
foundation.

### Starter Options Considered

Cross-platform frameworks (Flutter, React Native, KMP) were evaluated and
rejected upstream in TR Step 2 — native VoIP integration (CallKit/PushKit
+ ConnectionService/FCM), on-device ML (Whisper.cpp + on-device
translation), Insertable Streams E2EE, and the §7 polish bar all require
direct platform SDK access that wrappers complicate without payoff at
2-user scale.

Community "starter kits" in the Next.js/T3/Expo sense do not exist for
the Android-native + iOS-native + LiveKit-OSS-self-host combination.
What exists are the platform SDKs' own project wizards plus LiveKit's
official Docker image, sample apps, and CLI.

### Selected Starter: Per-Stack Native Scaffold Set

**Rationale for Selection:**

Lightest credible foundation. Each platform's own wizard + LiveKit's
official Docker image + Firebase CLI scaffold to a known-good baseline
with no third-party "starter" middleware to maintain. Heavy themeing
(UX spec monochrome-glass override) makes opinionated Material/HIG
starter kits a net negative.

**Initialization (per stack, in order — first sprint's four scaffold
stories):**

1. Android: Android Studio → New Project → Empty Activity (Compose) →
   Kotlin → minSDK 33 / targetSDK 35. Add deps: LiveKit Android SDK
   (latest stable; verify Insertable Streams e2eeOptions API),
   Firebase BOM, Coroutines, security-crypto, Room + SQLCipher,
   on-device translation runtime (TBD step 4 ADR).
2. iOS: Xcode → New Project → iOS App → SwiftUI lifecycle → Swift.
   Add via SPM: LiveKit Swift SDK (latest stable), Firebase SDK,
   Whisper.cpp XCFramework. Info.plist additions: UIBackgroundModes
   voip, NSMicrophoneUsageDescription, NSCameraUsageDescription,
   NSPhotoLibraryUsageDescription, com.apple.developer.voip
   entitlement.
3. SFU: `docker pull livekit/livekit-server` on Oracle ARM A1; copy
   sample config.yaml from livekit/livekit repo and customize keys,
   TLS (via Caddy or Nginx companion), and TURN/STUN. Single Redis
   sidecar for state. NO egress/recording service in v1.
4. Backend (conditional — pending step 4 ADR on Cloud Run vs.
   collapse): `firebase init` (Auth anonymous + Firestore + App Check
   for both platforms). If LiveKit token minting moves off Cloud Run,
   add an auth-proxy on the Oracle VM adjacent to the SFU.

**Architectural Decisions Provided by Starter:**

**Language & Runtime:** Kotlin (Android), Swift (iOS), Node.js (only if
backend survives). Gradle Kotlin DSL on Android; Swift Package Manager
on iOS.

**Styling Solution:** None from starter — UX spec monochrome-glass
token layer overrides Material 3 ColorScheme/Typography/Shapes
(Android) and applies a root `.translatorRepStyle()` view modifier
(iOS). Token source: hand-maintained YAML/JSON consumed by per-platform
generators OR manually synced at solo-dev scale.

**Build Tooling:** Android Studio + Gradle (AGP latest); Xcode 16+ with
SPM. CI/CD not in starter — GitHub Actions added later as an ADR
deliverable per PRD §3.

**Testing Framework:** Android: JUnit5 + Compose UI testing +
Roborazzi/Paparazzi for screenshot tests. iOS: XCTest + Swift Testing
(iOS 18+) + ViewInspector. Integration tests against LiveKit room
fixture.

**Code Organization:** Per-platform — defer to step 6 (structure)
ADR. Both platforms organize around shared concept names from the
spec (Pair, Call, Utterance, Caption, CaptionStack, AsrProvider,
TranslationProvider, etc.) so cross-platform story creation reads
consistently.

**Development Experience:** Android Studio hot reload + Compose
preview; Xcode SwiftUI preview; LiveKit Server runs locally via
`docker run` for dev (Oracle VM only for staging/prod).

### Versions and pinning policy

Versions in PRD/Addendum (LiveKit Android 2.25.3, LiveKit Swift 2.14.1,
etc.) were current as of 2026-05-22 and have likely moved. At
scaffolding time: verify (a) Insertable Streams `e2eeOptions` API is
stable on current SDK versions, (b) Whisper.cpp Core ML build
instructions are current, (c) LiveKit Server Docker tag is compatible
with current SDK versions, (d) Firebase BOM resolves App Check on both
platforms.

**Note:** Scaffolding using these commands is the first sprint's
first FOUR stories (one per stack), not a single initialization
story. CE workflow should plan accordingly.

## Core Architectural Decisions

### Decision Priority Analysis

**Critical (block implementation):** ADR-A1, A2, A3, A6, B1, B2, B3, C1, C3, E1.

**Important (shape architecture):** ADR-A4, A5, B4, B5, C2, D1, D2, E2, E3, E4, E5, F3.

**Deferred (post-v1 or non-blocking):** Grafana/Prometheus monitoring on
the Oracle VM (v1 = `docker logs` + journalctl only); Plan B activation
contingent on Week-1 quality gate; transcript-history encryption key
rotation (defer to v2); cross-device transcript sync (deferred to v2 per
PRD §10.2); App Store / Play Store distribution path (no plan).

### Domain & TLS

- **Domain:** `xaeryx.com`, registered via Cloudflare Registrar (~$10/yr).
  WHOIS privacy enabled by default; 2FA on the Cloudflare account.
- **TranslatorRep subdomain:** `sfu.xaeryx.com` → Oracle VM public IP.
  **DNS-only mode (no Cloudflare proxy)** — Cloudflare does not proxy
  UDP, and LiveKit's WebRTC media runs UDP on ports 7881 + 50000-60000.
- **Optional second subdomain:** `auth.xaeryx.com` for the LiveKit
  token-mint proxy, or same VM at a path under `sfu.xaeryx.com`.
- **TLS:** Caddy on the Oracle VM with auto Let's Encrypt; zero manual
  cert rotation forever after first deploy.
- **Plan A clarification:** "$0/month *operating* cost; ~$10/year fixed
  for `xaeryx.com` — annual not monthly, doesn't scale with usage,
  amortizes across all of Bania's projects. Treated as infrastructure
  not operating cost."

### A. SFU & Real-Time Media

**ADR-A1: Oracle ARM A1 deployment shape.**

| Item | Choice |
|---|---|
| VM | Oracle Always-Free Ampere A1 max (4 OCPU, 24 GB RAM, 200 GB block storage) |
| OS | Ubuntu 24.04 LTS ARM |
| Orchestration | **Docker Compose** (single compose file: `livekit-server`, `redis`, `caddy`) |
| TLS | **Caddy** with auto Let's Encrypt on `sfu.xaeryx.com` |
| State | Redis single-node sidecar |
| Recording / Egress | **Off entirely** (not in v1 scope; reduces attack surface) |
| Monitoring (v1) | `docker logs` + journalctl. Grafana deferred to v2 if needed. |
| Backup | `docker-compose.yml`, `livekit.yaml`, Caddy config in Git (NOT on the VM). No user data persists on the SFU. |
| `empty_timeout` | **300 seconds (5 min)** — enables leave-and-rejoin per ADR-A6 |

**ADR-A2: E2EE key exchange — per-call X25519 ECDH via Firestore.**

- Each device generates a long-term X25519 identity keypair on first
  launch. Private key in Keychain (iOS) / EncryptedSharedPreferences
  (Android). Public key published to `/users/{uid}/identityPub`.
- For each Call: each device generates an ephemeral X25519 keypair.
  Ephemeral public key published to
  `/calls/{callId}/ephemeralPub/{uid}` signed by identity key.
- Both devices compute `ECDH(myEphemeralPriv, theirEphemeralPub)` →
  HKDF-SHA256 → AES-GCM 256-bit key.
- That key feeds LiveKit's `e2eeOptions.keyProvider` for Insertable
  Streams encryption of audio + video + data channel.
- **Forward secrecy:** fresh key per Call; compromise of a current
  device does not decrypt past Calls.
- **MITM resistance:** ephemeral public signed by long-term identity
  key — Firebase admin (Bania) cannot inject a fake ephemeral.
- **Firestore admin (Bania) sees only public keys.** Cryptographically
  safe; this is what public keys are for.

**ADR-A3: Audio Call vs Video Call signaling.**

- Paired home: two buttons → `CallTypeSelector` component.
- Caller's app passes `callType: "audio" | "video"` in the LiveKit
  token claim (custom metadata).
- Recipient's incoming-call push payload carries `callType` so the
  native UI renders "Audio Call" vs "Video Call" header text.
- Initial track set: Audio Call publishes audio only; Video Call
  publishes audio + video. Either side can mute video mid-Call (audio
  stays); either side can switch from Audio Call to Video Call mid-Call
  only by ending and re-initiating (deferred to v2 — UX surface for
  in-Call upgrade is non-trivial).

**ADR-A4: Video pipeline & failure UX.**

| Item | Choice |
|---|---|
| Codec | LiveKit default (VP9 / H.264 negotiated; LiveKit decides per platform) |
| Resolution | 360p × 30fps for v1 (suitable for 1:1, well under 10 TB/mo Oracle egress cap) |
| Video drop on poor network | Video track suspends; audio continues; UI shows calm "video paused — retrying" state via `VideoPausedTile` — **neutral grey, NOT amber** (don't conflate with translation failure) |
| Auto-retry | Every 5 seconds; manual retry button visible |
| Peer-side state | Partner's `VideoTile` transitions to `VideoPausedTile` (name initial centered on dark surface); captions continue |
| Camera permission | **Lazy** — requested on first Video Call tap, never on first launch |

**ADR-A5: Audio routing (speaker / earpiece / Bluetooth).**

- Single toggle in `CallControlRow` (both Audio + Video Call) with
  three logical states: Earpiece / Speaker / Bluetooth-when-connected.
- iOS: `AVAudioSession.overrideOutputAudioPort()`.
- Android: `AudioManager.setSpeakerphoneOn()` + `setMode(MODE_IN_COMMUNICATION)`,
  Bluetooth SCO route via `setBluetoothScoOn()`.
- BT / wired auto-routes when device detected at Call start; toggle
  overrides default routing.

**ADR-A6: Leave-and-rejoin (new FR-32).**

- LiveKit `room.empty_timeout = 300` (5 min from when room becomes
  empty — not from when first participant leaves).
- **Either side ends Call** → that side's app returns to Paired home;
  the OTHER side's app remains in Call state with "Partner left — can
  rejoin" copy. Their captions continue rendering (FR-16); the
  remaining person's utterances are NOT queued for the leaver.
- **Leaver gets prompted to rejoin** via persistent local notification
  ("Bania is still in the call — Rejoin"). **NOT a CallKit re-ring** —
  that would feel like a new incoming Call. Notification cleared on
  tap → rejoin, on the other party leaving (room destroys), or on
  5-min timeout.
- **Paired home banner** on the leaver's side if app is foregrounded:
  "Call still active — Rejoin".
- **Symmetric** — same behavior in both directions.
- **Single end-Call gesture** — no "leave vs. end-for-both" distinction.
  v1 simplicity; defer compound gesture to later if a need surfaces.
- **Transient network drops** (ICE failure, brief app backgrounding)
  auto-rejoin within the 5-min window without user action.
- **After 5 min of empty room** — room destroys; both apps return to
  Paired home cleanly; Firestore `/calls/{callId}.endedAt` set on
  room-destroy callback.

### B. Translation Stack

**ADR-B1: On-device translation model — Week-1 validation order.**

| Rank | Model | Size (q4) | Particle handling | Why this position |
|---|---|---|---|---|
| 1 | **NLLB-200 distilled 600M** | ~150-200 MB | Rules-based pre/post | Smallest, fastest, lowest battery; purpose-built NMT; supports id+en well |
| 2 | MADLAD-400 (3B q4) | ~750 MB | Rules-based pre/post | Google's multilingual NMT; possibly better on conversational; size/quality sweet spot |
| 3 | Gemma 2 2B (instruction-tuned, q4) | ~1.5 GB | Prompt-based (degraded fidelity) | Last resort — only if NLLB and MADLAD both fail the ≥60% Week-1 gate. Heavy + slow; instruction-following imperfect |

If all three fail Week-1 → **Plan B: Vertex AI Gemini 2.5 Flash paid in
`asia-southeast1`** at ~$5/mo. Translation text leaves device only
under Plan B. Privacy posture downgrades from "WhatsApp-equivalent" to
"Google sees text but does not train on it" for the translation line
item; media stays E2EE regardless.

**ADR-B2: Translation runtime stack per platform.**

| Platform | Primary | Fallback (only if needed) |
|---|---|---|
| iOS | **Core ML** (unified with Whisper.cpp → shared Apple Neural Engine acceleration) | ONNX Runtime iOS |
| Android | **ONNX Runtime Mobile** with NNAPI / GPU delegates | TensorFlow Lite |
| Gemma path (both, if Plan A escalates to rank-3 candidate) | **llama.cpp** | — |

**ADR-B3: Particle preservation strategy — rules-based pre and post around the NMT call.**

The DR's v1 Gemini system prompt becomes a **rules specification
document** for v1 Plan A, not a runtime artifact. The prompt itself
activates only under Plan B (cloud Gemini fallback).

Pipeline (ID → EN direction):

1. **Pre-process source.** Scan for: 14 ID discourse particles (DR §1),
   12+ Sundanese lexical insertions (DR §4), endearments (DR §6),
   religious expressions (DR §6), Gen-Z slang (DR §3), honorifics to
   partner. Either (a) tag inline with stable markers (`Aku [P:loh]
   sayang kamu` → NMT preserves the marker), or (b) strip + remember
   position, splice back post-translation.
2. **NMT call.** Cleaned source through NLLB-200 (or whichever model
   wins Week-1).
3. **Post-process target.** Substitute particle markers back per DR §1
   English mappings (`kan` → "right?", `dong` → "come on", `sih` →
   "though", `kok` → "how come", etc.). Apply gender-neutral default
   (`dia` → "they" until disambiguated). Strip honorifics directed at
   partner (`mas` → "babe" or omit). Preserve religious expressions
   verbatim. Map indirect refusals (`nanti dulu` → "maybe later").
   Map Gen-Z slang per DR §3 table.

Same pattern in reverse for EN → ID.

**Rationale:** Rules are deterministic, testable, and don't depend on
the model's instruction-following ability. Sidesteps the "small models
can't follow prompts well" problem that the SCOPE EXPANSION flagged.
Particle preservation moves from "hope the prompt works" to
"guaranteed in code." Lower-bound quality is high; ceiling is bounded
by the rules' coverage (DR enumerated the canonical sets).

**ADR-B4: Sundanese lexical insertions in v1.**

DR §4 says ~12 high-frequency Sundanese tokens cover the bulk of
code-switching. Pre-processing rules substitute these (or their
Indonesian/English equivalents) before NMT. Full Sundanese clauses
(rare) → flagged `[su?]` → `SundanesePlaceholderRow` on the EN side.
This **softens** PRD §10.2's "v1 visibly fails on SU" framing: lexical
insertions work in v1; full-clause SU remains the v2 gap.

**ADR-B5: VAD strategy.**

Keep PRD FR-12 defaults: **700ms silence pause / 15s max Utterance**.
Re-evaluate in Week-1 — under 5-8s on-device translation latency,
partial captions are Android-only (iOS Whisper.cpp doesn't stream true
partials; surface as `AsrProvider.supportsStreamingPartials: Bool`
flag), so felt latency is pipeline-dominated not VAD-dominated.

### C. Backend & Auth

**ADR-C1: Cloud Run removed entirely.**

| Was on Cloud Run | Now on… |
|---|---|
| Gemini translation proxy | Gone — on-device translation per ADR-B1/B2 |
| LiveKit token minting | **Node auth-proxy co-hosted on Oracle VM** (same Docker Compose stack as LiveKit) |
| App Check token verification | Same auth-proxy (Firebase Admin SDK + JWKS) |
| Warmup ping pattern | Gone — Oracle VM is always-on, no cold-start window |

Result: one less service to maintain, one less cross-region hop,
$0/mo unchanged.

**ADR-C2: App Check verification on the Oracle auth-proxy.**

- Mobile app obtains Firebase App Check token (DeviceCheck on iOS,
  Play Integrity on Android).
- Sends to auth-proxy along with Firebase Auth UID.
- Auth-proxy verifies the token against Firebase JWKS
  (`https://firebaseappcheck.googleapis.com/v1/jwks`); JWKS keys
  cached automatically by Firebase Admin SDK.
- Auth-proxy mints LiveKit JWT with `livekit-server-sdk` Node package;
  includes `callType` claim in the JWT metadata.
- Returns LiveKit JWT to client; client connects to SFU.

**ADR-C3: Firestore schema (E2EE-aware).**

```
/users/{uid}
  displayName: string?
  pairId: string?
  identityPub: bytes          // X25519 long-term public key

/pairs/{pairId}
  memberA: uid
  memberB: uid
  createdAt: timestamp

/codes/{6digit}
  ownerUid: uid
  createdAt, expiresAt

/calls/{callId}
  participants: [uidA, uidB]
  startedAt: timestamp
  endedAt: timestamp?         // set only when LiveKit room destroys
  callType: "audio" | "video"
  /ephemeralPub/{uid}
    pub: bytes                // X25519 ephemeral public key
    sig: bytes                // signature over pub with identity key
    createdAt: timestamp
```

**No conversation content. No Caption text. Public keys only.**
Firestore rules restrict reads to members of the pair / call.

### D. Data & State (local)

**ADR-D1: Local persistence per platform.**

| Concern | Android | iOS |
|---|---|---|
| Anonymous Auth state | Firebase Auth | Firebase Auth |
| Identity X25519 privKey | EncryptedSharedPreferences (security-crypto) | Keychain with `kSecAttrAccessibleAfterFirstUnlock` |
| Pairing state mirror | Room (SQLCipher) | SwiftData |
| Transcript history (opt-in) | Room (SQLCipher) | SwiftData + `NSFileProtectionComplete` |
| Quality review entries (Bania only) | Room | SwiftData |
| Her one-tap reactions (her only) | Room | SwiftData |
| Theme + image-bg file path | EncryptedSharedPreferences + private app file | UserDefaults + app sandbox |

**ADR-D2: Data Channel Message payload (E2EE-aware update of PRD addendum).**

```json
{
  "utterance_id": "uuid",
  "speaker_uid": "string",
  "source_lang": "en|id",
  "source_text": "string",
  "target_lang": "en|id",
  "target_text": "string",
  "confidence": 0.0-1.0,
  "timestamp_start_ms": int,
  "translation_status": "ok|failed|low-confidence|sundanese-placeholder",
  "particles_preserved": ["kan", "loh"],
  "callType": "audio|video"
}
```

Under E2EE Insertable Streams, the Data Channel is encrypted
client-to-client; the SFU sees ciphertext bytes. Verify
`e2eeOptions` covers data channels on current LiveKit SDK versions at
integration time.

### E. UI Layout & Components

**ADR-E1: In-Call screen vertical layout.**

| Mode | Upper region | Lower region |
|---|---|---|
| Audio Call | **40%** — partner name + audio level + mic dot + `AudioCallControlRow` (mute audio / audio-routing toggle / end) | **60%** — Caption stack |
| Video Call | **50%** — `VideoTile` (partner) + corner-overlay PiP for local self-view + audio level + mic dot + `VideoCallControlRow` (mute audio / mute video / flip camera / audio-routing toggle / end) | **50%** — Caption stack |

**Explicit reject:** backdrop-video-with-captions-overlay. Violates
UX anti-emotion #5 (translation theater) — captions on her face
during emotional moments creates the wrong frame.

**ADR-E2: Theme C × Video interaction.**

- Audio Call under Theme C: custom image background full-screen with
  adaptive overlay; glass panels per UX spec.
- Video Call under Theme C: partner's video stream **replaces** the
  custom image in the upper region; Caption stack's lower region keeps
  the custom-image background with adaptive overlay.
- No setting required; smooth handoff at video track activation. The
  adaptive overlay (0.40 → 0.55) applies only to the Caption-stack
  region under Theme C + Video Call.

**ADR-E3: Camera permission flow (lazy).**

- Never requested on first launch.
- Triggered on first Video Call tap (caller) or first incoming Video
  Call accept (recipient).
- Denial → `CameraPermissionFlow` component with calm explanation +
  Settings deep-link.
- Permission state cached locally; checked at every Video Call start.

**ADR-E4: `VideoCallControlRow` composition.**

Five controls in a single row (no overflow needed at standard phone
widths):

1. Mute audio
2. Mute video (camera off → keeps audio; local tile transitions to
   `VideoMutedTile`; partner sees `VideoMutedTile`)
3. Flip camera (front ↔ back)
4. Audio-routing toggle (earpiece / speaker / BT-when-connected)
5. End-Call

**ADR-E5: New components added to UX inventory (CA hands these to UX as additions).**

`CallTypeSelector`, `VideoTile`, `VideoPausedTile`, `VideoMutedTile`,
`VideoCallControlRow`, `AudioCallControlRow` (renamed from
`CallControlRow` to disambiguate from video), `AudioRoutingToggle`,
`CameraPermissionFlow`, `E2EEKeyExchangeIndicator` (one-time on first
Call after pairing — confirms key established), `RejoinNotification`
(local notification), `CallWaitingForPartnerState` (banner/overlay
variant of remaining-side UI). Plus already-spec'd
`SundanesePlaceholderRow`.

### F. Observability & Failure Surfacing

**ADR-F1: Crashlytics — opt-in, default off.**

Per PRD §6.1. Conversation content NEVER logged. Only Call IDs,
language codes, error-type strings, model load timings,
ASR/translation duration metrics (sanitized — no source/target text).

**ADR-F2: Privacy-safe metadata logging.**

- Inter-turn gap (median + p95) + Call duration per Call → local-only
  on each device (no upload).
- Feeds the UX-spec "friction-gone" composite signal post-ship.
- Schema: extends `CallRecord` with `interTurnGapMedianMs`,
  `interTurnGapP95Ms`, `callDurationMs`.

**ADR-F3: First-class failure UI states.**

| State | Component | Color (Theme A) |
|---|---|---|
| Translation failed | `TranslationUnavailableMarker` | State A amber |
| ASR low-confidence | inline marker on Caption row | text-tertiary |
| Sundanese clause | `SundanesePlaceholderRow` | neutral; no alarm |
| Network drop | offline indicator + soft retry | State A amber |
| Video drop / paused | `VideoPausedTile` + auto-retry | **neutral grey** (NOT amber) |
| E2EE key not yet exchanged | one-time indicator on first Call after pair | neutral; tap for explanation |
| Model loading (first-call cold start) | one-time "preparing translator" | neutral; expected first-time-only |
| Partner left, can rejoin (ADR-A6) | "Partner left — can rejoin" overlay on remaining side | neutral |

### G. Reconciliation Deliverables — CA's output to upstream artifacts

Per the SCOPE EXPANSION's explicit deferral of PRD + UX-spec edits to
CA, the following document updates are CA deliverables (to be produced
after step 8 architecture completion):

**PRD updates:**

- §6.1 Privacy: WhatsApp-equivalent E2EE for media; translation never
  leaves device (Plan A); remove Gemini AI Studio data-handling caveat
  from v1 baseline (re-add it as a Plan B-only note).
- §10.2: remove video + true E2EE from v2-deferral list (now v1).
- §10.3 Timeline: ~7-10 weeks + 1 week ramp.
- §11 SM-2 / SM-4 / SM-5: documented relaxed targets (≥60% quality,
  <8s median latency, best-effort battery).
- **New FRs surfaced by CA:**
  - FR-26: Audio Call vs Video Call selection (ADR-A3)
  - FR-27: Video pipeline + failure UX (ADR-A4)
  - FR-28: Speaker / earpiece / Bluetooth toggle (ADR-A5)
  - FR-29: E2EE setup + per-call X25519 ECDH key exchange (ADR-A2)
  - FR-30: Camera permission flow (ADR-E3)
  - FR-31: On-device translation pipeline (ADR-B1/B2/B3)
  - FR-32: Leave-and-rejoin within 5-min window (ADR-A6)

**UX spec updates:**

- Add new components from ADR-E5 to Component Inventory.
- Revise In-Call screen specification for Audio (40/60) vs Video
  (50/50) layouts (ADR-E1).
- Document Theme C × Video interaction (ADR-E2).
- Add `RejoinNotification` + `CallWaitingForPartnerState` patterns
  (ADR-A6).

**Architecture document:** this document, completion at step 8.

### Decision Impact Analysis

**Implementation sequence (informs CE/SP sprint planning):**

1. Per-stack scaffolding (4 parallel stories — see "Starter Template
   Evaluation"). Includes Oracle VM provision + Docker Compose stack.
2. Anonymous Auth + Pairing + 6-digit code (FR-1 to FR-5) + identity
   X25519 keypair generation/publish.
3. LiveKit token auth-proxy + App Check verification (ADR-C1/C2).
4. Basic Audio Call end-to-end (CallKit/PushKit + ConnectionService/
   FCM + LiveKit join) — no captions yet.
5. **Week-1 validation gate**: on-device ASR + translation candidate
   bake-off; lock translation model OR escalate to Plan B.
6. Translation pipeline integration (ADR-B1/B2/B3) → first captions
   end-to-end.
7. Insertable Streams E2EE wiring + per-call ECDH (ADR-A2) →
   privacy verification (packet capture confirms SFU sees ciphertext).
8. Video pipeline (ADR-A3/A4) + Audio-vs-Video selection + camera
   permission.
9. Audio-routing toggle (ADR-A5).
10. Leave-and-rejoin behavior (ADR-A6).
11. Captions UI polish (partial captions Android-only; failure states;
    auto-scroll; jump-to-latest).
12. Settings, themes (incl. Theme C × Video), quality review tool
    (Bania-only), her one-tap reaction.
13. Crashlytics opt-in + privacy-safe metadata logging.

**Cross-component dependencies:**

- ADR-A2 (E2EE) depends on ADR-C3 (Firestore schema for identity +
  ephemeral keys) and LiveKit SDK ≥v2.0 `e2eeOptions`.
- ADR-B3 (rules-based particle preservation) depends on DR §1/§3/§4/§6
  reference tables; rules implementation is per-platform but
  spec'd centrally.
- ADR-A6 (leave-and-rejoin) depends on ADR-A1 (`empty_timeout` config)
  + ADR-C3 (`/calls/{callId}.endedAt` semantic).
- ADR-E1 (In-Call layout) depends on ADR-A3 (call-type signaling) +
  ADR-E5 (component inventory).
- ADR-F3 (failure UI states) depends on ADRs across the board for
  each state's source.
- Plan B escalation (ADR-B1) re-introduces Cloud Run OR a remote
  translation endpoint on the Oracle VM — decision deferred to Plan B
  activation time.

## Implementation Patterns & Consistency Rules

### Pattern Categories Defined

Twelve pattern categories addressing every conflict surface where AI
agents (story-creation, dev-story, code-review) or the solo dev could
make divergent choices across Kotlin and Swift implementations. Refined
through Party Mode (Winston / Amelia / Sally / Paige) — 17 changes
incorporated.

Load-bearing constraint: **Experience Principle 5 — "identical to the eye"
cross-platform parity.** Patterns below exist to make that achievable.

### 1. Canonical Concept Names & Term Forms Table

This architecture document is the **canonical glossary going forward.**
PRD §3 remains as historical reference (pre-SCOPE-EXPANSION); any
conflict resolves to this table. Every downstream artifact (stories,
ACs, code, code reviews, PR descriptions) uses these terms verbatim.

| Prose form | Identifier form (code) | JSON / wire form | Forbidden synonyms | Rationale |
|---|---|---|---|---|
| Pair | `Pair` (entity), `pair()` (action verb) | `pairId` | PairedUsers, Couple, Bond | "Pair" = relationship entity; "pair" verb = the act of forming one; PairingCode is the artifact. Three concepts, three words, no overlap. |
| Pairing Code | `PairingCode` | `pairingCode` | PairCode, Code, OTP | Distinct from generic "Code" — only ever the 6-digit pairing artifact |
| Call | `Call` | `callId`, `callType` | CallSession, Conversation, Session | Single noun for the active media exchange. `CallSession` is a different concept (see below) |
| CallSession | `CallSession` | n/a (local layer) | CallController, CallManager, CallVM | The orchestration layer between UI and providers — owns LiveKit room lifecycle, ASR/Translation flow, RoomState. NOT the Call itself. |
| CallType | `CallType` (enum: `audio`, `video`) | `call_type: "audio" \| "video"` | — | — |
| Utterance | `Utterance` | `utteranceId`, `utterance_id` (wire) | Segment, Phrase, Speech | One VAD-bounded speech unit |
| Caption | `Caption` | — | CaptionLine, Subtitle, Transcript | Source + Target text pair rendered on screen |
| PartialCaption | `PartialCaption` | — | InProgressCaption, StreamingCaption | Pre-finalize Android-only row |
| Source Text | `SourceText` | `source_text` (wire) | OriginalText, InputText | Symmetric pair with TargetText — Original/Translated implies hierarchy that breaks for symmetric bidirectional translation |
| Target Text | `TargetText` | `target_text` (wire) | TranslatedText, OutputText | Same reason |
| ASR Provider | `AsrProvider` | — | SpeechRecognizer, TranscriptionProvider | PRD §6.4 abstraction |
| Translation Provider | `TranslationProvider` | — | Translator, TranslationEngine | PRD §6.4 abstraction |
| ParticleProcessor | `ParticleProcessor` | — | RulesEngine, ParticleHandler, ParticleLayer | Named module owning DR §1/§3/§4/§6 pre/post rules (see §12 below) |
| TranslationStatus | `TranslationStatus` (enum: `ok`, `failed`, `lowConfidence`) | `translation_status` | TranslationResult.kind | Status = what happened. Render mode is orthogonal (see below) |
| RenderMode | `RenderMode` (enum: `default`, `sundanesePlaceholder`) | `render_mode` | DisplayMode, CaptionStyle | What we display, separate from why |
| RoomState | `RoomState` (enum: `active`, `waitingForPartner`, `ended`) | n/a (local) | CallState, SessionState | ADR-A6 leave-and-rejoin lifecycle |
| CaptionStack | `CaptionStackView` (iOS) / `CaptionStackComposable` (Android) | — | CaptionList, CaptionHistory, ChatList | UI component name. Underlying data is `utterances: List<Utterance>` — "Stack" refers to visual vertical stacking, NOT LIFO semantics |
| MonochromeGlassPanel | `MonochromeGlassPanel` | — | GlassPanel, BlurPanel | UX spec primitive |
| E2EE Key Exchange | `E2EEKeyExchange` | — | KeyAgreement, KeyHandshake | The full ADR-A2 X25519 ECDH flow |
| LanguageCode | `LanguageCode` (BCP 47 string wrapper) | `source_lang`, `target_lang` (BCP 47) | LocaleCode, Lang | Wrapper enforces BCP 47 format |

### 2. Rename Policy

- **Canonical-name changes** (any row in §1) require: (a) an ADR
  amendment in this document, (b) a simultaneous PR touching both Kotlin
  and Swift code, (c) updates to downstream artifacts that reference the
  old name (PRD reconciliation, UX spec, story templates).
- **Code-review agent rejects** any single-platform rename PR.
- **No ad-hoc rename in stories.** A story description introducing a new
  synonym for a canonical concept is a CR-block.

### 3. Naming Conventions

| Surface | Convention |
|---|---|
| Kotlin types | PascalCase |
| Kotlin vals / funs / params | camelCase |
| Swift types | PascalCase |
| Swift vars / funcs / params | camelCase |
| File names | match contained primary type (`CaptionRow.kt` / `CaptionRow.swift`) |
| Data Channel JSON fields | **snake_case** (locked from PRD addendum) |
| Firestore document fields | **camelCase** (Firebase convention) |
| Language codes (external) | **BCP 47** (`id-ID`, `en-US`, `su-ID`) |
| NLLB Flores-200 codes (internal) | `ind_Latn`, `eng_Latn`, `sun_Latn` — **only between `TranslationProvider.translate()` input and the model call. Never in logs, telemetry, error codes, model-download metadata, file paths, or any other surface.** Convert at boundary, no exceptions |
| Logging field keys | snake_case |
| Error codes | `ERR_<DOMAIN>_<CONDITION>` SCREAMING_SNAKE_CASE (see §10) |

### 4. ID Format (Locked Globally)

- **All canonical-entity IDs** (`Pair.id`, `Call.id`, `Utterance.id`,
  `Caption.id`, `MessageId` in Data Channel payloads):
  **`String`, ULID format, 26 characters**.
- ULIDs are time-sortable (helpful for ordering + debugging) and
  collision-resistant at 2-user scale.
- Both platforms use the same ULID library family (e.g.,
  `com.aventrix.jnanoid` or `de.huxhorn.sulky.ulid` on Android;
  `ulid-swift` on iOS) — pick one per platform at scaffolding time; both
  emit canonical Crockford base32.
- Firestore document IDs follow the same ULID convention where the app
  generates them (calls, utterances). Where Firebase auto-generates
  (e.g., `pairs/{pairId}`), Firebase IDs are accepted but treated as
  opaque strings by the app code.

### 5. Data Channel Schema (Versioned + Idempotent + Ordered)

```json
{
  "schema_version": 1,
  "seq": 1234,
  "utterance_id": "01HKZN...",
  "speaker_uid": "string",
  "source_lang": "id-ID",
  "source_text": "string",
  "target_lang": "en-US",
  "target_text": "string",
  "confidence": 0.92,
  "timestamp_offset_ms": 4520,
  "translation_status": "ok",
  "render_mode": "default",
  "particles_preserved": ["kan", "loh"],
  "call_type": "audio"
}
```

**Schema-version policy:**

- Receiver MUST drop messages with unknown major versions silently
  (forward-incompat releases).
- Receiver MUST accept unknown fields within same major version
  (forward-compat additions).
- Field add / remove / rename → major-version bump.
- Both platforms decode tolerantly — unknown fields ignored, missing
  optional fields default per documented defaults.

**Idempotency + ordering:**

- `seq` is a monotonic per-sender counter starting at 1 at Call start;
  increments by 1 per published Data Channel message.
- Receiver dedup key: `(speaker_uid, seq)`. Duplicate received → drop.
- Receiver renders in `seq` order for each speaker, NOT in arrival order
  — re-orders if a later `seq` arrives before an earlier one
  (small reorder buffer, ~500ms).

**Clock-skew handling:**

- `timestamp_offset_ms` is sender's offset from local `call_start_ms`
  (the moment that sender joined the room). NOT wall-clock UTC.
- Receiver computes display "X seconds ago" from its own clock; never
  trusts sender's wall-clock.
- Ordering uses `seq` (above), not timestamps.
- Eliminates phone-clock-skew rendering bugs.

### 6. Firestore Schema

See ADR-C3 for the full schema. Conventions:

- camelCase field names
- Firestore native `Timestamp` type for server-side time
- `bytes` for X25519 keys (long-term + ephemeral)
- Collection names plural (`users`, `pairs`, `calls`, `codes`)
- Document IDs: ULIDs where app-generated; Firebase auto-IDs where
  Firebase generates (treated as opaque strings)

### 7. Failure-State Taxonomy + State-Priority Choreography

**Status is orthogonal to RenderMode.** Translation can succeed
(`status = ok`) with `render_mode = sundanesePlaceholder` because the
NMT translated something but the ParticleProcessor flagged a full-clause
SU situation. Two separate decisions.

**Failure-state identifiers:**

| State identifier | UI component | Error code (§10) |
|---|---|---|
| `translationFailed` | `TranslationUnavailableMarker` (amber) | `ERR_TRANS_PROVIDER_UNAVAIL` |
| `asrLowConfidence` | inline tertiary marker | `WARN_ASR_LOW_CONFIDENCE` |
| `sundaneseClause` | `SundanesePlaceholderRow` | `INFO_SUNDANESE_PLACEHOLDER` |
| `networkDropped` | offline indicator + soft retry | `ERR_NETWORK_DROPPED` |
| `videoPaused` | `VideoPausedTile` (neutral grey) | `WARN_VIDEO_TRACK_SUSPENDED` |
| `e2eeKeyNotReady` | one-time `E2EEKeyExchangeIndicator` | `WARN_E2EE_KEY_NOT_READY` |
| `modelLoading` | "preparing translator" indicator | `INFO_MODEL_LOADING` |
| `waitingForPartner` | `CallWaitingForPartnerState` overlay | `INFO_WAITING_FOR_PARTNER` |

**State-priority choreography rule** (when multiple states fire
simultaneously — common at cold-start). One state-banner shown at a
time; per-Caption inline markers continue independently. Priority:

```
e2eeKeyNotReady
  > modelLoading
  > waitingForPartner
  > networkDropped
  > translationFailed
  > videoPaused
  > sundanesePlaceholder
  > asrLowConfidence
```

Felt as **one problem** by the user, not three. Cross-platform UI MUST
implement same priority; CR agent checks.

**New state identifier** → requires ADR amendment first (no ad-hoc
states from stories). See §8 for the provisional escape valve.

### 8. Provisional State Namespace (Two-Week Escape Valve)

- `RoomState.provisional.*` and `TranslationStatus.provisional.*` allowed
  for design experiments before ADR-promotion.
- Time-boxed: 14 days from first commit introducing the provisional.
- Code-review agent flags any `provisional.*` still live after 14 days
  → forces ADR promotion or removal.
- Enables Sally's "discovered emotion we didn't design for" cases
  (e.g., `provisional.partnerTypingButSilent`) without stalling on a
  full ADR cycle.

### 9. Provider Abstraction Symmetric Surface

Both platforms expose interfaces with identical method names + return
contracts (each in native idiom). Names, params, semantics must match.

```kotlin
// Android
interface AsrProvider {
    val supportsStreamingPartials: Boolean
    fun start(language: LanguageCode): Flow<AsrEvent>
    fun stop()  // see cancellation contract below
}

interface TranslationProvider {
    suspend fun translate(
        sourceText: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode,
    ): TranslationResult
}
```

```swift
// iOS
protocol AsrProvider {
    var supportsStreamingPartials: Bool { get }
    func start(language: LanguageCode) -> AsyncStream<AsrEvent>
    func stop()  // see cancellation contract below
}

protocol TranslationProvider {
    func translate(
        sourceText: String,
        sourceLang: LanguageCode,
        targetLang: LanguageCode
    ) async throws -> TranslationResult
}
```

**Cancellation contract (testable AC, both platforms):**
`stop()` MUST terminate the stream within **500 ms** and release ASR
resources — Whisper context disposal (iOS), audio thread teardown
(Android), buffer cleanup. Verified via unit test that asserts no
allocated resources remain 500ms after `stop()`.

**`TranslationResult` shared concept:**

```
TranslationResult {
  targetText: String
  particlesPreserved: List<String>
  status: TranslationStatus
  renderMode: RenderMode
  confidence: Double
}
```

Identical field names across both platforms.

### 10. Error-Code Registry

Stable string codes. 1:1 mapping to taxonomy in §7. Used in logs,
telemetry, and any cross-platform diagnostic surface. **Locked now to
prevent future analytics-dashboard refactors.**

| Code | Severity | Maps to |
|---|---|---|
| `ERR_TRANS_PROVIDER_UNAVAIL` | error | `translationFailed` |
| `ERR_TRANS_PROVIDER_TIMEOUT` | error | `translationFailed` |
| `ERR_ASR_INIT_FAILED` | error | (fatal, surfaces as crash) |
| `WARN_ASR_LOW_CONFIDENCE` | warn | `asrLowConfidence` |
| `INFO_SUNDANESE_PLACEHOLDER` | info | `sundaneseClause` |
| `ERR_NETWORK_DROPPED` | error | `networkDropped` |
| `ERR_E2EE_KEY_EXCHANGE_FAILED` | error | (fatal, surfaces; call ends) |
| `WARN_E2EE_KEY_NOT_READY` | warn | `e2eeKeyNotReady` |
| `WARN_VIDEO_TRACK_SUSPENDED` | warn | `videoPaused` |
| `INFO_MODEL_LOADING` | info | `modelLoading` |
| `INFO_WAITING_FOR_PARTNER` | info | `waitingForPartner` |
| `ERR_LIVEKIT_ROOM_FAILED` | error | (fatal, call ends) |
| `ERR_PAIRING_CODE_INVALID` | error | (UI inline) |
| `ERR_PAIRING_CODE_EXPIRED` | error | (UI inline) |

Adding a code → ADR amendment + simultaneous Kotlin/Swift PR (same as
canonical-name rename policy).

### 11. ParticleProcessor Module (Named Peer to Providers)

**Composition:**

```
CaptionStack <- CallSession
                   ├── RuleBasedTranslationProvider (decorator)
                   │     ├── ParticleProcessor (DR §1/§3/§4/§6 rules)
                   │     └── RawTranslationProvider (NLLB/MADLAD/Gemma)
                   └── AsrProvider
```

**Location:** `translation/particles/` on both platforms. Files mirror
across:

- `ParticleProcessor.kt` / `ParticleProcessor.swift`
- `ParticleRules.kt` / `ParticleRules.swift` (the actual DR-table data)
- `SundaneseInsertions.kt` / `SundaneseInsertions.swift`
- `HonorificStripping.kt` / `HonorificStripping.swift`
- `IndirectRefusals.kt` / `IndirectRefusals.swift`
- `GenZSlang.kt` / `GenZSlang.swift`
- `ReligiousExpressions.kt` / `ReligiousExpressions.swift`

**Test fixture format (golden-file pattern):**

```
test/fixtures/particles/<rule_name>/
  case_001/
    source.txt           # raw source utterance
    expected_processed.txt   # source after pre-processing
    expected_target.txt      # target after post-processing
    metadata.json        # { source_lang, target_lang, expected_particles }
```

Both platforms run the same fixture set; identical outputs required.
This is the testable proof of cross-platform translation parity.

### 12. Project Structure (Top-Level by Feature)

```
app/
  pairing/                — FRs 1-5, code entry/display, E2EEKeyExchange initial publish
  call/
    callSession/          — CallSession orchestration; RoomState; leave-and-rejoin
    livekit/              — LiveKit integration; token fetch; room lifecycle
    videoTracks/          — camera capture + render; VideoTile components
  translation/
    asr/                  — AsrProvider impls (Whisper.cpp on iOS; SpeechRecognizer on Android)
    nmt/                  — RawTranslationProvider impls (NLLB / MADLAD / Gemma)
    particles/            — ParticleProcessor + rule tables (§11)
    rulebased/            — RuleBasedTranslationProvider decorator
  captions/               — CaptionStack(View|Composable), CaptionRow, PartialCaption, failure markers
  e2ee/                   — X25519 ECDH, ephemeral key publish/subscribe, AES-GCM key derivation
  ui/components/          — MonochromeGlassPanel, theme tokens, shared widgets
  settings/               — themes (incl. Theme C), transcript history, privacy summary
  quality/                — Bania-only review tool, her one-tap reaction
  data/                   — Firestore mirrors, local persistence (Room/SwiftData)
  platform/               — CallKit/PushKit (iOS); ConnectionService/FCM (Android)
  logging/                — SafeLog facade + allowlist
  ids/                    — ULID generator wrapper
```

Tests adjacent to source per platform convention:

- Android: `src/test/` (JVM unit) + `src/androidTest/` (instrumented)
- iOS: separate test target with `*Tests.swift` files

### 13. State Management

| Platform | Primary primitive |
|---|---|
| Android | `StateFlow<T>` (exposed) backed by `MutableStateFlow` (private); `SharedFlow<T>` for one-shot events |
| iOS | `@Observable` (Swift 5.9+) OR `@Published` on `ObservableObject` |

**UI never calls providers directly.** All flows go through
`CallSession` (or feature-specific session/ViewModel layer). CR agent
flags direct Provider calls from View code.

### 14. Logging — SafeLog Facade + Explicit Allowlist

**The allowlist is now an explicit set of typed keys** (was: denylist).

```kotlin
// Android
object SafeLog {
    fun event(key: AllowedLogKey, value: Any) { /* routes to Crashlytics + local */ }
}

enum class AllowedLogKey {
    CALL_ID, UTTERANCE_ID, PROVIDER_NAME, MODEL_NAME,
    LATENCY_MS, ERROR_CODE, SCHEMA_VERSION,
    SOURCE_LANG, TARGET_LANG, ROOM_STATE, NETWORK_TYPE,
    CAPTION_RENDER_LATENCY_MS, TRANSLATION_STATUS_TRANSITION_COUNT,
    VIDEO_PAUSE_DURATION_BUCKET, INTER_TURN_GAP_MEDIAN_MS,
    INTER_TURN_GAP_P95_MS, CALL_DURATION_MS
}
```

```swift
// iOS
enum AllowedLogKey: String {
    case callId, utteranceId, providerName, modelName
    case latencyMs, errorCode, schemaVersion
    case sourceLang, targetLang, roomState, networkType
    case captionRenderLatencyMs, translationStatusTransitionCount
    case videoPauseDurationBucket, interTurnGapMedianMs
    case interTurnGapP95Ms, callDurationMs
}

struct SafeLog {
    static func event(_ key: AllowedLogKey, _ value: Any) { /* ... */ }
}
```

**Forbidden everywhere (compile + lint enforcement):**

- `source_text`, `target_text`, `caption_text`, any conversation content
- `participant_name`, `display_name`, any PII
- Flores codes (`ind_Latn`, etc. — see §3)
- Direct `Log.d/i/w/e` / `Timber.*` (Android) — banned outside SafeLog
- Direct `print()` / `os_log` / `Logger.*` (iOS) — banned outside SafeLog

**Lint enforcement:**

- Android: custom **detekt** rule banning `android.util.Log.*` and
  `timber.log.Timber.*` outside `logging/SafeLog.kt`
- iOS: custom **SwiftLint** rule banning `print`, `os_log`, `Logger`
  outside `Logging/SafeLog.swift`
- Both lints run in CI; PR cannot merge with violations.

### 15. Clock & Ordering (cross-reference §5)

- Wall-clock timestamps appear ONLY in Firestore (`Timestamp` type) and
  local persistence; never on the wire.
- Cross-device ordering uses `seq` (per-sender monotonic).
- UI "X ago" computed from receiver's local clock.
- Caption row identity = `utterance_id` (ULID, time-sortable as a
  debugging-tie-breaker fallback only).

### 16. Enforcement Guidelines

**Story-creation + dev-story agents MUST:**

1. Use canonical names verbatim from §1 — no synonyms.
2. Reference Data Channel by `schema_version`; bump on any field change.
3. Use BCP 47 externally; convert to Flores-200 only at
   `TranslationProvider.translate()` input → model call boundary.
4. Map all failure surfaces to the named taxonomy → named UI component
   (§7). New states require ADR amendments first; provisional namespace
   (§8) available for time-boxed experiments.
5. Use only `AllowedLogKey` enum values via `SafeLog.event()`.
6. Place tests adjacent to source per platform conventions (§12).
7. Use ULID for any ID generation (§4).
8. Honor cancellation contract on `AsrProvider.stop()` (§9).
9. Reference error codes from §10; adding a code requires ADR.

**Code-review agent (CR) checks:**

- Canonical-name compliance (grep forbidden synonyms from §1).
- ID format = ULID 26-char (regex check on ID generation sites).
- Logging payload audit — any direct `Log.*` / `print` / `os_log`
  outside `SafeLog`. Any non-`AllowedLogKey` field.
- Provider interface symmetry — same method names, params, return
  contracts across both platforms (manual + spec-diff).
- Data Channel field-naming compliance (snake_case + `schema_version`
  present + `seq` present).
- Failure-state names map to known UI components.
- State-priority choreography (§7) implemented identically across
  platforms.
- Single-platform canonical-name renames → reject.
- Provisional states older than 14 days → flag for ADR-or-remove.
- Cancellation contract test exists for any `AsrProvider`
  implementation.
- Flores-200 codes never leak outside `TranslationProvider.translate()`
  → model boundary.

## Project Structure & Boundaries

### Repo Shape — Monorepo, Per-Stack Roots

Solo dev. Cross-stack changes must land atomically (new Data Channel
field touches Android + iOS + schema spec simultaneously). Monorepo
is the right call.

```
TranslatorRep/                        # Git repo root
├── README.md
├── .gitignore
├── .editorconfig
├── .github/workflows/                # GitHub Actions
│   ├── android-ci.yml
│   ├── ios-ci.yml
│   └── infra-ci.yml
│
├── android/                          # Android Studio project (see below)
├── ios/                              # Xcode project (see below)
├── infra/                            # Oracle VM deployment (LiveKit + auth-proxy + Caddy)
├── firebase/                         # Firebase config + Firestore rules
├── shared/                           # Cross-platform specs (NOT runtime code)
│   ├── data-channel-schema-v1.json   # canonical JSON Schema (patterns §5)
│   ├── canonical-names.md            # mirrors patterns §1
│   ├── error-codes.md                # mirrors patterns §10
│   └── particle-rules-fixtures/      # golden-file fixtures (patterns §11)
│       └── <rule>/case_NNN/{source.txt, expected_processed.txt, expected_target.txt, metadata.json}
│
├── docs/
│   ├── ADRs/                         # one file per ADR (auto-generated from this doc)
│   └── runbooks/{deploy-oracle-vm, week-1-validation, plan-b-escalation}.md
│
└── _bmad-output/planning-artifacts/  # BMAD outputs (incl. this architecture.md)
```

### Android Project Tree

Gradle Kotlin DSL + version catalog; single `app/` module for v1
(extract feature modules only if compile times become painful).

```
android/
├── build.gradle.kts                  # root build script
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml         # version catalog
├── detekt-config.yml                 # custom rule banning android.util.Log.* outside SafeLog (patterns §14)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml   # permissions: RECORD_AUDIO, CAMERA, FOREGROUND_SERVICE, FOREGROUND_SERVICE_PHONE_CALL, MANAGE_OWN_CALLS, POST_NOTIFICATIONS
        │   ├── kotlin/com/xaeryx/translatorrep/
        │   │   ├── TranslatorRepApp.kt          # Application: Firebase init, theme init
        │   │   ├── MainActivity.kt
        │   │   │
        │   │   ├── pairing/                     # FR-1..5 + identityPub publish
        │   │   │   ├── PairingViewModel.kt
        │   │   │   ├── PairingCodeGenerator.kt
        │   │   │   ├── PairingFirestoreRepository.kt
        │   │   │   └── ui/{PairedEmptyScreen, PairingCodeDisplay, PairingCodeInput}.kt
        │   │   │
        │   │   ├── call/
        │   │   │   ├── callSession/             # orchestration layer (patterns §1)
        │   │   │   │   ├── CallSession.kt
        │   │   │   │   ├── RoomState.kt         # enum: active|waitingForPartner|ended
        │   │   │   │   ├── LeaveAndRejoinManager.kt   # FR-32
        │   │   │   │   └── DataChannelMessage.kt # snake_case kotlinx.serialization
        │   │   │   ├── livekit/
        │   │   │   │   ├── LiveKitRoomManager.kt
        │   │   │   │   ├── LiveKitTokenFetcher.kt   # POST sfu.xaeryx.com/token
        │   │   │   │   └── InsertableStreamsE2EE.kt # keyProvider wiring
        │   │   │   ├── videoTracks/             # FR-27
        │   │   │   │   ├── VideoCapture.kt
        │   │   │   │   └── ui/{VideoTile, VideoPausedTile, VideoMutedTile}.kt
        │   │   │   └── ui/
        │   │   │       ├── PairedHomeScreen.kt
        │   │   │       ├── CallTypeSelector.kt   # FR-26
        │   │   │       ├── InCallScreen.kt
        │   │   │       ├── AudioCallControlRow.kt
        │   │   │       ├── VideoCallControlRow.kt
        │   │   │       ├── AudioRoutingToggle.kt # FR-28
        │   │   │       └── CallWaitingForPartnerState.kt  # FR-32
        │   │   │
        │   │   ├── translation/                  # FR-31 + PRD §6.4
        │   │   │   ├── asr/{AsrProvider, OnDeviceAsrProvider}.kt
        │   │   │   ├── nmt/
        │   │   │   │   ├── RawTranslationProvider.kt
        │   │   │   │   ├── Nllb200OnnxProvider.kt   # Week-1 Rank 1
        │   │   │   │   ├── Madlad400OnnxProvider.kt # Week-1 Rank 2
        │   │   │   │   ├── Gemma2OnnxProvider.kt    # Week-1 Rank 3
        │   │   │   │   └── VertexGeminiProvider.kt  # Plan B
        │   │   │   ├── particles/                # patterns §11 ParticleProcessor
        │   │   │   │   ├── ParticleProcessor.kt
        │   │   │   │   ├── ParticleRules.kt
        │   │   │   │   ├── SundaneseInsertions.kt
        │   │   │   │   ├── HonorificStripping.kt
        │   │   │   │   ├── IndirectRefusals.kt
        │   │   │   │   ├── GenZSlang.kt
        │   │   │   │   └── ReligiousExpressions.kt
        │   │   │   └── rulebased/RuleBasedTranslationProvider.kt
        │   │   │
        │   │   ├── captions/                     # FR-11..20
        │   │   │   ├── {CaptionState, Caption, Utterance, VadProcessor}.kt
        │   │   │   └── ui/
        │   │   │       ├── CaptionStackComposable.kt
        │   │   │       ├── CaptionRow.kt
        │   │   │       ├── PartialCaption.kt
        │   │   │       ├── TranslationUnavailableMarker.kt
        │   │   │       ├── SundanesePlaceholderRow.kt
        │   │   │       └── JumpToLatestPill.kt
        │   │   │
        │   │   ├── e2ee/                         # FR-29 + ADR-A2
        │   │   │   ├── X25519Identity.kt
        │   │   │   ├── EphemeralKeyExchange.kt
        │   │   │   ├── KeyDerivation.kt          # HKDF-SHA256
        │   │   │   └── ui/E2EEKeyExchangeIndicator.kt
        │   │   │
        │   │   ├── ui/components/
        │   │   │   ├── MonochromeGlassPanel.kt   # RenderEffect + BlurEffect
        │   │   │   ├── theme/{TranslatorRepTheme, ThemeTokens, DarkTheme, LightTheme, ImageBackgroundTheme}.kt
        │   │   │   └── BackgroundImageOverlay.kt
        │   │   │
        │   │   ├── settings/                     # FR-21..24 + FR-30
        │   │   │   ├── {SettingsViewModel, ThemeRepository, TranscriptHistoryRepository, PrivacySummary}.kt
        │   │   │   └── ui/{SettingsScreen, ThemePicker, BackgroundImagePicker, CameraPermissionFlow}.kt
        │   │   │
        │   │   ├── quality/                      # UX spec quality tool + her reaction
        │   │   │   ├── {QualityReviewRepository, HerReactionRepository}.kt
        │   │   │   └── ui/{QualityReviewScreen, HerSideOneTapReaction}.kt
        │   │   │
        │   │   ├── data/
        │   │   │   ├── firestore/{UserDocument, PairDocument, CallDocument}.kt
        │   │   │   ├── local/                    # Room + SQLCipher
        │   │   │   │   ├── TranslatorRepDatabase.kt
        │   │   │   │   ├── records/{CallRecord, CaptionRecord, QualityReviewRecord, HerReactionRecord, ThemeStateRecord}.kt
        │   │   │   │   └── dao/
        │   │   │   └── secure/SecureStorage.kt   # EncryptedSharedPreferences wrapper
        │   │   │
        │   │   ├── platform/
        │   │   │   ├── connectionservice/{TranslatorRepConnectionService, PhoneAccountManager}.kt   # FR-7/8
        │   │   │   ├── fcm/IncomingCallMessagingService.kt
        │   │   │   ├── appcheck/AppCheckTokenProvider.kt
        │   │   │   ├── permissions/PermissionManager.kt
        │   │   │   └── audio/AudioRouter.kt
        │   │   │
        │   │   ├── logging/                      # patterns §14 SafeLog
        │   │   │   ├── SafeLog.kt
        │   │   │   ├── AllowedLogKey.kt
        │   │   │   ├── ErrorCode.kt              # patterns §10 registry
        │   │   │   └── CrashlyticsConfig.kt
        │   │   │
        │   │   └── ids/UlidGenerator.kt
        │   │
        │   └── res/                              # strings (en + id), drawables, mipmaps
        │
        ├── test/kotlin/...                       # JVM unit (mirrors src/main; fixtures from /shared)
        └── androidTest/kotlin/...                # Compose UI + instrumented integration
```

### iOS Project Tree

Xcode App template, SwiftUI lifecycle, SPM dependencies, single app
target + test target.

```
ios/
├── TranslatorRep.xcodeproj/
├── .swiftlint.yml                    # custom rule banning print/os_log/Logger outside SafeLog (patterns §14)
├── TranslatorRep/
│   ├── TranslatorRepApp.swift        # @main App
│   ├── Info.plist                    # UIBackgroundModes:voip, mic + camera + photo lib usage descriptions
│   ├── TranslatorRep.entitlements    # com.apple.developer.voip, push environment
│   │
│   ├── Pairing/                      # FR-1..5
│   │   ├── {PairingViewModel, PairingCodeGenerator, PairingFirestoreRepository}.swift
│   │   └── UI/{PairedEmptyScreen, PairingCodeDisplay, PairingCodeInput}.swift
│   │
│   ├── Call/
│   │   ├── CallSession/{CallSession, RoomState, LeaveAndRejoinManager, DataChannelMessage}.swift
│   │   ├── LiveKit/{LiveKitRoomManager, LiveKitTokenFetcher, InsertableStreamsE2EE}.swift
│   │   ├── VideoTracks/{VideoCapture, VideoTile, VideoPausedTile, VideoMutedTile}.swift
│   │   └── UI/{PairedHomeScreen, CallTypeSelector, InCallScreen, AudioCallControlRow, VideoCallControlRow, AudioRoutingToggle, CallWaitingForPartnerState}.swift
│   │
│   ├── Translation/
│   │   ├── ASR/{AsrProvider, WhisperCppAsrProvider}.swift   # Whisper.cpp XCFramework + Core ML
│   │   ├── NMT/{RawTranslationProvider, Nllb200CoreMLProvider, Madlad400CoreMLProvider, Gemma2LlamaCppProvider, VertexGeminiProvider}.swift
│   │   ├── Particles/{ParticleProcessor, ParticleRules, SundaneseInsertions, HonorificStripping, IndirectRefusals, GenZSlang, ReligiousExpressions}.swift
│   │   └── RuleBased/RuleBasedTranslationProvider.swift
│   │
│   ├── Captions/
│   │   ├── {CaptionState, Caption, Utterance, VadProcessor}.swift
│   │   └── UI/{CaptionStackView, CaptionRow, PartialCaption, TranslationUnavailableMarker, SundanesePlaceholderRow, JumpToLatestPill}.swift
│   │
│   ├── E2EE/
│   │   ├── {X25519Identity, EphemeralKeyExchange, KeyDerivation}.swift
│   │   └── UI/E2EEKeyExchangeIndicator.swift
│   │
│   ├── UI/Components/
│   │   ├── {MonochromeGlassPanel, BackgroundImageOverlay}.swift
│   │   └── Theme/{TranslatorRepStyle, ThemeTokens, DarkTheme, LightTheme, ImageBackgroundTheme}.swift
│   │
│   ├── Settings/                     # FR-21..24 + FR-30
│   │   ├── {SettingsViewModel, ThemeRepository, TranscriptHistoryRepository, PrivacySummary}.swift
│   │   └── UI/{SettingsScreen, ThemePicker, BackgroundImagePicker, CameraPermissionFlow}.swift
│   │
│   ├── Quality/
│   │   ├── {QualityReviewRepository, HerReactionRepository}.swift
│   │   └── UI/{QualityReviewScreen, HerSideOneTapReaction}.swift
│   │
│   ├── Data/
│   │   ├── Firestore/{UserDocument, PairDocument, CallDocument}.swift
│   │   ├── Local/                    # SwiftData with NSFileProtectionComplete
│   │   │   ├── TranslatorRepStore.swift
│   │   │   └── Records/{CallRecord, CaptionRecord, QualityReviewRecord, HerReactionRecord, ThemeStateRecord}.swift
│   │   └── Secure/SecureStorage.swift # Keychain wrapper
│   │
│   ├── Platform/
│   │   ├── CallKit/{TranslatorRepCXProvider, IncomingCallHandler}.swift   # FR-7/8
│   │   ├── PushKit/PushKitHandler.swift
│   │   ├── AppCheck/AppCheckTokenProvider.swift
│   │   ├── Permissions/PermissionManager.swift
│   │   └── Audio/AudioRouter.swift   # AVAudioSession routing
│   │
│   ├── Logging/{SafeLog, AllowedLogKey, ErrorCode, CrashlyticsConfig}.swift
│   │
│   └── IDs/UlidGenerator.swift
│
├── TranslatorRepTests/               # XCTest + Swift Testing (iOS 18+)
│   └── (mirrors source; fixtures from /shared via Bundle reference)
└── TranslatorRepUITests/             # golden screen flows
```

### Oracle VM Deployment (`infra/`) — ADR-A1 + ADR-C1

```
infra/
├── docker-compose.yml                # livekit-server + redis + caddy + auth-proxy
├── livekit.yaml                      # LiveKit config (keys, TURN, empty_timeout: 300)
├── Caddyfile                         # TLS for sfu.xaeryx.com + auth.xaeryx.com (Let's Encrypt auto)
├── .env.example                      # env var template
├── .env                              # gitignored; real values (LiveKit keys, Firebase project ID, etc.)
│
├── auth-proxy/                       # Node.js LiveKit token mint + App Check verification
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── index.ts                  # Express app
│   │   ├── routes/token.ts           # POST /token → verify App Check → mint LiveKit JWT with callType
│   │   ├── middleware/appCheck.ts    # Firebase Admin SDK JWKS verification
│   │   └── livekit.ts                # livekit-server-sdk wrapper
│   ├── Dockerfile
│   └── tests/
│
├── scripts/
│   ├── provision-oracle-vm.sh        # idempotent VM setup (Docker, firewall, swap)
│   ├── deploy.sh                     # git pull + docker compose up -d
│   └── rotate-livekit-keys.sh
│
└── README.md                         # runbook for first-time deploy + cert renewal monitoring
```

### Firebase Config (`firebase/`)

```
firebase/
├── firebase.json                     # project config (no Functions; Auth + Firestore + App Check only)
├── .firebaserc                       # project IDs
├── firestore.rules                   # restrict access to pair members + call participants
├── firestore.indexes.json
├── appcheck/
│   ├── android-providers.md          # Play Integrity setup notes
│   └── ios-providers.md              # DeviceCheck + Apple Developer cert setup notes
└── README.md
```

### Architectural Boundaries

| Boundary | Protocol | Auth | E2EE? |
|---|---|---|---|
| Mobile ↔ LiveKit SFU | WSS (signaling) + DTLS-SRTP (media) over UDP | LiveKit JWT (minted by auth-proxy) | **YES** — Insertable Streams on top of DTLS-SRTP (media + data channel) |
| Mobile ↔ Auth-proxy | HTTPS | Firebase App Check token + Auth UID | TLS only |
| Mobile ↔ Firebase Auth | HTTPS (Firebase SDK) | Anonymous Auth state | TLS only |
| Mobile ↔ Firestore | HTTPS (Firebase SDK) | Anonymous Auth + Firestore rules | TLS only — only public keys + metadata stored, no conversation content |
| Mobile ↔ Crashlytics | HTTPS (Firebase SDK) | Anonymous Auth | TLS only; sanitized fields per patterns §14 |
| Auth-proxy ↔ Firebase Admin JWKS | HTTPS | none required (public JWKS) | TLS only |
| Auth-proxy ↔ LiveKit server | localhost (Docker network) | Shared API key/secret | unnecessary (single VM) |
| iOS ↔ APNs PushKit | APNs over HTTP/2 | Apple Developer cert | TLS only |
| Android ↔ FCM | FCM over HTTPS (Firebase SDK) | Anonymous Auth | TLS only |

**Within mobile clients (logical boundaries):**

- **UI ↔ CallSession**: UI observes `RoomState`, `CaptionState`
  (StateFlow / `@Observable`). UI never calls providers directly
  (patterns §13).
- **CallSession ↔ Providers**: `AsrProvider` +
  `RuleBasedTranslationProvider` (which wraps `RawTranslationProvider`
  + `ParticleProcessor`).
- **ParticleProcessor ↔ RawTranslationProvider**: composition;
  ParticleProcessor never touches model APIs directly.
- **Provider impls ↔ Models**: only here Flores-200 codes appear
  (patterns §3).
- **CallSession ↔ LiveKitRoomManager**: CallSession owns lifecycle;
  LiveKit room is CallSession-internal.
- **CallSession ↔ E2EE module**: CallSession injects key from
  `EphemeralKeyExchange` into LiveKit `e2eeOptions.keyProvider` at
  room-create.

### Requirements → Structure Mapping

| FR / source | Lives in |
|---|---|
| FR-1..5 Pairing + FR-29 E2EE setup | `pairing/` + `e2ee/` |
| FR-6..10 Calling (Place/Receive/End/Audio Quality) | `call/callSession/` + `call/livekit/` + `platform/{callkit,connectionservice,fcm,pushkit}/` |
| FR-11..16 Translation Pipeline + FR-31 on-device | `translation/` (asr + nmt + particles + rulebased) + `captions/VadProcessor.*` |
| FR-17..20 Captions UI | `captions/ui/` |
| FR-21..24 Settings | `settings/` |
| FR-26 Audio vs Video selector | `call/ui/CallTypeSelector.*` + `pairing/ui/PairedEmptyScreen.*` |
| FR-27 Video pipeline + failure UX | `call/videoTracks/` + `call/ui/VideoCallControlRow.*` + failure markers in `captions/ui/` |
| FR-28 Audio routing toggle | `platform/audio/` + `call/ui/AudioRoutingToggle.*` |
| FR-30 Camera permission flow | `settings/ui/CameraPermissionFlow.*` + `platform/permissions/` |
| FR-32 Leave-and-rejoin | `call/callSession/LeaveAndRejoinManager.*` + `call/ui/CallWaitingForPartnerState.*` + `infra/livekit.yaml` (`empty_timeout: 300`) |
| UX-spec quality review (Bania-only) | `quality/QualityReviewRepository.*` + `quality/ui/QualityReviewScreen.*` |
| UX-spec her one-tap reaction | `quality/HerReactionRepository.*` + `quality/ui/HerSideOneTapReaction.*` |
| UX-spec 3 themes + image bg | `ui/components/theme/` + `settings/ui/{ThemePicker, BackgroundImagePicker}.*` |
| UX-spec metadata logging | `call/callSession/CallSession.*` (emits) + `data/local/records/CallRecord.*` (persists) |
| Plan B Gemini prompt | `translation/nmt/VertexGeminiProvider.*` referencing `shared/prompts/id-en-plan-b.md` |
| ADR-A1 SFU deployment | `infra/` |
| ADR-C1 auth-proxy | `infra/auth-proxy/` |
| ADR-C3 Firestore schema + rules | `firebase/firestore.rules` + `data/firestore/` (per platform) |

### Integration Points (Data Flow Summary)

```
FIRST LAUNCH (per device):
  App → Firebase Auth (anonymous sign-in) → uid
  App → X25519Identity.generate() → store privKey in Keychain / EncryptedSharedPreferences
  App → Firestore: write /users/{uid}.identityPub

PAIRING (UJ-1):
  Bania: PairingCodeGenerator → write /codes/{code} → display on screen
  Her: input code → PairingFirestoreRepository.pair() → write /pairs/{pairId}
  Both: Firestore observer → transition UI to PairedHomeScreen

CALL PLACEMENT (UJ-2):
  Caller: CallSession.startCall(callType) →
    1. EphemeralKeyExchange.generate() → publish to /calls/{callId}/ephemeralPub/{uid} (signed)
    2. LiveKitTokenFetcher.fetch() → POST sfu.xaeryx.com/token (App Check + uid + callType)
    3. LiveKitRoomManager.join(token, e2eeOptions{keyProvider: ECDH(myEphemeralPriv, peerEphemeralPub)})
    4. Trigger callee's APNs PushKit / FCM via LiveKit room webhook OR Firestore observer
  Callee: CallKit/ConnectionService incoming UI → accept → same join flow

IN-CALL PER-UTTERANCE LOOP:
  Mic → WebRTC local audio track tap → AsrProvider.start() → AsrEvent.PartialText / .FinalText
  FinalText → RuleBasedTranslationProvider.translate():
    ParticleProcessor.preProcess() → RawTranslationProvider.translate() → ParticleProcessor.postProcess()
    → TranslationResult { targetText, particlesPreserved, status, renderMode }
  CallSession.publishCaption() → LiveKit Data Channel (E2EE) → peer's CallSession receives → CaptionStack renders

LEAVE-AND-REJOIN (FR-32):
  User taps end-Call → LeaveAndRejoinManager.leave() → LiveKit room.disconnect()
  Other side: LiveKit participantDisconnected event → RoomState = waitingForPartner → CallWaitingForPartnerState overlay
  Leaver: post local notification "Bania is still in the call — Rejoin"
  Rejoin: tap notification → LeaveAndRejoinManager.rejoin() → fetch fresh LiveKit token → rejoin same room (if empty_timeout not yet fired)
  After 5 min empty: LiveKit destroys room → both apps observe /calls/{callId}.endedAt → return to PairedHome
```

### CI/CD Per Stack

| Workflow | Triggers | Steps |
|---|---|---|
| `android-ci.yml` | PR + push to main, paths `android/**` or `shared/**` | detekt → unit tests → Compose UI tests → Roborazzi screenshot diff → assembleRelease APK artifact |
| `ios-ci.yml` | PR + push to main, paths `ios/**` or `shared/**` | SwiftLint → xcodebuild test → snapshot tests → archive (TestFlight Ad Hoc on tag) |
| `infra-ci.yml` | PR + push to main, paths `infra/**` | yamllint livekit.yaml + Caddyfile → docker compose config validate → on tag: ssh + deploy.sh on Oracle VM |

GitHub Actions free tier covers all three at solo-dev volume.

### Local Dev Workflow

- **Android**: open `android/` in Android Studio → connect physical Galaxy → run `app` configuration
- **iOS**: open `ios/TranslatorRep.xcodeproj` in Xcode → connect physical iPhone (TestFlight Ad Hoc cert) → run
- **LiveKit (local)**: `cd infra && docker compose up` → `livekit-server` on `localhost:7880` → point mobile dev builds at `ws://10.0.2.2:7880` (Android emulator) or `ws://localhost:7880` (iOS simulator)
- **Firebase emulator**: optional; `firebase emulators:start` for offline Auth + Firestore testing

## Architecture Validation Results

### Coherence Validation ✅

**Decision compatibility:**

| Pairing | Verdict |
|---|---|
| LiveKit OSS Docker on Oracle ARM A1 + Insertable Streams E2EE | ✅ LiveKit ≥v2.0 supports `e2eeOptions`; multi-arch Docker covers ARM A1 |
| X25519 ECDH via Firestore + LiveKit `keyProvider` | ✅ Firestore handles public keys only; ECDH-derived AES-GCM key feeds Insertable Streams as opaque bytes |
| On-device translation + ParticleProcessor rules-based pre/post | ✅ ParticleProcessor sits outside the model — model-agnostic; works for NLLB/MADLAD/Gemma/Plan-B |
| Cloud Run removal + Oracle VM auth-proxy | ✅ Same App Check verification; same LiveKit token mint; one fewer hop |
| Leave-and-rejoin (FR-32) + LiveKit `empty_timeout: 300` | ✅ Room lifecycle naturally supports re-join during empty window |
| BCP 47 external + Flores-200 internal | ✅ Strict boundary at `TranslationProvider.translate()` |
| 40/60 audio + 50/50 video layout + Theme C × video | ✅ Theme C video replaces upper bg only |

No contradictions found.

**Pattern consistency:** naming + structure align (patterns §1/§3/§12); Data Channel
snake_case vs Firestore camelCase is intentional/documented; provider
symmetry + ParticleProcessor decorator compose cleanly; failure-state
taxonomy maps 1:1 to UI components + error codes; SafeLog enforcement
is mechanical (detekt/SwiftLint), not aspirational.

**Structure alignment:** project structure supports all ADRs;
boundaries table covers every cross-component edge; data-flow summary
traces each FR to runtime path.

### Requirements Coverage Validation ✅

**Functional Requirements (24 PRD + 7 CA-surfaced = 31 total):**

All FR-1..FR-24 and FR-26..FR-32 have architectural homes per the
Requirements→Structure Mapping table. FR-25 reserved (unused).

**Non-Functional Requirements:**

| NFR | Architecturally supported? |
|---|---|
| Privacy (WhatsApp-equivalent) | ✅ E2EE media + on-device translation + metadata-only Firestore; Week-1 verification gate |
| Provider abstraction | ✅ AsrProvider + TranslationProvider interfaces, symmetric surface, decorator pattern |
| Cost ($0/mo + ~$10/yr domain) | ✅ Oracle Always-Free + Firebase Spark + on-device ML + sideload; domain as fixed infra cost |
| Latency (<8s median, p95 <12s) | ✅ Relaxed targets documented; tunable via VAD + provider swap |
| Quality (≥60% Plan A) | ✅ Relaxed target documented; Week-1 validation gate locks model; Plan B escape |
| Battery (best-effort) | ✅ Acknowledged; Whisper.cpp `small→base` downgrade path noted |
| App size (+250 MB to ~1.5 GB) | ✅ Sideload makes this acceptable |
| Reliability (never silent drop) | ✅ Failure-state taxonomy + state-priority choreography |
| Cold-start | ✅ Cloud Run removed → Oracle VM always-on |
| Observability | ✅ Crashlytics opt-in + SafeLog facade + sanitized allowlist + metadata local-only |

### Implementation Readiness Validation ✅

- **Decisions complete:** all ADRs A-G specified with rationale; Plan B
  escalation path explicit; reconciliation deliverables enumerated.
- **Patterns comprehensive:** 16 categories (canonical names, rename
  policy, ID format, schema versioning, idempotency, clock skew,
  failure taxonomy, state priority, provisional namespace, provider
  symmetry, error codes, ParticleProcessor module, project structure,
  state mgmt, SafeLog, enforcement). Examples provided.
- **Structure complete:** per-stack file trees, boundary table,
  data-flow summary, CI/CD per stack, local dev workflow.

### Gap Analysis Results

**Critical gaps (block implementation): None.** All load-bearing
decisions are made.

**Important gaps (block first-sprint execution, not architecture):**

| # | Gap | Owner / location |
|---|---|---|
| I.1 | Week-1 validation runbook | `docs/runbooks/week-1-validation.md` — recording setup, transcription protocol, paired ✓/⚠/✗ rating, model swap procedure. **Includes Mary's pre-validation conversation with girlfriend** (I.18 below) and Winston's regression corpus + kill criteria |
| I.2 | NLLB-200 ONNX export specifics | scaffolding-time — exact model variant, quantization, tokenizer source, expected load time |
| I.3 | Whisper.cpp model file (iOS) | scaffolding-time — `ggml-small.bin` vs `ggml-base.bin`; Core ML conversion script; bundle-size impact |
| I.4 | Sundanese-insertions canonical list | `shared/SundaneseInsertions.md` — extract DR §4's ~12 high-frequency tokens to a versioned file both platforms reference |
| I.5 | PRD reconciliation deliverables | upstream PRD doc (per ADR-G; deferred to post-CA — CE workflow picks up) |
| I.6 | UX spec reconciliation deliverables | upstream UX spec doc (per ADR-G; includes Theme B Light *removal* per S3 below + new components) |
| I.7 | `livekit.yaml` E2EE config verification | scaffolding-time — **gate to Story 3 minimum**; E2EE off until media/captions flow, else Story 1 `connect()` fails silently |
| I.8 | `firestore.rules` policy | `firebase/firestore.rules` — schema in ADR-C3; rules implementation deferred to story |
| I.9 | App Check JWKS verification implementation | `infra/auth-proxy/src/middleware/appCheck.ts` |
| I.10 | Plan B Gemini prompt file | `shared/prompts/id-en-plan-b.md` — only needed if Plan B activates |
| **I.11** | **Auth-proxy API contract** | `shared/auth-proxy-api.md` — `POST /token` request/response shape, error codes, token TTL |
| **I.12** | **ULID library pinning per platform** | `shared/canonical-names.md` — pick Android lib (e.g. `com.aallam.ulid:ulid-kotlin`); pick iOS lib at scaffolding |
| **I.13** | **State-derivation rules** | `shared/state-derivation.md` — RoomState transitions are client-derived from `room.remoteParticipants`, NOT server-pushed |
| **I.14** | **Frozen regression corpus** | `shared/regression-corpus/` — ~200 utterances tagged by DR section (§1 particles / §3 slang / §4 SU insertions / §6 cultural-pragmatic); run on every model swap AND every ParticleProcessor rule change |
| **I.15** | **Three-layer translation capture pipeline** | SafeLog-redacted debug-flag-gated trace: raw NMT output → post-processed → displayed text. Enables week-4 quality-regression attribution |
| **I.16** | **Written kill-criterion per model** | Part of I.1 — quantitative threshold per model (not vibes); e.g., "if WER-equivalent on SU pair drops below X, fall to next provider" |
| **I.17** | **Solo-dev morale-collapse line** | Part of I.1 — scope-cut criteria when timeline slips (e.g., week-5 trigger: defer quality-review tool to v1.1) |
| **I.18** | **Pre-validation conversation with girlfriend** | Part of I.1 — structured 15-min: "what would make you stop using this?" + "is ID-only acceptable for 4-8 weeks while SU comes in v2?" Converts inferred emotional spec to evidence |
| **I.19** | **Document single failure domain risk** | docs/runbooks — auth-proxy + LiveKit + Redis on one A1 VM = single failure domain; accepted v1 risk, monitored |
| **I.20** | **Gate I.7 E2EE verification to Story 3** | sprint planning note — Story 1 connects without E2EE; Story 3 wires `e2eeOptions` after media/captions baseline established |

**Nice-to-have gaps:** Grafana/Prometheus monitoring (deferred to v2);
exact detekt/SwiftLint rule configs (scaffolding-time boilerplate);
localization beyond en+id (out of v1 scope); network-condition
simulation testing harness (defer to QA story).

### Validation Issues Addressed (Party-Mode Outcomes)

Final Party Mode brought in Mary (BA), John (PM), plus Winston and
Amelia returning. Four substantive critiques. Resolutions:

- **Mary's evidence-quality framing** — ~40% verified, ~35% reasonable
  inference, ~25% load-bearing assumption. Addressed via I.14
  (regression corpus) for the translation-quality assumption, and I.18
  (pre-validation conversation) for the emotional-spec inference.
- **John's scope-to-value challenge (S1/S2/S3/S4)** — surfaced as a
  strategic question to Bania. Resolutions:
  - **S1 (Plan B vs Plan A on translation):** Hold on Plan A (on-device).
    SCOPE EXPANSION rationale (privacy axiom + WhatsApp-equivalent
    posture for translation text) is deliberate, not accidental.
  - **S2 (LiveKit Cloud vs self-host):** Hold on self-host. SCOPE
    EXPANSION specifically noted LiveKit Cloud Build tier would exceed
    50 GB/mo free egress cap with video.
  - **S3 (theme count):** **Middle path — drop Theme B (Light high-contrast)
    from v1. Keep Theme A (Dark default) + Theme C (Custom image bg).**
    UX spec reconciliation (I.6) updates §"Color System" accordingly;
    `ThemePicker` becomes 2-option.
  - **S4 (E2EE):** Hold. Insertable Streams is config + key provider,
    not a week of work; trust narrative for her is real.
- **Winston's quality-cliff warning** — addressed via I.14 + I.15 + I.16
  (regression corpus + three-layer capture + written kill criteria).
- **Amelia's at-keyboard gaps** — addressed via I.11 + I.12 + I.13 + I.20.

### Architecture Completeness Checklist

**Requirements Analysis**

- [x] Project context thoroughly analyzed
- [x] Scale and complexity assessed (HIGH + personal-scale)
- [x] Technical constraints identified
- [x] Cross-cutting concerns mapped (10 concerns)

**Architectural Decisions**

- [x] Critical decisions documented with versions
- [x] Technology stack fully specified with version-pinning policy
- [x] Integration patterns defined (boundary table + data flow)
- [x] Performance considerations addressed (relaxed targets + Week-1 gate)

**Implementation Patterns**

- [x] Naming conventions established (canonical names + Term Forms)
- [x] Structure patterns defined (feature folders, file naming)
- [x] Communication patterns specified (Data Channel, Firestore, providers)
- [x] Process patterns documented (failure taxonomy, choreography, error codes, SafeLog)

**Project Structure**

- [x] Complete directory structure defined (per stack)
- [x] Component boundaries established (boundary table)
- [x] Integration points mapped (data flow)
- [x] Requirements to structure mapping complete

**All 16 items checked. ✅**

### Architecture Readiness Assessment

**Overall Status: READY WITH MINOR GAPS**

Not full "READY FOR IMPLEMENTATION" only because reconciliation
deliverables (I.5, I.6) and Week-1 runbook (I.1, now expanded with
I.14-I.18) are scoped-but-unwritten. The architecture itself is
implementation-ready; the first sprint can't fully execute until those
land.

**Confidence Level: high** — every load-bearing decision has documented
rationale; failure modes have first-class taxonomy; cross-platform
parity is multiply-enforced; Plan B escape valve removes existential
translation-quality risk.

**Key Strengths:**

1. **Provider abstraction is genuinely load-bearing** — enables Plan B
   escape AND v2 Sundanese without rewrites
2. **Privacy posture is per-data-class explicit** — media E2EE,
   translation on-device, metadata only on Bania's infra; testable
   Week-1 verification
3. **Cross-platform parity discipline locked at multiple layers** —
   canonical names, schema fixtures, lint rules, code-review checks
4. **Failure-state taxonomy as first-class UI** — every failure has a
   name, an error code, a UI component, and a priority slot
5. **Self-hosted SFU on free tier delivers E2EE at $0/mo** — the
   architecturally hardest constraint is reconciled cleanly
6. **Rules-based particle preservation sidesteps the "small models
   can't follow prompts" problem** — deterministic, testable,
   model-agnostic

**Areas for Future Enhancement:**

- v2 Sundanese full-clause translation (chunked Recognize for SU)
- App Store / Play Store distribution if ever pursued (breaks +250 MB
  ceiling)
- Tor / I2P metadata anonymization (only if threat model rises)
- Adaptive battery management (model downgrade on low battery)

### Implementation Handoff

**AI Agent Guidelines:**

- Follow ADRs A-G exactly as documented; departure requires amendment
  to this doc first
- Use canonical names (patterns §1) verbatim in stories, ACs, code, and
  reviews
- Tests adjacent to source per platform conventions; cross-platform
  fixtures live in `/shared/`
- All logging through `SafeLog` with allowlisted keys only
- Failure surfaces map to taxonomy + UI components per patterns §7

**First Implementation Priority:**

Sprint 1's stories are **four parallel scaffolding stories**, one per
stack:

1. `android/` — Android Studio Empty Activity (Compose) scaffold + version catalog + dependency wiring
2. `ios/` — Xcode App / SwiftUI template + SPM deps + Info.plist VoIP entitlements
3. `infra/` — Oracle VM provision + Docker Compose stack (LiveKit + Redis + Caddy + auth-proxy stub)
4. `firebase/` — `firebase init` (Auth + Firestore + App Check both platforms)

Sprint 1's last story: **Week-1 validation gate setup** (translation
model bake-off, including Mary's pre-validation conversation with
girlfriend per I.18 and Winston's regression-corpus harness per I.14).
All FR work blocks on its outcome.

**Amelia's first end-to-end test** (catches the most architectural
mistakes early):

```
android/app/src/androidTest/.../CallConnectivityTest.kt
testTwoClientsJoinSameRoom_bothSeeRemoteParticipant_within10s()
```

Two `LiveKitRoom` instances against the real Oracle SFU using the real
auth-proxy; assert both observe `ParticipantConnected` < 10s; then
`disconnect()` and assert `RoomState.ended`. Exercises six
architectural surfaces in one red bar: auth-proxy contract, Firebase
token → LiveKit JWT exchange, Caddy TLS, livekit.yaml validity, Oracle
ARM reachability, RoomState derivation, clean teardown.
