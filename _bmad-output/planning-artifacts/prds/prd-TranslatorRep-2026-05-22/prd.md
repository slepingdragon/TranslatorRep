---
title: TranslatorRep
status: final
created: 2026-05-22
updated: 2026-05-22
revision: 3
reconciled-with: architecture.md
---

# PRD: TranslatorRep

## 0. Document Purpose

This PRD specifies the v1 requirements for TranslatorRep, a personal-use audio-only WebRTC calling app with live bidirectional Indonesian↔English translation captions. Audience: Bania (solo developer and owner) and the downstream BMAD workflows (UX, Architecture, Epics & Stories).

Upstream inputs referenced but not duplicated here:

- **[Product Brief](../briefs/brief-TranslatorRep-2026-05-22/brief.md)** — vision, scope shape, success criteria, why-this-matters narrative.
- **[Technical Research](../research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md)** — architecture, stack, free-tier substitutions, primary-source-cited feasibility findings.
- **[Domain Research](../research/domain-conversational-indonesian-sundanese-linguistics-research-2026-05-22.md)** — linguistic reference plus the v1 Gemini system prompt that defines translation quality.
- **[Addendum](./addendum.md)** — implementation-level references (SDK names, callback APIs, library choices, JSON schemas) extracted from the PRD to keep the PRD at capability level.

This PRD specifies *capabilities*; the *how* lives in TR / Addendum / the upcoming Architecture phase.

---

## 1. Vision

TranslatorRep is a custom audio-calling app that Bania and his girlfriend install on their phones to have live, captioned, bidirectional Indonesian↔English voice conversations. Each side speaks in their own language. Both see the other's speech rendered as Captions on screen within ~3 seconds, with attention to the discourse particles, slang, and code-switching patterns that carry the emotional content of conversational Indonesian.

The product exists because every "use existing call app + add translation overlay" path is technically impossible — OS-level sandbox restrictions on Android and iOS make WhatsApp/FaceTime audio capture and floating overlays unbuildable. The buildable answer is to own the Call itself, in a focused app that does one job well.

For Bania and his girlfriend specifically, this is not a market product — it is a tool to eliminate the translation labor she carries alone today, so the long deep conversations they already have stop having quiet miscommunication undercurrents.

---

## 2. Target User

### 2.1 Primary Personas

**Bania** (Android-user, English-speaker, American, ~20s).
- Solo developer (also the product owner).
- Speaks no Indonesian or Sundanese beyond a few words.
- Owns a Samsung Galaxy with S Pen [ASSUMPTION: inferred from prior conversation about "android pen"].
- Currently relies on his girlfriend to do all real-time translation during their voice calls.
- Wants warmer, less effortful conversation with her — especially the long deep ones.

**His girlfriend** (iPhone-user, Indonesian + Sundanese speaker, ~20s, West Java).
- Speaks some conversational English, not fluent.
- Native Indonesian; native Sundanese; switches between them mid-conversation.
- Currently carries the full cognitive translation load during their Calls.
- Will appreciate the tool both for what it does AND for the signal that he built it for them.

### 2.2 Jobs To Be Done

- Functional: have a live voice conversation where both participants speak in their native language and read translations of the other person's speech in real time.
- Emotional: stop missing nuance in conversations that matter; stop the asymmetric translation labor; feel like the relationship is meeting in the middle linguistically.
- Social (for her): be understood in her own language by her partner without doing her own translation in her head.
- Contextual: this app fits *only* the use case "we want to have a translated conversation right now." WhatsApp continues to host everything else (text messages, untranslated calls, video, media).

### 2.3 Non-Users (v1)

See §9 Non-Goals for the canonical scope-exclusion list. Briefly: no third parties, no other language pairs, no video, no App Store / Play Store distribution.

### 2.4 Key User Journeys

- **UJ-1. First-time pairing.** Bania installs the app on his Galaxy. He opens it, signs in anonymously silently, and lands on a Paired-Empty home screen with a 6-digit Pairing Code displayed prominently and a "Enter their code" field. He texts her his code via WhatsApp. She installs her copy on her iPhone, signs in anonymously, enters his code into her app, and taps Pair. Both screens transition to the Paired home screen with the Call button. **Climax:** the moment both phones show the partner's pairing confirmation. **Resolution:** ready to Call. **Edge case:** a mistyped code shows clear inline error without invalidating the original code; she retypes without him regenerating.

- **UJ-2. Calling for the first time.** Bania taps Call. Her iPhone rings via native iOS CallKit on the lock screen, displaying "TranslatorRep — Bania calling" exactly like any other VoIP call. She taps Accept from the lock screen, no unlock needed. Both screens transition to the In-Call view: her face is not shown (audio-only); his audio is live; the lower portion of the screen is a scrolling Caption area. He says "Hi, can you hear me?" — within ~3 seconds, her Caption area shows `EN: Hi, can you hear me?` on one line and `ID: Halo, bisa dengar aku?` on the next. She responds in Indonesian. **Climax:** the first translated Caption appears for both of them in real time. **Resolution:** they can talk. **Edge case:** if his ASR fails on his first utterance, the Caption area shows a soft retry indicator rather than silently dropping the line.

- **UJ-3. The deep conversation.** Three weeks in. They are on a 40-minute Call. She speaks Indonesian, sometimes inserting Sundanese words (`urang`, `atuh`, `mah`); he reads the English Captions and responds in English. The translation captures the discourse particles correctly — when she says "Aku kangen banget loh," he sees "I really miss you, you know," not "I miss you so much." There is a moment of vulnerability about her family that would have been clunky through her old self-translation; this time it lands. **Climax:** they get through the conversation without a single "wait, what did you mean?" interruption. **Resolution:** this is the qualitative success signal from the Brief.

---

## 3. Glossary

Downstream workflows and readers MUST use these terms exactly. No synonyms anywhere else in the PRD.

- **Paired Users** — the two users (and only two) who have completed the Pairing ceremony and can Call each other through TranslatorRep. Each user is paired with exactly one other user.
- **Pairing Code** — the 6-digit code one user generates and shares with the other through an out-of-band channel (e.g., WhatsApp text) to establish the Paired Users relationship.
- **Call** — a real-time bidirectional audio session between Paired Users, mediated by the WebRTC service, hosting the Translation Pipeline.
- **Utterance** — a unit of speech bounded by speaker pause, sent to the Translation Pipeline as a single ASR + translation request. Roughly one sentence.
- **Translation Pipeline** — the per-Utterance flow: local audio → ASR (on-device) → text → translation (Gemini via backend proxy) → result returned to speaker → result sent to peer via the Data Channel.
- **Caption** — a single line of rendered text on the Call screen showing one Utterance: its Source Text and its Target Text. Captions accumulate in a scrolling Caption history.
- **Source Text / Target Text** — the original-language transcript / the translated-language text for an Utterance.
- **Partial Caption** — a Caption being updated live as the ASR streams partial results before the Utterance ends. Visually distinct from a finalized Caption.
- **Translation Provider** — the abstracted backend that turns Source Text into Target Text. v1 binds to Gemini 2.5 Flash via Google AI Studio.
- **ASR Provider** — the abstracted on-device speech-to-text engine. v1 Android and iOS each bind to a platform-appropriate on-device implementation.
- **Data Channel** — the WebRTC peer-to-peer message channel used to send translation results from speaker to listener in-band with the Call.
- **Data Channel Message** — a JSON payload containing one finalized translated Utterance, sent over the Data Channel for the listener to render as a Caption.

