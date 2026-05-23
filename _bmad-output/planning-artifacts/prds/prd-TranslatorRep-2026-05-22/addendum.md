---
title: TranslatorRep PRD — Addendum
status: superseded-by-architecture
created: 2026-05-22
updated: 2026-05-22
reconciled-with: architecture.md
reconciliation-date: 2026-05-22
companion-to: prd.md
---

# Addendum: Implementation References for TranslatorRep PRD

> **⚠️ SCOPE EXPANSION NOTICE (2026-05-22):** Portions of this addendum (specifically: §FR-14 backend Cloud Run path + Gemini AI Studio binding; §6.5 warmup ping pattern; §FR-22 backend reflow chain) are **SUPERSEDED by `architecture.md`** following the SCOPE EXPANSION reconciled into `prd.md` rev 3. The on-device translation pipeline (Plan A — NLLB-200 / MADLAD / Gemma 2B) replaces FR-14's cloud path for v1; the Oracle VM (Ampere A1 + Caddy + Docker Compose `livekit-server` + `redis` + `auth-proxy`) replaces Cloud Run; FR-22 reflow is only relevant under Plan B (Vertex AI Gemini activation per Story 3.9). **For v1 implementation, treat this addendum as the canonical reference ONLY for: FR-1 → FR-5 (Pairing/Identity), FR-6 → FR-9 (Calling control surfaces — CallKit/PushKit/ConnectionService/FCM), FR-10 (Opus codec defaults), FR-11 + FR-13 (audio capture + on-device ASR), FR-15 Data Channel schema, FR-17 → FR-20 (Captions UI primitives), FR-21 (transcript history storage), FR-24 (privacy summary surface).** The §FR-14 backend-proxy specifications remain valid as the Plan B fallback reference.

This document holds the implementation-level references extracted from the PRD per the capability-vs-implementation discipline rule. The PRD specifies *capabilities*; this addendum gives downstream Architecture (CA) and Implementation (DS) phases the concrete technology choices and API surface names that v1 actually binds to.

Where this addendum and the [Technical Research](../research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md) overlap, the TR is the authoritative source for *why* a choice was made; this addendum is the canonical *what* for the PRD's FRs (subject to the SCOPE EXPANSION notice above).

---

## Capability → Implementation Mapping

### FR-1, FR-2, FR-3, FR-4, FR-5 (Pairing & Identity)

- **Anonymous sign-in:** Firebase Auth `signInAnonymously()`. Stable user ID stored in `FirebaseAuth.currentUser.uid`.
- **Pairing record:** Firestore document at `/pairs/{pairId}` with `{memberA: uid, memberB: uid, createdAt: timestamp}`. Both users hold a reference via `/users/{uid}/pairId`.
- **Pairing Code storage:** Firestore document at `/codes/{6digit}` with `{ownerUid: uid, createdAt, expiresAt}`. Generated client-side, collision-checked against `/codes/{6digit}` before commit.
- **Display name:** stored at `/users/{uid}/displayName` (string); defaulted to "Partner" client-side when absent.

### FR-6, FR-7, FR-8, FR-9 (Calling)

- **WebRTC service:** LiveKit Cloud Build tier. Room created via the LiveKit server SDK from the backend on Call initiation; client joins with a short-lived JWT minted by the backend.
- **iOS incoming-Call notification:** APNs VoIP push (PushKit) → `CXProvider.reportNewIncomingCall(with:update:)`. Required Info.plist entries: `UIBackgroundModes` includes `voip`; entitlement `com.apple.developer.voip`. Per Apple's iOS 13+ requirement: report to CallKit synchronously before completing the push handler, or the app is terminated and the token revoked.
- **Android incoming-Call notification:** FCM high-priority data message → `FirebaseMessagingService.onMessageReceived` → `telecomManager.addNewIncomingCall(phoneAccountHandle, extras)` against a self-managed `PhoneAccount` (`CAPABILITY_SELF_MANAGED`). Manifest permissions: `android.permission.FOREGROUND_SERVICE`, `android.permission.FOREGROUND_SERVICE_PHONE_CALL`, `android.permission.MANAGE_OWN_CALLS`. Foreground service type `phoneCall` (Android 14+ requirement).
- **End-Call flow:** client disconnects from LiveKit room; backend listens for `participant_disconnected` and tears down the room when both participants leave.

