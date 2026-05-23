---
stepsCompleted: [1, 2, 3, 4]
status: complete
completedAt: 2026-05-22
inputDocuments:
  - _bmad-output/planning-artifacts/briefs/brief-TranslatorRep-2026-05-22/brief.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/prd.md
  - _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/addendum.md
  - _bmad-output/planning-artifacts/architecture.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
  - _bmad-output/planning-artifacts/research/domain-conversational-indonesian-sundanese-linguistics-research-2026-05-22.md
  - _bmad-output/planning-artifacts/research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md
---

# TranslatorRep — Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for TranslatorRep, decomposing requirements from the PRD (24 FRs), Architecture (7 SCOPE-EXPANSION FRs + cross-cutting ADRs A–G + 16 implementation patterns), and UX Design Specification into implementable stories.

**Canonical conflict resolution:** Where PRD/UX file artifacts conflict with the Architecture document's SCOPE EXPANSION, Architecture wins (per architecture.md frontmatter). Architecture is the canonical glossary going forward (Architecture §1 "Canonical Concept Names & Term Forms").

## Requirements Inventory

### Functional Requirements

**Pairing & Identity (UJ-1) — PRD §4.1**

- **FR-1**: Anonymous sign-in on first launch (silent, <3s).
- **FR-2**: Generate Pairing Code on demand (6-digit, collision-checked, unique across active codes).
- **FR-3**: Enter partner's Pairing Code to pair (inline error on invalid; both apps transition to Paired home within 5s of successful pair).
- **FR-4**: Paired state persists across app restarts (Paired home shown on subsequent launches without re-pairing).
- **FR-5**: Unpair from current partner (two-tap confirmation; other side sees "Partner unpaired" on next Call).

**Calling — Place / Receive / End (UJ-2) — PRD §4.2**

- **FR-6**: Place a Call to paired partner (tap-to-ringing <3s; 30s timeout).
- **FR-7**: Receive an incoming Call via native CallKit (iOS) / ConnectionService (Android) on lock screen (<2s delivery).
- **FR-8**: Accept or reject via native call UI (accept transitions both to In-Call within 2s).
- **FR-9**: End an active Call (confirmation for Calls >5 min; teardown within 2s).
- **FR-10**: Audio quality acceptance (Opus ≥24 kbps; AEC enabled; paired listening test on 3 Calls ≥10 min).

**Translation Pipeline (UJ-2, UJ-3) — PRD §4.3**

- **FR-11**: Capture local mic audio during a Call (tap from WebRTC local audio track; not persisted).
- **FR-12**: Detect Utterance boundaries via VAD (700 ms silence commit; 15 s max Utterance; preserve sentence-final particles).
- **FR-13**: Convert Utterance audio to Source Text via on-device ASR (Android: `id-ID` + `en-US` SpeechRecognizer; iOS: Whisper.cpp small multilingual; <2s Android / <3s iOS).
- **FR-14**: Translate Source Text to Target Text *(superseded by FR-31; backend proxy path now Plan B only).*
- **FR-15**: Deliver Target Text to peer via Data Channel (reliable-ordered; <300ms same-region; <1 KB normal).
- **FR-16**: Render Caption on speaker's screen (speaker sees own Source within 1s, Target within 4s).

**Captions UI (UJ-2, UJ-3) — PRD §4.4**

- **FR-17**: Display a scrollable Caption history during the Call (Source/Target visually differentiated; discarded on Call end unless FR-21 enabled).
- **FR-18**: Render Partial Captions for in-progress speech (Android only — iOS uses `SpeakingIndicator` per Architecture ADR-B5; local-only, never sent to peer).
- **FR-19**: Auto-scroll Caption history to newest; manual scroll-up suspends auto-scroll until "jump to latest" affordance or scroll-back-to-bottom.
- **FR-20**: Visually distinguish translation failures via `TranslationUnavailableMarker` (state-amber); long-press for explanation.

**Settings — PRD §4.5**

- **FR-21**: Opt in to per-device transcript history (Room+SQLCipher Android / SwiftData+NSFileProtectionComplete iOS; never synced).
- **FR-22**: Toggle translation post-editor *(status uncertain under on-device translation per Architecture; defer to Plan B activation).*
- **FR-23**: Set custom display name (default "Partner").
- **FR-24**: View privacy summary (plain-English; surfaces §6.1; updated post-SCOPE-EXPANSION to remove Gemini caveat from v1 baseline).

*(FR-25 reserved — Unpair is FR-5, surfaced in Settings.)*

**SCOPE EXPANSION — surfaced by Architecture (CA), additions to v1**

- **FR-26**: Audio Call vs Video Call selection on Paired home (two-button `CallTypeSelector`; `callType` claim in LiveKit JWT metadata; native incoming-call header reflects type) — ADR-A3.
- **FR-27**: Video pipeline + failure UX (360p × 30fps; lazy camera permission; video drop → `VideoPausedTile` neutral grey + auto-retry 5s + manual retry; audio continues) — ADR-A4.
- **FR-28**: Speaker / earpiece / Bluetooth audio-routing toggle in `CallControlRow` (both Audio + Video Call) — ADR-A5.
- **FR-29**: E2EE setup + per-call X25519 ECDH key exchange (long-term identity keypair on first launch; ephemeral per Call; HKDF-SHA256 → AES-GCM 256-bit feeds LiveKit `e2eeOptions.keyProvider`; forward secrecy; ephemeral pub signed by identity key) — ADR-A2.
- **FR-30**: Camera permission flow (lazy; requested on first Video Call tap, never on first launch; `CameraPermissionFlow` on denial with Settings deep-link) — ADR-E3.
- **FR-31**: On-device translation pipeline (replaces FR-14 cloud path for v1 Plan A — NLLB-200 / MADLAD / Gemma 2B candidates; Week-1 bake-off locks model; `RuleBasedTranslationProvider` decorator wraps `RawTranslationProvider` + `ParticleProcessor`) — ADR-B1/B2/B3.
- **FR-32**: Leave-and-rejoin within 5-min window (`empty_timeout: 300`; either-side end → other side sees `CallWaitingForPartnerState` overlay; `RejoinNotification` local notification on leaver; symmetric; single end-Call gesture) — ADR-A6.

### NonFunctional Requirements