---

## 4. Features

*Each subsection is a coherent feature: behavioral description first, FRs nested with globally-numbered stable IDs, optional feature-specific NFRs and notes. Cross-cutting NFRs live in §6. FRs reference User Journeys by ID at the Feature level — individual FRs do not repeat the journey tag unless they cross feature boundaries.*

### 4.1 Pairing & Identity *(Realizes UJ-1)*

**Description:** First-launch flow that establishes the singular Paired Users relationship without requiring email, password, or any PII. Anonymous Firebase Auth issues a stable per-device user identity silently on first launch. The user lands on a Paired-Empty home screen showing their own Pairing Code and a "Enter their code" input. When both partners have entered each other's codes (or one entered the other's), Pairing is committed. Subsequent launches go directly to the Paired home screen (with the Call button) and skip the pairing UI entirely. The displayed partner name defaults to "Partner"; users can set a custom display name in Settings.

**Functional Requirements:**

#### FR-1: Anonymous sign-in on first launch.
The system signs the user in anonymously before showing any UI.
**Consequences (testable):**
- After a clean install and first app open, an anonymous user session is established within 3 seconds.
- The user identity persists across app restarts on the same device.
- No login UI is ever shown.

#### FR-2: Generate Pairing Code on demand.
A user can view their own 6-digit Pairing Code at any time when not yet paired.
**Consequences (testable):**
- The code is a 6-digit decimal string.
- The code is unique across all currently-active codes [ASSUMPTION: 1M code space is sufficient for a personal app with collision check at generation time].
- A code remains valid until used in a successful Pairing or the user explicitly regenerates it.

#### FR-3: Enter partner's Pairing Code to pair.
A user enters their partner's 6-digit code into a labeled input; on submission, the system attempts to pair the two user identities.
**Consequences (testable):**
- An invalid code (not 6 digits, no such code in the backend) shows an inline error within 2 seconds; the user's own code remains valid.
- A valid code creates a Pairing record referenced by both users.
- Both apps transition to the Paired home screen within 5 seconds of successful Pairing.

#### FR-4: Paired state persists across app restarts.
Once paired, both apps remember the Pairing and show the Paired home screen on subsequent launches without re-pairing.
**Consequences (testable):**
- After successful Pairing, killing and reopening the app shows the Paired home screen, not the Paired-Empty screen.
- The paired-partner's display name and user identity are recoverable from local storage without network access.

#### FR-5: Unpair from current partner.
A user can dissolve the current Pairing from the Settings screen, returning to the Paired-Empty state.
**Consequences (testable):**
- Unpair requires an explicit two-tap confirmation (no accidental unpair).
- Unpair removes the Pairing record from the backend and clears local pairing state.
- The previously-paired partner's app does not break — they see "Partner unpaired" on next Call attempt and return to Paired-Empty.

### 4.2 Calling (Place, Receive, End) *(Realizes UJ-2)*

**Description:** Native-feeling VoIP Call experience for placing and receiving a Call between Paired Users. The Paired home screen exposes a `CallTypeSelector` with two buttons — **Audio Call** and **Video Call** — letting the caller choose the modality before initiating. Incoming Calls ring like any other phone call (lock screen, native call UI on iOS and Android), and the native call header reflects which type is being received. Audio routing (speaker / earpiece / Bluetooth) is exposed via a toggle on the In-Call control row and auto-routes to Bluetooth or wired headset when one is detected at Call start. Implementation references (PushKit/CallKit, FCM/ConnectionService) live in the Addendum.

**Functional Requirements:**

#### FR-6: Place a Call to paired partner.
A user can initiate a Call to their paired partner by tapping the Call button on the Paired home screen.
**Consequences (testable):**
- Tap-to-call-ringing latency <3 seconds on a typical 4G connection.
- The caller's UI shows a "Calling..." state until accepted, rejected, or timed out (30 seconds).
- The WebRTC room is created and the caller admitted before the ringing notification is sent.

#### FR-7: Receive an incoming Call notification.
The recipient's device shows an incoming-call UI integrated with the platform's native call surface (including lock-screen ring).
**Consequences (testable):**
- Delivery latency from caller initiation to recipient native call UI: <2 seconds under typical network conditions.
- The notification displays the caller's display name and the "TranslatorRep" app identity.
- The recipient can answer without unlocking the device.

#### FR-8: Accept or reject an incoming Call.
The recipient can accept or reject via the native call UI.
**Consequences (testable):**
- Accepting transitions both apps to the In-Call screen within 2 seconds.
- Rejecting sends an explicit reject signal; the caller sees "Call declined" and returns to Paired home within 3 seconds.
- Not responding within 30 seconds counts as missed; both sides return to Paired home.

#### FR-9: End an active Call.
Either user can end the Call from the In-Call screen.
**Consequences (testable):**
- End-call button has a confirmation pattern proportionate to Call duration [ASSUMPTION: confirmation only for Calls >5 minutes].
- Ending the Call disconnects WebRTC, stops audio capture, closes the Translation Pipeline, and returns both apps to the Paired home screen within 2 seconds.

#### FR-10: Audio quality acceptance.
Voice quality during a Call is verified before v1 ships.
**Consequences (testable):**
- Audio uses Opus codec at ≥24 kbps sustained per direction.
- Echo cancellation is enabled via the WebRTC stack's built-in AEC; no audible echo when both parties are on speakers OR on headsets in a paired listening test.
- Audio remains intelligible at simulated 4G network conditions with 200ms RTT and 1% packet loss.
- Acceptance: a paired listening test with Bania + his girlfriend on 3 Calls of ≥10 minutes each, rating audio as "as good as WhatsApp voice" or better, with no Call falling below "intelligible but degraded."

#### FR-26: Select Audio Call or Video Call before initiating. *(Realizes UJ-2)*
The Paired home screen presents a `CallTypeSelector` with two visually equal buttons — **Audio Call** and **Video Call** — and the caller must pick one to initiate.
**Consequences (testable):**
- Both buttons are reachable as primary actions on the Paired home screen; neither is hidden behind a menu or secondary affordance.
- The selected call type is carried in the LiveKit JWT metadata as a `callType` claim (`"audio"` | `"video"`) issued by the auth-proxy.
- The recipient's native incoming-call header reflects the type — "TranslatorRep Audio Call" vs "TranslatorRep Video Call" — so the recipient knows what they are accepting before answering.
- Once a Call is in progress, the type is fixed for the duration of that Call; switching modality requires ending and re-initiating.