### FR-10 (Audio quality)

- **Codec:** Opus, LiveKit default settings (24 kbps target). LiveKit's WebRTC stack handles AEC, AGC, and noise suppression.
- **Acceptance test:** paired listening test protocol — both users on speakers; both users on headsets; one of each. Three Calls of ≥10 minutes each. Recorded subjective rating against a 5-point scale (1=unusable, 5=clear).

### FR-11, FR-13 (Audio capture and on-device ASR)

- **Audio tap from WebRTC local track:**
  - **Android:** LiveKit Kotlin SDK `localAudioTrack.setAudioBufferCallback(AudioBufferCallback)`. Receives PCM 16-bit mono in real time.
  - **iOS:** LiveKit Swift SDK `AudioManager.shared.capturePostProcessingDelegate` implementing `AudioCustomProcessingDelegate`. Receives PCM via `audioProcessingProcess(audioBuffer:)`.
- **ASR Provider (Android):** `android.speech.SpeechRecognizer.createOnDeviceSpeechRecognizer(context)` (API 33+). Runtime availability probe at app launch via `SpeechRecognizer.checkRecognitionSupport(intent, executor, callback)` against `RecognitionSupport.getInstalledOnDeviceLanguages()` — confirm `id-ID` and `en-US` present.
- **ASR Provider (iOS):** Bundled Whisper.cpp small multilingual model, ~140 MB. Compiled with Core ML support; inference via `whisper_full_with_state` against PCM buffers chunked into Utterance-sized windows. Model file shipped inside the app bundle (no on-demand download).
- **VAD:** WebRTC VAD (`webrtcvad` library, mode 2 — moderate sensitivity) running on the tapped PCM stream. Segment commit on 700ms of silence after detected speech. Force segment at 15 seconds.

### FR-14 (Translation via backend proxy)

- **Backend service:** Cloud Run in `asia-southeast1`, Node.js runtime, container memory 512 MiB / 1 vCPU, min-instances=0, max-instances=3, concurrency=80, timeout 30s.
- **App attestation:** Firebase App Check verified via Firebase Admin SDK `getAppCheck().verifyToken(token)` on every request. JWKS endpoint: `https://firebaseappcheck.googleapis.com/v1/jwks` (cached automatically by Admin SDK).
- **Translation Provider:** Google AI Studio Gemini 2.5 Flash via `@google/genai` SDK. Model: `gemini-2.5-flash`. Call: `ai.models.generateContent({model, contents, config: {systemInstruction, temperature: 0.2}})`.
- **System prompt:** version-controlled at `prompts/id-en-v1.md` and `prompts/en-id-v1.md` in the backend repo. Source: [DR §"Improved Gemini 2.5 Flash System Prompt"](../research/domain-conversational-indonesian-sundanese-linguistics-research-2026-05-22.md).
- **Caching:** Gemini context caching enabled; system prompt counts as cached input on hits (10% of input price).
- **Retry/backoff:** exponential backoff with full jitter, base 1s, cap 32s, max 3 retries. Retry only on 429 / 5xx. Translation is idempotent; safe to retry.

### FR-15 (Data Channel Message)

- **Transport:** WebRTC data channel (LiveKit `LocalParticipant.publishData(byteArray, reliable=true)`).
- **Payload schema:**
  ```json
  {
    "utterance_id": "uuid",
    "speaker_uid": "string",
    "source_lang": "en|id",
    "source_text": "string",
    "target_lang": "en|id",
    "target_text": "string",
    "confidence": 0.0-1.0,
    "timestamp_start_ms": 1234567890,
    "translation_status": "ok|failed|low-confidence"
  }
  ```
- **Encoding:** UTF-8 JSON; max payload <1 KB normal case. Hard cap 4 KB.

### FR-17, FR-18, FR-19, FR-20 (Captions UI)

- **Android (Compose):**
  - `LazyColumn(state = rememberLazyListState())` with `items(captions, key = { it.id })`.
  - `LaunchedEffect(captions.size)` auto-scrolls to `lastIndex` unless user has manually scrolled up.
  - Partial Caption: `mutableStateOf` wrapping the in-flight Caption row's text; stable `id` prevents row reorder.