- **NFR-Privacy** *(Architecture-elevated from PRD §6.1 + SCOPE EXPANSION):* WhatsApp-equivalent or better — E2EE media client-to-client via Insertable Streams; translation text never leaves device (Plan A); Firestore stores public keys + metadata only; no conversation content server-side. Week-1 verification gate: packet capture confirms SFU sees ciphertext + manual e2e test + pipeline review.
- **NFR-Provider-Abstraction** *(PRD §6.4):* `AsrProvider` and `TranslationProvider` interfaces, symmetric across Kotlin/Swift surfaces; v1 binds on-device impls; Plan B + v2 Sundanese swap without rewrite. Cancellation contract: `stop()` releases resources within 500 ms (testable).
- **NFR-Cost:** $0/month operating + ~$10/year fixed for `xaeryx.com` domain (treated as infrastructure, amortized across Bania's projects). Cloud Billing alerts at $1/$5/$10; hard killswitch at $50.
- **NFR-Latency** *(SM-4 relaxed per Architecture):* Median end-to-end Utterance-to-Caption <8s; p95 <12s on typical 4G. Driven by on-device inference: ~5–8s iOS / ~3–5s Android realistic.
- **NFR-Quality** *(SM-2 relaxed):* ≥60% of Captions rated ✅ accurate-and-natural in TQ-AT for v1 Plan A (reverts toward ≥80% under Plan B). Quality acceptance test is paired (Bania + girlfriend together).
- **NFR-Battery** *(SM-5 best-effort):* <30%/hour target acknowledged best-effort; potential 50–70%/hour worst case with Whisper.cpp + on-device translation per-Utterance; Whisper.cpp `small→base` downgrade path noted.
- **NFR-App-Size:** +250 MB to ~1.5 GB acceptable; sideload distribution makes this fine. Would block App Store / Play Store submission.
- **NFR-Reliability:** Never silently drop captions (FR-20); offline-degraded ASR works without network; translation requires network — offline shows Source Text with network-error indicator; graceful video → audio fallback on poor network.
- **NFR-Cold-Start:** Removed warmup-ping pattern — Oracle VM always-on, no cold-start window. Cloud Run removed entirely per ADR-C1.
- **NFR-Observability** *(privacy-safe):* Crashlytics opt-in, default off; conversation content NEVER logged; only Call IDs, language codes, error-type strings, model load timings, ASR/translation duration metrics (sanitized). SafeLog facade + explicit allowlist (`AllowedLogKey` enum). Inter-turn-gap + Call-duration metadata logging local-only on each device (feeds "friction-gone" composite signal).
- **NFR-Cross-Platform-Parity** *(Experience Principle 5):* "Identical to the eye" — layouts, element positions, animation timings, color tokens, spacing, touch targets all identical across iOS/Android per chosen theme. Native primitives below the visible layer diverge acceptably.
- **NFR-Translation-Quality-Preservation Targets** *(PRD §5.2):* TQ-1 discourse particles, TQ-2 pronoun register, TQ-3 gender neutrality, TQ-4 Sundanese lexical insertions, TQ-5 partner honorifics, TQ-6 religious expressions, TQ-7 indirect refusals, TQ-8 Gen-Z slang. Owned by `ParticleProcessor` rules-based pre/post (ADR-B3) for Plan A; system prompt for Plan B.

### Additional Requirements

*(Architecture-driven items required for v1 implementation but not user-facing FRs. These become stories in scaffolding/infra/observability epics.)*

- **AR-1 Oracle VM deployment**: Ubuntu 24.04 LTS ARM on Always-Free Ampere A1 (4 OCPU, 24 GB RAM); Docker Compose stack (`livekit-server`, `redis`, `caddy`, `auth-proxy`); recording/egress off. — ADR-A1
- **AR-2 Domain & DNS**: `xaeryx.com` via Cloudflare Registrar; `sfu.xaeryx.com` DNS-only mode (no Cloudflare proxy — LiveKit UDP on 7881 + 50000-60000); WHOIS privacy + 2FA.
- **AR-3 TLS via Caddy**: Auto Let's Encrypt for `sfu.xaeryx.com` (+ optional `auth.xaeryx.com`); zero manual cert rotation after first deploy.
- **AR-4 Node.js auth-proxy** on Oracle VM: `POST /token` endpoint; verifies Firebase App Check (DeviceCheck iOS / Play Integrity Android) via Admin SDK JWKS; mints LiveKit JWT including `callType` claim. — ADR-C1/C2
- **AR-5 Firestore schema**: `/users/{uid}`, `/pairs/{pairId}`, `/codes/{6digit}`, `/calls/{callId}/ephemeralPub/{uid}`; public keys + metadata only; ULID-or-Firebase-auto IDs. — ADR-C3
- **AR-6 Firestore rules**: Restrict reads/writes to pair members + call participants; explicit deny on conversation-content fields.
- **AR-7 Firebase App Check setup**: DeviceCheck registration (iOS) + Play Integrity registration (Android); JWKS cached by Admin SDK.
- **AR-8 Per-stack scaffolding** (Sprint 1, 4 parallel stories): Android Studio Compose project (minSDK 33, target 35); Xcode SwiftUI project (minOS 17, target iOS 26); Oracle VM Docker Compose; `firebase init` (Auth anonymous + Firestore + App Check).
- **AR-9 ParticleProcessor module** with rule tables sourced from DR §1/§3/§4/§6: particles, Gen-Z slang, Sundanese insertions, honorifics, religious expressions, indirect refusals; golden-file fixtures in `/shared/particle-rules-fixtures/` ensuring cross-platform parity. — Patterns §11
- **AR-10 Week-1 validation gate**: On-device model bake-off (NLLB-200 Rank 1 → MADLAD Rank 2 → Gemma Rank 3); kill-criteria per model; Plan B escalation decision-point. — Gap I.1 + I.16
- **AR-11 Plan B Gemini fallback path**: `VertexGeminiProvider` impl + `shared/prompts/id-en-plan-b.md`; activates if all Plan A candidates fail Week-1 gate; cost +~$5/month.
- **AR-12 Regression corpus**: ~200 utterances in `/shared/regression-corpus/` tagged by DR section (§1 particles / §3 slang / §4 SU insertions / §6 cultural-pragmatic); run on every model swap AND every ParticleProcessor rule change. — Gap I.14
- **AR-13 Three-layer translation capture pipeline**: Debug-flag-gated SafeLog-redacted trace: raw NMT output → post-processed → displayed text. Enables Week-4 quality-regression attribution. — Gap I.15
- **AR-14 SafeLog facade + enforcement**: `SafeLog.event(AllowedLogKey, value)` on both platforms; detekt rule banning `android.util.Log.*` outside `SafeLog.kt`; SwiftLint rule banning `print`/`os_log`/`Logger` outside `SafeLog.swift`; CI blocks merge on violations. — Patterns §14
- **AR-15 ULID for all canonical-entity IDs** (`Pair.id`, `Call.id`, `Utterance.id`, `Caption.id`, `MessageId`): 26-char Crockford base32; ULID library pinned per platform at scaffolding time. — Patterns §4 + Gap I.12
- **AR-16 Data Channel schema**: Versioned (`schema_version: 1`); idempotent (`(speaker_uid, seq)` dedup key, monotonic `seq` counter); ordered with ~500ms reorder buffer; snake_case wire format; tolerant decoder (drop unknown major version, accept unknown fields within major). — Patterns §5
- **AR-17 CI/CD per stack**: `.github/workflows/android-ci.yml` (detekt → unit → Compose UI → Roborazzi → APK), `ios-ci.yml` (SwiftLint → xcodebuild test → snapshot → TestFlight Ad Hoc on tag), `infra-ci.yml` (yamllint → docker compose config → deploy via ssh on tag). GitHub Actions free tier.
- **AR-18 Cross-platform spec sharing**: `/shared/canonical-names.md`, `/shared/error-codes.md`, `/shared/data-channel-schema-v1.json`, `/shared/particle-rules-fixtures/`, `/shared/regression-corpus/`, `/shared/auth-proxy-api.md`, `/shared/state-derivation.md`.
- **AR-19 Privacy verification protocol** (Week 1): Packet capture confirms SFU sees only ciphertext bytes for media + data channel; manual e2e test confirms translation never reaches network; pipeline-review confirms no conversation content in Firestore writes. — Gap I.19 single-failure-domain documentation
- **AR-20 Pre-validation conversation with girlfriend** (per Mary's BA framing): Structured ~15-min: "what would make you stop using this?" + "is ID-only acceptable for 4–8 weeks while SU comes in v2?". Converts inferred emotional spec to evidence. — Gap I.18
- **AR-21 Solo-dev scope-cut criteria**: Documented morale-collapse triggers in `docs/runbooks/` (e.g., week-5 trigger: defer quality-review tool to v1.1). — Gap I.17
- **AR-22 Failure-state taxonomy + state-priority choreography**: 8 failure states (`translationFailed`, `asrLowConfidence`, `sundaneseClause`, `networkDropped`, `videoPaused`, `e2eeKeyNotReady`, `modelLoading`, `waitingForPartner`); state-priority order locked; one banner at a time; per-Caption inline markers independent. Cross-platform identical priority. — Patterns §7
- **AR-23 PRD reconciliation deliverables**: Update §6.1 privacy, §10.2 v2-deferral, §10.3 timeline (~7-10 weeks + 1 ramp), §11 success metrics (SM-2/4/5 relaxed targets), surface FR-26 to FR-32 in §4. — Gap I.5 + ADR-G
- **AR-24 UX spec reconciliation deliverables**: Remove Theme B (Light high-contrast) per Architecture S3 decision; add ADR-E5 new components (`CallTypeSelector`, `VideoTile`, `VideoPausedTile`, `VideoMutedTile`, `VideoCallControlRow`, `AudioCallControlRow` rename, `AudioRoutingToggle`, `CameraPermissionFlow`, `E2EEKeyExchangeIndicator`, `RejoinNotification`, `CallWaitingForPartnerState`); revise In-Call layouts (Audio 40/60, Video 50/50); document Theme C × Video interaction (video replaces image in upper region). — Gap I.6 + ADR-G

### UX Design Requirements

*(From UX-spec component inventory + locked layout decisions + accessibility specs + Architecture ADR-E5 additions. Each is specific enough to generate a story.)*

**Foundation & theming**

- **UX-DR1**: Design token system — single monochrome neutral scale + 2 state colors (state-amber, state-red); typography scale validated for Indonesian compound words (Caption-primary 22pt iOS / 20sp Android, 1.4× line height); spacing scale 4/8/12/16/24/32/48/64; shape radii (16–24dp/pt sheets, 4–6dp/pt inline); motion timings (200–300ms primary, 150ms micro, cubic-bezier).
- **UX-DR2**: Theme A (Dark, default) — full token map per UX §"Color System".
- **UX-DR3**: Theme C (Custom image background) — user-chosen local photo, `BackgroundImageOverlay` adaptive 0.40–0.55 darkening, `.thickMaterial` blur uniformly, local-only storage (never synced), file-picker via PHPickerViewController/PickVisualMedia, sandbox storage.
- **UX-DR4**: 2-option `ThemePicker` in Settings (Dark / Custom image background) — Theme B removed per Architecture S3.
- **UX-DR5**: `MonochromeGlassPanel` primitive — backdrop blur (3 intensities: thick/regular/thin), 1px low-opacity border, translucent fill; iOS native `Material` thicknesses + Android `RenderEffect.createBlurEffect`.

**Caption stack (load-bearing)**

- **UX-DR6**: `CaptionStack` with `LazyVStack`/`LazyColumn`, stable row identity, auto-scroll + manual-scroll-suspend; `JumpToLatestPill` visible only when suspended.
- **UX-DR7**: `CaptionRow` — Source on top (`text-secondary` opacity) / Target below (`text-primary` opacity) for partner rows; peripheral styling for self rows (~60% opacity, smaller body, right-aligned); TQ-1 / TQ-2 emotional-weight letter-spacing (+0.3pt tracking, no weight/color/animation change). — Direction 1 + D1a + D2c locked
- **UX-DR8**: `PartialCaption` (Android-only) — streaming partial transcripts at `text-tertiary` opacity + italic, pinned at stack bottom, finalize without row reorder.
- **UX-DR9**: `SpeakingIndicator` (iOS-only) — calm "speaking…" pill + 5-bar audio-level pulse at stack bottom; honest asymmetry vs Android; same screen position partial row would occupy; surfaces `AsrProvider.supportsStreamingPartials = false`.
- **UX-DR10**: `TranslationUnavailableMarker` — state-amber, replaces target slot, long-press shows error reason inline (no separate sheet); retry button if error type retryable.
- **UX-DR11**: `SundanesePlaceholderRow` — `[Sundanese]` in dim italic at `text-tertiary` opacity; designed against anti-emotion #2 (othering); no retry/alarm; conversation continues.
- **UX-DR12**: `JumpToLatestPill` — bottom-right glass pill, ↓ glyph + "Latest", appears only when auto-scroll suspended.

**Pairing**

- **UX-DR13**: `PairingCodeInput` — single 6-character field with letter-spacing, numeric keyboard, large hit targets (≥48dp/44pt), inline errors below input ("Code not found" / "That's your own code" / "Code expired"), submit disabled until 6 digits entered.
- **UX-DR14**: `PairingCodeDisplay` — Display typography (48pt/44sp), 6 digits with ~10–12pt tracking, tap-to-copy with announced confirmation, long-press regenerate.
- **UX-DR15**: Partner-input-first Paired-Empty home layout (D4b) — partner-code input field foregrounded, own code below divider.

**In-Call screen — Audio & Video**

- **UX-DR16**: In-Call vertical layout — Audio Call **40% upper / 60% Caption stack**; Video Call **50% upper (`VideoTile` + corner-overlay PiP local self-view) / 50% Caption stack**. Reject backdrop-video-with-captions-overlay (translation theater).
- **UX-DR17**: `CallTypeSelector` — two buttons on Paired home: Audio Call / Video Call. — FR-26
- **UX-DR18**: `AudioCallControlRow` (renamed from `CallControlRow`) — mute audio, audio-routing toggle, end-Call.
- **UX-DR19**: `VideoCallControlRow` — 5 controls single row: mute audio, mute video, flip camera (front/back), audio-routing toggle, end-Call. — FR-27 + ADR-E4
- **UX-DR20**: `AudioRoutingToggle` — three logical states (Earpiece / Speaker / Bluetooth-when-connected); auto-routes BT/wired when detected at Call start; toggle overrides default. — FR-28
- **UX-DR21**: `VideoTile` + `VideoPausedTile` (**neutral grey, NOT amber** — disambiguates from translation failure) + `VideoMutedTile` (camera off via partner — name initial centered on dark surface); auto-retry every 5s with manual retry button. — FR-27
- **UX-DR22**: `CameraPermissionFlow` — lazy permission request triggered on first Video Call (caller) or first incoming Video Call accept (recipient); on denial, calm explanation + Settings deep-link. — FR-30
- **UX-DR23**: `AudioLevelIndicator` — 5 vertical monochrome bars + mic-pulse dot; anti-evaluative (no green-yellow-red gradient, no "too quiet" copy).
- **UX-DR24**: `E2EEKeyExchangeIndicator` — one-time on first Call after pairing, neutral, tap for explanation; confirms key established. — FR-29

**Leave-and-rejoin**

- **UX-DR25**: `CallWaitingForPartnerState` — banner/overlay on remaining side when partner leaves: "Partner left — can rejoin"; captions continue rendering; remaining person's utterances NOT queued for the leaver. — FR-32
- **UX-DR26**: `RejoinNotification` — local notification on leaver side ("Bania is still in the call — Rejoin"); cleared on tap-rejoin / other-party-leave / 5-min-timeout. **NOT a CallKit re-ring.** — FR-32

**Post-Call surfaces (asymmetric privacy)**

- **UX-DR27**: `QualityReviewRow` (Bania-side ONLY) — per-Caption ✓/⚠️/✗ rating for last N captions, post-Call sheet, feeds SM-2 + system-prompt iteration loop, rendered ONLY on Bania's device, never synced.
- **UX-DR28**: `HerSideOneTapReaction` (her-side ONLY) — single ✓/✗ on Call-end dismissal sheet, aggregated locally; surfaces to Bania as counts only ("4 of 5 ✓"), never per-utterance review of her speech. Designed against anti-emotion #1 (self-monitoring anxiety).

**Settings**

- **UX-DR29**: Settings sheet contains: `ThemePicker` (UX-DR4), `BackgroundImagePicker` (visible only when Image theme selected), transcript history toggle (FR-21), translation post-editor toggle (FR-22; status pending Plan B), display name input (FR-23), Crashlytics opt-in toggle (default off), privacy summary entry (FR-24), unpair entry (FR-5 two-tap confirm).
- **UX-DR30**: `BackgroundImagePicker` — native file picker, sandbox storage, thumbnail + "Choose photo" / "Change" / "Remove" affordances, "Stored on this device only" hint at Footnote size.

**Caption Loop choreography (per-Utterance UX)**

- **UX-DR31**: 9-beat Caption Loop renders correctly: (1) speech onset → mic-active dot pulses, (2a) Android partial streams to `PartialCaption` / (2b) iOS shows `SpeakingIndicator`, (3) Utterance commit transitions row identity-stable, (4) translation in flight shows subtle "thinking…" indicator (no spinner/shimmer), (5) translation arrives on speaker's row (peripheral styling), (6) peer's row appends with 200ms Linear-easing fade-in + auto-scroll, (7) TQ-1 hit renders +0.3pt letter-spacing, (8) failure → `TranslationUnavailableMarker` (both sides), (9) Sundanese clause → `SundanesePlaceholderRow` on EN side. — UX §"Defining Experience"
- **UX-DR32**: State-priority choreography enforced cross-platform — one banner at a time per Architecture §7; per-Caption inline markers continue independently; priority: `e2eeKeyNotReady > modelLoading > waitingForPartner > networkDropped > translationFailed > videoPaused > sundanesePlaceholder > asrLowConfidence`.

**Accessibility & motion**

- **UX-DR33**: Accessibility — WCAG AAA contrast (>15:1 primary text on Theme A); Dynamic Type with caption clamping `[18pt, 28pt]` iOS / `[16sp, 26sp]` Android; VoiceOver/TalkBack labels per UX §"Accessibility Considerations" (e.g., *"Bania said: 'I love you.' Translated: 'Aku cinta kamu.'"*); **captions NOT auto-announced during a Call** (audio is already there); no AI framing in hints; High Contrast deepens text to 1.0 opacity; Reduced Motion ≤80ms timing + skip fade-ins + jump-scroll.
- **UX-DR34**: Forbidden-strings audit — no UI copy matching any of 8 forbidden strings (self-monitoring anxiety, othering, performance anxiety, instrumentalization, translation theater, app-pride, apologetic-corporate, pity, AI-app feeling). Code-review gate.

**Navigation, feedback, forms**

- **UX-DR35**: Navigation — single-surface app, no bottom tab bar / hamburger / nav rail; sheet-modal pattern (`.sheet()` iOS / `ModalBottomSheet` Android) for Settings + post-Call + Quality Review + Theme picker + Background image picker + Unpair; In-Call full-screen modal-like with back-gesture suppressed; top-right Settings gear on Paired home(s).
- **UX-DR36**: Feedback patterns — inline errors (default), `TranslationUnavailableMarker` (in-Call), `SundanesePlaceholderRow` (in-Call), snackbar (only for clipboard-copy), sheet confirmation (only for destructive). **Forbidden v1**: toasts, alert dialogs (OK/Cancel), banner notifications at top, push notifications during a Call.
- **UX-DR37**: Form patterns — single-purpose, no wizards; inline validation never blocking; no required-field decoration; no Save button on Settings (persist on toggle); native numeric keyboard for `PairingCodeInput`.
- **UX-DR38**: Button hierarchy — three tiers only (primary filled glass pill / secondary text-secondary opacity / destructive state-red fill for End-Call only); two-tap confirm for Unpair + Calls >5 min End.

### FR Coverage Map

| FR | Epic | Notes |
|---|---|---|
| FR-1 Anonymous sign-in | Epic 1 | |
| FR-2 Generate Pairing Code | Epic 1 | |
| FR-3 Enter Pairing Code | Epic 1 | |
| FR-4 Paired state persists | Epic 1 | |
| FR-5 Unpair | Epic 1 | Settings entry-point shell here; full Settings UI in Epic 8 |
| FR-6 Place Call | Epic 2 | |
| FR-7 Incoming Call notification | Epic 2 | |
| FR-8 Accept/reject Call | Epic 2 | |
| FR-9 End Call | Epic 2 | |
| FR-10 Audio quality | Epic 2 | Paired listening test as part of acceptance |
| FR-11 Mic capture | Epic 3 | |
| FR-12 VAD | Epic 3 | |
| FR-13 On-device ASR | Epic 3 | |
| FR-14 *(superseded)* | Epic 3 | Replaced by FR-31; Plan B fallback referenced only |
| FR-15 Data Channel delivery | Epic 4 | |
| FR-16 Speaker's caption render | Epic 3 | |
| FR-17 Caption history | Epic 4 | |
| FR-18 Partial Captions | Epic 3 (speaker) / Epic 4 (peer) | Speaker-side in Epic 3, full bidirectional in Epic 4 |
| FR-19 Auto-scroll | Epic 4 | |
| FR-20 Translation failure marker | Epic 3 (basic) / Epic 4 (full) | Basic marker speaker-side in Epic 3; full UX with explanation in Epic 4 |
| FR-21 Transcript history | Epic 8 | |
| FR-22 Post-editor toggle | Epic 8 | Status pending Plan B (likely deferred to v1.1) |
| FR-23 Display name | Epic 8 | |
| FR-24 Privacy summary | Epic 8 | Reflects post-SCOPE-EXPANSION privacy posture |
| FR-25 *(reserved)* | — | Unused; Unpair is FR-5 |
| FR-26 Audio vs Video selector | Epic 2 (single button) / Epic 6 (full selector) | Single Call button ships in Epic 2; CallTypeSelector evolves in Epic 6 |
| FR-27 Video pipeline + failure UX | Epic 6 | |
| FR-28 Audio routing toggle | Epic 2 | Earpiece / Speaker / Bluetooth |
| FR-29 E2EE setup + ECDH | Epic 5 | |
| FR-30 Camera permission lazy | Epic 6 | |
| FR-31 On-device translation pipeline | Epic 3 | Includes Week-1 validation gate |
| FR-32 Leave-and-rejoin | Epic 7 | |

**31 FRs total → all mapped. FR-25 explicitly reserved (no story). FR-14 explicitly superseded by FR-31 for v1 Plan A.**

## Epic List

### Epic 1: Foundation & Pairing

Both partners install the app, sign in anonymously, exchange a 6-digit pairing code, and reach the Paired home screen — the singular Paired Users relationship is formed. Lays scaffolding (Android Compose, iOS SwiftUI, Oracle VM Docker Compose, Firebase) and produces the PRD + UX-spec reconciliation deliverables that downstream epics reference.

**FRs covered:** FR-1, FR-2, FR-3, FR-4, FR-5
**ARs covered:** AR-1, AR-2, AR-3, AR-5, AR-6, AR-7, AR-8, AR-14, AR-15, AR-17, AR-18, AR-21, AR-23, AR-24
**UX-DRs covered:** UX-DR1, UX-DR2, UX-DR5, UX-DR13, UX-DR14, UX-DR15, UX-DR29 (shell), UX-DR33 (a11y baseline), UX-DR34, UX-DR35, UX-DR37, UX-DR38

### Epic 2: Audio Calling

Paired users place, receive, and end native-feeling audio Calls; lock-screen ring works; audio quality matches WhatsApp; audio routes between earpiece, speaker, and Bluetooth. Captions not yet wired.

**FRs covered:** FR-6, FR-7, FR-8, FR-9, FR-10, FR-28
**ARs covered:** AR-4, AR-22 (seeds)
**UX-DRs covered:** UX-DR16 (Audio 40/60), UX-DR17 (single Call button), UX-DR18, UX-DR20, UX-DR23, UX-DR36 (in-Call feedback)

### Epic 3: One-Direction Translation Pipeline

Speaker speaks; their own captions stream live (Android partials) or "speaking…" pill appears (iOS); on-device ASR finalizes; on-device translation produces target text; speaker sees source + target on their own row. Proves the end-to-end translation pipeline works for one direction and includes the Week-1 model validation gate.

**FRs covered:** FR-11, FR-12, FR-13, FR-16, FR-31 *(supersedes FR-14)*
**ARs covered:** AR-9, AR-10, AR-11, AR-12, AR-13, AR-20
**UX-DRs covered:** UX-DR6 (basic), UX-DR7 (speaker-side only), UX-DR8, UX-DR9, UX-DR10 (basic), UX-DR31 (beats 1–5)

### Epic 4: Bidirectional Captions & Failure States

Both partners see each other's translated captions in real time. Caption history is scrollable with auto-scroll + suspend + JumpToLatestPill; failures render calmly (translation unavailable with explanation, Sundanese placeholder); state-priority choreography unified cross-platform.

**FRs covered:** FR-15, FR-17, FR-18 (full peer), FR-19, FR-20 (full)
**ARs covered:** AR-16, AR-22 (fully wired)
**UX-DRs covered:** UX-DR6 (full), UX-DR7 (full), UX-DR10 (full), UX-DR11, UX-DR12, UX-DR31 (beats 6–9), UX-DR32

### Epic 5: End-to-End Encryption

Calls are end-to-end encrypted (media + Data Channel) via Insertable Streams + per-Call X25519 ECDH; the SFU sees only ciphertext; forward-secrecy per Call; MITM-resistant via identity-key signing. Privacy verification protocol (packet capture + manual e2e + pipeline review) validates NFR-Privacy.

**FRs covered:** FR-29
**ARs covered:** AR-19
**UX-DRs covered:** UX-DR24

### Epic 6: Video Calling

Paired users choose between Audio Call and Video Call from the Paired home; Video Calls show 360p partner tile with corner-overlay self-PiP; video can be muted, camera flipped; network drops gracefully pause video while keeping audio; lazy camera permission flow.

**FRs covered:** FR-26 (full), FR-27, FR-30
**ARs covered:** *(none new)*
**UX-DRs covered:** UX-DR16 (Video 50/50), UX-DR17 (full selector), UX-DR19, UX-DR21, UX-DR22

### Epic 7: Leave-and-Rejoin Resilience

When either side ends, the room survives for 5 minutes: remaining side stays in-Call with quiet "Partner left — Rejoin" overlay (captions continue); leaver gets a persistent local notification to rejoin; transient drops auto-recover without user action; after 5 min empty, room cleanly destroys.

**FRs covered:** FR-32
**ARs covered:** AR-22 (`waitingForPartner` choreography wired)
**UX-DRs covered:** UX-DR25, UX-DR26

### Epic 8: Personalization, Settings & Post-Call Surfaces

Each user personalizes their app (theme: Dark or Custom image background; display name; transcript history opt-in; privacy summary; Crashlytics opt-in). Bania has a private per-Caption quality-review tool post-Call; she has a single one-tap reaction; aggregated counts surface to him only (never per-utterance). Post-editor toggle status finalized.

**FRs covered:** FR-21, FR-22, FR-23, FR-24
**ARs covered:** *(none new)*
**UX-DRs covered:** UX-DR3, UX-DR4, UX-DR27, UX-DR28, UX-DR29 (full), UX-DR30

---

## Epic 1: Foundation & Pairing

Both partners install the app, sign in anonymously, exchange a 6-digit pairing code, and reach the Paired home screen — the singular Paired Users relationship is formed. Lays scaffolding (Android Compose, iOS SwiftUI, Oracle VM Docker Compose, Firebase) and produces the PRD + UX-spec reconciliation deliverables that downstream epics reference.

### Story 1.1: Android Project Scaffold — 🟡 FILES GENERATED 2026-05-22 (build verification pending Android Studio open)

As a solo developer,
I want a clean Android Studio project scaffolded with Compose, the project's locked dependencies, and the Theme A monochrome-glass token layer,
So that I have a baseline native Android app structure that all future stories build on top of.

**Acceptance Criteria:**

**Given** no Android project exists in `/android/`,
**When** I scaffold the project,
**Then** an Android Studio Empty Activity (Compose) project exists at `/android/` with minSDK 33 + target SDK 35
**And** Gradle Kotlin DSL + `gradle/libs.versions.toml` version catalog is wired
**And** dependencies include LiveKit Android SDK (latest stable with Insertable Streams `e2eeOptions`), Firebase BOM (Auth, Firestore, App Check, Crashlytics), `androidx.security:security-crypto`, Room + SQLCipher, Kotlin Coroutines + Flow
**And** `AndroidManifest.xml` declares permissions `RECORD_AUDIO`, `CAMERA`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `MANAGE_OWN_CALLS`, `POST_NOTIFICATIONS`
**And** `Theme.kt` overrides Material 3 `darkColorScheme` with Theme A tokens (UX-DR2) — `setDefaultNightMode(MODE_NIGHT_YES)` at `Application.onCreate()`, Material You dynamic color disabled
**And** `MonochromeGlassPanel.kt` exists in `ui/components/` rendering backdrop blur via `RenderEffect.createBlurEffect` at thick/regular/thin intensities (UX-DR5)
**And** a hello-world screen launches on a physical Galaxy device showing the glass panel

**Status (2026-05-22):** 🟡 All scaffold files written under `/android/` and ready for Android Studio import. Specifically:
- ✅ `build.gradle.kts` (top-level) + `settings.gradle.kts` + `gradle.properties` + `gradle/wrapper/gradle-wrapper.properties` (Gradle 8.10.2) + `gradle/libs.versions.toml` (version catalog with bump-rationale comments; AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, LiveKit 2.25.3, Firebase BOM 33.7.0)
- ✅ `app/build.gradle.kts` (Android application module with all required dependency bundles: compose, firebase, room, lifecycle, coroutines; namespace `com.xaeryx.translatorrep`; minSDK 33, target+compile SDK 35, Java 17 toolchain)
- ✅ `app/src/main/AndroidManifest.xml` with all 9 permissions Story 1.1 requires (`RECORD_AUDIO`, `CAMERA`, `BLUETOOTH`+`BLUETOOTH_CONNECT`, `MANAGE_OWN_CALLS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `POST_NOTIFICATIONS`, `INTERNET`+`ACCESS_NETWORK_STATE`, `MODIFY_AUDIO_SETTINGS`, `WAKE_LOCK`) + hardware-feature declarations + placeholder for Story 2.5 ConnectionService/MessagingService (commented)
- ✅ `TranslatorRepApplication.kt` with `setDefaultNightMode(MODE_NIGHT_YES)` at `onCreate()` per AC; Firebase init deferred to Story 1.4 (TODO marked)
- ✅ `MainActivity.kt` with hello-world Compose host rendering `MonochromeGlassPanel` + title text
- ✅ `ui/theme/Color.kt` with all 10 Theme A monochrome-glass tokens per UX-DR2 (surface-base, surface-glass, border-glass, text-primary/secondary/peripheral/tertiary, state-amber, state-red) + Theme C overlay tokens (deferred to Epic 8)
- ✅ `ui/theme/Theme.kt` overriding Material 3 `darkColorScheme` with Theme A tokens; Material You dynamic color explicitly NOT invoked; transparent status/nav bars
- ✅ `ui/theme/Type.kt` with all 7 typography roles per UX spec (Display 44sp tracking 4sp, Title 24sp Medium, Caption-primary 20sp 1.4× line height, Caption-peripheral 16sp, Caption-source 14sp, Body 16sp, Footnote 12sp)
- ✅ `ui/components/MonochromeGlassPanel.kt` per UX-DR5 with `RenderEffect.createBlurEffect` at Thick (24px) / Regular (16px) / Thin (8px) intensities (file-level note documents the Android-vs-iOS asymmetry: SwiftUI native `Material` produces true backdrop blur; Android `RenderEffect` blurs the panel's own content — visual upgrade to true backdrop blur via `haze` library deferred to a Phase-5 polish story; the public API surface is stable)
- ✅ `res/values/strings.xml` (app_name, scaffold_ready) + `res/values/themes.xml` (XML theme parent for system-chrome behavior) + `res/xml/data_extraction_rules.xml` + `res/xml/backup_rules.xml` (cloud-backup denial per NFR-Privacy)
- ✅ `proguard-rules.pro` placeholder with checklist of rules to add when isMinifyEnabled flips to true
- ✅ `/android/README.md` documenting first-time setup steps + version-pinning notes + known-not-wired items (google-services.json, app icon, SafeLog, ULID library)
- ✅ `/android/.gitignore`

**Verification pending (NOT autonomously completable):**
- ⏸ Bania opens `/android/` in Android Studio and first Gradle sync completes successfully.
- ⏸ Hello-world Compose screen launches on Bania's physical Samsung Galaxy via USB debugging.
- ⏸ Build verification — the version pins are best-knowledge at 2026-05-22 scaffolding; first-sync may surface AGP/Compose-BOM compatibility issues that require small bumps. Documented in `libs.versions.toml` comments.

**Known build-blocker on first sync:** `google-services` plugin will fail until `app/google-services.json` exists. Two workarounds documented in `/android/README.md`: (a) comment-out the two Firebase plugin aliases in `app/build.gradle.kts` for the first sync; or (b) drop in a placeholder `google-services.json`. Story 1.4 makes this permanent.

### Story 1.2: iOS Project Scaffold

As a solo developer,
I want a clean Xcode SwiftUI project scaffolded with locked SPM dependencies, VoIP entitlements, and the `.translatorRepStyle()` token layer,
So that I have a baseline native iOS app structure that all future stories build on top of.

**Acceptance Criteria:**

**Given** no iOS project exists in `/ios/`,
**When** I scaffold the project,
**Then** an Xcode iOS App project (SwiftUI lifecycle, Swift 5.9+) exists at `/ios/` with minOS 17 + target iOS 26
**And** SPM dependencies include LiveKit Swift SDK (latest stable), Firebase iOS SDK (Auth, Firestore, App Check, Crashlytics), Whisper.cpp XCFramework
**And** `Info.plist` declares `UIBackgroundModes: voip`, `NSMicrophoneUsageDescription`, `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`
**And** `TranslatorRep.entitlements` declares `com.apple.developer.voip` + push environment
**And** `TranslatorRepStyle.swift` view modifier exists at root applying Theme A tokens (UX-DR2); `WindowGroup { ... }.preferredColorScheme(.dark)` is set; no accent color set
**And** `MonochromeGlassPanel.swift` exists in `UI/Components/` rendering `.thickMaterial`/`.regularMaterial`/`.ultraThinMaterial` variants (UX-DR5)
**And** a hello-world view launches on a physical iPhone (TestFlight Ad Hoc cert) showing the glass panel

### Story 1.3: Oracle VM + LiveKit Docker Compose Stack + Domain

As a solo developer,
I want an Oracle Always-Free Ampere A1 VM running the LiveKit OSS Docker Compose stack with auto-TLS for `sfu.xaeryx.com`,
So that the app has a self-hosted SFU at $0/month operating cost ready for media + signaling.

**Acceptance Criteria:**

**Given** I have an Oracle Cloud account,
**When** I provision the VM and deploy the Docker Compose stack,
**Then** an Oracle Ampere A1 VM (4 OCPU, 24 GB RAM) runs Ubuntu 24.04 LTS ARM with Docker
**And** `/infra/docker-compose.yml` defines services `livekit-server` + `redis` + `caddy` (no egress, no recording)
**And** `/infra/livekit.yaml` sets `empty_timeout: 300` and standard TURN/STUN config
**And** `/infra/Caddyfile` configures auto Let's Encrypt for `sfu.xaeryx.com`
**And** the domain `xaeryx.com` is registered via Cloudflare Registrar with WHOIS privacy + account 2FA
**And** the `sfu.xaeryx.com` subdomain points to the VM IP via DNS-only mode (no Cloudflare proxy — UDP 7881 + 50000-60000 must not pass through proxy)
**And** `https://sfu.xaeryx.com/` returns the LiveKit landing page with a valid Let's Encrypt cert
**And** `/infra/scripts/provision-oracle-vm.sh` and `/infra/scripts/deploy.sh` exist and are idempotent

### Story 1.4: Firebase Init + Firestore Rules Baseline + App Check Providers

As a solo developer,
I want Firebase Auth (anonymous), Firestore, and App Check configured for both Android and iOS,
So that the app can sign users in anonymously, store metadata-only docs, and gate backend calls behind device attestation.

**Acceptance Criteria:**

**Given** no Firebase project exists,
**When** I run `firebase init` in `/firebase/` and configure providers,
**Then** a Firebase project is created and configured in `/firebase/firebase.json` (Auth + Firestore + App Check only — no Functions)
**And** `/firebase/firestore.rules` enforces: `/users/{uid}` writable only by owner; `/pairs/{pairId}` readable only by `memberA` or `memberB`; `/codes/{code}` readable by anyone authenticated (lookup-only), writable only by owner; `/calls/{callId}/ephemeralPub/{uid}` readable only by participants
**And** App Check is configured with DeviceCheck provider on iOS and Play Integrity on Android; setup notes captured in `/firebase/appcheck/{android,ios}-providers.md`
**And** Android `google-services.json` and iOS `GoogleService-Info.plist` are added to respective projects (gitignored where containing secrets)
**And** a smoke test on both platforms successfully calls `signInAnonymously()` and writes a doc to `/users/{uid}` under the rules

### Story 1.5: SafeLog Facade + Lint Enforcement + ULID Library Wiring

As a solo developer,
I want a `SafeLog` facade on both platforms with `AllowedLogKey` enum, lint rules banning direct logging APIs, and a pinned ULID library,
So that conversation content can never accidentally leak to logs and all canonical entity IDs are time-sortable and cross-platform consistent.

**Acceptance Criteria:**

**Given** the Android and iOS scaffolds exist,
**When** I add the logging + ID primitives,
**Then** `SafeLog.kt` exists in `android/.../logging/` with `event(key: AllowedLogKey, value: Any)` API and `AllowedLogKey` enum containing all keys from Architecture Patterns §14
**And** `SafeLog.swift` exists in `ios/.../Logging/` with identical surface (same key names, same semantics)
**And** a custom detekt rule bans `android.util.Log.*` and `timber.log.Timber.*` outside `logging/SafeLog.kt`
**And** a custom SwiftLint rule bans `print`, `os_log`, `Logger` outside `Logging/SafeLog.swift`
**And** both lint rules block CI on violation (verified by introducing a violation and seeing CI fail, then reverting)
**And** an Android ULID library (e.g., `com.aallam.ulid:ulid-kotlin`) is pinned in `libs.versions.toml` and wrapped in `ids/UlidGenerator.kt`
**And** an iOS ULID library (e.g., `ulid-swift`) is added via SPM and wrapped in `IDs/UlidGenerator.swift`
**And** both wrappers produce 26-char Crockford base32 ULIDs verified against a shared test vector
**And** the chosen libraries are documented in `/shared/canonical-names.md` under "ULID library pinning" (Gap I.12)

### Story 1.6: CI/CD Per Stack

As a solo developer,
I want three GitHub Actions workflows — one per stack — that run lints, tests, and produce build artifacts,
So that every PR catches regressions before merge.

**Acceptance Criteria:**

**Given** the scaffolds + lint primitives exist,
**When** a PR opens against `main`,
**Then** `.github/workflows/android-ci.yml` (triggers on `android/**` or `shared/**`) runs detekt → unit tests → Compose UI tests → Roborazzi screenshot diff → assembles a release APK artifact
**And** `.github/workflows/ios-ci.yml` (triggers on `ios/**` or `shared/**`) runs SwiftLint → `xcodebuild test` → snapshot tests → archives a TestFlight Ad Hoc build on tag
**And** `.github/workflows/infra-ci.yml` (triggers on `infra/**`) runs yamllint against `livekit.yaml` + `Caddyfile`, validates `docker compose config`, and on tag SSHes into the Oracle VM and runs `deploy.sh`
**And** all three workflows fit within the GitHub Actions free tier minute budget at solo-dev volume
**And** a deliberately-broken commit on each stack confirms CI blocks merge

### Story 1.7: Shared Specs Directory + Cross-Platform Fixtures — ✅ COMPLETED 2026-05-22

As a solo developer,
I want a `/shared/` directory holding the cross-platform contracts (canonical names, error codes, Data Channel schema, auth-proxy API, state derivation rules) plus stubs for particle-rules fixtures and the regression corpus,
So that Android and iOS implementations stay in sync via a single source of truth and the regression-corpus + golden-file harness has a place to live.

**Acceptance Criteria:**

**Given** the canonical specs exist in Architecture but not yet as standalone files,
**When** I extract them into `/shared/`,
**Then** `/shared/canonical-names.md` mirrors Architecture Patterns §1 (term-forms table)
**And** `/shared/error-codes.md` mirrors Architecture Patterns §10 (error-code registry)
**And** `/shared/data-channel-schema-v1.json` is a valid JSON Schema describing the snake_case wire payload from Architecture Patterns §5 (including `schema_version`, `seq`, all required fields)
**And** `/shared/auth-proxy-api.md` documents `POST /token` request/response shape, error codes, token TTL (Gap I.11)
**And** `/shared/state-derivation.md` documents how RoomState transitions derive client-side from `room.remoteParticipants` (Gap I.13)
**And** `/shared/particle-rules-fixtures/` directory exists with one `<rule>/case_001/{source.txt, expected_processed.txt, expected_target.txt, metadata.json}` example fixture
**And** `/shared/regression-corpus/` directory exists with a README describing the planned ~200 tagged utterances (Gap I.14)
**And** both platforms' test infra references fixtures from `/shared/` (verified by a smoke test on each platform — *smoke test deferred to Stories 1.1 + 1.2 once Android Studio / Xcode projects exist; the fixture files themselves are in place*)

**Status (2026-05-22):** ✅ All shared spec files created under `/shared/`:
- `canonical-names.md` (mirrors Architecture §1 + §3 + §4 — naming conventions + ID format + glossary)
- `error-codes.md` (mirrors Architecture §10 — 14 codes, severity, taxonomy mapping)
- `data-channel-schema-v1.json` (valid JSON Schema for the snake_case wire payload per Architecture §5 — includes `schema_version`, `seq`, all required + optional fields with descriptions + constraints)
- `auth-proxy-api.md` (canonical `POST /v1/token` contract — request shape, response shape, error responses with codes, idempotency rules, logging contract, health-check endpoint)
- `state-derivation.md` (canonical `RoomState` derivation rules from LiveKit observable state + ADR-A6 leave-and-rejoin asymmetry, UI surface mapping, side-effects per transition, anti-patterns)
- `particle-rules-fixtures/` directory with starter case `particles/loh/case_001/{source.txt, expected_processed.txt, expected_target.txt, metadata.json}` validating the fixture-harness format; full population deferred to Story 3.2
- `regression-corpus/README.md` describing the planned ~200-utterance JSONL corpus + tag conventions + bake-off harness output schema; full population deferred to Story 3.7

Smoke-test assertion on each platform that fixtures load and parse correctly is deferred to the platform scaffolding stories (1.1 Android, 1.2 iOS); the format and contract are in place and validated against Architecture §1/§3/§4/§5/§10.

### Story 1.8: Anonymous Sign-In on First Launch

As a new user (Bania or his girlfriend),
I want the app to sign me in silently on first launch with no login UI,
So that I can use the app without creating an account or remembering a password.

**Acceptance Criteria:**

**Given** the app is installed and never previously launched,
**When** I open the app for the first time,
**Then** Firebase Auth `signInAnonymously()` is called before any UI is rendered
**And** a stable anonymous user UID is established within 3 seconds on a typical 4G connection (FR-1)
**And** no login, signup, or account UI is ever shown
**And** the UID persists across app kills/restarts on the same device (verified by killing the app and confirming `FirebaseAuth.currentUser.uid` is unchanged)
**And** an X25519 identity keypair is generated on first launch (deferred to Story 1.12 for the publish step — this story handles only sign-in)
**And** `SafeLog.event(AllowedLogKey.callId, ...)` is NOT used here (no Call yet); only privacy-safe sign-in events are logged

### Story 1.9: Display Own Pairing Code

As Bania (or his girlfriend) on first launch when not yet paired,
I want to see my own 6-digit Pairing Code prominently on the Paired-Empty home screen,
So that I can share it with my partner via WhatsApp text.

**Acceptance Criteria:**

**Given** I am signed in anonymously and not paired,
**When** the Paired-Empty home screen renders,
**Then** a 6-digit decimal Pairing Code is generated client-side and persisted at `/codes/{6digit}` in Firestore with `{ownerUid, createdAt, expiresAt}`
**And** the code is collision-checked at generation time — if a collision occurs, one digit is regenerated and re-checked (FR-2)
**And** the code is displayed via the `PairingCodeDisplay` component (UX-DR14): 6 digits in 48pt/44sp Display typography with ~10–12pt tracking, below "Share this with your partner" hint at Footnote size
**And** tapping the code copies it to the clipboard and shows a 2-second "Code copied" snackbar
**And** long-pressing the code shows a "Regenerate code" option that creates a new code and invalidates the prior one in Firestore
**And** the code remains valid until used in a successful Pairing or until the user explicitly regenerates it (FR-2)
**And** the code's display position is BELOW the partner-input field per UX-DR15 (partner-input-first home layout)

### Story 1.10: Enter Partner's Pairing Code to Pair

As Bania (or his girlfriend),
I want to enter my partner's 6-digit Pairing Code on the Paired-Empty home and have my app transition to the Paired home,
So that I can establish the Paired Users relationship and start having translated Calls.

**Acceptance Criteria:**

**Given** I am on the Paired-Empty home and my partner has shared their 6-digit code,
**When** I focus the `PairingCodeInput` field and tap "Pair",
**Then** the field renders as a single 6-character input with `text-primary` opacity digits, placeholder `— — — — — —`, and large hit targets (≥48dp/44pt) (UX-DR13)
**And** the device's native numeric keypad opens on focus
**And** the "Pair" button is disabled until exactly 6 digits are entered
**And** submitting performs a Firestore lookup at `/codes/{6digit}` to resolve the owner UID
**And** on invalid code (not found, expired, owned-by-self), an inline error renders below the input within 2 seconds (FR-3): "Code not found" / "Code expired" / "That's your own code"
**And** on valid code, a `/pairs/{pairId}` document is created with `{memberA, memberB, createdAt}` and both users' `/users/{uid}/pairId` are updated
**And** my app transitions to the Paired home (with the Call button) within 5 seconds of successful pairing (FR-3)
**And** the inline error never invalidates my own code — my code remains shareable

### Story 1.11: Paired State Persists Across App Restarts

As Bania (or his girlfriend) once paired,
I want my app to skip the pairing UI entirely on subsequent launches and go straight to the Paired home,
So that pairing is a one-time event and the app feels like WhatsApp (just-there).

**Acceptance Criteria:**

**Given** I have successfully paired with my partner,
**When** I kill the app and reopen it,
**Then** the Paired home screen renders (not the Paired-Empty screen) (FR-4)
**And** my partner's display name (defaulting to "Partner" if never set per FR-23) is shown without requiring a network call
**And** my partner's UID is recoverable from local storage (Room/SwiftData mirror) without network access
**And** a real-time Firestore listener on `/users/{my-uid}/pairId` updates the local mirror if changed remotely
**And** the partner's display name is loaded from `/users/{partner-uid}/displayName` on first online connection and cached locally
**And** if Firestore is briefly unreachable on launch, the app still renders the Paired home from local state (offline-degraded)

### Story 1.12: X25519 Identity Keypair — Generate + Publish

As Bania (or his girlfriend) on first launch,
I want my device to generate and publish a long-term X25519 identity public key,
So that future E2EE per-Call key exchanges (Epic 5) can sign ephemeral public keys against my identity, preventing MITM.

**Acceptance Criteria:**

**Given** I am signing in anonymously for the first time on a device (Story 1.8),
**When** the first-launch flow runs,
**Then** an X25519 long-term identity keypair is generated client-side using a vetted crypto library
**And** the private key is stored in Keychain (iOS, `kSecAttrAccessibleAfterFirstUnlock`) or EncryptedSharedPreferences (Android, via `security-crypto`) — never written to plaintext storage and never sent over the network (ADR-A2)
**And** the public key is published to `/users/{uid}/identityPub` as Firestore `bytes` type
**And** the keypair survives app restarts on the same device (private key recoverable from secure storage)
**And** a re-installed app on the same device generates a NEW keypair (no key recovery across reinstalls — accepted v1 limitation)
**And** unit tests verify: (a) generation succeeds, (b) private key is never serialized to logs (via SafeLog audit), (c) public-key bytes are 32 bytes Curve25519-compressed

### Story 1.13: Settings Sheet Shell with Unpair

As Bania (or his girlfriend) once paired,
I want a Settings entry from any non-In-Call screen that opens a sheet with an Unpair option using two-tap confirmation,
So that I can dissolve the pairing if needed (rare; v1 has only one partner).

**Acceptance Criteria:**

**Given** I am on the Paired home (or Paired-Empty home),
**When** I tap the top-right Settings gear icon,
**Then** a Settings sheet slides up using native `.sheet()` (iOS) or `ModalBottomSheet` (Android) (UX-DR35)
**And** the sheet contains an "Unpair from [Partner display name]" row plus a placeholder section for future Settings items (filled in Epic 8)
**And** tapping Unpair opens a confirm sheet: "Unpair? You can re-pair later."
**And** confirming opens a second confirm: "This will end the pairing." (two-tap confirmation per FR-5)
**And** confirming the second sheet deletes `/pairs/{pairId}`, clears local pairing state (Room/SwiftData), and navigates to the Paired-Empty home
**And** the partner's Firestore listener fires and silently updates their local pairing state (no notification)
**And** on the partner's next Call attempt, their app sees the missing pair record and displays "Partner unpaired" (deferred edge-case rendering to Epic 2 — this story records the backend state correctly so Epic 2 can render it)
**And** if the partner is in an active Call when Unpair fires, the active LiveKit room continues until normal end (Unpair takes effect when she leaves — verified by integration test stub against LiveKit room)

### Story 1.14a: PRD Reconciliation — ✅ COMPLETED 2026-05-22 (executed as Epic-1 prerequisite)

As a solo developer following the BMAD workflow,
I want `prd.md` updated with the SCOPE EXPANSION changes (privacy posture, video, E2EE, relaxed targets, new FRs FR-26 → FR-32) per Architecture §G "Reconciliation Deliverables,"
So that all downstream stories in Epics 2–8 reference canonical FR numbers and NFR targets that actually exist in the upstream PRD.

**Sequencing note (Implementation Readiness 2026-05-22):** This story was executed as the FIRST Epic-1 deliverable — *before* any scaffolding (Stories 1.1 → 1.13) — so downstream stories in Epics 2 / 5 / 6 / 7 / 8 have a coherent PRD to reference from the moment they are picked up. Original sequencing placed Story 1.14 as the LAST Epic-1 story, which would have left ~25 downstream stories referencing FR identifiers (FR-26 → FR-32) and NFR targets that did not exist in the upstream document.

**Acceptance Criteria (all met):**

**Given** the SCOPE EXPANSION decisions recorded in `architecture.md` §G "Reconciliation Deliverables — CA's output to upstream artifacts" (lines 641-674),
**When** the reconciliation is applied,
**Then** ✅ `prd.md` frontmatter bumped to `revision: 3` with `reconciled-with: architecture.md`
**And** ✅ §4.2 extended with FR-26 (Audio vs Video selection), FR-28 (audio routing toggle), FR-30 (lazy camera permission)
**And** ✅ §4.3 description rewritten to on-device-first translation pipeline; FR-14 marked SUPERSEDED by FR-31 (cloud Gemini path retained as Plan B fallback only)
**And** ✅ FR-31 (on-device translation: NLLB-200 / MADLAD / Gemma 2B Week-1 bake-off; `RuleBasedTranslationProvider` decorator) added
**And** ✅ §4.5 FR-22 NOTE-FOR-PM extended to reference Story 3.9 Plan-A/B outcome
**And** ✅ §4.6 NEW (Video Pipeline) with FR-27; §4.7 NEW (E2EE) with FR-29; §4.8 NEW (Resilience) with FR-32
**And** ✅ §6.1 Privacy fully rewritten — WhatsApp-equivalent E2EE; Gemini AI Studio caveat demoted to Plan-B-only note
**And** ✅ §6.4 Provider Abstraction closing note added (cross-ref to Architecture Pattern §9)
**And** ✅ §6.5 Warmup ping pattern REMOVED; replaced with always-on Oracle VM backend
**And** ✅ §8 Platform — Cloud Run replaced with Oracle Ampere A1 VM + Caddy + Node.js auth-proxy + Docker Compose; xaeryx.com domain added
**And** ✅ §9 — Video and "true E2EE between clients" removed from v2-deferred list
**And** ✅ §10.1 — 4 new in-scope bullets (Audio/Video selector, E2EE media+Data Channel, leave-and-rejoin, on-device translation)
**And** ✅ §10.2 — Video and True E2EE bullets removed
**And** ✅ §10.3 — Build timeline revised to 7–10 weeks + 1 week ramp
**And** ✅ §11 SM-2 (≥60% / reverts to ≥80% under Plan B), SM-4 (median <8s, p95 <12s), SM-5 (best-effort) relaxed targets documented
**And** ✅ §12 Open Questions 2, 4, 5, 7 updated/marked resolved
**And** ✅ §13 Assumptions Index appended with 10 reconciliation-resolution entries
**And** ✅ No FR numbers in Epics 2–8 reference an FR that doesn't exist in the updated PRD (re-verified after reconciliation)

**Result:** prd.md grew from 543 → 640 lines.

### Story 1.14b: UX Spec Reconciliation — ✅ COMPLETED 2026-05-22 (executed as Epic-1 prerequisite)

As a solo developer following the BMAD workflow,
I want `ux-design-specification.md` updated with the SCOPE EXPANSION changes (Theme B removal, Video 50/50 layout, 10 new component specifications, Caption Loop failure-state extensions, `CallControlRow → AudioCallControlRow` rename) per Architecture ADR-E1 through ADR-E5 + §G,
So that all downstream stories in Epics 2 / 5 / 6 / 7 / 8 have canonical UX component specifications to implement against.

**Sequencing note:** Executed in parallel with Story 1.14a as the FIRST Epic-1 deliverable.

**Acceptance Criteria (all met):**

**Given** the SCOPE EXPANSION decisions recorded in `architecture.md` ADR-E1 (In-Call layouts), ADR-E2 (Theme C × Video), ADR-E3 (camera permission), ADR-E4 (`VideoCallControlRow`), ADR-E5 (10 net-new components), §G (UX-spec update list), and Patterns §7 (failure-state taxonomy),
**When** the reconciliation is applied,
**Then** ✅ Frontmatter updated with `reconciled-with: architecture.md`, `reconciled-date: 2026-05-22`, `reconciliation-notes:` summary
**And** ✅ §"Color System — Three Themes" renamed to "Two Themes"; entire Theme B subsection deleted
**And** ✅ 16 Theme-B reference sites updated throughout the file (headings, accessibility table, parity contract, testing matrix, design directions, Phase-5 risk note, mockup deliverable)
**And** ✅ §"Spacing & Layout > In-Call screen vertical split" replaced with Audio 40/60 + Video 50/50 table + explicit-reject note
**And** ✅ NEW subsection "Theme C × Video Interaction" added per ADR-E2
**And** ✅ `CallControlRow` renamed to `AudioCallControlRow` throughout (component spec heading + all inline references in `JumpToLatestPill`, `AudioLevelIndicator`, journey flows, Customization Strategy table)
**And** ✅ `ThemePicker` component spec rewritten to 2-option (Dark + Image)
**And** ✅ 10 new component specs added in §"Custom Components" with full Purpose/Anatomy/States/Variants/Accessibility/Interaction blocks per template:
  - `CallTypeSelector` (~1.1 KB) — Epic 6 Story 6.1
  - `AudioRoutingToggle` (~1.2 KB) — Epic 2 Story 2.9
  - `VideoCallControlRow` (~1.3 KB) — Epic 6 Story 6.7
  - `CameraPermissionFlow` (~1.5 KB) — Epic 6 Story 6.2
  - `VideoTile` (~1.1 KB) — Epic 6 Story 6.4
  - `VideoPausedTile` (~1.3 KB) — Epic 6 Story 6.5 (neutral grey, NOT amber, per ADR-F3 disambiguation from translation failure)
  - `VideoMutedTile` (~1.1 KB) — Epic 6 Story 6.6
  - `E2EEKeyExchangeIndicator` (~1.3 KB) — Epic 5 Story 5.4
  - `RejoinNotification` (~1.2 KB) — Epic 7 Story 7.4
  - `CallWaitingForPartnerState` (~1.5 KB) — Epic 7 Story 7.3
**And** ✅ §"Defining Experience > Caption Loop" extended with "Additional failure-state beats (per Architecture §7)" sub-section listing all 8 priority-ordered states + one-banner-at-a-time rule
**And** ✅ `ux-design-directions.html` mockup flagged as stale (still renders 3 themes; regenerate or delete pending separate deliverable)
**And** ✅ Phase-2 Implementation Roadmap row updated to include `AudioRoutingToggle` + `CallTypeSelector`
**And** ✅ Components-NOT-in-v1 list updated (Video tile + E2EE indicator removed from deferred list since they're now v1)

**Result:** ux-design-specification.md grew from 1565 → 1743 lines (+178 lines, +22 KB).

### Story 1.14c: Solo-Dev Scope-Cuts Runbook — ✅ COMPLETED 2026-05-22

As a solo developer wanting a documented "morale-collapse trigger" reference,
I want `/docs/runbooks/solo-dev-scope-cuts.md` documenting cut-trigger criteria (week-5 trigger: defer `QualityReviewRow` to v1.1; week-7 trigger: defer Theme C image background) and single-failure-domain risk acceptance (auth-proxy + LiveKit + Redis all on one Oracle A1 VM),
So that scope-cut decisions during the build are pre-thought rather than reactive (Gap I.17 + I.19).

**Acceptance Criteria:**

**Given** the scope-cut and single-failure-domain risks are recorded in the Architecture document,
**When** I author the runbook,
**Then** `/docs/runbooks/solo-dev-scope-cuts.md` exists with: week-by-week trigger criteria, pre-decided cuts at each trigger, and single-failure-domain accepted-risk documentation
**And** the runbook is referenced from this story closure and from Story 3.9 (Plan A/B gate) for context

**Status (2026-05-22):** ✅ `/docs/runbooks/solo-dev-scope-cuts.md` exists with: 5 week-by-week trigger criteria (Week 1 / 3 / 5 / 7 / 9) each with pre-decided default cut + alternative cut + rationale; single-failure-domain accepted-risk documentation (Oracle Ampere A1 VM hosts livekit-server + redis + caddy + auth-proxy — accepted for personal-use scale, mitigation via idempotent deploy scripts); architectural lock-ins explicitly named (cannot cut: NFR-Privacy, NFR-Provider-Abstraction, NFR-Reliability silent-drop ban, SafeLog enforcement); append-only "Actual cuts taken" log for retrospective.

---

## Epic 2: Audio Calling

Paired users place, receive, and end native-feeling audio Calls; lock-screen ring works; audio quality matches WhatsApp; audio routes between earpiece, speaker, and Bluetooth. Captions not yet wired.

### Story 2.1: Auth-Proxy on Oracle VM — App Check Verification + LiveKit JWT Mint

As a solo developer,
I want a Node.js auth-proxy co-hosted on the Oracle VM that verifies Firebase App Check tokens and mints short-lived LiveKit JWTs,
So that the client can authenticate to LiveKit without exposing the LiveKit API secret and only attested devices can mint tokens.

**Acceptance Criteria:**

**Given** the Oracle VM Docker Compose stack from Story 1.3 is running and `/shared/auth-proxy-api.md` defines the contract,
**When** I add the auth-proxy service,
**Then** `/infra/auth-proxy/` contains an Express + TypeScript app with `Dockerfile` integrated into the existing `docker-compose.yml`
**And** `POST /token` accepts `{ firebaseIdToken, appCheckToken, callType: "audio" | "video", peerUid }` and returns `{ livekitJwt, roomName, expiresAt }`
**And** every request is verified against Firebase App Check via Firebase Admin SDK `getAppCheck().verifyToken(token)` using cached JWKS from `https://firebaseappcheck.googleapis.com/v1/jwks`; invalid → 401
**And** the LiveKit JWT is minted with `livekit-server-sdk` and includes a `callType` claim in metadata + a room name derived from a deterministic hash of `(sorted uids, callId)`
**And** the JWT TTL is ≤ 60 seconds (short-lived; Architecture pattern)
**And** the proxy is reachable at `https://auth.xaeryx.com/token` (or path under sfu.xaeryx.com per ADR-C1 decision) with valid TLS
**And** a smoke test from both Android and iOS client successfully obtains a JWT and connects to the LiveKit room
**And** App Check failure path verified by sending a fabricated token → 401 returned

### Story 2.2: Paired Home with Call Button + CallSession Scaffolding

As Bania (or his girlfriend) once paired,
I want a Paired home screen with a prominent Call button and the orchestration layer that will own the LiveKit room lifecycle,
So that I have a one-tap entry into a Call and the codebase has the architectural seam (`CallSession`) for future captions/E2EE/video to plug into.

**Acceptance Criteria:**

**Given** I am paired and on the Paired home,
**When** the screen renders,
**Then** the partner display name (default "Partner") is centered at top
**And** a single "Call" primary action button (filled glass pill, ≥48dp/44pt, full `text-primary` opacity per UX-DR38) is centered below the partner name
**And** in this epic the button initiates an Audio Call (the two-button `CallTypeSelector` evolves in Epic 6 per UX-DR17)
**And** `CallSession` exists on both platforms in `call/callSession/` (Android) / `Call/CallSession/` (iOS) exposing `startCall(callType: CallType): Flow<RoomState>` / `AsyncStream<RoomState>` (Architecture Patterns §9)
**And** `RoomState` enum with values `active`, `waitingForPartner`, `ended` is wired (waitingForPartner unused until Epic 7)
**And** `LiveKitRoomManager` is owned by `CallSession`; UI never calls LiveKit APIs directly (Architecture Patterns §13)
**And** tapping Call invokes `CallSession.startCall(.audio)` — full place-call mechanics covered in Story 2.3

### Story 2.3: Place an Audio Call

As Bania (or his girlfriend),
I want to tap Call on the Paired home and have my partner's phone ring within 3 seconds,
So that I can initiate a conversation immediately.

**Acceptance Criteria:**

**Given** I am on the Paired home and have mic permission granted (request flow inline below),
**When** I tap the Call button,
**Then** if mic permission is not yet granted, the system mic-permission prompt appears (`RECORD_AUDIO` / `NSMicrophoneUsageDescription`) — denial shows inline error "Mic permission required to start a Call" with Settings deep-link, no further call attempt
**And** on grant, `CallSession.startCall(.audio)` fires: client requests a LiveKit JWT from the auth-proxy (Story 2.1) including `callType: "audio"` and peer UID
**And** the client connects to the LiveKit room and publishes the local audio track before the ringing notification fires (FR-6)
**And** my UI shows a "Calling…" state with the partner name + `AudioLevelIndicator` showing my own mic level (calm, anti-evaluative per UX-DR23)
**And** tap-to-ringing latency is <3 seconds on typical 4G (FR-6 acceptance)
**And** if the partner does not answer within 30 seconds, the Call times out with "Call missed" and returns to Paired home within 3 seconds
**And** if the LiveKit connect fails, `ERR_LIVEKIT_ROOM_FAILED` is logged via SafeLog (with Call ID + language codes, no conversation content) and the UI shows a `networkDropped` indicator (Architecture Patterns §7)
**And** the In-Call screen renders the Audio 40/60 layout (UX-DR16) from Story 2.7

### Story 2.4: iOS PushKit + CallKit Incoming Call

As his girlfriend on iPhone with the app installed and paired,
I want my phone to ring with the native iOS lock-screen call UI when Bania calls,
So that I can accept the Call without unlocking my phone — just like any other VoIP call.

**Acceptance Criteria:**

**Given** I am paired with Bania, the app is registered with PushKit, and my device is online,
**When** Bania places an Audio Call (Story 2.3),
**Then** an APNs VoIP push (priority 10) is delivered to my device within 2 seconds of his initiation (FR-7 acceptance)
**And** my `PushKitHandler` reports the incoming Call to CallKit via `CXProvider.reportNewIncomingCall(with:update:)` **synchronously before completing the push handler** (Apple iOS 13+ requirement — failure to do so terminates the app + revokes the token)
**And** the native CallKit ring-on-lock-screen displays "TranslatorRep — Bania calling" using Bania's display name from `/users/{Bania-uid}/displayName`
**And** the call UI shows "Audio Call" as the call type (derived from the `callType` claim in the push payload)
**And** I can accept the Call from the lock screen without unlocking the device
**And** if PushKit delivery fails (rare), the caller's "Calling…" times out at 30 seconds; my device shows no notification (silent failure on my side — acknowledged edge case)
**And** Info.plist `UIBackgroundModes: voip` and `com.apple.developer.voip` entitlement from Story 1.2 are exercised end-to-end

### Story 2.5: Android FCM + ConnectionService Incoming Call

As Bania on his Galaxy with the app installed and paired,
I want my phone to ring with the native Android telecom UI when his girlfriend calls,
So that I can accept the Call from the lock screen with the native incoming-call surface.

**Acceptance Criteria:**

**Given** I am paired with my girlfriend, the app's `PhoneAccount` is registered with `CAPABILITY_SELF_MANAGED`, and my device is online,
**When** she places an Audio Call,
**Then** an FCM high-priority data message is delivered to `IncomingCallMessagingService.onMessageReceived` within 2 seconds of her initiation (FR-7 acceptance)
**And** the handler invokes `telecomManager.addNewIncomingCall(phoneAccountHandle, extras)` with the caller display name + `callType: "audio"` in extras
**And** `TranslatorRepConnectionService` extends `android.telecom.ConnectionService` and starts a foreground service of type `phoneCall` (Android 14+ requirement)
**And** the native incoming-call UI displays "TranslatorRep — [her display name]" with audio-call iconography
**And** I can answer from the lock screen without unlocking the device
**And** AndroidManifest permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_PHONE_CALL`, `MANAGE_OWN_CALLS`, `POST_NOTIFICATIONS` from Story 1.1 are exercised end-to-end
**And** if FCM high-priority is throttled (rare; OEM doze edge cases), the caller's "Calling…" times out at 30 seconds; my device shows no notification

### Story 2.6: Accept or Reject Incoming Call

As Bania or his girlfriend receiving an incoming Call,
I want to accept or reject from the native call UI and have both apps transition to the In-Call screen (on accept) or back to Paired home (on reject),
So that the flow feels like any other phone call.

**Acceptance Criteria:**

**Given** I have an incoming Call ringing on my device,
**When** I tap Accept on the native UI,
**Then** the platform delivers an "answer" callback (`CXAnswerCallAction` iOS / `Connection.onAnswer()` Android)
**And** my `CallSession` accepts the Call: it requests a LiveKit JWT from the auth-proxy (Story 2.1), connects to the same room, and publishes my local audio track
**And** both apps transition to the In-Call screen within 2 seconds of accept (FR-8 acceptance)
**And** when I tap Reject instead, the platform delivers a "decline" callback; my `CallSession` sends an explicit reject signal (via Firestore write to `/calls/{callId}.declinedAt` or LiveKit data message — implementer's choice)
**And** the caller's UI shows "Call declined" and returns to Paired home within 3 seconds (FR-8)
**And** not responding within 30 seconds counts as missed; both sides return to Paired home (covered by Story 2.3 timeout)
**And** the speaker / Bluetooth / earpiece routing is determined by Story 2.9 — this story focuses on the accept/reject control flow only

### Story 2.7: In-Call Screen — Audio 40/60 Layout

As Bania or his girlfriend on an active audio Call,
I want a calm In-Call screen with the partner's name + audio level + mute + end-Call in the upper 40% and a (currently empty) caption stack area in the lower 60%,
So that the layout is set up for Epics 3–4 captions to drop in without restructuring.

**Acceptance Criteria:**

**Given** I am in an active Call (Story 2.6 accept fired),
**When** the In-Call screen renders,
**Then** the upper 40% contains, centered: partner display name (Title typography), `mic-active dot` (state-red, pulsing during active audio capture), `AudioLevelIndicator` (5 monochrome bars + mic-pulse dot — anti-evaluative per UX-DR23), `AudioCallControlRow` at the bottom edge with mute + audio-routing toggle + end-Call (UX-DR18)
**And** the lower 60% renders an empty space reserved for the `CaptionStack` component (introduced in Epic 3)
**And** the layout follows UX-DR16 (Audio 40/60 vertical split)
**And** back-gesture navigation is suppressed (you don't accidentally exit a Call by swiping back) per UX §"Navigation Patterns"
**And** the screen ignores OS theme (Theme A Dark only in this epic; Theme C added in Epic 8)
**And** no captions, no "AI listening" copy, no spinner — the upper region is calm per UX §"Effortless Interactions"

### Story 2.8: End an Active Call

As Bania or his girlfriend in an active Call,
I want to tap the end-Call button to disconnect immediately (or with a quick two-tap confirm if the Call is >5 min),
So that ending a short Call feels as fluid as WhatsApp and longer Calls have a one-step safety net.

**Acceptance Criteria:**

**Given** I am in an active Call,
**When** I tap the end-Call button in `AudioCallControlRow`,
**Then** if the Call has been active for **≤5 minutes**, the Call ends immediately (FR-9 / UX-DR38 destructive action)
**And** if the Call has been active for **>5 minutes**, a confirm sheet appears: "End this call?" with Cancel + End (two-tap confirm)
**And** on confirm (or immediate-end), `CallSession.endCall()` fires: LiveKit room disconnects, audio capture stops, the Translation Pipeline (Epic 3) is signaled to stop (no-op in this epic)
**And** both apps return to the Paired home screen within 2 seconds (FR-9 acceptance)
**And** the partner's `LiveKitRoomManager` observes `participantDisconnected` and ends the Call on their side as well
**And** the room is destroyed when both participants have left (note: leave-and-rejoin behavior, including the 5-min `empty_timeout` from Story 1.3, is implemented in Epic 7 — in this epic, ending always ends for both)

### Story 2.9: Audio Routing Toggle — Earpiece / Speaker / Bluetooth

As Bania or his girlfriend in a Call,
I want a single toggle in the control row that switches audio between earpiece, speaker, and connected Bluetooth/wired devices,
So that I can adapt to context (private listening vs hands-free vs headphones).

**Acceptance Criteria:**

**Given** I am in an active Call (Audio or, eventually, Video — covered for both per FR-28),
**When** the `AudioCallControlRow` renders,
**Then** an `AudioRoutingToggle` (UX-DR20) is visible with three logical states: Earpiece / Speaker / Bluetooth-when-connected
**And** on iOS, switching state calls `AVAudioSession.overrideOutputAudioPort()` with the appropriate port
**And** on Android, switching state calls `AudioManager.setSpeakerphoneOn()` + `setMode(MODE_IN_COMMUNICATION)`, and `setBluetoothScoOn(true)` for the BT route
**And** when a Bluetooth or wired device is connected at Call start, the route auto-selects to that device; the toggle reflects current state
**And** disconnecting the BT/wired device mid-Call falls back to earpiece (default) without dropping the Call
**And** the toggle is reachable from the natural thumb arc in portrait orientation (lower 40% per UX accessibility)
**And** state changes apply within 500 ms of tap (perceived as instant)

### Story 2.10: Audio Quality Acceptance Test

As Bania (product owner) before declaring v1 ready to ship the Calling slice,
I want a documented paired listening test that verifies real-world audio quality meets the FR-10 acceptance bar,
So that we have evidence (not just spec) that audio quality matches WhatsApp before layering captions on top.

**Acceptance Criteria:**

**Given** Stories 2.1–2.9 are complete (Calls work end-to-end between Bania's Galaxy and his girlfriend's iPhone),
**When** I run the paired listening test,
**Then** the test protocol document `/docs/runbooks/audio-quality-acceptance.md` exists with the 3-Call protocol (≥10 min each — both on speakers, both on headsets, one of each)
**And** Opus codec is verified active at ≥24 kbps sustained per direction (LiveKit metrics or packet inspection)
**And** AEC/AGC/NS verified enabled via LiveKit defaults (no audible echo when both parties on speakers OR on headsets)
**And** audio remains intelligible at simulated 4G conditions with 200 ms RTT and 1% packet loss (Network Link Conditioner on iOS / Charles Proxy on Android dev setup)
**And** subjective rating from Bania + his girlfriend on a 5-point scale (1=unusable, 5=clear): all 3 Calls rated ≥4 ("as good as WhatsApp"); no Call falling below 3 ("intelligible but degraded")
**And** test results are recorded in the runbook with timestamps + ratings + any observed issues; failures trigger a bugfix story before Epic 3 starts

### Story 2.11: Failure-State Taxonomy Seeds — Network Drop + Call Lifecycle

As a solo developer setting up the cross-cutting failure-handling baseline,
I want the failure-state taxonomy + error-code registry primitives wired before captions ship (Epic 4 wires the rest),
So that Call-level failures (network drop, fatal LiveKit failure, mid-Call disconnect) have first-class UI states from day one and Epic 4 can extend the same taxonomy.

**Acceptance Criteria:**

**Given** `/shared/error-codes.md` exists (Story 1.7),
**When** I implement the Call-level failure UI states,
**Then** the `networkDropped` state renders as an offline indicator + soft retry banner in the In-Call upper region (state-amber color, calm copy: "Connection lost — reconnecting…")
**And** the `ERR_LIVEKIT_ROOM_FAILED` error (fatal, call ends) shows: native call-end via CallKit/ConnectionService + a return to Paired home with inline error "Call ended unexpectedly"
**And** the `ERR_PAIRING_CODE_INVALID` / `ERR_PAIRING_CODE_EXPIRED` codes (already in use by Epic 1 stories) are formally registered in `/shared/error-codes.md`
**And** the `RoomState.ended` transition cleanly terminates the In-Call screen and returns to Paired home
**And** the state-priority choreography stub is in place: only one banner shows at a time in the In-Call upper region (full priority order locked in Epic 4 Story when more states are added — Architecture Patterns §7)
**And** SafeLog emits `errorCode` + `roomState` + `callId` keys for every state transition (no conversation content — there's none yet)
**And** an integration test simulates a network drop mid-Call and verifies the offline indicator appears, then auto-reconnects, then resumes

---

## Epic 3: One-Direction Translation Pipeline

Speaker speaks; their own captions stream live (Android partials) or "speaking…" pill appears (iOS); on-device ASR finalizes; on-device translation produces target text; speaker sees source + target on their own row. Proves the end-to-end translation pipeline works for one direction and includes the Week-1 model validation gate.

### Story 3.1: Pre-Validation Conversation with Girlfriend

As Bania (product owner) before locking the Week-1 translation model,
I want a structured ~15-minute conversation with his girlfriend that converts the inferred emotional spec into evidence,
So that Week-1 kill-criteria are informed by her actual reactions rather than my assumptions about her reactions.

**Acceptance Criteria:**

**Given** the inferred emotional spec from PRD §2.4 + UX §"Desired Emotional Response" is currently unvalidated by direct conversation (Mary's I.18 gap),
**When** I have the conversation,
**Then** `/docs/runbooks/pre-validation-conversation.md` exists with: question set ("what would make you stop using this?", "is ID-only acceptable for 4–8 weeks while SU comes in v2?", "if a caption is awkward but accurate, does that bug you or do you ignore it?", "what would feel like being graded vs. being understood?") + her verbatim or paraphrased responses
**And** the findings are summarized into 3–5 evidence statements that update or confirm the Week-1 kill-criteria
**And** specifically, the ≥60% quality target is either confirmed acceptable to her or revised based on her input
**And** any new anti-emotions or trust-failure surfaces she names are added to the UX-spec §"Anti-Emotions" inventory
**And** I treat the conversation as input-only — no commitment to her about specific feature ordering until the Week-1 gate results are in

### Story 3.2: ParticleProcessor Module + Golden-File Fixtures

As a solo developer implementing the rules-based particle preservation strategy (ADR-B3),
I want a `ParticleProcessor` module on both platforms with rule tables for the 6 DR-defined categories and a golden-file fixture harness that runs the same tests on both,
So that particle preservation is deterministic, testable, model-agnostic, and the same cases pass identically on Android and iOS.

**Acceptance Criteria:**

**Given** DR §1 (particles), §3 (Gen-Z slang), §4 (Sundanese insertions), and §6 (honorifics + religious + indirect refusals) reference tables are available,
**When** I implement the module,
**Then** Android has `translation/particles/` containing `ParticleProcessor.kt`, `ParticleRules.kt`, `SundaneseInsertions.kt`, `HonorificStripping.kt`, `IndirectRefusals.kt`, `GenZSlang.kt`, `ReligiousExpressions.kt` (Architecture Patterns §11 file list)
**And** iOS has `Translation/Particles/` containing identically-named Swift files
**And** `ParticleProcessor` exposes `preProcess(text, sourceLang, targetLang): ProcessedText` and `postProcess(rawTarget, originalSource, sourceLang, targetLang): PostProcessed` on both platforms (identical signatures per Architecture Patterns §9)
**And** rule tables include: 14 ID discourse particles (TQ-1), ≥20 Gen-Z slang items (TQ-8), ≥12 Sundanese lexical insertions (TQ-4), partner honorifics with strip rules (TQ-5), religious expressions preserved verbatim (TQ-6), indirect refusals (TQ-7), gender-neutral defaults `dia → they` (TQ-3)
**And** `/shared/particle-rules-fixtures/` (from Story 1.7) is populated with ≥3 fixture cases per rule type, each containing `source.txt`, `expected_processed.txt`, `expected_target.txt`, `metadata.json` with `{source_lang, target_lang, expected_particles}`
**And** both platforms' test suites load fixtures from `/shared/` and assert identical outputs (cross-platform parity test)
**And** CI on both platforms blocks merge on any fixture-test failure

### Story 3.3: AsrProvider + TranslationProvider Interfaces — Symmetric Surface + Cancellation Contract

As a solo developer enforcing the Provider Abstraction NFR (PRD §6.4 / Architecture Patterns §9),
I want both platforms to expose `AsrProvider` and `TranslationProvider` interfaces with identical method names, parameter shapes, and a cross-platform-testable cancellation contract,
So that v2 Sundanese, Plan B Gemini, and any future provider can swap behind the abstraction without rewriting call sites.

**Acceptance Criteria:**

**Given** Architecture Patterns §9 defines the interface shapes,
**When** I add the interfaces,
**Then** Android has `translation/asr/AsrProvider.kt` with `val supportsStreamingPartials: Boolean`, `fun start(language: LanguageCode): Flow<AsrEvent>`, `fun stop()` and `translation/nmt/TranslationProvider.kt` with `suspend fun translate(sourceText: String, sourceLang: LanguageCode, targetLang: LanguageCode): TranslationResult`
**And** iOS has `Translation/ASR/AsrProvider.swift` (protocol) with `var supportsStreamingPartials: Bool { get }`, `func start(language: LanguageCode) -> AsyncStream<AsrEvent>`, `func stop()` and `Translation/NMT/TranslationProvider.swift` with `func translate(sourceText: String, sourceLang: LanguageCode, targetLang: LanguageCode) async throws -> TranslationResult`
**And** the `TranslationResult` data class/struct has identical fields on both platforms: `targetText`, `particlesPreserved: List<String>`, `status: TranslationStatus`, `renderMode: RenderMode`, `confidence: Double`
**And** `FakeAsrProvider` and `FakeTranslationProvider` test doubles exist on both platforms for use in CaptionStack tests without burning real ASR/model cost
**And** cancellation contract test: `stop()` MUST terminate the stream and release all ASR resources within **500 ms**, verified on both platforms by a unit test that asserts no allocated resources remain 500 ms after `stop()` (Architecture Patterns §9)
**And** `LanguageCode` wrapper enforces BCP 47 format (`id-ID`, `en-US`, `su-ID`); Flores-200 codes (`ind_Latn`, `eng_Latn`) appear ONLY between `TranslationProvider.translate()` input and the model call — never in logs, error codes, or elsewhere (Architecture Patterns §3)
**And** UI never calls Providers directly — all flows go through `CallSession` (Architecture Patterns §13); a detekt/SwiftLint rule flags direct Provider calls from View code

### Story 3.4: Android On-Device ASR Provider

As Bania on his Galaxy speaking English during a Call,
I want my speech to be transcribed on-device using Android's `SpeechRecognizer.createOnDeviceSpeechRecognizer` with `id-ID` and `en-US` locales,
So that my source text is captured at <2 s latency without any audio leaving my device.

**Acceptance Criteria:**

**Given** my device runs Android 13+ (API 33+) and the `AsrProvider` interface exists (Story 3.3),
**When** the Android `OnDeviceAsrProvider` initializes,
**Then** a runtime availability probe runs at app launch via `SpeechRecognizer.checkRecognitionSupport(intent, executor, callback)` against `RecognitionSupport.getInstalledOnDeviceLanguages()` — confirms `id-ID` AND `en-US` are present (FR-13 Android)
**And** if either locale is missing, `INFO_MODEL_LOADING` state surfaces and the user is offered a "Download language pack" Settings deep-link (Android's locale download mechanism)
**And** the provider sets `supportsStreamingPartials = true` (Android streams ASR partials per FR-18 / UX-DR8)
**And** `start(language)` emits `AsrEvent.PartialText` events every ~300–500 ms during speech and `AsrEvent.FinalText` on Utterance commit (driven by VAD from Story 3.6)
**And** time-to-finalized Source Text is <2 s on Bania's Samsung Galaxy after Utterance end (FR-13 acceptance)
**And** if ASR returns empty / low confidence (`WARN_ASR_LOW_CONFIDENCE`), the event surfaces with `confidence` field set; downstream handles soft-retry display (Story 3.15 / Epic 4)
**And** audio is NOT persisted to disk at any point (FR-11)
**And** stop() releases resources within 500 ms (cancellation contract from Story 3.3)

### Story 3.5: iOS Whisper.cpp On-Device ASR Provider

As his girlfriend on iPhone speaking Indonesian during a Call,
I want my speech to be transcribed on-device using Whisper.cpp's bundled small multilingual model with Core ML acceleration,
So that my source text is captured at <3 s latency without any audio leaving my device — since Apple's APIs do not support `id-ID` on-device.

**Acceptance Criteria:**

**Given** the iOS scaffold (Story 1.2) bundles the Whisper.cpp XCFramework and the `AsrProvider` interface exists (Story 3.3),
**When** the iOS `WhisperCppAsrProvider` initializes,
**Then** the bundled Whisper.cpp small multilingual model (~140 MB, `ggml-small.bin` or `ggml-base.bin` per scaffolding-time decision per Gap I.3) loads from the app bundle on first Call start
**And** Core ML support is compiled in; inference runs via `whisper_full_with_state` against PCM buffers chunked into Utterance-sized windows (FR-13 iOS)
**And** the provider sets `supportsStreamingPartials = false` (iOS does NOT stream true partials per ADR-B5 honest asymmetry; UX-DR9 SpeakingIndicator covers the gap)
**And** `start(language)` emits only `AsrEvent.FinalText` events (no `PartialText`); the language hint biases the multilingual model
**And** time-to-finalized Source Text is <3 s on her iPhone after Utterance end (FR-13 acceptance; Whisper.cpp small model assumption verified in Week-1 probe per PRD §13)
**And** model load timing is logged via `SafeLog.event(AllowedLogKey.modelName, ...)` + `latencyMs`; first-call cold-start surfaces as `INFO_MODEL_LOADING` indicator (Architecture Patterns §7)
**And** if Whisper.cpp returns empty or extremely low-confidence output, the event surfaces `WARN_ASR_LOW_CONFIDENCE`
**And** audio is NOT persisted to disk at any point (FR-11)
**And** `stop()` releases Whisper context within 500 ms (cancellation contract)
**And** battery measurement: a 30-min Call shows <30%/hour drain on her iPhone model (SM-5 target; if exceeded, escalate to `small→base` downgrade per ADR-B5)

### Story 3.6: VAD + Audio Tap from WebRTC Local Track

As a solo developer wiring the per-Utterance pipeline,
I want a WebRTC VAD that segments the local mic stream into Utterances at 700 ms silence + 15 s max, tapping audio from the LiveKit local track without affecting the peer's audio,
So that ASR receives clean Utterance-sized windows and sentence-final particles settle before commit (DR §7 prosody finding).

**Acceptance Criteria:**

**Given** a Call is active and the local audio track is publishing to LiveKit,
**When** the VAD subsystem initializes,
**Then** on Android, audio is tapped via LiveKit Kotlin SDK `localAudioTrack.setAudioBufferCallback(AudioBufferCallback)` — receives PCM 16-bit mono in real time (FR-11)
**And** on iOS, audio is tapped via LiveKit Swift SDK `AudioManager.shared.capturePostProcessingDelegate` implementing `AudioCustomProcessingDelegate.audioProcessingProcess(audioBuffer:)`
**And** the tap does NOT affect the audio sent to the peer (verified by listening test — peer's audio quality unchanged when tap is engaged vs disengaged)
**And** WebRTC VAD (`webrtcvad` library, mode 2 — moderate sensitivity) runs on the tapped PCM stream
**And** an Utterance commit fires after 700 ms of silence following detected speech (FR-12 default, tunable via internal flag)
**And** max Utterance length is 15 seconds; longer continuous speech force-segments (FR-12)
**And** partial transcripts BEFORE the 700 ms silence are NOT translated and NOT sent to the peer (FR-12 prosody preservation per DR §7)
**And** mic permission is requested before the first Call; user cannot start a Call without it (FR-11)
**And** audio is not persisted to disk at any point during normal operation (FR-11)

### Story 3.7: Regression Corpus + Bake-Off Harness

As Bania before the Week-1 validation gate,
I want a regression corpus of ~200 tagged utterances and a harness that runs each candidate translation model against the corpus,
So that Week-1 gate decisions are evidence-based (quantitative kill criteria, not vibes) and any future model swap can be regression-tested against the same fixture set.

**Acceptance Criteria:**

**Given** the DR linguistics research provides reference tables for particles, slang, Sundanese insertions, and cultural-pragmatic expressions,
**When** I assemble the corpus and harness,
**Then** `/shared/regression-corpus/` contains ~200 utterances stored as JSON or YAML, each with `{ source_lang, source_text, expected_target_text, tags: ["particle:loh", "register:aku", "su:urang", ...], expected_particles: [...] }`
**And** corpus tags trace back to DR section IDs (§1 particles / §3 slang / §4 SU insertions / §6 cultural-pragmatic) — Gap I.14 satisfied
**And** the corpus includes both ID→EN and EN→ID directions in roughly balanced numbers
**And** a CLI bake-off harness at `/shared/bakeoff/` (or per-platform integration tests) runs a candidate `TranslationProvider` against the corpus and produces a results CSV: `{ utterance_id, raw_target, post_processed_target, expected_target, particles_preserved, exact_match, fuzzy_score, qa_rating? }`
**And** the harness supports plug-in providers (current candidates + future v2 Sundanese provider) via the `TranslationProvider` interface from Story 3.3
**And** kill-criteria are defined upfront in `/docs/runbooks/week-1-validation.md` per the pre-validation conversation findings (Story 3.1): e.g., "if NLLB-200 fuzzy-score median < X OR particle-preservation rate < Y on TQ-1 cases, fall to MADLAD" (Gap I.16)
**And** the harness runs on CI on every PR touching `translation/` (regression check)

### Story 3.8: NLLB-200 Distilled 600M On-Device Translation Provider (Rank 1 Candidate)

As Bania running the Week-1 model bake-off,
I want NLLB-200 distilled 600M (q4) wrapped as a `RawTranslationProvider` implementation on both platforms,
So that the Rank-1 candidate (smallest, fastest, lowest battery) gets evaluated against the regression corpus first per ADR-B1.

**Acceptance Criteria:**

**Given** ONNX Runtime Mobile is set up on Android (NNAPI/GPU delegates) and Core ML on iOS,
**When** I add the NLLB-200 provider,
**Then** Android `translation/nmt/Nllb200OnnxProvider.kt` loads the q4-quantized NLLB-200 distilled 600M model (~150–200 MB) via ONNX Runtime
**And** iOS `Translation/NMT/Nllb200CoreMLProvider.swift` loads the same model via Core ML (unified with Whisper.cpp's Neural Engine acceleration per ADR-B2)
**And** the provider implements `TranslationProvider.translate()` with: BCP 47 → Flores-200 conversion at the model-call boundary (`id-ID` → `ind_Latn`, `en-US` → `eng_Latn` per Architecture Patterns §3), inference, output decoding, Flores → BCP 47 conversion back
**And** model load timing logged via SafeLog (`modelName: "nllb-200-distilled-600m-q4"`, `latencyMs`)
**And** per-Utterance translation latency is logged (`latencyMs` per call) for use in the bake-off harness
**And** the provider runs against `/shared/regression-corpus/` via the Story 3.7 harness and produces a results CSV
**And** the bake-off CSV is committed to `/docs/runbooks/week-1-validation-results-nllb.csv` after the run
**And** ULID `model_run_id` ties each row to the bake-off run that produced it
**And** unit test asserts the call site for translation is the `TranslationProvider` interface, not the vendor type (PRD §6.4 / Architecture validation test)

### Story 3.9: Week-1 Validation Gate — Lock Model or Escalate

As Bania at the end of Week 1 of the build,
I want a documented decision moment where Plan A candidates are reviewed against pre-defined kill criteria and either a Plan A model is locked OR the next candidate (MADLAD-400 / Gemma 2B) runs OR Plan B (Vertex AI Gemini) activates,
So that the most-risky decision in v1 is made explicitly with evidence rather than drifting into the highest-cost outcome.

**Acceptance Criteria:**

**Given** Story 3.8 produced NLLB-200 bake-off results AND Story 3.1's kill criteria are documented,
**When** I run the gate review,
**Then** `/docs/runbooks/week-1-validation.md` records the gate result: candidate name, fuzzy-score median + p95, particle-preservation rate per TQ category, qualitative paired review with his girlfriend (Mary's I.18 finding informs criteria)
**And** the gate produces one of three outcomes:
  - **Outcome A — NLLB-200 passes:** model locked as v1 Plan A; `Nllb200*Provider` wired as the production `RawTranslationProvider`; Stories 3.12–3.15 proceed
  - **Outcome B — NLLB-200 fails, escalate to MADLAD-400 (Rank 2) then Gemma 2B (Rank 3):** add `Madlad400Provider` (Android: ONNX, iOS: Core ML, ~750 MB) and/or `Gemma2LlamaCppProvider` (llama.cpp, ~1.5 GB) following the same pattern as Story 3.8; re-run bake-off; if any passes, lock it
  - **Outcome C — all Plan A candidates fail:** activate Plan B: add `VertexGeminiProvider` (Vertex AI Gemini 2.5 Flash in `asia-southeast1`); cost +~$5/month, re-approved by Bania; `/shared/prompts/id-en-plan-b.md` authored using DR §"Improved Gemini 2.5 Flash System Prompt"; privacy posture downgrades to "Google sees translation text but does not train on it" — PRD §6.1 Plan-B note already in place from Story 1.14
**And** the chosen provider is documented in `architecture.md` ADR-B1 amendment + decision-log
**And** scope-cut criteria are reviewed (Story 1.14 runbook) — if all candidates fail AND Plan B is rejected, scope cuts trigger (e.g., defer captions to v1.1, ship Audio Calling alone)
**And** Bania manually approves the gate result before Story 3.10 begins

### Story 3.10: RuleBasedTranslationProvider Decorator

As a solo developer wiring the production translation flow,
I want a `RuleBasedTranslationProvider` decorator that composes `ParticleProcessor` (Story 3.2) with the locked `RawTranslationProvider` (Story 3.9 outcome),
So that every translation goes through pre-processing → NMT → post-processing deterministically and the rules are guaranteed in code rather than depending on the model's instruction-following ability (ADR-B3).

**Acceptance Criteria:**

**Given** `ParticleProcessor` (Story 3.2) and the locked `RawTranslationProvider` (Story 3.9 outcome) both exist,
**When** I implement the decorator,
**Then** Android `translation/rulebased/RuleBasedTranslationProvider.kt` and iOS `Translation/RuleBased/RuleBasedTranslationProvider.swift` each implement `TranslationProvider` and compose:
  1. `ParticleProcessor.preProcess(sourceText, sourceLang, targetLang)` → `processed`
  2. `RawTranslationProvider.translate(processed, sourceLang, targetLang)` → `rawResult`
  3. `ParticleProcessor.postProcess(rawResult.targetText, originalSource, sourceLang, targetLang)` → `final`
  4. Return `TranslationResult { targetText: final.text, particlesPreserved: final.particles, status: rawResult.status, renderMode: final.renderMode, confidence: rawResult.confidence }`
**And** `CallSession` is wired to call `RuleBasedTranslationProvider`, NOT the raw provider directly (architectural composition)
**And** the decorator handles `RenderMode.sundanesePlaceholder` per Architecture Patterns §7: when the ParticleProcessor flags a full Sundanese clause, `renderMode = sundanesePlaceholder` is set even if `status = ok` (orthogonal — full UI in Epic 4)
**And** the regression corpus harness (Story 3.7) re-runs through the decorator and shows particle-preservation rates that are **higher than** the raw provider alone (the rules add measurable value)
**And** integration test: a known-particle Utterance ("Aku kangen banget loh") flows through decorator and produces target with `kan`/`loh` correctly mapped to English ("you know") AND `particlesPreserved` field contains `["loh"]`
**And** the test fixture set from Story 3.2 also runs through the full decorator (round-trip test)

### Story 3.11: Three-Layer Translation Capture Pipeline (Debug Tracing)

As a solo developer preparing for Week-4 quality regression attribution,
I want a debug-flag-gated three-layer trace (raw NMT output → post-processed → displayed text) routed through SafeLog with redaction,
So that when a caption looks wrong, I can attribute the failure to the model vs the ParticleProcessor vs the rendering step without re-running the Call (Gap I.15).

**Acceptance Criteria:**

**Given** the production translation flow runs through `RuleBasedTranslationProvider` (Story 3.10),
**When** I enable the capture pipeline,
**Then** a debug flag (`BuildConfig.TRANSLATION_TRACE_ENABLED` Android / build setting + `#if DEBUG` iOS) gates the capture (default OFF in release builds)
**And** when ON, every translation captures: `{ utterance_id, raw_nmt_output, post_processed, displayed_text, particles_preserved, processing_durations_ms, timestamp }` to local-only debug storage on the device
**And** the captured content is stored to a local-only debug file (NOT uploaded, NOT crashlytics-logged, NOT synced) — verified by network packet inspection during a captured Call
**And** SafeLog routing through the capture is privacy-safe: per Architecture Patterns §14, source/target text is captured to debug storage ONLY when the trace flag is on AND only in debug builds; SafeLog itself still forbids `source_text` / `target_text` field keys per the allowlist
**And** a "View latest translation trace" debug-build menu item exists in Settings to surface the trace for inspection
**And** when the flag is OFF, no trace is recorded (zero overhead path); a unit test verifies this
**And** Bania can enable/disable the flag without rebuilding (e.g., via secret tap pattern on the privacy summary screen in debug builds)

### Story 3.12: Basic CaptionStack + CaptionRow — Speaker-Side Rendering

As Bania or his girlfriend speaking during a Call,
I want my own captions to render in a scrollable stack in the lower 60% of the In-Call screen with the canonical speaker-peripheral styling (~60% opacity, smaller body, right-aligned),
So that I can verify what was sent to my partner without being drawn to my own captions (UX-DR7 D2c locked).

**Acceptance Criteria:**

**Given** the In-Call screen from Story 2.7 reserves the lower 60% for the CaptionStack and a translation has been produced (Story 3.10),
**When** my own translation arrives,
**Then** Android `captions/ui/CaptionStackComposable.kt` renders a `LazyColumn(state = rememberLazyListState())` with `items(captions, key = { it.id })` (Architecture pattern in Addendum)
**And** iOS `Captions/UI/CaptionStackView.swift` renders `ScrollViewReader { proxy in ScrollView { LazyVStack { ForEach(captions) { ... }.id(c.id) } }.defaultScrollAnchor(.bottom) }`
**And** `CaptionRow` is rendered for each Utterance:
  - Speaker (self) row: `text-peripheral` opacity (~60%), `caption-peripheral` typography (18pt iOS / 16sp Android Regular), RIGHT-aligned (off-center toward speaker's mic side per UX-DR7 / UX §"Visual Foundation")
  - Source on top (smaller, `caption-source` size 16pt/14sp), Target below (larger, `caption-peripheral` size 18pt/16sp)
**And** when `particlesPreserved` is non-empty for the Utterance, the target text renders with `+0.3 pt` letter-spacing (TQ-1 / TQ-2 emotional-weight whisper per UX-DR7)
**And** captions accumulate from oldest (top) to newest (bottom) and auto-scroll keeps newest visible (full auto-scroll-suspend + JumpToLatestPill in Epic 4)
**And** Captions are cleared on Call end (unless transcript history is enabled — FR-21 in Epic 8)
**And** ULID `utterance_id` is the row key (Architecture Patterns §4); row identity is stable across partial → final transitions

### Story 3.13: PartialCaption (Android) + SpeakingIndicator (iOS) — Honest Asymmetry

As Bania on Android (sees partials) or his girlfriend on iOS (sees "speaking..."),
I want a calm in-progress indicator at the bottom of the Caption stack that signals "the app is hearing me" before my Utterance commits,
So that the speech-onset → final-caption gap feels like presence not absence (UX §"Latency-as-doubt").

**Acceptance Criteria:**

**Given** the AsrProvider from Story 3.4 (Android, streams partials) or Story 3.5 (iOS, no partials),
**When** I begin speaking,
**Then** on Android: `captions/ui/PartialCaption.kt` renders a single pinned row at the bottom of `CaptionStack` with source text streaming in at `text-tertiary` opacity (0.38) + italic styling; updates at the cadence of ASR partials (every ~300–500 ms); subtle blinking cursor or partial-indicator dot at end of line (UX-DR8)
**And** on Android, on Utterance finalization (700 ms silence per Story 3.6), the PartialCaption transitions to a finalized `CaptionRow` with NO row reorder or visual jump (stable identity); the partial is local-only and NEVER sent to peer (FR-12 + FR-18)
**And** on iOS: `Captions/UI/SpeakingIndicator.swift` renders a small horizontal pill at the same position the partial row would occupy — text "speaking…" in `text-tertiary` opacity + 5-bar audio-level pulse next to it (UX-DR9)
**And** on iOS, on Utterance finalization, the SpeakingIndicator dismisses and a fresh `CaptionRow` appears in the same position (no row reorder)
**And** the UI branches on `AsrProvider.supportsStreamingPartials: Bool` flag (Story 3.3) — not on platform string
**And** `mic-active dot` (state-red, pulsing) in the In-Call upper region (from Story 2.7) animates during the partial/speaking window
**And** the partial → final transition timing feels smooth (≤200 ms ease) verified by visual review on physical devices

### Story 3.14: End-to-End Speaker-Side Translation — Caption Loop Beats 1–5

As Bania or his girlfriend speaking during a Call,
I want the full 9-beat Caption Loop to fire correctly for beats 1–5 (speech onset → partial/speaking → Utterance commit → translation in flight → translation arrives on my screen),
So that I see my own source + target rendered with peripheral styling, proving the entire on-device pipeline works for one direction (FR-16, UX-DR31).

**Acceptance Criteria:**

**Given** Stories 3.1–3.13 are complete,
**When** I speak an Utterance during a Call,
**Then** **Beat 1 (speech onset):** mic-active dot pulses (from Story 2.7), `AudioLevelIndicator` reflects amplitude (Story 2.3)
**And** **Beat 2 (partial/speaking):** Android shows `PartialCaption`; iOS shows `SpeakingIndicator` (Story 3.13)
**And** **Beat 3 (Utterance commit):** at 700 ms silence (or 15 s force-segment), the Partial/Speaking dismisses; a finalized `CaptionRow` with source text only appears in same position (Story 3.6 + 3.12 + 3.13)
**And** **Beat 4 (translation in flight):** a subtle "thinking..." indicator (single low-opacity dot or weight shift — NO spinner, NO shimmer per UX-DR31 anti-AI rule) renders inline on the pending row while `RuleBasedTranslationProvider.translate()` runs (Story 3.10)
**And** **Beat 5 (translation arrives — speaker's screen):** the row updates to render Source + Target with full peripheral styling (Story 3.12 — `text-peripheral` opacity, right-aligned, TQ-1 letter-spacing when applicable)
**And** SM-4-equivalent timing: speaker sees own Source within 1 s of Utterance commit; Target within **8 s median** end-to-end (relaxed NFR-Latency target — was 4 s in PRD but updated per Architecture)
**And** FR-16 acceptance: speaker sees their own Source Text within 1 s of speaking, and their Target Text within the relaxed latency budget
**And** integration test: speak ID Utterance "Aku kangen kamu loh" → speaker sees own row with `ID: Aku kangen kamu loh` / `EN: I miss you, you know` with `loh` particle preserved (TQ-1 evidence)

### Story 3.15: Translation Failure Marker — Speaker-Side Basic

As Bania or his girlfriend on the speaker side when translation fails,
I want my own row to render the source text + a calm `TranslationUnavailableMarker` (state-amber) instead of the target text,
So that I know my partner did not receive a translated caption (FR-20 speaker-side, UX-DR10 basic) — full bidirectional + long-press explanation lands in Epic 4.

**Acceptance Criteria:**

**Given** `TranslationProvider.translate()` may fail (timeout, model load failure, OOM, etc.),
**When** translation fails for my Utterance,
**Then** Android `captions/ui/TranslationUnavailableMarker.kt` and iOS `Captions/UI/TranslationUnavailableMarker.swift` render in the target slot of my `CaptionRow`: state-amber color + text "Translation unavailable" + a subtle ⚠ glyph (UX-DR10)
**And** the source text remains visible above the marker (never silent-drop per FR-20 / NFR-Reliability)
**And** the failure-state taxonomy fires: `translationFailed` per Architecture Patterns §7 + error code `ERR_TRANS_PROVIDER_UNAVAIL` or `ERR_TRANS_PROVIDER_TIMEOUT` (logged via SafeLog with `errorCode` + `callId` + `utteranceId` keys, no conversation content)
**And** if the failure was a timeout (and Plan B is active), retry/backoff applies per ADR-B1 retry policy (Architecture Addendum FR-14) — exponential backoff with full jitter, base 1 s, cap 32 s, max 3 retries
**And** in Plan A (on-device), the model failure modes are different (OOM, GPU init failure) — surface as `ERR_TRANS_PROVIDER_UNAVAIL` without retry (model state likely persistent)
**And** long-press to expand the marker for an error reason ("Translation service unavailable" / "Model failed to load" / "Network error" for Plan B) is **deferred to Epic 4 Story 4.4** — this story renders only the basic marker
**And** integration test: force `RuleBasedTranslationProvider` to throw → CaptionRow renders with marker + source intact + correct SafeLog event emitted

---

## Epic 4: Bidirectional Captions & Failure States

Both partners see each other's translated captions in real time. Caption history is scrollable with auto-scroll + suspend + JumpToLatestPill; failures render calmly (translation unavailable with explanation, Sundanese placeholder); state-priority choreography unified cross-platform. Completes the 9-beat Caption Loop.

### Story 4.1: Data Channel Schema — Versioned, Idempotent, Ordered

As a solo developer wiring the peer-to-peer translation delivery contract,
I want the Data Channel payload schema implemented as code on both platforms with versioning, monotonic `seq` counters, dedup, ~500 ms reorder buffer, and tolerant decoding,
So that peer caption rendering survives schema evolution, duplicate delivery, and out-of-order arrival without rendering glitches (Architecture Patterns §5, AR-16).

**Acceptance Criteria:**

**Given** `/shared/data-channel-schema-v1.json` (Story 1.7) defines the canonical wire format,
**When** I implement the schema on both platforms,
**Then** Android `call/callSession/DataChannelMessage.kt` and iOS `Call/CallSession/DataChannelMessage.swift` decode/encode the payload with snake_case field names (Architecture Patterns §3): `schema_version: 1`, `seq`, `utterance_id` (ULID), `speaker_uid`, `source_lang` (BCP 47), `source_text`, `target_lang`, `target_text`, `confidence`, `timestamp_offset_ms` (sender's offset from local `call_start_ms`, NOT wall-clock UTC per Patterns §5), `translation_status` (`ok | failed | low-confidence | sundanese-placeholder`), `render_mode` (`default | sundanesePlaceholder`), `particles_preserved: List<String>`, `call_type` (`audio | video`)
**And** the sender emits a monotonic per-sender `seq` counter starting at 1 at Call start, incrementing by 1 per published message
**And** the receiver implements dedup keyed on `(speaker_uid, seq)` — duplicates dropped silently
**And** the receiver renders in `seq` order per speaker (NOT arrival order); a ~500 ms reorder buffer holds late-arriving lower-seq messages and re-emits in order
**And** the decoder tolerates unknown major versions (drops silently per Patterns §5 forward-incompat) and unknown fields within the same major version (forward-compat additions)
**And** field add/remove/rename triggers a major version bump (documented in `/shared/data-channel-schema-v1.json` headers)
**And** clock-skew handling: receiver computes display "X seconds ago" from its own clock, never trusting sender's wall-clock
**And** payload size ≤1 KB normal case; hard cap 4 KB enforced at send-side (oversized → log `WARN_PAYLOAD_OVERSIZE` and truncate `source_text`/`target_text` with `…` marker)
**And** unit tests: round-trip encode/decode, dedup, reorder buffer, unknown-field tolerance, oversize handling

### Story 4.2: Publish Translation to Peer via Data Channel

As Bania or his girlfriend after my translation completes,
I want my `CallSession` to publish the translated Caption to my partner over the WebRTC Data Channel reliable-ordered,
So that my partner receives the Caption within 300 ms and sees my translated speech on their screen (FR-15).

**Acceptance Criteria:**

**Given** Story 4.1's schema exists and a translation has completed via `RuleBasedTranslationProvider` (Story 3.10),
**When** my `CallSession.publishCaption(result)` runs,
**Then** on Android, `LiveKitRoomManager` invokes `LocalParticipant.publishData(byteArray, reliable=true)` with the encoded payload
**And** on iOS, the equivalent `LocalParticipant.publishData(data:reliability:.reliable)` API publishes the payload
**And** the message includes the next `seq` value for this Call from the sender's counter
**And** UTF-8 JSON encoding; payload size logged via SafeLog (`AllowedLogKey.payloadSizeBytes` — privacy-safe, no content)
**And** message latency from translation-arrived to peer's render: <300 ms in same-region peer connection (FR-15 acceptance)
**And** if the LiveKit Data Channel publish fails (rare; SFU partition or local channel closed), the failure surfaces as `ERR_DATA_CHANNEL_FAILED` and the peer's row will show `TranslationUnavailableMarker` instead (handled by Story 4.4 receiver-side); my own row still shows my translation correctly
**And** SafeLog emits `utteranceId` + `seq` + `latencyMs` + `payloadSizeBytes` (no `source_text`/`target_text`) per Patterns §14
**And** an integration test verifies a published Caption arrives at a second test client in the same room with all fields preserved

### Story 4.3: Receive and Render Peer's Caption — Bidirectional CaptionRow

As Bania or his girlfriend listening to my partner speak,
I want their translated Caption to appear in my Caption stack with full-emphasis styling (left-aligned, `text-primary` opacity, larger size) within seconds of them speaking,
So that I read their meaning in my own language during the conversation (FR-15 receive, FR-17, FR-18 full bidirectional, UX-DR7 partner-row anatomy, Caption Loop beat 6).

**Acceptance Criteria:**

**Given** a peer publishes a Caption via Story 4.2 and my `CallSession` subscribes to the Data Channel,
**When** my client receives the Data Channel message,
**Then** the message is decoded per Story 4.1 (with dedup + reorder buffer applied) and routed to my `CallSession.captionState`
**And** `CaptionStack` appends a new `CaptionRow` with **partner-row anatomy** per UX-DR7:
  - LEFT-aligned (off-center toward speaker's mic side on her phone — opposite of self-row)
  - Source text at `text-secondary` opacity (`caption-source` 16pt iOS / 14sp Android, smaller, on top)
  - Target text at `text-primary` opacity (`caption-primary` 22pt iOS / 20sp Android, larger, below — the load-bearing reading line)
**And** when `particlesPreserved` is non-empty on the received message, target renders with `+0.3 pt` letter-spacing (TQ-1 / TQ-2 emotional-weight whisper)
**And** the new row appears with a 200 ms fade-in animation (Linear-grade easing per UX §"Inspirations") — Caption Loop beat 6
**And** auto-scroll fires to bring the new row into view (full suspend/resume mechanics in Story 4.4)
**And** Captions accumulate from oldest (top) to newest (bottom) and the history is scrollable independently of the rest of the In-Call screen (FR-17)
**And** Captions are cleared on Call end unless transcript history opt-in (FR-21) is active (Epic 8); in this epic, always clear on Call end
**And** the peer does NOT see my Partial Captions / SpeakingIndicator — only finalized Captions arrive over the Data Channel (FR-18 + FR-12)
**And** integration test: peer publishes Caption with `particles_preserved: ["loh"]` → my row renders with letter-spacing visible at the verifiable typographic delta

### Story 4.4: Translation Failure Marker — Full UX with Long-Press Explanation and Retry

As Bania or his girlfriend when a translation fails on either side,
I want the `TranslationUnavailableMarker` to be long-pressable to reveal the specific error reason and (if retryable) tap-to-retry,
So that I understand whether the issue is recoverable and can route around it conversationally (FR-20 full, UX-DR10 full, Caption Loop beat 8).

**Acceptance Criteria:**

**Given** Story 3.15 renders the basic marker on the speaker side,
**When** Bania (or his girlfriend) long-presses a `TranslationUnavailableMarker` row,
**Then** a brief inline expansion (NOT a separate sheet, NOT a toast — per UX-DR36 feedback-pattern constraints) reveals the error reason mapped from the `ERROR_CODE`:
  - `ERR_TRANS_PROVIDER_UNAVAIL` → "Translation service unavailable"
  - `ERR_TRANS_PROVIDER_TIMEOUT` → "Translation timed out"
  - `ERR_NETWORK_DROPPED` → "Network error"
  - `ERR_DATA_CHANNEL_FAILED` (peer side only) → "Connection error — partner did not receive this"
**And** when the error type is retryable (network drops; not 5xx-after-max-retries; not OOM/model-state errors), a "Retry" button surfaces inline; tapping it re-invokes `RuleBasedTranslationProvider.translate()` for the same Utterance
**And** on retry success, the marker transitions to a normal `CaptionRow` (Source + Target), and a new Data Channel message with the same `utterance_id` (but bumped `seq`) is published to the peer; peer's row updates in place (idempotency via `utterance_id` per Patterns §5)
**And** on retry failure, the marker remains; further retries are user-initiated (no automatic retry loop beyond the initial backoff from Story 3.15)
**And** also on the PEER side: when an Utterance's Data Channel message arrives with `translation_status: failed`, the peer's row renders the marker too — both Bania and his girlfriend see the failure, not just the speaker (NFR-Reliability)
**And** VoiceOver/TalkBack: the rotor "show error reason" custom action exposes the explanation without requiring long-press (UX-DR33 accessibility)
**And** the marker copy follows UX §"Voice of product-generated text": plain, kind, never apologetic-corporate (no "We're terribly sorry…")

### Story 4.5: SundanesePlaceholderRow — Quiet `[Sundanese]` Rendering on EN Side

As Bania when his girlfriend speaks a full Sundanese clause,
I want the EN side to render a quiet `[Sundanese]` placeholder while her side still shows her original source text,
So that the conversation continues without alarm — designed against anti-emotion #2 (othering) (UX-DR11, Caption Loop beat 9).

**Acceptance Criteria:**

**Given** her `RuleBasedTranslationProvider` flags an Utterance with `render_mode: sundanesePlaceholder` (the `ParticleProcessor` detected a full Sundanese clause per ADR-B4 / Story 3.10),
**When** the Caption is published via Data Channel (Story 4.2) and arrives on my side,
**Then** my client renders `SundanesePlaceholderRow` (Android `captions/ui/SundanesePlaceholderRow.kt`, iOS `Captions/UI/SundanesePlaceholderRow.swift`) in the target slot of the CaptionRow:
  - `[Sundanese]` text in dim italic at `text-tertiary` opacity (0.38)
  - Her source text remains visible above at `text-secondary` opacity (so I can ask "what did you say?")
  - NO state-amber color (not a failure), NO ⚠ glyph (designed against othering)
**And** her own side shows her source Utterance with target as `[Sundanese]` too (consistency — both sides see the same shape), or alternatively her side shows only source with no marker (per UX §"Mid-Call: Translation Failure Recovery" — implementer's choice, documented)
**And** the row is NOT a failure state in the taxonomy — `render_mode` is orthogonal to `translation_status` per Patterns §7 (status can be `ok` AND render_mode `sundanesePlaceholder` simultaneously)
**And** no retry, no alternate translation surface, no long-press explanation (the placeholder IS the explanation)
**And** the conversation continues naturally — auto-scroll, history, all other behavior remains as for a normal `CaptionRow`
**And** the row is announced to VoiceOver/TalkBack as "Sundanese phrase — translation not available in v1." per UX-DR33
**And** integration test: simulate a Sundanese-clause Utterance ("Urang bade ka pasar, atuh") → her side flagged + sent + my side renders SundanesePlaceholderRow without firing any error UI

### Story 4.6: Auto-Scroll with Manual-Scroll-Suspend

As Bania or his girlfriend during a Call,
I want the Caption stack to auto-scroll to the newest Caption, except when I have manually scrolled up to read earlier content — in which case auto-scroll suspends until I scroll back to the bottom,
So that I can read at my own cadence without losing context, and the stack pins to the latest when I'm following along (FR-19, UX-DR6 part).

**Acceptance Criteria:**

**Given** the `CaptionStack` from Story 3.12 (Android) / 3.12 (iOS) is rendering Captions,
**When** a new Caption is appended (mine or peer's),
**Then** if auto-scroll is currently ACTIVE (not user-suspended), the stack animates to bring the new row into view within 200 ms (FR-19 acceptance)
**And** when the user manually scrolls upward (away from the bottom), auto-scroll is SUSPENDED — new appended Captions DO NOT cause the stack to jump
**And** auto-scroll resumes when the user scrolls back to the bottom (within ~50 dp/pt of the last row) — verified by integration test that simulates scroll-up → caption-append → no jump → scroll-down → next caption-append → auto-scroll fires
**And** auto-scroll also resumes when the user taps the `JumpToLatestPill` (Story 4.7)
**And** scroll behavior is identical-to-the-eye across Android (`LazyColumn` + `rememberLazyListState`) and iOS (`ScrollViewReader.scrollTo`) — same easing curve (Linear-grade 200 ms cubic-bezier), same suspend threshold (UX-DR16 + Experience Principle 5)
**And** the suspend state survives orientation changes within the same Call (state stored in the screen-level ViewModel / `@Observable`)

### Story 4.7: JumpToLatestPill

As Bania or his girlfriend who has scrolled up to read earlier Captions,
I want a bottom-right pill that appears when I am scrolled away from the latest Caption, with one tap to jump back to bottom and resume auto-scroll,
So that returning to live Caption flow is a single deliberate action (UX-DR12).

**Acceptance Criteria:**

**Given** auto-scroll is suspended (Story 4.6 — user has scrolled up),
**When** I look at the bottom-right of the In-Call screen,
**Then** the `JumpToLatestPill` (Android `captions/ui/JumpToLatestPill.kt`, iOS `Captions/UI/JumpToLatestPill.swift`) is visible — pill-shaped glass panel with backdrop blur, ↓ glyph + "Latest" label
**And** the pill is positioned above the `AudioCallControlRow` (won't overlap mute/end-Call buttons) and right-aligned to the safe area
**And** tapping the pill scrolls the stack to the bottom (animated, 200 ms ease) AND resumes auto-scroll
**And** the pill HIDES automatically when auto-scroll is active (within ~50 dp/pt of the bottom)
**And** keyboard activatable (VoiceOver/TalkBack: "Jump to latest caption" — UX-DR33)
**And** accessibility label updates dynamically when new Captions arrive while suspended (e.g., "Jump to latest caption — 3 new")
**And** integration test: scroll up → pill appears → 3 new Captions arrive → pill remains, "3 new" indicator surfaces optionally → tap → stack jumps to bottom → pill disappears → auto-scroll resumes

### Story 4.8: State-Priority Choreography — Cross-Platform Enforced

As Bania or his girlfriend during a Call when multiple failure states fire simultaneously (common at cold-start),
I want the In-Call screen to show ONE banner at a time using a fixed priority order (with per-Caption inline markers continuing independently),
So that I perceive ONE problem at a time, not three (UX-DR32, Architecture Patterns §7, AR-22 fully wired).

**Acceptance Criteria:**

**Given** the failure-state taxonomy from Architecture Patterns §7 + the state seeds from Story 2.11,
**When** multiple states fire simultaneously,
**Then** only ONE banner-level state renders in the In-Call upper region at a time, selected by the fixed priority:
  ```
  e2eeKeyNotReady   (Epic 5)
    > modelLoading
    > waitingForPartner   (Epic 7)
    > networkDropped
    > translationFailed   (when a global condition, not per-row)
    > videoPaused         (Epic 6)
    > sundanesePlaceholder
    > asrLowConfidence
  ```
**And** per-Caption inline markers (`TranslationUnavailableMarker`, `SundanesePlaceholderRow`, `asrLowConfidence` inline indicator) continue rendering INDEPENDENTLY at the row level — they are not deduplicated by the priority choreography (they are per-Utterance signals)
**And** the priority logic is implemented identically on both platforms — verified by a cross-platform contract test that simulates each pair of simultaneous states and asserts the same banner is shown
**And** state transitions are animated: 200 ms cross-fade when one state replaces another in priority order (UX §"Visual Foundation" motion timing)
**And** SafeLog emits a `TRANSLATION_STATUS_TRANSITION_COUNT` event each time the active banner changes (Patterns §14)
**And** new state identifiers cannot be added ad-hoc — Story 4.8 enforces this via Architecture's provisional namespace rule (Patterns §8): `RoomState.provisional.*` and `TranslationStatus.provisional.*` allowed for 14 days, then flagged by code-review agent for ADR promotion or removal
**And** error codes from `/shared/error-codes.md` map 1:1 to taxonomy entries (Patterns §10)

### Story 4.9: End-to-End Bidirectional Caption Loop Validation

As Bania (product owner) before declaring Epic 4 done,
I want a documented test pass demonstrating the full 9-beat Caption Loop firing correctly in both directions on a real Call between his Galaxy and her iPhone,
So that we have evidence the captioned-Call experience is genuinely working before layering E2EE (Epic 5), Video (Epic 6), and resilience (Epic 7) on top.

**Acceptance Criteria:**

**Given** Stories 4.1–4.8 are complete and a Call is established between the two physical devices,
**When** I run the validation,
**Then** `/docs/runbooks/epic-4-bidirectional-caption-loop-test.md` exists documenting a 10-minute Call with both partners speaking alternately
**And** Beat-by-beat verification: beats 1–9 from UX-DR31 fire as expected for each Utterance on both devices (mic-active → partial/speaking → commit → thinking → arrive on speaker → arrive on peer → TQ-1 spacing when applicable → failure path tested → Sundanese placeholder tested)
**And** **Auto-scroll + JumpToLatestPill** verified: manual scroll-up → captions accumulate without jumping → pill appears → tap → jump-to-bottom + auto-scroll resumes
**And** **Translation failure recovery** verified: simulate network drop (Airplane mode → on) mid-Call → `networkDropped` banner appears → reconnects → Captions resume; failed Captions show `TranslationUnavailableMarker` with long-press explanation
**And** **Sundanese placeholder** verified: she speaks "Urang bade ka pasar atuh" → his side shows `[Sundanese]` placeholder; conversation continues
**And** **State-priority choreography** verified: induce two simultaneous states (e.g., `modelLoading` on first call after rebuild + `networkDropped` during) — only the higher-priority banner renders
**And** **TQ-1 evidence**: speak "Aku kangen kamu loh" → partner side renders target with visible letter-spacing whisper on "you know"
**And** all 9 beats are visually identical-to-the-eye across both phones (Experience Principle 5)
**And** results recorded with screen recordings + screenshots; failures trigger a bugfix story before Epic 5 starts

---

## Epic 5: End-to-End Encryption

Calls are end-to-end encrypted (media + Data Channel) via Insertable Streams + per-Call X25519 ECDH; the SFU sees only ciphertext; forward-secrecy per Call; MITM-resistant via identity-key signing. Privacy verification protocol validates NFR-Privacy.

### Story 5.1: Per-Call Ephemeral X25519 Keypair — Generate + Sign + Publish

As Bania or his girlfriend at the start of every Call,
I want my device to generate a fresh X25519 ephemeral keypair, sign the public key with my long-term identity key (Story 1.12), and publish the signed public key to Firestore under the Call,
So that the Call gets fresh forward-secret key material and my partner can verify the ephemeral key was authored by my device (MITM-resistant per ADR-A2).

**Acceptance Criteria:**

**Given** my long-term X25519 identity keypair exists (Story 1.12 — private key in secure storage, public key at `/users/{my-uid}/identityPub`) and I'm initiating or accepting a Call,
**When** the Call's E2EE setup runs,
**Then** a fresh X25519 ephemeral keypair is generated client-side per Call (NOT reused across Calls — forward-secrecy)
**And** my ephemeral public key is **signed using my long-term identity private key** (Ed25519 signature over the ephemeral public key bytes, or equivalent X25519/Ed25519 hybrid pattern)
**And** the signed package is published to `/calls/{callId}/ephemeralPub/{my-uid}` with fields `{ pub: bytes, sig: bytes, createdAt: timestamp }` per ADR-C3
**And** my ephemeral private key is held in memory ONLY for the duration of the Call — never persisted to disk
**And** at Call end (or app foreground loss for >5 min, whichever first), the ephemeral private key is zeroed/released from memory
**And** Firestore rules (Story 1.4) restrict reads of `/calls/{callId}/ephemeralPub/*` to the two participants of the Call (verified by a rules unit test)
**And** SafeLog emits `callId` + `errorCode` for any keypair-generation failure (no key material logged); generation failures are fatal — `ERR_E2EE_KEY_EXCHANGE_FAILED` → Call ends per Patterns §10
**And** the ephemeral public key bytes are 32-byte Curve25519-compressed; signature bytes match the chosen signature scheme's standard size (e.g., 64 bytes for Ed25519)

### Story 5.2: ECDH + HKDF Key Derivation on Peer's Ephemeral Public Key

As Bania or his girlfriend at the start of every Call,
I want my device to subscribe to my partner's published ephemeral public key, verify its signature against their identity public key, perform X25519 ECDH, and derive an AES-GCM 256-bit key via HKDF-SHA256,
So that both devices independently arrive at the same symmetric key — no key transmission, no MITM possible (per ADR-A2).

**Acceptance Criteria:**

**Given** Story 5.1 publishes both sides' signed ephemeral public keys to `/calls/{callId}/ephemeralPub/{uid}`,
**When** my client receives my partner's signed ephemeral pub via a Firestore listener,
**Then** my client **verifies the signature** of the peer's ephemeral pub against the peer's `/users/{peer-uid}/identityPub` long-term identity key
**And** signature verification failure fires `ERR_E2EE_KEY_EXCHANGE_FAILED` and the Call is terminated (fatal per Patterns §10) — UI shows "Could not securely connect" inline error
**And** on successful verification, the client performs ECDH: `sharedSecret = ECDH(myEphemeralPriv, peerEphemeralPub)` using the curve's standard X25519 operation
**And** `sharedSecret` is passed through HKDF-SHA256 to derive a 256-bit AES-GCM key (`hkdfExtract(sharedSecret) → hkdfExpand(info: "TranslatorRep-Call-v1", length: 32)` — info string locked in `/shared/canonical-names.md`)
**And** both sides derive **the same 256-bit key** (verified by integration test: two clients compute `key` and assert equality)
**And** the derived AES-GCM key is held only in memory; released at Call end alongside the ephemeral private key
**And** SafeLog emits the E2EE setup duration (`latencyMs` keyed) — typical <1 s on warm cache, <2 s cold; longer times surface `WARN_E2EE_KEY_NOT_READY` indicator (handled by Story 5.4)
**And** Firestore admin (Bania) auditing the database can confirm only public keys + signatures are stored — no symmetric keys, no shared secrets, no plaintext (verified manually with database export)
**And** unit test: a forged ephemeral pub (correct shape, wrong signature) is rejected before ECDH runs

### Story 5.3: LiveKit Insertable Streams Wiring with KeyProvider

As Bania or his girlfriend during a Call,
I want the derived AES-GCM 256-bit key (Story 5.2) fed into LiveKit's `e2eeOptions.keyProvider` so that **all** media tracks (audio, and Epic-6 video) + Data Channel messages are end-to-end encrypted between our two devices,
So that the SFU forwards ciphertext only — the operator (myself, Bania, on the Oracle VM) cannot decrypt our conversation media or captions even with full server access (NFR-Privacy: WhatsApp-equivalent).

**Acceptance Criteria:**

**Given** the AES-GCM key from Story 5.2 is available before `LiveKitRoomManager.connect(token, ...)` fires,
**When** the room is created,
**Then** Android `call/livekit/InsertableStreamsE2EE.kt` and iOS `Call/LiveKit/InsertableStreamsE2EE.swift` configure `e2eeOptions` with a custom `keyProvider` that supplies the derived AES-GCM key on demand
**And** the LiveKit SDK version pinned in scaffolding (Story 1.1 / 1.2) supports `e2eeOptions` on current versions (Architecture Gap I.7 verification — confirm at integration time)
**And** **audio tracks** are E2EE: SFU forwards encrypted RTP; manual verification of a sniffed packet shows audio payload is unintelligible without the key (Story 5.5 protocol)
**And** **Data Channel messages** (Story 4.2 caption publishes) are E2EE: SFU forwards ciphertext; receiver decrypts before decoding the schema
**And** the keyProvider does NOT export the key bytes outside its scope (no SafeLog of key bytes, no Firestore write, no logging anywhere)
**And** E2EE is wired AFTER Stories 1–4 baseline is verified working (Architecture Gap I.20: "Gate I.7 E2EE verification to Story 3" — applied here as Story 5.3 within Epic 5, AFTER Epic 4 baseline)
**And** failure mode: if LiveKit fails to initialize the keyProvider at room-create (rare; SDK incompatibility), `ERR_E2EE_KEY_EXCHANGE_FAILED` fires and the Call terminates rather than proceeding without E2EE
**And** Architecture Patterns §5 verification: `e2eeOptions` covers Data Channel on current LiveKit SDK versions — confirmed by integration test that publishes a Data Channel message, captures the wire, and verifies ciphertext
**And** there is **no fallback to non-E2EE Call** path — E2EE is mandatory for v1 per NFR-Privacy (different from "optional E2EE" patterns)

### Story 5.4: `E2EEKeyExchangeIndicator` — One-Time Visual Confirmation

As Bania or his girlfriend on the first Call after pairing (or after a re-pair),
I want a calm one-time on-screen indicator confirming that the E2EE key exchange completed successfully,
So that I have direct visual evidence the privacy promise was set up — without making subsequent Calls feel ceremonial (UX-DR24).

**Acceptance Criteria:**

**Given** the E2EE key exchange (Stories 5.1 + 5.2 + 5.3) has completed for a Call,
**When** the Call connects for the FIRST time after pairing,
**Then** Android `e2ee/ui/E2EEKeyExchangeIndicator.kt` and iOS `E2EE/UI/E2EEKeyExchangeIndicator.swift` render a brief neutral indicator in the In-Call upper region (NOT in the Caption stack) — single-line: "Calls are encrypted end-to-end" or similar plain-English copy
**And** the indicator is shown ONLY on the first Call after pairing (tracked by a local flag in EncryptedSharedPreferences / Keychain — e.g., `e2ee_indicator_shown_for_pair_id`)
**And** the indicator auto-dismisses after 3 seconds OR on user tap (whichever first)
**And** tapping the indicator shows a brief inline explanation: "Your conversation is encrypted on your device and decrypted on Bania's device. The server only carries scrambled data." (Plain language, no AI/marketing copy per UX §"Voice of product-generated text")
**And** subsequent Calls do NOT show the indicator (the privacy posture is the default, not a feature to celebrate per anti-emotion #6 "App-pride")
**And** while the key exchange is **in-progress** (Story 5.2 typical <1 s but cold-start ≤2 s), a `WARN_E2EE_KEY_NOT_READY` banner shows: "Setting up secure connection…" per Patterns §7 state-priority choreography (highest priority — Story 4.8)
**And** if key exchange **fails** (`ERR_E2EE_KEY_EXCHANGE_FAILED`), the Call terminates with "Could not securely connect" — see Story 5.2
**And** VoiceOver/TalkBack: indicator labeled "End-to-end encryption confirmed" without AI framing (UX-DR33)

### Story 5.5: Privacy Verification Protocol — Packet Capture + Manual E2E + Pipeline Review

As Bania (product owner) before declaring the privacy posture validated,
I want a documented protocol that runs (a) packet capture on the Oracle VM confirming the SFU sees only ciphertext for media + Data Channel, (b) a manual end-to-end test confirming translation never reaches the network, and (c) a pipeline review confirming no conversation content reaches Firestore writes or SafeLog,
So that the WhatsApp-equivalent privacy claim is backed by evidence — not asserted (NFR-Privacy, AR-19, Architecture Gap I.19).

**Acceptance Criteria:**

**Given** Stories 5.1–5.4 are complete and a Call is running between the two physical devices,
**When** I run the verification protocol,
**Then** `/docs/runbooks/privacy-verification.md` documents the protocol with three sections — each with a pass/fail checkbox and timestamped evidence
**And** **Section A — Packet capture (SFU side):** SSH into Oracle VM during an active Call → `tcpdump` (or LiveKit-aware capture) on the LiveKit UDP port range → for the audio track, the captured packets show RTP payloads that do NOT contain recognizable audio bytes (entropy ≈ random — verified by Shannon entropy ≥7.5 bits/byte over a sampled window) → for the Data Channel, captured packets do NOT contain recognizable JSON, source_text, or target_text strings — verified by `grep "source_text"`, `grep "target_text"`, `strings` output returning nothing recognizable
**And** **Section B — Manual end-to-end test (network egress):** Run Charles Proxy on Android dev device + Network Link Conditioner on iOS during a captioned Call → assert NO outbound HTTPS POST to any Gemini/Vertex/Anthropic/AI-provider endpoint occurs during a Plan-A (on-device) Call → assert the only network traffic is to `sfu.xaeryx.com`, `auth.xaeryx.com`, `firebase`/`firestore.googleapis.com`, and `firebaseappcheck.googleapis.com` → assert Plan-A translation calls produce NO network traffic during the translation phase (proves on-device translation per ADR-B1/B2)
**And** **Section C — Pipeline review (Firestore + SafeLog audit):** Export Firestore data during/after a Call → assert no conversation content (no fields named `source_text`, `target_text`, `caption_text`; no field VALUES that look like utterance text) — verified by `jq` or manual scan → audit SafeLog output for the Call (filtered logs by `callId`) — assert no forbidden keys (per Patterns §14 forbidden list) → assert only `AllowedLogKey` enum values present
**And** **Section D — Single-failure-domain risk acceptance** (Gap I.19): document that auth-proxy + LiveKit + Redis on one A1 VM is a single failure domain; explicitly accepted for v1 at 2-user scale; monitored via `docker logs` + journalctl (no Grafana in v1)
**And** all three sections pass before Epic 5 is closed
**And** results recorded with screen recordings of Charles Proxy + tcpdump output + Firestore export `.json` snippet (sanitized of UIDs); failures trigger a bugfix story before Epic 6 starts

---

## Epic 6: Video Calling

Paired users choose between Audio Call and Video Call from the Paired home; Video Calls show 360p partner tile with corner-overlay self-PiP; video can be muted, camera flipped; network drops gracefully pause video while keeping audio; lazy camera permission flow.

### Story 6.1: CallTypeSelector — Two-Button Audio / Video on Paired Home

As Bania or his girlfriend on the Paired home,
I want two buttons — Audio Call and Video Call — instead of the single Call button from Epic 2,
So that I choose the call type before initiating, and the `callType` claim flows through the auth-proxy + LiveKit JWT + APNs/FCM push so the receiver's native call UI displays the correct type (FR-26, UX-DR17 full).

**Acceptance Criteria:**

**Given** the single Call button from Story 2.2 is currently on the Paired home,
**When** I update the Paired home,
**Then** Android `call/ui/CallTypeSelector.kt` and iOS `Call/UI/CallTypeSelector.swift` render TWO primary action buttons side-by-side: "Audio Call" (left) and "Video Call" (right), both as filled glass pills, ≥48dp/44pt, full `text-primary` opacity per UX-DR38
**And** the partner display name remains centered above the buttons (Story 2.2 layout preserved)
**And** tapping "Audio Call" invokes `CallSession.startCall(.audio)` — unchanged behavior from Epic 2
**And** tapping "Video Call" invokes `CallSession.startCall(.video)` — full Video Call flow from this epic
**And** the `callType` enum is passed through:
  1. Auth-proxy `POST /token` request body (Story 2.1)
  2. LiveKit JWT metadata claim (Story 2.1 already wires `callType`)
  3. APNs/FCM push payload (Story 2.4 + 2.5 already include `callType`)
**And** the receiver's native CallKit/ConnectionService header reflects "Audio Call" vs "Video Call" (FR-26 / ADR-A3)
**And** mid-Call upgrade from Audio to Video is NOT supported in v1 — explicitly deferred to v2 per ADR-A3 (no in-Call "upgrade to video" affordance exists; end and re-initiate is the workaround)
**And** layout is identical-to-the-eye across both platforms per Experience Principle 5
**And** integration test: tap "Video Call" on caller → receiver's CallKit shows "Video Call" type → accept → both apps transition to Video In-Call screen (Story 6.8)

### Story 6.2: Camera Permission — Lazy Flow

As Bania or his girlfriend tapping Video Call for the first time (or accepting an incoming Video Call for the first time),
I want the camera permission to be requested at that moment — not on first app launch — and have a calm recovery path on denial,
So that camera permission is requested only when actually needed and denial doesn't trap me in a broken state (FR-30, UX-DR22).

**Acceptance Criteria:**

**Given** camera permission has not been granted previously on this device,
**When** I tap "Video Call" on the Paired home (caller) OR I tap Accept on an incoming Video Call (recipient),
**Then** the system camera-permission prompt appears (`CAMERA` Android permission / `NSCameraUsageDescription` iOS) — purpose string copy follows UX §"Voice of product-generated text" (plain, kind: "TranslatorRep uses your camera for video calls")
**And** on grant, the Video Call proceeds normally (Story 6.3+)
**And** on denial, Android `settings/ui/CameraPermissionFlow.kt` and iOS `Settings/UI/CameraPermissionFlow.swift` render: a calm explanation ("Video calls need camera access. You can change this in Settings.") + a "Open Settings" button that deep-links to the OS camera-permission settings page
**And** the user can dismiss the explanation and return to the Paired home (caller side) or to the Audio Call state (recipient side — Video Call becomes an Audio Call if recipient denies camera, audio still works) — explicit fallback path
**And** subsequent Video Call attempts re-check the permission state at every Video Call entry; cached locally; checked at every Video Call start (ADR-E3)
**And** permission is NEVER requested on first app launch (lazy — ADR-E3 explicit rule)
**And** integration test: install fresh app, never trigger Video Call → camera permission prompt never appears → tap Video Call → prompt appears → deny → calm flow renders → tap "Open Settings" → OS settings page opens

### Story 6.3: Local Video Capture + Self-View PiP

As Bania or his girlfriend during a Video Call,
I want my own camera feed captured and published to LiveKit, AND a small corner-overlay preview of my own video so I know what my partner is seeing of me,
So that the Video Call has bidirectional video and I can frame myself before/during the conversation (FR-27 caller side).

**Acceptance Criteria:**

**Given** camera permission is granted (Story 6.2),
**When** my Video Call session starts,
**Then** Android `call/videoTracks/VideoCapture.kt` and iOS `Call/VideoTracks/VideoCapture.swift` capture from the default camera (front camera at Call start) at 360p × 30fps (ADR-A4 — well under Oracle 10 TB/mo egress cap)
**And** the captured video track is published via `LocalParticipant.publishVideoTrack` with LiveKit default codec (VP9 / H.264 negotiated)
**And** the video track is encrypted via the same `e2eeOptions.keyProvider` from Story 5.3 (verified by Story 5.5 Section A packet capture extended to video RTP)
**And** a self-view PiP renders in the upper region of the Video In-Call screen — corner-overlay (bottom-right of the upper 50% region), small (~120×160 dp/pt), draggable to other corners (optional v1 — locked to bottom-right is acceptable)
**And** the self-view uses the LOCAL video track directly (no round-trip through SFU — keeps latency at zero on my own preview)
**And** when I mute video via VideoCallControlRow (Story 6.7), the local self-view shows `VideoMutedTile` (Story 6.6) and the local track stops publishing
**And** when I un-mute, the local track resumes publishing and the self-view shows live capture again
**And** flip-camera control (front ↔ back) — also wired via Story 6.7 — swaps the capture source within ~500 ms with smooth transition

### Story 6.4: VideoTile — Render Partner's Video Stream

As Bania or his girlfriend during a Video Call,
I want my partner's video to render in the upper region of the In-Call screen as a calm `VideoTile`,
So that I can see them while the captioned conversation happens in the lower stack (FR-27 receive side, UX-DR21 VideoTile basic).

**Acceptance Criteria:**

**Given** my partner has published their video track via Story 6.3 (with E2EE per Story 5.3),
**When** my client subscribes to their remote video track,
**Then** Android `call/videoTracks/ui/VideoTile.kt` and iOS `Call/VideoTracks/VideoTile.swift` render the remote video stream filling the upper 50% region (`AspectFill` / `ContentMode.scaleAspectFill`; clipped to bounds)
**And** the LiveKit video stream renderer is wired identically across platforms (per Experience Principle 5 — same anchor, same aspect handling)
**And** the partner's display name is overlaid in the top-left corner of the VideoTile at `text-primary` opacity (or hidden behind the safe-area corner if it conflicts visually — UX decision at integration time)
**And** the partner's `AudioLevelIndicator` continues rendering (from Story 2.7) but its visual prominence is reduced (the video provides the speaking signal)
**And** the mic-active dot (state-red, pulsing) remains visible to signal mic activity even when video is the primary surface
**And** the Caption stack remains in the lower 50% (UX-DR16 video variant — handled by Story 6.8)
**And** Video tile RENDERS the decrypted media per Story 5.3 keyProvider — verified by integration test that the rendered pixels are NOT decryptable from a man-in-the-middle SFU packet capture
**And** when the partner mutes their camera, VideoTile transitions to `VideoMutedTile` (Story 6.6)
**And** when the partner's video track drops or pauses, transitions to `VideoPausedTile` (Story 6.5)

### Story 6.5: VideoPausedTile — Network-Drop / Failure UX

As Bania or his girlfriend during a Video Call when network conditions degrade,
I want my partner's `VideoTile` to transition to a calm `VideoPausedTile` (neutral grey, NOT amber — disambiguates from translation failure) with auto-retry and a manual retry button, while audio continues uninterrupted,
So that video drops feel resilient rather than alarming (FR-27 failure UX, UX-DR21).

**Acceptance Criteria:**

**Given** a Video Call is active and the partner's video track suspends (LiveKit `participantTrackUnpublished` or local quality-degradation downgrade),
**When** the suspension fires,
**Then** Android `call/videoTracks/ui/VideoPausedTile.kt` and iOS `Call/VideoTracks/VideoPausedTile.swift` render in the upper region: **neutral grey surface (NOT state-amber)** + calm copy "Video paused — retrying" + a small auto-retry spinner (subtle, not alarming) + a manual "Retry now" button (ADR-A4)
**And** the partner display name initial is centered on the dark surface (e.g., "B" for Bania) at `text-primary` opacity
**And** the audio track CONTINUES uninterrupted — confirmed by audio still flowing during the paused state (FR-27 audio-continues requirement)
**And** auto-retry fires every 5 seconds via LiveKit's reconnection mechanism (or manual `subscribeToVideo` retry)
**And** captions continue rendering in the lower stack — the video drop does NOT affect translation flow (NFR-Reliability)
**And** the `videoPaused` state is logged via SafeLog with `videoPauseDurationBucket` (privacy-safe per Patterns §14)
**And** the failure-state taxonomy fires `videoPaused` per Patterns §7 — state-priority choreography (Story 4.8) ensures this does NOT replace higher-priority banners like `networkDropped` or `e2eeKeyNotReady`
**And** error code `WARN_VIDEO_TRACK_SUSPENDED` is registered in `/shared/error-codes.md` (already in Story 1.7 / 4.8 lists)
**And** integration test: simulate poor network with Network Link Conditioner → VideoPausedTile appears + audio still flows + auto-retry restores video when network recovers

### Story 6.6: VideoMutedTile — Partner Muted Their Camera Intentionally

As Bania or his girlfriend during a Video Call when my partner mutes their camera,
I want my partner's `VideoTile` to transition to a `VideoMutedTile` showing their display-name initial on a neutral surface,
So that I understand they intentionally turned off video (vs network failure), and audio continues uninterrupted (UX-DR21).

**Acceptance Criteria:**

**Given** a Video Call is active and the partner mutes their camera via their `VideoCallControlRow` (Story 6.7),
**When** their mute is signaled (LiveKit track-muted event),
**Then** Android `call/videoTracks/ui/VideoMutedTile.kt` and iOS `Call/VideoTracks/VideoMutedTile.swift` render in the upper region: dark neutral surface (matches Theme A `surface-base`) + partner display-name initial centered (large, `text-primary` opacity)
**And** the visual is distinct from `VideoPausedTile` (Story 6.5): no spinner, no "retrying" copy, no retry button — this is intentional, not failed
**And** audio CONTINUES uninterrupted (same as Story 6.5)
**And** captions continue rendering in the lower stack
**And** when the partner un-mutes, `VideoMutedTile` transitions back to live `VideoTile` (Story 6.4) within 500 ms
**And** the local self-view (Story 6.3) is also a `VideoMutedTile` variant when I mute my own video — same component, rendered with my own initial
**And** no failure code logged (this is NOT a failure path; it's user-intentional)
**And** integration test: partner taps mute-video → my screen shows `VideoMutedTile` → partner un-mutes → my screen shows live `VideoTile`

### Story 6.7: VideoCallControlRow — 5 Controls in Single Row

As Bania or his girlfriend during a Video Call,
I want a single control row with five buttons — mute audio, mute video, flip camera, audio-routing toggle, end-Call,
So that all in-Call actions are at thumb reach in the upper region without overflow (UX-DR19, ADR-E4).

**Acceptance Criteria:**

**Given** I am in an active Video Call,
**When** the upper region of the In-Call screen renders,
**Then** Android `call/ui/VideoCallControlRow.kt` and iOS `Call/UI/VideoCallControlRow.swift` render five controls in a single horizontal row positioned at the bottom edge of the upper 50% region:
  1. **Mute audio** (toggle): icon-only glass pill, state-driven label ("Mute microphone" / "Unmute microphone"); reuses logic from Story 2.7 `AudioCallControlRow`
  2. **Mute video** (toggle): icon-only glass pill, state-driven label ("Mute video" / "Unmute video"); fires camera off → local capture stops → `VideoMutedTile` renders locally + remote sees `VideoMutedTile` (Story 6.6)
  3. **Flip camera** (action): icon-only glass pill (camera with arrow icon); on tap, swaps `front ↔ back` capture source within ~500 ms with smooth transition (Story 6.3)
  4. **Audio-routing toggle** (Earpiece / Speaker / Bluetooth-when-connected): reuses Story 2.9 `AudioRoutingToggle`
  5. **End-Call** (destructive): state-red background glass pill (the one place state-red is used as fill per UX-DR38); same end logic as Story 2.8 including >5 min two-tap confirm
**And** all five controls fit at standard phone widths without overflow (verified on smallest target device — iPhone SE / smaller Android — at integration time)
**And** when video is muted, the "Flip camera" button is greyed-out (disabled, not hidden — maintains layout stability)
**And** the row uses `MonochromeGlassPanel.thick` background per UX §"Backdrop blur intensity"
**And** each button has 44pt iOS / 48dp Android minimum touch target (WCAG AAA)
**And** VoiceOver/TalkBack labels per UX-DR33

### Story 6.8: Video 50/50 In-Call Layout

As Bania or his girlfriend in an active Video Call,
I want the In-Call screen to render with a 50/50 vertical split — partner `VideoTile` + local self-view PiP + `VideoCallControlRow` in the upper 50%, Caption stack in the lower 50%,
So that I can see them, see my own framing, control the Call, AND read captions all in one calm layout (UX-DR16 video variant, ADR-E1).

**Acceptance Criteria:**

**Given** the Audio Call In-Call screen from Story 2.7 uses a 40/60 split,
**When** a Video Call is active,
**Then** the In-Call screen renders the **50/50 split** layout (UX-DR16 video variant):
  - **Upper 50%**: partner's `VideoTile` (fills the region; aspect-fill clipped) + corner-overlay self-PiP (~120×160 dp/pt, bottom-right) + partner display name overlay + mic-active dot + audio level + `VideoCallControlRow` at the bottom edge of the upper region (Story 6.7)
  - **Lower 50%**: `CaptionStack` (from Epic 4) — same component, just shorter vertical span; auto-scroll + JumpToLatestPill still work; row-anatomy unchanged
**And** the layout is the explicit reject of "backdrop-video-with-captions-overlay" (ADR-E1 explicit reject — violates UX anti-emotion #5 translation theater)
**And** the layout adapts when video drops/pauses/mutes (Stories 6.5 + 6.6 fill the upper region with the appropriate Tile component)
**And** back-gesture navigation remains suppressed (you don't accidentally exit a Video Call by swiping back)
**And** identical-to-the-eye across both platforms (Experience Principle 5) — element positions, region sizes, anchor points
**And** screen rotation: portrait is primary; landscape support is acceptable v1 stretch (per UX §"Platform Strategy") — at minimum, portrait must work flawlessly; landscape can defer behavior tuning to v1.1
**And** Theme C × Video interaction (image bg replaced by VideoTile in upper region) is deferred to Epic 8 (Theme C is delivered there); in Epic 6, only Theme A (Dark) is active so no Theme C × Video conflict arises here

---

## Epic 7: Leave-and-Rejoin Resilience

When either side ends, the room survives for 5 minutes: remaining side stays in-Call with quiet "Partner left — Rejoin" overlay (captions continue); leaver gets a persistent local notification to rejoin; transient drops auto-recover without user action; after 5 min empty, room cleanly destroys.

### Story 7.1: LiveKit `empty_timeout: 300` Config + Room-Destroy Hook

As a solo developer wiring the lifecycle that makes leave-and-rejoin possible,
I want `livekit.yaml` configured with `empty_timeout: 300` and a room-destroy hook that writes `/calls/{callId}.endedAt` when the room finally destroys,
So that the server-side primitive (5-min grace window) is in place and both clients can observe the canonical "room destroyed" event (FR-32 backend, ADR-A6).

**Acceptance Criteria:**

**Given** `livekit.yaml` from Story 1.3 currently sets `empty_timeout: 300` (already configured in scaffolding),
**When** I add the room-destroy hook,
**Then** the auth-proxy (Story 2.1) exposes a LiveKit webhook endpoint (or LiveKit server-side hook) that fires on `room_finished` events
**And** on `room_finished`, the hook writes `endedAt` (Firestore Timestamp) to `/calls/{callId}` along with `endReason: "empty_timeout" | "all_left" | "fatal"`
**And** both clients have Firestore listeners on `/calls/{callId}.endedAt` — when set, they treat the Call as canonically over
**And** the 300-second timer starts from the moment the room becomes empty (not from when first participant leaves) — ADR-A6 explicit clarification
**And** if a participant rejoins within the 300-second window, the timer resets (room is no longer empty)
**And** if no one rejoins within 300 s, the room destroys; the hook fires; both clients return to Paired home cleanly
**And** auth-proxy logs the room-destroy event via SafeLog (`callId`, `errorCode` if applicable, `callDurationMs`) per Patterns §14 — no conversation content
**And** integration test: two clients connect → both disconnect → wait 6 minutes → verify `/calls/{callId}.endedAt` is set + `endReason: "empty_timeout"`

### Story 7.2: LeaveAndRejoinManager — Client-Side State Machine

As a solo developer wiring the client-side lifecycle,
I want a `LeaveAndRejoinManager` on both platforms that owns `RoomState` transitions (`active` ↔ `waitingForPartner` → `ended`) and observes LiveKit room events plus Firestore `endedAt` to decide what state to be in,
So that the leave-and-rejoin behavior is centralized, testable, and identical-to-the-eye across iOS/Android (FR-32 client orchestration, AR-22 `waitingForPartner` wired).

**Acceptance Criteria:**

**Given** `CallSession` and `RoomState` enum (`active`, `waitingForPartner`, `ended`) exist from Story 2.2,
**When** I add the manager,
**Then** Android `call/callSession/LeaveAndRejoinManager.kt` and iOS `Call/CallSession/LeaveAndRejoinManager.swift` expose `currentState: StateFlow<RoomState>` / `@Observable var currentState: RoomState` and observe:
  - LiveKit `room.remoteParticipants` changes (partner joined / left)
  - Firestore `/calls/{callId}.endedAt` changes (canonical room destroyed)
  - Local LiveKit connection state (connected / reconnecting / failed)
**And** state transitions follow this finite state machine:
  - `active` + partner-disconnect → `waitingForPartner`
  - `waitingForPartner` + partner-reconnect → `active`
  - `waitingForPartner` + `endedAt` set in Firestore → `ended`
  - `active` + local end-Call → `ended` (this side); peer side transitions to `waitingForPartner`
  - `*` + `ERR_LIVEKIT_ROOM_FAILED` → `ended` (fatal)
**And** the state-derivation rules from `/shared/state-derivation.md` (Story 1.7 / Gap I.13) are honored: RoomState transitions are client-derived from `room.remoteParticipants`, NOT server-pushed
**And** the manager is unit-tested against simulated LiveKit + Firestore event streams (no real network) — verifies all state transitions
**And** the manager is the SOLE owner of these transitions — UI observes `currentState` flow, never mutates directly (Architecture Patterns §13)

### Story 7.3: CallWaitingForPartnerState — Overlay on Remaining Side

As Bania or his girlfriend remaining in a Call after my partner leaves,
I want a calm overlay/banner saying "Partner left — Rejoin" with captions continuing to render (mine still appear; theirs stop until they rejoin),
So that I understand the situation without it feeling like the Call ended (UX-DR25, FR-32 remaining-side UI).

**Acceptance Criteria:**

**Given** my `LeaveAndRejoinManager` transitions to `waitingForPartner` (Story 7.2),
**When** the In-Call screen reflects the new state,
**Then** Android `call/ui/CallWaitingForPartnerState.kt` and iOS `Call/UI/CallWaitingForPartnerState.swift` render an overlay/banner in the In-Call upper region: calm grey surface + "Partner left — they can still rejoin" + a small countdown ("Reconnect window: 4:32") that ticks down (5-min `empty_timeout` from Story 7.1)
**And** the state-priority choreography from Story 4.8 places `waitingForPartner` at priority 3 (above `networkDropped`, below `modelLoading`)
**And** my captions continue rendering normally (FR-16 — my own translations still appear locally)
**And** my partner's caption-stack rows are not added during this state (they're not speaking; nothing to render)
**And** my own utterances are NOT queued for the leaver — they happen as I speak but the leaver receives nothing while disconnected (FR-32 explicit "remaining person's utterances are NOT queued for the leaver" — ADR-A6)
**And** my Caption stack history is preserved (the rows from before they left are still scrollable)
**And** if partner rejoins (Story 7.5), the overlay dismisses smoothly and `RoomState` returns to `active`
**And** the overlay has a "Hang up" action (small destructive button) that lets me end the Call before the timer expires — bypasses the 5-min wait, transitions to `ended`
**And** the overlay copy follows UX §"Voice of product-generated text": plain, kind, not apologetic-corporate ("Partner left" not "Connection lost — partner has disconnected from the call")

### Story 7.4: RejoinNotification — Local Notification on Leaver Side

As Bania or his girlfriend who just ended my side of a Call (intentional end-Call tap),
I want a persistent local notification saying "[Partner] is still in the call — Rejoin" with a tap action that returns me to the Call,
So that I realize the partner is still there and can rejoin without a CallKit re-ring (which would feel like a new incoming Call) (UX-DR26, FR-32 leaver-side notification).

**Acceptance Criteria:**

**Given** I tapped end-Call from the In-Call screen (Story 2.8) while my partner was still in the Call,
**When** my `RoomState` transitions to `ended-locally` AND the partner has NOT yet left,
**Then** a local notification (NOT a push notification, NOT a CallKit re-ring) fires on my device:
  - Title: "TranslatorRep" + "[Partner display name] is still in the call"
  - Body: "Tap to rejoin"
  - Persistent (not auto-dismissing); high priority on Android; UNUserNotificationCenter on iOS
**And** on Android, the notification uses `NotificationCompat.PRIORITY_HIGH` + `setOngoing(true)` (persistent until user dismisses or taps)
**And** on iOS, the notification uses `UNMutableNotificationContent` with `interruptionLevel: .active`
**And** **explicitly NOT a CallKit re-ring** — that would feel like a new incoming Call from my partner, which is wrong (per ADR-A6 explicit rule)
**And** tapping the notification triggers the rejoin flow (Story 7.5) — opens the app + auto-rejoins the same room
**And** the notification is CLEARED when any of:
  - I tap the notification (rejoin fires)
  - My partner leaves the Call (room destroys, `endedAt` set, both sides know it's over)
  - 5-min timeout fires (`empty_timeout` from Story 7.1)
**And** if I'm in the app foreground when I end my side, a Paired-home banner shows the same "Call still active — Rejoin" message in addition to the notification (UX-DR26 + ADR-A6)
**And** integration test: end-Call → notification fires → partner unrelated taps end-Call → notification clears within seconds via Firestore listener

### Story 7.5: Rejoin Flow — Manual + Transient Auto-Rejoin

As Bania or his girlfriend who left a Call within the 5-min window,
I want a single tap (on the notification or banner) to return me to the same Call AND have transient network drops (ICE failure, brief backgrounding) auto-rejoin without my involvement,
So that returning to a Call is one action, and short interruptions feel like they didn't happen (FR-32 rejoin flow).

**Acceptance Criteria:**

**Given** I left a Call and the room is still alive (Story 7.1's 5-min window) OR my LiveKit connection dropped transiently,
**When** I tap RejoinNotification (manual) OR the LiveKit SDK reports a transient disconnect (auto),
**Then** my `LeaveAndRejoinManager.rejoin()` fires:
  1. Request a fresh LiveKit JWT from the auth-proxy (Story 2.1) for the same `roomName`
  2. Re-perform E2EE key exchange (Stories 5.1 + 5.2) — fresh ephemeral keypair, sign + publish, derive new AES-GCM key (cannot reuse the prior Call's key after a manual leave; for transient drops within seconds, the prior key may still be valid per LiveKit reconnect semantics — implementer's choice with explicit decision recorded)
  3. Connect to the same room with `e2eeOptions` re-wired
**And** on success, my `RoomState` transitions to `active`; partner sees me reappear in `room.remoteParticipants` and their `CallWaitingForPartnerState` (Story 7.3) dismisses
**And** on failure (room destroyed, network failed, app-check rejected), my `RoomState` transitions to `ended` and I return to Paired home with inline message "Call ended"
**And** **transient drops** (network blip <5 s, brief app backgrounding <30 s) auto-rejoin without user action — implemented via LiveKit's built-in reconnect mechanism + my `LeaveAndRejoinManager` observation
**And** the partner experiences this as: their `RoomState` briefly transitions to `waitingForPartner` for a few seconds (or doesn't transition at all if very brief) → back to `active`
**And** SafeLog emits `roomState` transition events with `latencyMs` for the rejoin operation
**And** my Caption stack history is **lost** on manual rejoin (new local session); transient auto-rejoin within seconds preserves it (implementer's choice — documented in `/docs/runbooks/`)
**And** integration test: manually end-Call → tap RejoinNotification → land back in same Call within 5 s → partner sees me rejoin

### Story 7.6: After-5-Min Timeout — Clean Room Destroy + Both Sides Return Home

As Bania or his girlfriend when the 5-min `empty_timeout` fires because nobody rejoined,
I want both apps to cleanly observe the room-destroy event and return to the Paired home, with the rejoin notification cleared and all per-Call resources released,
So that there are no zombie states or orphaned notifications (FR-32 timeout cleanup, ADR-A6).

**Acceptance Criteria:**

**Given** a Call was active, one or both sides left, and 5 min passed without rejoin,
**When** LiveKit's `empty_timeout: 300` fires server-side (Story 7.1),
**Then** the auth-proxy webhook (Story 7.1) writes `/calls/{callId}.endedAt` + `endReason: "empty_timeout"` to Firestore
**And** both apps' Firestore listeners observe `endedAt` being set → `LeaveAndRejoinManager` (Story 7.2) transitions `RoomState` to `ended`
**And** the remaining side (if still in-Call): `CallWaitingForPartnerState` overlay (Story 7.3) dismisses; native call UI ends via CallKit/ConnectionService; app returns to Paired home with inline message "Call ended"
**And** the leaver side (if not already returned): `RejoinNotification` (Story 7.4) is cleared; app returns to Paired home if it was open
**And** per-Call resources are released: ephemeral X25519 private keys zeroed (Story 5.1), AES-GCM derived keys released (Story 5.2), LiveKit room handles disposed, audio/video tracks unpublished, Caption stack cleared (unless transcript history opt-in — Epic 8)
**And** Firestore: `/calls/{callId}/ephemeralPub/*` documents are cleaned up by an auth-proxy garbage-collection sweep (or Firestore TTL policy on the `createdAt` field — implementer's choice)
**And** SafeLog emits a single `callDurationMs` summary event for the Call (privacy-safe — no content)
**And** integration test: simulate 5-min idle empty room → both apps return cleanly + no orphan notifications + no leaked memory (verified by Android Profiler / Xcode Instruments)

### Story 7.7: End-to-End Leave-and-Rejoin Validation

As Bania (product owner) before declaring Epic 7 done,
I want a documented test pass covering the four leave-and-rejoin scenarios on real devices,
So that the resilience feature is validated against actual network and lifecycle conditions, not just unit tests (FR-32 acceptance).

**Acceptance Criteria:**

**Given** Stories 7.1–7.6 are complete and a Call works end-to-end (Audio + Video + E2EE + captions),
**When** I run the validation,
**Then** `/docs/runbooks/leave-and-rejoin-validation.md` documents four scenarios with pass/fail checkboxes:
**And** **Scenario A — Manual leave + manual rejoin within window:** I tap end-Call → partner sees `CallWaitingForPartnerState` + countdown → I see `RejoinNotification` → I tap notification within 2 min → I rejoin → partner sees me return + overlay dismisses → conversation continues
**And** **Scenario B — Manual leave + 5-min timeout (no rejoin):** I tap end-Call → partner waits 5 min → both apps return to Paired home cleanly → `RejoinNotification` cleared → `/calls/{callId}.endedAt` set in Firestore
**And** **Scenario C — Transient network drop (network blip):** I toggle Airplane mode on for ~10 s then off → my client auto-reconnects via LiveKit reconnect → my `RoomState` briefly went `waitingForPartner` (visible to partner) → returned to `active` without user action → conversation continues
**And** **Scenario D — Both sides leave simultaneously:** both tap end-Call within 1 s of each other → room destroys immediately (empty + no rejoin) → both apps return to Paired home → no notifications fire on either side
**And** **Edge case: app-killed-while-rejoinable:** I force-quit the app while in `ended-locally + RejoinNotification-pending` state → app reopens within 5 min → `RejoinNotification` still active → tap → rejoin succeeds
**And** results recorded with screen recordings of both phones synchronized; failures trigger a bugfix story before Epic 8 starts

---

## Epic 8: Personalization, Settings & Post-Call Surfaces

Each user personalizes their app (theme: Dark or Custom image background; display name; transcript history opt-in; privacy summary; Crashlytics opt-in). Bania has a private per-Caption quality-review tool post-Call; she has a single one-tap reaction; aggregated counts surface to him only (never per-utterance). Post-editor toggle status finalized.

### Story 8.1: Theme C Tokens + 2-Option ThemePicker

As Bania or his girlfriend wanting to personalize how the app looks,
I want a Settings option to switch between Theme A (Dark, default) and Theme C (Custom image background), with the chosen theme persisting per-device and applying app-wide immediately,
So that the app feels like ours rather than generic — each on our own phone, no sync (UX-DR4, ADR-E2).

**Acceptance Criteria:**

**Given** Theme A (Dark) tokens are already wired from Story 1.1 / 1.2 and Theme B was dropped per Architecture S3 + UX reconciliation (Story 1.14),
**When** I add the theme system,
**Then** Android `ui/components/theme/ThemeTokens.kt` defines two complete token sets: Theme A Dark (existing) + Theme C ImageBackground (image-bg layer + adaptive overlay tokens per UX §"Color System")
**And** iOS `UI/Components/Theme/ThemeTokens.swift` mirrors the same two token sets identically
**And** Android `settings/ui/ThemePicker.kt` and iOS `Settings/UI/ThemePicker.swift` render two radio rows in a Settings list:
  1. "Dark (default)" — Theme A
  2. "Custom background image" — Theme C; tapping this opens `BackgroundImagePicker` (Story 8.2)
**And** each row shows a small swatch preview (~32 dp/pt thumbnail) on its left edge
**And** tapping a row applies the theme app-wide immediately (no "Apply" button) — `setDefaultNightMode` Android and `.preferredColorScheme` iOS observe a local-Settings-backed value rather than the OS preference
**And** the chosen theme persists per-device (EncryptedSharedPreferences Android / UserDefaults iOS); themes are NEVER synced between paired devices (UX-DR3 per-device)
**And** system theme is IGNORED (the app does not follow OS preferences)
**And** integration test: switch from A to C → all screens (Paired-Empty, Paired, In-Call, Settings) re-render with Theme C tokens within 200 ms

### Story 8.2: BackgroundImagePicker + Sandbox Storage

As Bania or his girlfriend who has selected the Custom-image theme,
I want to pick a photo from my device gallery and have it stored in the app sandbox as my background,
So that the app's chrome sits over a photo that means something to me (UX-DR30, Theme C selection).

**Acceptance Criteria:**

**Given** Theme C is selected in `ThemePicker` (Story 8.1) and no image is set yet,
**When** the BackgroundImagePicker renders in Settings,
**Then** Android `settings/ui/BackgroundImagePicker.kt` and iOS `Settings/UI/BackgroundImagePicker.swift` render the picker affordance with: thumbnail of current image (placeholder when none set) + "Choose a photo" button + hint text "Stored on this device only" at Footnote size in `text-tertiary` opacity
**And** tapping "Choose a photo" opens the native photo picker: `PHPickerViewController` on iOS / `PickVisualMedia` on Android (no full Photos / Storage permission needed — uses limited-access photo picker per current platform conventions)
**And** the selected image is downscaled (~1080p max) and copied to the app sandbox: `Application Support` (iOS, with `NSFileProtectionComplete` — see Architecture ADR-D1) / private app file under app data dir (Android, with file path stored in EncryptedSharedPreferences)
**And** image storage path is referenced in Theme C tokens (Story 8.1) — the renderer reads the path and decodes the image as the `surface-base` layer
**And** auto-cropped to fit screen (object-cover) with safe-area respected; default position center crop
**And** only ONE image at a time per device (replacing it removes the prior file)
**And** "Change" / "Remove" affordances appear after an image is set; Remove returns to no-image + switches Theme back to Dark (default) if user was on Image theme
**And** files are never synced to Firestore or iCloud / Google Backup (NSFileProtectionComplete + manual exclusion from iOS device backup; Android `noBackupAttribute`) — UX-DR3 "Local-only storage" rule
**And** if the picker fails (rare; OS-level), inline error renders below the picker: "Couldn't load that photo. Try another."

### Story 8.3: BackgroundImageOverlay — Adaptive 0.40-0.55 Dark Tint

As Bania or his girlfriend with Theme C active,
I want a dark overlay (default 0.40 opacity, optionally adaptive up to 0.55) sitting between the background image and every glass panel + caption row,
So that Caption text remains legible (WCAG AA 4.5:1+ contrast) regardless of the photo's content (UX-DR3 overlay layer).

**Acceptance Criteria:**

**Given** a background image is set (Story 8.2) and Theme C is active,
**When** any screen renders with Theme C,
**Then** Android `ui/components/BackgroundImageOverlay.kt` and iOS `UI/Components/BackgroundImageOverlay.swift` render a full-screen dark overlay between the background image layer and all UI elements
**And** the overlay opacity defaults to **0.40** (fixed for v1 acceptable per UX §"Adaptive overlay implementation note")
**And** an OPTIONAL adaptive mode samples the average luminance of the screen region behind the Caption stack and adjusts opacity from 0.40 (dark images) to 0.55 (bright images) — defer adaptive implementation if testing shows fixed 0.40 is sufficient
**And** the overlay is ONLY active when Theme C is selected — Theme A renders without an overlay (its `surface-base` is `#0A0A0B`)
**And** glass panel blur intensity uses `.thickMaterial` (iOS) / `RenderEffect 24f` (Android) UNIFORMLY across all panels under Theme C — UX §"Backdrop blur intensity" rule — to prevent image detail from bleeding through into glass panels
**And** text foreground tokens follow Theme A (white-on-overlay) since the overlay reads dark enough for AA-contrast white text per UX §"Color System"
**And** integration test: load a high-luminance test image (white field) → measure rendered text contrast in CaptionStack → assert ≥4.5:1 against the overlay+image composite (WCAG AA)

### Story 8.4: Theme C × Video Interaction

As Bania or his girlfriend with Theme C active and in a Video Call,
I want the partner's `VideoTile` to REPLACE the custom image in the upper region of the In-Call screen while the lower Caption stack region KEEPS the custom-image background with the adaptive overlay,
So that I see them clearly during video calls AND the captioned conversation still feels personalized (UX-DR3 × UX-DR16 interaction, ADR-E2).

**Acceptance Criteria:**

**Given** Theme C is selected (Story 8.1), a background image is set (Story 8.2), and I'm in a Video Call (Epic 6),
**When** the In-Call screen renders,
**Then** the **upper 50% region** (UX-DR16 video variant) shows the partner's `VideoTile` (Story 6.4) at full aspect-fill — the custom image is HIDDEN behind the video in this region (no double-layering)
**And** the **lower 50% region** (Caption stack) shows the custom-image background WITH the `BackgroundImageOverlay` (Story 8.3) applied — captions remain legible
**And** when video pauses to `VideoPausedTile` (Story 6.5) or mutes to `VideoMutedTile` (Story 6.6), those tiles render in the upper region (neutral grey surface) — they REPLACE the video but do not reveal the custom image (the tiles cover the full upper region per their design)
**And** at Audio Call time (no video), the entire screen uses the custom image + overlay normally — no transition needed
**And** there is NO setting to "disable Theme C during Video Calls" — the interaction is automatic (ADR-E2 "no setting required; smooth handoff at video track activation")
**And** the adaptive overlay (Story 8.3 — 0.40 → 0.55) applies ONLY to the Caption-stack region under Theme C + Video Call (not the upper region which is video)
**And** integration test: switch to Theme C with photo → start Video Call → upper region shows video, lower region shows image+overlay+captions → pause video → upper region shows VideoPausedTile (covers image) → end Call → return to Paired home with full-screen custom image

### Story 8.5: Display Name Setting

As Bania or his girlfriend wanting to personalize how my partner sees me on their screen,
I want a Settings field where I can set a custom display name (default "Partner") that appears on my partner's incoming-call UI + Paired home + Caption stack,
So that my partner sees my actual name rather than the generic placeholder (FR-23).

**Acceptance Criteria:**

**Given** the Settings sheet from Story 1.13 exists with placeholder rows for future items,
**When** I add the display-name row,
**Then** the Settings sheet contains a "Your name" row with a `TextField` showing current value (or empty if never set)
**And** the field accepts up to 30 characters; native keyboard; auto-trim on save
**And** the value persists to `/users/{my-uid}/displayName` in Firestore on blur or "Done" tap (no Save button per UX-DR37 "no Save button on Settings")
**And** my partner's `PairingFirestoreRepository` listener observes the field change and updates their local cache + UI within seconds
**And** my partner's incoming-call UI on the NEXT Call shows my new name (FR-23 — "appears on the partner's screen on the next Call")
**And** default behavior unchanged: when never set, my partner sees "Partner" everywhere (FR-23 default)
**And** the user is NEVER asked to set a display name during pairing (FR-23 "never asks during Pairing")
**And** integration test: set name on Bania's device → his girlfriend's app updates within 5 s → next Call shows new name in CallKit + Paired home

### Story 8.6: Transcript History Opt-In + Local Encrypted Storage

As Bania or his girlfriend wanting to revisit captions from past Calls,
I want a Settings toggle (default OFF) that, when ON, saves all finalized Captions from each Call to local-only encrypted storage on my device, with a per-Call view + delete option,
So that I can re-read past conversations on my own phone without ever syncing the content to a server (FR-21).

**Acceptance Criteria:**

**Given** Captions exist in `CallSession.captionState` during a Call (Epic 4),
**When** the Settings sheet renders the toggle,
**Then** a "Save transcript history" toggle (`Switch` Android / `Toggle` iOS) is present in Settings — **default OFF** (FR-21)
**And** the toggle's helper text reads: "Saves captions from each call on this device only. Never synced." (Footnote size, `text-secondary` opacity)
**And** when toggle is ON, on Call-end (Story 2.8) all finalized Captions are persisted:
  - Android: Room database `TranslatorRepDatabase` (Architecture ADR-D1) with SQLCipher; encryption key in EncryptedSharedPreferences (`security-crypto`)
  - iOS: SwiftData store in `Application Support` with `NSFileProtectionComplete`
**And** schema (both platforms — Architecture Addendum): `CallRecord { id (ULID), startTimestamp, endTimestamp, callType }` + `CaptionRecord { callId, utteranceId, sourceLang, sourceText, targetLang, targetText, timestamp }`
**And** when toggle is OFF, no captions persist beyond the active Call (FR-21)
**And** transcript history is NEVER synced to a server or iCloud / Google Backup — verified by network packet inspection during a Call-end with toggle ON
**And** a "View transcript history" entry surfaces in Settings (visible only when toggle is ON OR when prior history exists); tapping opens a list of Calls with timestamp + duration; tapping a Call opens its Caption stack as static read-only history
**And** per-Call delete: long-press a Call in the history list → "Delete this transcript" with confirmation; deletion is irreversible
**And** turning the toggle OFF does NOT delete existing history — user must explicitly delete via the per-Call mechanism (UX decision: less destructive)
**And** integration test: toggle ON → complete a Call → kill app → reopen → view history → see Call → tap → see Captions identical to in-Call render → delete → confirm gone

### Story 8.7: Translation Post-Editor Toggle — Status Resolved

As Bania (product owner) when finalizing FR-22 for v1,
I want a decision on whether the optional translation post-editor toggle ships in v1 (and how) based on the Plan A/Plan B outcome from Story 3.9,
So that the Settings UI either includes the working toggle or explicitly defers it to v1.1 — no half-finished state (FR-22 resolution).

**Acceptance Criteria:**

**Given** Story 3.9 locked either Plan A (on-device) or Plan B (Vertex AI Gemini),
**When** I evaluate FR-22 status,
**Then** ONE of three outcomes is documented in `/docs/runbooks/fr-22-status.md`:
  - **Outcome A — Plan A locked AND post-editor deferred:** Settings sheet does NOT show the toggle; PRD §4.5 / FR-22 marked "deferred to v1.1" in the reconciled PRD (Story 1.14); rationale documented (e.g., "on-device second-pass is high-cost battery-wise; reflow value uncertain without conversational evidence")
  - **Outcome B — Plan B locked AND post-editor toggle ships:** Settings sheet shows a "Polish translations (slower)" toggle (default OFF); toggle copy matches Architecture Addendum FR-22 spec: "Polish translations (slower) — extra step that makes translations sound more natural. Each translation takes about half a second longer." When ON, backend chains two Gemini calls: primary prompt → reflow prompt from `prompts/reflow-v1.md`; reflow adds ~400 ms per Utterance
  - **Outcome C — Plan A locked AND post-editor ships on-device (rare):** Settings shows the toggle; on-device second-pass through the locked model with a "reflow" instruction prompt (rare feasibility — only if Gemma 2B is the locked Plan A model with good instruction-following)
**And** whichever outcome ships, the Settings copy follows UX §"Voice of product-generated text" (no cryptic jargon)
**And** the reflow prompt file (`prompts/reflow-v1.md`) is authored during Phase 3 of build per PRD note (deferred to actual reflow implementation if Outcome B or C)
**And** SafeLog emits `featureFlagState: postEditorEnabled` on toggle change (privacy-safe)
**And** Bania approves the outcome before this story is closed (manual decision gate)

### Story 8.8: Privacy Summary Screen + Crashlytics Opt-In Toggle

As Bania or his girlfriend wanting to understand the app's privacy posture,
I want a Settings entry that opens a plain-English one-screen privacy summary, and a separate toggle for Crashlytics (default OFF) that I can flip if I want to help improve the app,
So that the privacy story is transparent and I have explicit control over telemetry (FR-24, NFR-Observability).

**Acceptance Criteria:**

**Given** the reconciled PRD §6.1 (Story 1.14) defines the privacy posture post-SCOPE-EXPANSION,
**When** I add the privacy surface to Settings,
**Then** a "Privacy" row in Settings opens a sub-sheet (or detail screen) titled "Your data" with plain-English bullet points (FR-24):
  - **Lists what is collected:** anonymous Firebase user identity, pair record (UIDs only), Caption text in transit (E2EE), Crashlytics opt-in (if enabled), transcript history (if enabled — local only)
  - **Lists what is NEVER stored server-side:** audio, Captions, conversation content, video frames
  - **Privacy posture summary:** "Calls are end-to-end encrypted. Translation happens on your device. The server you connect to (Bania's Oracle VM) only forwards encrypted data."
  - **Plan B note (conditional, only if Plan B is active per Story 3.9):** "Translation uses Google Vertex AI in Singapore. Google sees translation text but does not use it to train models."
**And** a "Help improve the app (anonymous crash reports)" toggle is present in Settings — **default OFF** per NFR-Observability
**And** when toggle is ON: Crashlytics SDK is initialized; conversation content is NEVER logged (verified by SafeLog allowlist from Story 1.5); only Call IDs, language codes, error-type strings, model load timings, and `AllowedLogKey` enum values reach Crashlytics
**And** when toggle is OFF: Crashlytics SDK is disabled at runtime (no telemetry leaves device)
**And** toggle helper text: "Sends anonymous crash reports to help improve TranslatorRep. No conversation content is ever included." (Footnote, plain language)
**And** the privacy summary updates whenever §6.1 changes — version-noted with `updated: <date>` in code (so PRD §6.1 changes flow into the in-app surface)

### Story 8.9: QualityReviewRow — Bania-Side Post-Call Quality Review Tool

As Bania (product owner) after each Call,
I want a private post-Call sheet on my device only showing the last N captions with ✓/⚠️/✗ rating tap-targets per row,
So that I can flag good/awkward/wrong translations to feed SM-2 quality acceptance + system-prompt iteration without my girlfriend ever seeing the review surface (UX-DR27, anti-emotion #1 self-monitoring anxiety design).

**Acceptance Criteria:**

**Given** a Call has ended on Bania's device (Story 2.8),
**When** the Call-end flow runs **on Bania's device only**,
**Then** Android `quality/ui/QualityReviewScreen.kt` and iOS `Quality/UI/QualityReviewScreen.swift` show a post-Call sheet listing the LAST N captions from the Call (N = 20 or full Call, whichever smaller — UX decision documented)
**And** each row shows the Caption text (source + target, smaller than In-Call version) + three tap targets: ✓ (good, "accurate and natural") / ⚠️ (awkward but accurate) / ✗ (wrong or lost meaning) — UX-DR27 anatomy
**And** tapping a rating highlights the selected option; tapping the selected rating clears it
**And** ratings persist locally in Room (Android) / SwiftData (iOS) with schema `QualityReviewRecord { callId, utteranceId, rating, ratedAt }` — Architecture ADR-D1
**And** Bania can dismiss the sheet at any point (swipe-down / Cancel) — partial ratings save; remaining captions can be reviewed later via a Settings entry "Recent quality reviews"
**And** the sheet renders **ONLY on Bania's device** — never on his girlfriend's device; gated by checking which user account is signed in vs. Bania's known UID (stored locally during pairing — pre-flight: her side never sees `QualityReviewScreen` regardless of toggles)
**And** the ratings are **NEVER synced** between devices — never reach Firestore, never reach Crashlytics, never reach his girlfriend
**And** the data feeds SM-2 quality acceptance test + future system-prompt iteration; aggregate counts displayed in Bania's own Settings as "Last 20 captions: 16 ✓, 3 ⚠️, 1 ✗"
**And** integration test: complete a Call on Bania's device → sheet shows → rate 5 captions → dismiss → reopen app → ratings persist; on his girlfriend's device → same Call → no review sheet appears

### Story 8.10: HerSideOneTapReaction + Aggregated Counts to Bania

As his girlfriend after each Call,
I want a single ✓/✗ tap target on the post-Call dismissal sheet (her side only) to signal "this was a good call" or "this wasn't" — a feedback channel that is one tap, ignorable, and never per-utterance review of her speech,
So that her trust in the product surfaces honestly without the surveillance feeling of being graded (UX-DR28, anti-emotion #1 self-monitoring anxiety, Mary's Step 4 feedback gap).

**Acceptance Criteria:**

**Given** a Call has ended on her device (Story 2.8),
**When** the Call-end flow runs **on her device only**,
**Then** Android `quality/ui/HerSideOneTapReaction.kt` and iOS `Quality/UI/HerSideOneTapReaction.swift` render a sheet (or in-line section of the dismissal sheet) with: hint text "Was this call good?" (Body typography) + two large tap targets side-by-side — ✓ (left) and ✗ (right) — UX-DR28 anatomy
**And** tapping either records the reaction LOCALLY on her device (Room Android / SwiftData iOS, schema `HerReactionRecord { callId, reaction: thumbs_up | thumbs_down, reactedAt }`) and dismisses the sheet
**And** the sheet is SKIPPABLE: swipe-down dismisses without recording (saved as "no signal," not "neutral" — UX §"Asymmetric privacy surfaces" rule)
**And** the sheet renders **ONLY on her device** (gated by which user is signed in vs. Bania's UID — opposite gate from Story 8.9)
**And** her reactions are **per-Call aggregate** — NEVER per-utterance — by design (UX-DR28 explicit constraint)
**And** the reactions surface to **Bania** as **AGGREGATED COUNTS ONLY**: a Settings row on Bania's device shows "Recent calls — 4 ✓, 1 ✗ in the last 5" (synced via a Firestore-stored aggregate doc at `/pairs/{pairId}/herReactionAggregate` — only aggregate fields, never individual reactions or call IDs traceable back to a specific utterance)
**And** Bania NEVER sees:
  - Per-Call detail of which Calls she rated
  - Timestamps of when she rated
  - Any commentary or per-utterance information
**And** the aggregate doc respects Firestore rules: writeable by her, readable by both pair members
**And** UX copy follows anti-emotion #1: NEVER "Rate this conversation" / "How did Bania do?" / any framing that implies grading; uses "Was this call good?" — about the experience, not the performer
**And** integration test: complete 5 Calls on her device → tap ✓ on 4, ✗ on 1 → kill app → reopen Bania's Settings → see "Recent calls — 4 ✓, 1 ✗ in the last 5" rendered; Bania CANNOT navigate to per-Call detail (no such UI exists)

### Story 8.11: TQ-AT v1 Ship Gate Execution

As Bania (product owner) at the final pre-ship gate of v1,
I want to execute the Translation Quality Acceptance Test (TQ-AT) per PRD §5.3 using the `QualityReviewRow` tool (Story 8.9) on a real sample of Calls, with Bania and his girlfriend reviewing together, and a ship/no-ship decision recorded,
So that SM-2 (Plan A ≥60% / Plan B ≥80% accuracy threshold) is gated against actual conversation evidence rather than assumed-met.

**Acceptance Criteria:**

**Given** all Epics 1–8 stories prior to this are complete AND `QualityReviewRow` (Story 8.9) has been capturing per-Caption ratings during real use,
**When** the TQ-AT ship gate runs,
**Then** `/docs/runbooks/tqat-v1-ship-gate.md` exists documenting the test protocol per PRD §5.3:
  - **Sample:** 10 sample Calls recorded across at least 3 distinct sessions, each Call ≥20 minutes
  - **Review method:** Bania and his girlfriend review TOGETHER (not separately) — her input is essential for register/tone errors Bania cannot evaluate; Bania catches EN-side naturalness errors she may miss
  - **Rating per Utterance:** ✅ accurate-and-natural / ⚠️ accurate-but-awkward / ❌ wrong-or-lost-meaning, recorded via the `QualityReviewRow` tool on Bania's device
  - **Per-TQ-category breakdown:** ratings tagged by which preservation target (TQ-1 particles, TQ-2 register, TQ-3 gender neutrality, TQ-4 Sundanese insertions, TQ-5 honorifics, TQ-6 religious expressions, TQ-7 indirect refusals, TQ-8 Gen-Z slang) the Utterance exercised, so per-category pass rates are computable
  - **Ship threshold:** 
    - **Under Plan A (on-device translation):** ≥60% ✅ AND no individual Call has >2 ❌ ratings (NFR-Quality relaxed target)
    - **Under Plan B (Vertex AI Gemini):** ≥80% ✅ AND no individual Call has >2 ❌ ratings (PRD original SM-2 target)
**And** the test result is recorded in the runbook with: timestamps, per-Call ✅/⚠️/❌ counts, per-TQ-category breakdown, qualitative notes from the paired review
**And** the ship/no-ship decision is documented:
  - **PASS:** v1 ships; runbook archived; the ratings feed the v1.1 system-prompt iteration loop (per PRD §5.1 "Iteration cadence: review after the first 10 real Calls; revise based on actual error patterns")
  - **FAIL on Plan A:** Activate Plan B (Vertex AI Gemini per Story 3.9 Outcome C); re-run TQ-AT against the higher ≥80% threshold; cost +~$5/month re-approved by Bania
  - **FAIL on both Plan A and Plan B:** scope-cut decision per `/docs/runbooks/solo-dev-scope-cuts.md` (Story 1.14c) — e.g., revise the prompt and re-run, or defer v1 ship to address specific TQ category failures
**And** the SM-7 qualitative win-condition (PRD §11 / UX SM-7) is verified alongside the TQ-AT: at least one ≥20-min Call within the first month where no miscommunication occurs and neither partner falls back to WhatsApp text translation or in-conversation clarification
**And** Bania manually approves the ship/no-ship decision before this story is closed
**And** the `HerSideOneTapReaction` aggregate (Story 8.10) is also reviewed as a corroborating signal — high ✗ rate on her side warrants a deeper look even if the per-Caption TQ-AT passes