#### FR-28: Audio routing toggle (speaker / earpiece / Bluetooth). *(Realizes UJ-2)*
The In-Call screen exposes an `AudioRoutingToggle` in both `AudioCallControlRow` and `VideoCallControlRow` so the user can pick the output route mid-Call.
**Consequences (testable):**
- The toggle cycles between Speaker, Earpiece, and Bluetooth (when a BT device is connected); a wired headset is treated as the equivalent of Bluetooth in the routing logic.
- On Call start, the system auto-routes to Bluetooth or wired headset if one is detected at connect time; otherwise defaults to earpiece for Audio Calls and speaker for Video Calls.
- A user tap on the toggle overrides the auto-route for the remainder of the Call.
- Connecting or disconnecting a Bluetooth device mid-Call updates the available toggle options within 1s and routes audio per standard platform behavior.

#### FR-30: Camera permission requested lazily on first Video Call. *(Realizes UJ-2)*
Camera permission is NEVER requested on first app launch; it is requested only at the first user-initiated touch point with the camera.
**Consequences (testable):**
- A clean install followed by Audio-Call-only usage never triggers a camera permission prompt.
- Camera permission is requested the first time the caller taps **Video Call**, OR the first time the recipient accepts an incoming Video Call.
- If the user denies camera permission, the `CameraPermissionFlow` component surfaces a plain-language explanation with a one-tap Settings deep-link to re-grant.
- A denied permission does NOT downgrade an outgoing Video Call to Audio silently; the system surfaces the denial and lets the user reconsider.

### 4.3 Translation Pipeline (Audio → Caption) *(Realizes UJ-2, UJ-3)*

**Description:** The per-Utterance flow that turns local mic audio into a translated Caption rendered on both sides' screens. For each Utterance, the user's local audio is tapped from the WebRTC local audio track, fed into the platform-specific on-device ASR Provider, and the resulting Source Text is fed into the on-device Translation Provider. v1 Plan A binds the Translation Provider to an on-device neural model — **NLLB-200 distilled 600M q4 (Rank 1) → MADLAD-400 (Rank 2) → Gemma 2B (Rank 3)** — locked by a Week-1 bake-off (FR-31). The raw model is wrapped by a `RuleBasedTranslationProvider` decorator that composes the underlying provider with `ParticleProcessor` pre/post-processing, applying the §5.2 preservation targets (TQ-1 through TQ-8) as deterministic rules. The Target Text renders as the speaker's own Caption locally, and the speaker's app sends a Data Channel Message to the peer carrying both Source and Target Text, which the peer renders. If all Plan A candidates fail Week-1 kill-criteria, the system activates **Plan B** — Vertex AI Gemini in `asia-southeast1` via the backend auth-proxy with the §5.1 system prompt (FR-14 path). Implementation references (SDK callback APIs, JSON schemas, library names, model file paths) live in the Addendum.

**Functional Requirements:**

#### FR-11: Capture local mic audio during a Call.
The system captures the user's spoken audio in real time while a Call is active.
**Consequences (testable):**
- Audio is tapped from the WebRTC local audio track without affecting the audio sent to the peer.
- Microphone permission is requested before the first Call; a user cannot start a Call without it.
- Audio is not persisted to disk at any point during normal operation.

#### FR-12: Detect Utterance boundaries via Voice Activity Detection.
The system segments continuous mic audio into Utterances at natural speech pauses, with awareness that Indonesian places emotional load in sentence-final particles.
**Consequences (testable):**
- An Utterance is committed for translation when a silence pause of **700 ms** is detected after speech (v1 shipping default; tunable via internal flag for later iteration).
- Maximum Utterance length is **15 seconds**; longer continuous speech is force-segmented to maintain Caption cadence.
- The Utterance commit step waits for sentence-final particle settle before sending to the Translation Provider — partial transcripts before the final pause are NOT translated or sent to the peer (DR §7 prosody finding).