- **iOS (SwiftUI, iOS 17+):**
  - `ScrollViewReader { proxy in ScrollView { LazyVStack { ForEach(captions) { ... }.id(c.id) } }.defaultScrollAnchor(.bottom).onChange(of: captions.last?.id) { ... } }`.
- **State store:** `StateFlow<List<Caption>>` (Android) / `@Observable` ViewModel (iOS). Captions are append-only during a Call; cleared on Call end unless FR-21 transcript history is enabled.

### FR-21 (Transcript history)

- **Android:** Room database with `androidx.sqlite.SupportSQLiteOpenHelper` configured for SQLCipher (key stored in EncryptedSharedPreferences via `security-crypto`).
- **iOS:** SwiftData (iOS 17+) with the store file in the Application Support directory, protected with NSFileProtectionComplete.
- **Schema (both platforms):** `CallRecord { id, startTimestamp, endTimestamp }`; `CaptionRecord { callId, utteranceId, sourceLang, sourceText, targetLang, targetText, timestamp }`. No sync. No backup beyond local device backup mechanisms.

### FR-22 (Translation post-editor)

- **Implementation:** when toggled on, the backend translation handler chains two Gemini calls: first the primary system prompt (DR §5.1), then a reflow system prompt loaded from `prompts/reflow-v1.md` (to be authored in Phase 3 of build).
- **Reflow prompt placeholder (Phase 3 deliverable):** "You are polishing an English-to-Indonesian translation to feel like natural conversational speech in a young couple's voice. Read the source utterance and the draft translation. Output ONLY the polished translation, preserving all discourse particles and pronoun register. Do not over-edit; if the draft is already natural, output it verbatim."
- **Settings copy direction:** "Polish translations (slower) — extra step that makes translations sound more natural. Each translation takes about half a second longer."

### FR-24 (Privacy summary)

- Surfaces the §6.1 privacy section as plain-English bullet points in-app. Updated whenever §6.1 changes.

### §6.5 Warmup Ping

- **Pattern:** client sends an HTTP GET to `https://<backend>/healthz` within 30 seconds of (a) app foreground from background, (b) navigation to the Paired home screen, (c) user tapping the Call button if no warmup ping has fired in the last 10 minutes.
- **Backend:** `/healthz` endpoint returns 200 OK immediately; the side effect of being hit is to keep the Cloud Run container warm for ~15 minutes (undocumented but observed Cloud Run idle window).

---

## Why these are in the addendum and not the PRD

The BMAD PRD discipline: PRDs specify *capabilities* — the *what* and the *for whom* and the *acceptance criteria*. Specific SDK names, callback APIs, payload schemas, and library choices are *implementation*. They live in the Architecture document or, in a thinner project like this one, in the Technical Research and this Addendum.

For TranslatorRep specifically:
- Bania is a solo developer; the PRD-vs-Architecture boundary is fuzzier than in a team setting, but the discipline still matters because it keeps the PRD readable as a capability spec rather than a tech laundry list.
- The TR already serves as the de-facto technical addendum for higher-level architectural choices (LiveKit-vs-self-host, on-device-vs-cloud ASR, etc.). This addendum is the narrower companion that maps each FR to its concrete v1 binding.
- The next BMAD phase, CA (Architecture), will produce a fuller Architecture Decision Records (ADR) document. This addendum is the bridge between the PRD's capability language and that ADR document.

---

## Sources

- [TR §"v1 Architecture (locked at Step 4)"](../research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md) — full architecture rationale.
- [TR §"Implementation Specifics (Code-Level Details)"](../research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md) — extended code snippets, SDK quickstarts, library version pinning.
- [DR §"Improved Gemini 2.5 Flash System Prompt"](../research/domain-conversational-indonesian-sundanese-linguistics-research-2026-05-22.md) — v1 production system prompt content.
- LiveKit Android SDK 2.25.3 ([Maven](https://central.sonatype.com/artifact/io.livekit/livekit-android)) and LiveKit Swift SDK 2.14.1 ([GitHub](https://github.com/livekit/client-sdk-swift)).
- Firebase Admin SDK App Check verification ([docs](https://firebase.google.com/docs/app-check/custom-resource-backend)).
- Whisper.cpp Core ML integration ([docs](https://github.com/ggerganov/whisper.cpp#core-ml-support)).