#### FR-13: Convert Utterance audio to Source Text via on-device ASR.
The system runs platform-appropriate on-device speech-to-text on each Utterance using the ASR Provider abstraction.
**Consequences (testable):**
- Android: on-device ASR with `id-ID` and `en-US` locales; gated by runtime availability probe at app launch.
- iOS: on-device ASR via a bundled multilingual model (Apple's APIs do not support `id-ID` on-device — see Addendum).
- Time-to-finalized Source Text: <2s on Android, <3s on iOS after Utterance end [ASSUMPTION: Whisper.cpp small model on A17+; verify in Week 1 probe].
- If on-device ASR returns empty or low-confidence, the system surfaces a soft retry rather than silently dropping the Utterance.

#### FR-14: Translate Source Text to Target Text via backend proxy. *(SUPERSEDED by FR-31 for v1 Plan A; retained as Plan B fallback.)*
The speaker's app sends the finalized Source Text to a backend translation endpoint, which calls the Translation Provider with the v1 system prompt and returns Target Text. **Status:** under v1 Plan A (on-device translation per FR-31) this path is NOT used; it activates only if Week-1 bake-off kill-criteria trip and the project escalates to Plan B (Vertex AI Gemini in `asia-southeast1` via the Oracle VM auth-proxy).
**Consequences (testable):**
- POST round-trip <1.5s under typical conditions (median) when the backend container is warm.
- The backend enforces app-attestation on every request; missing or invalid attestation returns HTTP 401.
- A failed Translation Provider call (timeout, 5xx, rate limit) returns a typed error; the Caption renders with a "translation unavailable" marker rather than silently dropping.
- Direction is determined by the user's language preference at app setup: Bania's app sends EN-source → ID-target; girlfriend's app sends ID-source → EN-target.

#### FR-15: Deliver Target Text to peer via Data Channel.
The speaker's app sends a Data Channel Message containing the translated Utterance to the peer over the WebRTC data channel.
**Consequences (testable):**
- Message latency from speaker's received Target Text to peer's render: <300ms in same-region peer connection.
- Messages are delivered reliable-ordered.
- Each message is <1 KB under normal Caption length.

#### FR-16: Render Caption on speaker's screen.
The speaker's own app renders the Source Text + Target Text as a Caption in their Caption history so the speaker can verify what was sent to their partner.
**Consequences (testable):**
- Speaker sees their own Source Text within 1s of speaking, and their Target Text within 4s.
- If translation fails for an Utterance, the speaker sees "translation unavailable" alongside their Source Text — they know the partner did not receive a translation.

#### FR-31: Translate Source Text on-device (v1 Plan A). *(Realizes UJ-2, UJ-3)*
The Translation Provider runs entirely on the speaker's own device. Translation text NEVER leaves the device under Plan A. v1 binds to one of three candidate models — **NLLB-200 distilled 600M q4** (Rank 1), **MADLAD-400** (Rank 2), **Gemma 2B** (Rank 3) — selected via a Week-1 bake-off against the §5.3 TQ-AT preservation targets and quantitative kill-criteria.
**Consequences (testable):**
- The Week-1 bake-off ranks each candidate against TQ-1 through TQ-8 on a fixed regression corpus and against latency / battery / memory kill-criteria (defined in epics.md AR-10).
- The first candidate to pass all kill-criteria is locked as the v1 Plan A model; if all three fail, the project escalates to Plan B (Vertex AI Gemini per FR-14).
- The raw model is wrapped by a `RuleBasedTranslationProvider` decorator that composes the underlying `RawTranslationProvider` with `ParticleProcessor` rules-based pre-processing and post-processing — particle preservation, gender-neutral default, Sundanese lexical insertions, honorific handling, religious-expression preservation per §5.2.
- On-device inference cancellation honors the `TranslationProvider.stop()` contract within 500ms (per §6.4 Provider Abstraction cancellation requirement).
- Cold-start "preparing translator" indicator surfaces on the first Call after install or model swap; warm subsequent Calls show no such indicator.
- Translation text is never sent to any server under Plan A; the privacy summary (FR-24) reflects this.

### 4.4 Captions UI *(Realizes UJ-2, UJ-3)*

**Description:** The In-Call Caption history rendering. The bottom portion of the In-Call screen [ASSUMPTION: lower 60%; UX to confirm] is a scrollable list of Captions, oldest at top, newest at bottom. Each Caption shows: speaker indicator (you/them), Source Text, Target Text, optional timestamp. Source Text and Target Text are visually distinct via weight or opacity (not color, per §7 aesthetic).

**Functional Requirements:**

#### FR-17: Display a scrollable Caption history during the Call.
The Caption history is always visible during the Call and discarded on Call end unless transcript history is enabled (FR-21).
**Consequences (testable):**
- Captions remain in the visible history for the duration of the Call.
- Source Text and Target Text are visually differentiated.
- The history is scrollable independently of the rest of the In-Call screen.

#### FR-18: Render Partial Captions for in-progress speech.
While the ASR is producing partial results before Utterance end, the speaker's own caption row shows the evolving Source Text in a visually distinct "in-progress" style. Partial Captions are local-only — they are NOT translated and NOT sent to the peer (per FR-12 and DR §7 prosody finding).
**Consequences (testable):**
- Partial Caption updates at the cadence of ASR partials.
- On Utterance finalization, the Partial Caption transitions to a finalized Caption with no row reorder or visual jump.
- The peer does not see the speaker's Partial Captions, only finalized Captions.

#### FR-19: Auto-scroll Caption history to the newest Caption.
When a new Caption is appended, the list scrolls to keep it visible.
**Consequences (testable):**
- New Caption appended → list animates to the bottom within 200ms.
- If the user has manually scrolled up (e.g., to read earlier in the conversation), auto-scroll is suspended until they scroll back to the bottom or tap a "jump to latest" affordance.

#### FR-20: Visually distinguish translation failures.
A Caption whose translation failed is rendered with a clear visual indicator so the speaker knows the partner did not see a translation.
**Consequences (testable):**
- Failed translations show a "translation unavailable" marker.
- Tapping the marker shows a brief explanation (e.g., "Network error" / "Translation service unavailable").

### 4.5 Settings

**Description:** Minimal settings — transcript history opt-in, post-editor toggle, unpair, privacy summary, display name. No clutter.

**Functional Requirements:**

#### FR-21: Opt in to per-device transcript history.
A user can enable saving Captions from past Calls to a local-only history on their own device.
**Consequences (testable):**
- Toggle in Settings; default off.
- When on, all finalized Captions from each Call are written to local encrypted storage on Call-end (see Addendum for storage choices).
- When off, no captions persist beyond the active Call.
- Transcript history is NEVER synced to a server.
- The user can view and delete history entries from Settings.

#### FR-22: Toggle translation post-editor (optional Gemini reflow).
A user can enable a translation post-edit step that runs the Translation Provider twice — once for primary translation, once to reflow for natural conversational tone. Off by default for v1.
**Consequences (testable):**
- Toggle in Settings; default off.
- When on, adds ~400ms to per-Utterance translation latency.
- The reflow prompt is authored during Phase 3 of the build, informed by real-conversation evidence; v1 ships with both the toggle and a working reflow prompt or with FR-22 deferred to v1.1.
- [NOTE FOR PM]: Whether FR-22 ships in v1 or defers to v1.1 is gated on the Plan A vs Plan B outcome (see epics.md Story 8.7 three-outcome decision matrix). Under **Plan A** (on-device translation), running the on-device model twice per Utterance is likely too expensive battery-wise — FR-22 most likely defers to v1.1. Under **Plan B** (Vertex AI Gemini), FR-22 ships with a Vertex AI Gemini reflow chain since the cloud call adds only network round-trip cost.
- [NOTE FOR PM]: Settings copy must explain in plain language what the toggle does and what the user is trading. Bania's requirement: not a cryptic toggle. Example copy direction: *"Polish translations (slower) — extra step that makes translations sound more natural, but each translation takes about half a second longer."*

#### FR-23: Set custom display name.
A user can override the default "Partner" display name shown to their paired partner during Calls.
**Consequences (testable):**
- Setting available in Settings.
- The new name appears on the partner's screen on the next Call.
- Default is "Partner" when never set; never asks during Pairing.

#### FR-24: View privacy summary.
Settings includes a one-screen plain-English summary of what data the app collects, where it goes, and what is never stored.
**Consequences (testable):**
- Lists: anonymous user identity, Pairing record, Caption text in transit, Crashlytics if opted in, transcript history if opted in.
- Lists what is never stored server-side: audio, Captions, conversation content.
- States the Gemini AI Studio data-handling caveat (see §6.1).

(FR-25 reserved — Unpair from current partner is FR-5; surfaced in Settings via that same FR.)

### 4.6 Video Pipeline *(Realizes UJ-2)*

**Description:** When the caller selects **Video Call** at initiation (FR-26), both sides establish a bidirectional video track in addition to audio. Video is intentionally modest — 360p × 30fps — to fit on the same WebRTC connection without degrading audio or burning unnecessary battery. Video failure is treated as a soft-degraded state: a dropped video track does NOT end the Call; audio continues uninterrupted, and the affected tile shows a neutral `VideoPausedTile`. Implementation references (LiveKit video track configuration, camera capture setup) live in the Addendum.

**Functional Requirements:**

#### FR-27: Video pipeline with neutral failure UX.
During a Video Call, each side publishes a 360p × 30fps video track and subscribes to the peer's. A dropped or failing video track shows a neutral grey `VideoPausedTile` with auto-retry every 5s plus a manual retry tap; audio is unaffected.
**Consequences (testable):**
- Video resolution is fixed at 360p × 30fps for v1; not user-configurable.
- A video track drop (camera error, encoder error, peer-side publish failure) renders the affected tile as `VideoPausedTile` in neutral grey — NOT in amber/red. This is per ADR-F3: video pause is not an alarm state.
- Auto-retry attempts re-establish the video track every 5 seconds while the Call remains active; the user can tap the tile for an immediate manual retry.
- Audio continues uninterrupted during any video drop — Calls do not end because of a video failure.
- Camera permission is requested lazily per FR-30; a permission denial does NOT auto-downgrade the Call.

### 4.7 End-to-End Encryption *(Realizes UJ-2)*

**Description:** v1 ships with WhatsApp-equivalent client-to-client end-to-end encryption for both media and Data Channel traffic. The SFU on the Oracle VM forwards only ciphertext; it cannot decrypt any conversation content. Encryption is built on WebRTC Insertable Streams with a per-Call symmetric key derived from a per-Call X25519 ECDH exchange. Each user holds a long-term identity keypair generated at first launch (private key in platform secure storage; public key in Firestore). Per-Call ephemeral keypairs provide forward secrecy: a Call's session key cannot be derived from any long-term key. Ephemeral public keys are signed by the identity key to prevent SFU-mediated MitM. Implementation references (libsodium, key derivation, LiveKit `e2eeOptions.keyProvider` wiring) live in the Addendum.

**Functional Requirements:**

#### FR-29: E2EE setup and per-Call X25519 ECDH key exchange.
Each user generates a long-term X25519 identity keypair on first launch; per-Call ephemeral X25519 keypairs are generated at Call initiation, exchanged via Firestore, combined via ECDH, expanded via HKDF-SHA256 to an AES-GCM 256-bit symmetric key, and supplied to LiveKit's `e2eeOptions.keyProvider`. The ephemeral public key is signed by the long-term identity key so the peer can verify the SFU did not substitute keys.
**Consequences (testable):**
- The long-term identity private key is generated on first launch and stored in iOS Keychain / Android Keystore; the public key is written to Firestore at `/users/{uid}.identityPub`.
- An ephemeral X25519 keypair is generated per Call; the ephemeral public key is signed by the identity key and written to Firestore at `/calls/{callId}/ephemeralPub/{uid}` for the peer to fetch and verify.
- The derived AES-GCM 256-bit symmetric key is fed into LiveKit `e2eeOptions.keyProvider`; media and Data Channel frames are encrypted client-side via Insertable Streams before reaching the SFU.
- Forward secrecy per Call: a Week-1 packet capture (per AR-19) confirms the SFU sees only ciphertext bytes for media + Data Channel.
- On the first Call after pairing, a one-time `E2EEKeyExchangeIndicator` confirms the key has been established; subsequent Calls show no such indicator.
- A failed key exchange (signature verification fails, Firestore write fails) blocks the Call from starting and surfaces a clear error rather than silently falling back to unencrypted.

### 4.8 Resilience: Leave-and-Rejoin *(Realizes UJ-2)*

**Description:** A Call is not torn down the instant one side ends. The LiveKit room remains alive with `empty_timeout: 300` (5 minutes), giving either user time to rejoin without re-initiating from the Paired home. This matters because cellular drops, train tunnels, and accidental gesture-taps are real, and re-initiating a Call from cold mid-conversation breaks flow. UI is symmetric: whichever side ends, the other side sees a `CallWaitingForPartnerState` overlay and the leaver gets a local `RejoinNotification`. There is only one end-Call gesture — no separate "leave" vs "end" distinction to confuse the user. Implementation references (LiveKit room config, local notification setup) live in the Addendum.

**Functional Requirements:**

#### FR-32: Leave-and-rejoin within a 5-minute window.
When either user ends a Call (or their device drops the connection), the LiveKit room remains alive for 300 seconds, allowing rejoin without re-pairing or re-dialing. After 5 minutes empty, the room is destroyed cleanly.
**Consequences (testable):**
- LiveKit room is configured with `empty_timeout: 300`.
- When one side leaves, the remaining side's In-Call screen renders a `CallWaitingForPartnerState` overlay (neutral, not alarming) showing the partner left and can rejoin.
- The leaver receives a local `RejoinNotification` on their device immediately after leaving, with a tap action that re-joins the same Call.
- Behavior is symmetric: same overlay, same notification, regardless of which side ended.
- The end-Call gesture is single and unified — there is no separate "leave" gesture in v1.
- After 300 seconds empty, the room is destroyed; both sides return to the Paired home screen on next foreground.
- A side that rejoins within the window resumes Captions, audio, and (if Video Call) video without losing the prior Caption history of that Call.

---

## 5. Translation Quality Requirements

*Translation quality is the load-bearing v1 success criterion. It is governed by the Gemini system prompt — a versioned, first-class artifact — and a set of named preservation targets. This section is structured so SM-2 and downstream stories can reference targets by ID.*

### 5.1 The System Prompt is a Versioned Artifact

The Gemini 2.5 Flash system prompt produced by DR is treated as a first-class component, not a config value:

- **Storage:** version-controlled in the backend service repo (e.g., `prompts/id-en-v1.md`); see Addendum for exact path.
- **Two variants:** ID→EN and EN→ID, sharing the same caching context.
- **Versioning:** changes increment the version and are recorded in `.decision-log.md`.
- **Iteration cadence:** review after the first 10 real Calls; revise based on actual error patterns.

### 5.2 Preservation Targets

The Translation Pipeline MUST preserve each of the following. Each target has a stable ID for traceability into SM-2 and downstream story creation:

- **TQ-1. Indonesian discourse particles** (`lah`, `sih`, `kok`, `dong`, `deh`, `ya/iya`, `kan`, `nih`, `tuh`, `aja`, `gitu`, `udah`, `loh/lho`, `mah`). Particle loss is the highest-impact quality regression.
- **TQ-2. Pronoun register signals.** `aku`/`kamu` (default intimate) vs `saya`/`Anda` (formal/cold shift) vs `gue`/`lo` (Jakarta playful) carry distinct relational signals.
- **TQ-3. Gender neutrality.** Indonesian `dia` / `-nya` translates to singular "they" when ambiguous; never default to "he" or "she."
- **TQ-4. Sundanese lexical insertions** (≥12 high-frequency tokens defined in DR §4) translated as known vocabulary; full Sundanese clauses flagged `[su?]` rather than confabulated.
- **TQ-5. Honorifics directed at partner.** `mas`, `abang`, `kak` → "babe" or omit; never literalize as "older brother."
- **TQ-6. Religious expressions.** `insya Allah`, `alhamdulillah`, `astaghfirullah`, `masya Allah` preserved verbatim (with optional first-use gloss).
- **TQ-7. Indirect refusals.** `nanti dulu` → "maybe later"; `mungkin` → "maybe (often soft no)"; `liat nanti` → "we'll see".
- **TQ-8. Gen-Z 2026 slang dictionary** (~20 high-frequency items defined in DR §3) mapped to natural English equivalents, not literal expansions.

### 5.3 Quality Acceptance Test (TQ-AT)

Before v1 ships:

- 10 sample Calls recorded across at least 3 sessions, each ≥20 minutes.
- **Bania and his girlfriend together** review all Captions and rate each Utterance as: ✅ accurate-and-natural, ⚠️ accurate-but-awkward, or ❌ wrong / lost meaning. Her input is essential — she catches register and tone errors that Bania (as a non-Indonesian-speaker) cannot evaluate. Bania catches English-side naturalness errors she may miss.
- v1 ships when: ≥80% of Utterances are ✅ AND no individual Call has >2 ❌ ratings.
- Failures inform a system-prompt revision before v1.1.

---

## 6. Constraints and Guardrails

### 6.1 Privacy

- **WhatsApp-equivalent end-to-end encryption between clients.** Media and Data Channel traffic are encrypted client-to-client via WebRTC Insertable Streams using an AES-GCM 256-bit symmetric key derived from a per-Call X25519 ECDH exchange + HKDF-SHA256 (FR-29). The SFU (LiveKit on the Oracle VM) sees only ciphertext bytes — it cannot decrypt audio, video, or Data Channel messages. Forward secrecy per Call; ephemeral public keys signed by long-term identity key prevent SFU-mediated MitM.
- **Translation text NEVER leaves the speaker's device under v1 Plan A.** On-device inference (FR-31) keeps Source Text and Target Text local; no translation traffic reaches any network.
- **Firestore stores public keys and metadata only.** Schema is limited to `identityPub`, `ephemeralPub`, `pairId`, `callId`, language codes, and similar non-content metadata. No audio, no transcripts, no Captions, no conversation content is ever written to Firestore.
- **Anonymous Firebase Auth, no PII, no login UI.** A stable per-device user identity is issued silently on first launch; the app never asks for email, phone number, or any other PII.
- **Crashlytics is opt-in, default off.** Conversation content is NEVER logged. Only Call IDs, language codes, error-type strings, model load timings, and sanitized ASR/translation duration metrics are logged. Enforcement is structural: a `SafeLog` facade + `AllowedLogKey` enum + per-platform lint rules (detekt on Android, SwiftLint on iOS) prevent direct logger access and block CI merges on violations.
- **Transcript history is local-only and per-device.** Never synced. Never reaches the backend or any server.
- **TLS 1.3 in transit** on all client-backend connections; QUIC/DTLS+SRTP under the E2EE wrapper for WebRTC.
- **Plan B note (conditional):** If the Week-1 validation gate trips Plan A kill-criteria, the project activates Plan B — Vertex AI Gemini in `asia-southeast1` via the Oracle VM auth-proxy (FR-14). Under Plan B, translation text is sent to Google Vertex AI in Singapore over TLS 1.3. Google's published Vertex AI data handling: enterprise terms, no model-training on inputs, regional data residency. This is a downgrade from Plan A's "never leaves device" posture but materially stronger than the original Gemini AI Studio free-tier caveat — which had no residency guarantee and was training-eligible. The privacy summary (FR-24) surfaces the active mode (Plan A vs Plan B) so the user always knows which posture is in effect.

### 6.2 Cost

- **$0/month operating cost is a hard requirement** for v1 at 2-user / ~30-min/day scale.
- **Cloud Billing budget alerts at $1 / $5 / $10**; hard killswitch (detach billing) at $50.
- **No paid-tier substitution without explicit re-approval.** If a free tier becomes insufficient (e.g., on-device ASR quality unacceptable), surface to Bania before swapping in a paid path.

### 6.3 Safety / Reliability

- **A failed Translation Pipeline must never silently drop a Caption.** All failures surface as the "translation unavailable" indicator (FR-20).
- **The app must work offline-degraded for ASR.** On-device ASR works without network. Translation still requires network — if offline, the speaker sees their Source Text with no Target Text and a clear network-error indicator.
- **No third-party analytics SDK that touches conversation content.** Crashlytics is the only allowed telemetry source.

### 6.4 Provider Abstraction *(NFR — load-bearing for v2)*

The system MUST expose two abstract interfaces — `AsrProvider` and `TranslationProvider` — and bind v1 implementations behind them. Every FR that calls ASR or Translation routes through the abstraction, never directly to a vendor SDK.

**Rationale:** v2 Sundanese support requires swapping the Translation Provider (TLLM does not support `su`; classic Google NMT does) and adding an ASR Provider variant (chirp_2 chunked Recognize for `su-ID`). v2 must be a config swap, not a rewrite. This requirement is the single largest enabler of the v2 roadmap and the strongest insurance against vendor lock-in.

**Consequences (testable):**
- A v1 unit test confirms the call site for ASR is the `AsrProvider` interface, not a vendor type.
- A v1 unit test confirms the call site for translation is the `TranslationProvider` interface, not a vendor type.
- Adding a second `AsrProvider` implementation (e.g., a stub used in tests) requires zero changes to call sites in §4.3 features.

**Note:** Architecture Pattern §9 "Provider Abstraction Symmetric Surface" specifies the concrete cross-platform interface shape (Kotlin / Swift signatures with the 500 ms cancellation contract referenced in FR-31).

### 6.5 Performance and Cold-Start Mitigation

- **Always-on backend.** v1 deploys the LiveKit SFU + auth-proxy + Redis on an Oracle Ampere A1 VM (Always-Free tier, 4 OCPU / 24 GB RAM). The VM is always-on; there is no cold-start window to mitigate. Cloud Run is not used.
- **End-to-end latency target.** See SM-4. Architecture choices that materially impact median or p95 latency must be documented in the Addendum or TR before adoption.

---

## 7. Aesthetic and Tone

*Polish from day one is a stated brief requirement.*

- **Visual language: monochrome glass.** Pure black and white as the dominant palette. UI surfaces use glassmorphism — translucent panels with backdrop blur, fine 1px borders at low opacity, depth conveyed via subtle shadow. No saturated color accents in the chrome. Color is reserved exclusively for state signals (translation-failure indicator, mic-active dot, etc.) at minimum saturation.
- **Layout: centered.** Primary actions and content (Call button, Pairing Code display, In-Call Caption stream) are horizontally centered on the screen. Avoid left-aligned or asymmetric layouts that feel like list/feed apps. The product should feel like a focused instrument, not a feed.
- **Density: simplistic, no clutter.** Generous whitespace. One primary action per screen wherever possible. No accessory chips, no badges, no notification dots unless functionally required. The Caption area is the densest part of the app — everywhere else, less. If a section can be removed without losing function, remove it.
- **Typography:** System defaults (Android Roboto, iOS SF), generously sized for Caption readability. The Caption stack uses clear visual hierarchy between Source Text and Target Text via weight or opacity differentiation — not color.
- **Voice of product-generated text:** Plain, kind, never apologetic-corporate. "Translation unavailable" beats "We're terribly sorry, an error occurred."
- **Anti-references:**
  - Anything enterprise-y or productivity-app-y (chips, badges, accent colors, sidebars).
  - AI-aesthetic clichés (glowing gradients, sparkle iconography, "AI" branding, holographic chrome).
  - Anything busy, colorful, or social-media-like.
  - Left-aligned, top-heavy layouts that feel like feeds.
- **Visual touchstones:** monochrome-glass aesthetic of iOS 26 system UI (Control Center, lock-screen widgets); minimalist single-purpose apps like Bear (notes) or Linear (PM tool) — but quieter than Linear's accent color, fully monochrome.

---

## 8. Platform

- **Android**: native, Compose-based, minimum API 33 (required for on-device ASR APIs); target API 35.
- **iOS**: native, SwiftUI-based, minimum iOS 17 (for newer ScrollView APIs); target iOS 26.
- **Backend:** LiveKit OSS Docker Compose stack on an Oracle Always-Free Ampere A1 VM (4 OCPU / 24 GB RAM, Ubuntu 24.04 LTS ARM); Caddy reverse proxy provides auto-TLS for `sfu.xaeryx.com` via Let's Encrypt; Node.js auth-proxy co-hosted on the same VM mints LiveKit JWTs and verifies Firebase App Check tokens (DeviceCheck iOS / Play Integrity Android).
- **Domain:** `xaeryx.com` registered via Cloudflare Registrar (DNS-only mode for `sfu.xaeryx.com` — no Cloudflare proxy because LiveKit needs UDP 7881 + 50000-60000); ~$10/year fixed cost amortized across Bania's projects.
- **Distribution v1**: personal sideload only. APK direct install on Android; TestFlight Ad Hoc on iOS. No App Store, no Play Store.

---

## 9. Non-Goals (Canonical)

*This section is the single source of truth for scope exclusions. §2.3 and the v2-candidates section in §10.2 reference it.*

**Not in v1, not in v2 candidates, not ever:**

- **WhatsApp / FaceTime / Meet / any third-party call platform integration.** Technically impossible per TR Findings A, B, F (OS-level sandbox restrictions on both Android and iOS).
- **Group calls.** Exactly two Paired Users, ever.
- **Public distribution.** App Store / Play Store submission is out of scope unless an explicit future decision changes this; see §10.4 for the conditional vision.
- **Conversation content analytics.** The privacy posture rules this out.
- **Third-party integrations.** No WhatsApp interop, no Calendar invites, no Slack integrations, no email summaries.

**Not in v1 (deferred to v2 — see §10.2):**

- **Sundanese full-clause translation.** v1 handles lexical insertions only.
- **Text chat or persistent messaging.** Not a chat app.
- **Cross-device transcript sync.**
- **Multiple paired partners per user.**

**Not goals (clarifying scope):**

- **Not a market product in v1.** No public distribution, no marketing, no growth funnel.
- **Not a transcription / dictation app.** Captions are ephemeral by default; transcript history is per-device convenience.
- **Not a privacy-perfect product.** Conversation text passes through the backend (in memory) and Gemini AI Studio (subject to Google's policies). E2EE deferred to v2.

---

## 10. MVP Scope

### 10.1 In Scope (v1)

- Bidirectional Indonesian ↔ English live captioned voice Calls between two Paired Users.
- Native Android + iOS apps with polished, native UI per §7.
- Anonymous Firebase Auth + 6-digit Pairing Code.
- WebRTC service free tier for signaling/media.
- On-device ASR Providers per platform.
- Auth-proxy on Oracle VM verifies Firebase App Check, mints LiveKit JWTs; translation runs on-device (Plan A) or via Vertex AI Gemini auth-proxy chaining (Plan B).
- Provider Abstraction enforced per §6.4.
- Inline Captions UI with Partial Captions, auto-scroll, translation-failure indication.
- Native incoming-Call UX.
- Settings: transcript history opt-in, post-editor toggle, display name, unpair, privacy summary.
- $0/month operating cost per §6.2.
- Audio Call OR Video Call selectable from Paired home; Video Calls at 360p × 30fps.
- End-to-end encryption (media + Data Channel) via WebRTC Insertable Streams + per-Call X25519 ECDH key exchange.
- Leave-and-rejoin within 5-min window; remaining side sees `CallWaitingForPartnerState` overlay; leaver gets `RejoinNotification` local notification.
- On-device translation pipeline (NLLB-200 / MADLAD / Gemma 2B — Week-1 bake-off locks model) wrapped by `RuleBasedTranslationProvider` decorator composing `ParticleProcessor` rules; Vertex AI Gemini in `asia-southeast1` as Plan B fallback.

### 10.2 Out of Scope for MVP (v2 Candidates)

- **Sundanese support.** v1's Translation Pipeline cannot reliably handle Sundanese. When his girlfriend switches into Sundanese mid-conversation — her actual daily pattern — those phrases will be garbled, mistranslated, or flagged as uncertain. The DR found that lexical insertions (single Sundanese words like `urang`, `atuh`, `mah`) are mostly handled by the v1 system prompt, but full Sundanese clauses (emotional moments, jokes, quoting family) will fail. **This is a real and visible gap.** v2 closes it using chirp_2 chunked Recognize + Google NMT for SU. Given her actual switching pattern, v2 is not optional — just sequenced after v1 ships. [NOTE FOR PM: Bania explicitly chose to keep this harder framing for honesty even though DR's lexical-insertions finding softens it somewhat. Preserve the "not optional" sequencing language in v2 planning.]
- **Quality upgrade: cloud STT.** Swap on-device ASR for cloud streaming STT if on-device quality is inadequate after real-world testing (estimated ~$15/month per platform).
- **Cross-device transcript sync.**

### 10.3 Timeline

Bania's stated speed constraint: he wants v1 fast. The architecture and stack have already been optimized for build speed (managed WebRTC over self-hosted, native over cross-platform, free tiers over enterprise providers). No further compression possible without sacrificing the §7 polish bar.

- **Ramp:** ~1 week (LiveKit room model, Compose StateFlow, SwiftUI Combine, PushKit/CallKit canonical pattern, backend + app-attestation end-to-end, Whisper.cpp Core ML setup).
- **Build:** 7–10 weeks of focused solo work + 1 week ramp, in phases (foundations → audio Calling → translation pipeline → bidirectional + peer display → E2EE → Video Calling → leave-and-rejoin → personalization & settings → testing/bugfix). Revised timeline reflects scope additions from Architecture's SCOPE EXPANSION (Video, E2EE, on-device translation, leave-and-rejoin); originally 4–6 weeks was for the narrower audio-only / cloud-translation v1. Phase plan lives in TR §5.

### 10.4 Vision Beyond v1

The honest core: TranslatorRep exists to help two specific people who love each other communicate without translation labor between them. Whether it ever becomes more than that is a question the experience itself will answer.

- **Months 1–3 after ship:** v1 in daily use; v2 closes the Sundanese gap.
- **Months 3–6:** video, polish, transcript-review features if real adoption justifies them.
- **Beyond v2:** publishing on App/Play Stores for other couples in similar situations is a *possibility, not a plan*. Bania wants this for him and his girlfriend first, fast. Anything beyond that has to be earned by sustained use of v1.

---

## 11. Success Metrics

*Each metric cross-references the FR(s) it validates. Counter-metrics counterbalance specific primary or secondary metrics. The qualitative win-condition is named explicitly.*

### Primary

- **SM-1: Real adoption.** Bania uses TranslatorRep ≥3x per week for the first month after v1 ship. Validates the entire product hypothesis (FR-6 through FR-10 and all Captions FRs). This is the metric that matters most — if they do not actually use it instead of WhatsApp-without-translation, the technical success is wasted.
- **SM-2: Translation quality acceptance.** **≥60% of Captions rated ✅ accurate-and-natural in the §5.3 (TQ-AT) quality acceptance test** for v1 Plan A (on-device translation), with Bania and his girlfriend rating together. Reverts to **≥80%** if Plan B (Vertex AI Gemini) activates. Validates TQ-1 through TQ-8 and FR-13, FR-31 (or FR-14 under Plan B), FR-15.
- **SM-3: Call reliability.** ≥5 successful Calls in the first 2 weeks without bugs (no crashes, no failed Pairings, no missed incoming-Call deliveries). Validates FR-6 through FR-10.

### Secondary

- **SM-4: Latency.** **Median end-to-end Utterance-to-Caption latency <8s; p95 <12s** on a typical 4G connection. Driven by on-device inference: ~5–8s iOS (Whisper.cpp + on-device NMT) / ~3–5s Android. Plan B cloud-STT-or-NMT swaps available at ~$15/month per platform if real-conversation friction surfaces. Validates FR-13, FR-31 (or FR-14 under Plan B), FR-15.
- **SM-5: Battery.** **iPhone battery drain best-effort target: <30% per hour of Call**; Whisper.cpp + on-device translation per-Utterance has 50–70%/hour worst-case observed; Whisper.cpp `small→base` downgrade path and on-device NMT model downsize are mitigations. Plan B (cloud STT or cloud NMT) eliminates the on-device inference cost. Validates FR-13 iOS-side viability.
- **SM-6: Cost.** $0/month sustained for first 3 months. Validates §6.2.

### Qualitative win-condition

- **SM-7: The deep-conversation signal.** Within the first month, Bania and his girlfriend have at least one ≥20-minute Call where no miscommunication occurs and neither has to fall back to WhatsApp text translation or in-conversation clarification. Validates the brief's qualitative success criterion.

### Counter-metrics (do not optimize)

- **SM-C1: Daily-active-user metric.** This is a 2-person personal app — never optimize for DAU. If "≥3x per week" is met and conversations are healthy, ignore raw usage frequency. Counterbalances SM-1.
- **SM-C2: Translation latency vs. translation accuracy.** Do not optimize SM-4 (latency) at the cost of SM-2 (accuracy). Conversational warmth requires correct particles and register; a slightly slower correct translation beats a fast wrong one. Counterbalances SM-4.
- **SM-C3: Caption volume.** Do not optimize for "more Captions per minute" — that incentivizes over-segmentation and breaks conversational flow. Counterbalances FR-12.

---

## 12. Open Questions

1. **Bania's specific Samsung Galaxy device — does the runtime ASR availability probe confirm `id-ID` is installed as on-device?** Probe in Week 1 of build. If absent, fall back to cloud STT for his side (~$15/month) and re-approve cost.
2. **Whisper.cpp battery drain on his girlfriend's specific iPhone model.** Real measurement required in Phase 3 / Phase 6 of the build. If unacceptable (>30%/hour), fall back to cloud STT for her side (~$15/month) and re-approve cost. If Plan A on-device translation also contributes to battery drain such that combined load exceeds 70%/hour, evaluate Plan B (Vertex AI cloud NMT) before further on-device optimizations.
3. **VAD pause threshold (FR-12).** Shipping default 700ms (committed). Will need tuning during real-conversation testing — too short and the system over-segments; too long and Caption cadence feels laggy.
4. **Default Caption screen area percentage (FR-17).** RESOLVED: Audio 40/60, Video 50/50 per Architecture ADR-E1 + UX-spec reconciliation.
5. **Post-editor toggle (FR-22) Settings copy and reflow prompt content.** Plain-language copy to be authored in UX; reflow prompt to be authored during Phase 3 of build (after first real conversations). Reflow prompt only relevant under Plan B (Vertex AI Gemini reflow chain); under Plan A, FR-22 likely deferred to v1.1 (Story 8.7 outcome).
6. **Out-of-band Pairing Code delivery.** [ASSUMPTION: users share their code over WhatsApp text]. v1 does not ship in-app QR or deep-link for pairing; could be added later if friction surfaces.
7. **Translation-quality review tool (DR recommendation).** RESOLVED: ships in v1 as `QualityReviewRow` (Bania-side only, Story 8.9) + `HerSideOneTapReaction` (her-side only, Story 8.10). Aggregated counts surface to Bania.

---

## 13. Assumptions Index

*Inline `[ASSUMPTION: …]` tags are canonical. This index is a navigable view, not a separate source of truth — keep them in sync via the decision log.*

**Open assumptions (not yet resolved):**

- §2.1: Bania owns a Samsung Galaxy with S Pen (inferred from prior conversation about "android pen").
- §4.1 FR-2: 1M code space (6-digit decimal) is sufficient for a personal app with collision check at generation time.
- §4.2 FR-9: End-call confirmation pattern only for Calls >5 minutes.
- §4.3 FR-13: Whisper.cpp small model on A17+ iPhone achieves <3s time-to-finalized-Source-Text.
- §4.4 FR-17: Caption history occupies lower 60% of the In-Call screen.
- §12 Open Question 6: Pairing Code delivery is out-of-band (WhatsApp text); no in-app QR/deep-link v1.

**Resolved during PRD review (2026-05-22) and SCOPE-EXPANSION reconciliation (2026-05-22):**

- §2.1: Her name kept generic ("his girlfriend") in the doc.
- §5.3 / SM-2: Quality acceptance test is paired (Bania + girlfriend together).
- §7 Aesthetic: monochrome glass, centered UI, simplistic / no clutter.
- §4.5 FR-22: Post-editor kept as optional toggle with NOTE FOR PM mandating plain-language explanation in UX; reflow prompt authored during Phase 3 of build.
- §10.2: Sundanese gap kept with harder framing for honesty (DR-softened version rejected).
- Display name: silent default ("Partner") with optional customization in Settings (FR-23).
- §4.3 FR-12: VAD shipping default 700ms / max Utterance 15s (committed from prior assumption).
- §11 SM-4: Latency target loosened to median <3.5s / p95 <5s per TR-reconciliation finding.
- §11 SM-5: Battery target corrected to <30%/hour (was incorrectly stated as <30%/30-min).
- §6.4 NEW: Provider Abstraction added as NFR (was previously glossary-only).
- §6.5 NEW: Warmup ping pattern added as NFR (was previously silent).
- §6.1: Gemini AI Studio data-handling caveat added to privacy section.
- Implementation refs (SDK callback names, library names, JSON schemas) extracted to [Addendum](./addendum.md).
- §4 NEW: 7 FRs added (FR-26 through FR-32) per Architecture SCOPE EXPANSION.
- §4.3 / FR-14: cloud Gemini translation path superseded by FR-31 on-device translation for v1 Plan A; FR-14 cloud path retained as Plan B fallback.
- §6.1: Privacy posture elevated to WhatsApp-equivalent E2EE; Gemini AI Studio caveat removed from v1 baseline (relevant only under Plan B as a Vertex AI note).
- §6.5: Warmup ping pattern removed; replaced with always-on Oracle VM backend.
- §8: Cloud Run replaced with Oracle Ampere A1 VM + Caddy + Node.js auth-proxy + Docker Compose.
- §9 / §10.2: Video and true E2EE removed from v2-deferral lists (now v1 per FR-27 / FR-29).
- §10.3: Timeline revised to 7–10 weeks + 1 ramp.
- §11 SM-2 / SM-4 / SM-5: targets relaxed per on-device inference reality; revert paths under Plan B documented.
- §12 Open Questions 4, 7: marked resolved.
