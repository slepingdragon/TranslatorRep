---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
  - post-assessment-reconciliation-execution
status: complete
completedAt: 2026-05-22
finalReadinessStatus: ready-post-reconciliation
assessor: bmad-check-implementation-readiness (BMad Method workflow)
notes: Initial assessment found NEEDS WORK (4 critical, 2 major, 4 minor issues all rooted in PRD↔UX↔Architecture drift). Bania authorized autonomous execution; all 4 critical + 1 of 2 major issues were resolved end-to-end in the same session. Post-execution status is READY FOR IMPLEMENTATION. See "Post-Assessment Reconciliation Execution Log" section at the end.
filesAssessed:
  prd: _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/prd.md
  prdAddendum: _bmad-output/planning-artifacts/prds/prd-TranslatorRep-2026-05-22/addendum.md
  architecture: _bmad-output/planning-artifacts/architecture.md
  epics: _bmad-output/planning-artifacts/epics.md
  ux: _bmad-output/planning-artifacts/ux-design-specification.md
  uxDirectionsDeck: _bmad-output/planning-artifacts/ux-design-directions.html
---

# Implementation Readiness Assessment Report

**Date:** 2026-05-22
**Project:** TranslatorRep

## Step 1 — Document Inventory

### PRD
- **Selected (authoritative):** `prds/prd-TranslatorRep-2026-05-22/prd.md` (44 KB, 2026-05-22 10:58)
- **Supplementary:** `prds/prd-TranslatorRep-2026-05-22/addendum.md` (11 KB, 2026-05-22 10:59)
- Sharded: none. Duplicates: none.

### Architecture
- **Selected:** `architecture.md` (91 KB, 2026-05-22 17:10)
- Sharded: none. Duplicates: none.

### Epics & Stories
- **Selected:** `epics.md` (179 KB, 2026-05-22 18:37)
- Sharded: none. Duplicates: none.

### UX Design
- **Selected (authoritative):** `ux-design-specification.md` (128 KB, 2026-05-22 15:00)
- **Historical / non-authoritative:** `ux-design-directions.html` (18 KB, 2026-05-22 14:38)
- Sharded: none. Duplicates: none.

### Notes
- No `project-context.md` exists in repo (persistent-fact load empty — non-blocking).
- All four required document types present; no duplicate-format conflicts to resolve.

---

## Step 2 — PRD Analysis

PRD `prd.md` (rev 2, final, 2026-05-22) + companion `addendum.md` read in full. Capability-vs-implementation discipline observed in source: PRD specifies capabilities, addendum carries SDK names / schemas.

### Functional Requirements

#### §4.1 Pairing & Identity (realizes UJ-1)

- **FR-1: Anonymous sign-in on first launch.** System signs user in anonymously before any UI. *Consequences:* session within 3s of clean install; identity persists across restarts; no login UI ever shown.
- **FR-2: Generate Pairing Code on demand.** User can view own 6-digit Pairing Code at any time when unpaired. *Consequences:* 6-digit decimal; unique across active codes (assumption: 1M code space + collision check); remains valid until used or regenerated.
- **FR-3: Enter partner's Pairing Code to pair.** Labeled input; submission attempts to pair the two user identities. *Consequences:* invalid code → inline error within 2s, own code stays valid; valid code creates Pairing record referenced by both users; both apps transition to Paired home within 5s.
- **FR-4: Paired state persists across app restarts.** *Consequences:* kill+reopen → Paired home, not Paired-Empty; partner display name + identity recoverable from local storage without network.
- **FR-5: Unpair from current partner.** From Settings; returns to Paired-Empty. *Consequences:* requires two-tap confirmation; removes backend Pairing record; clears local pairing state; partner's app does not break — sees "Partner unpaired" on next Call attempt.

#### §4.2 Calling — Place, Receive, End (realizes UJ-2)

- **FR-6: Place a Call to paired partner.** Tap Call on Paired home. *Consequences:* tap-to-ringing <3s on 4G; "Calling..." state until accepted/rejected/timed-out at 30s; WebRTC room created + caller admitted before ringing notification sent.
- **FR-7: Receive an incoming Call notification.** Integrated with platform native call surface (lock-screen ring). *Consequences:* caller→native UI latency <2s typical; displays caller display name + "TranslatorRep" identity; recipient can answer without unlocking.
- **FR-8: Accept or reject an incoming Call.** Via native call UI. *Consequences:* accept → both apps to In-Call within 2s; reject → caller sees "Call declined" + returns to Paired home within 3s; 30s no-answer counts as missed.
- **FR-9: End an active Call.** Either user, from In-Call screen. *Consequences:* end-call has confirmation pattern for Calls >5min (assumption); disconnects WebRTC, stops audio capture, closes Translation Pipeline, returns both to Paired home within 2s.
- **FR-10: Audio quality acceptance.** Verified before v1 ships. *Consequences:* Opus ≥24 kbps sustained per direction; WebRTC AEC enabled, no audible echo on speakers OR headsets paired listening test; intelligible at simulated 4G with 200ms RTT + 1% packet loss; acceptance = paired listening test on 3 Calls ≥10min, rating "as good as WhatsApp voice" or better with no Call below "intelligible but degraded".

#### §4.3 Translation Pipeline — Audio → Caption (realizes UJ-2, UJ-3)

- **FR-11: Capture local mic audio during a Call.** Real-time while Call active. *Consequences:* tapped from WebRTC local audio track without affecting audio sent to peer; mic permission requested before first Call (cannot start Call without it); audio NOT persisted to disk in normal operation.
- **FR-12: Detect Utterance boundaries via VAD.** Segments continuous mic audio at natural speech pauses, aware of Indonesian sentence-final particles. *Consequences:* Utterance committed on **700ms** silence after speech (v1 default, internally tunable); max Utterance length **15s**, longer is force-segmented; commit waits for sentence-final particle settle — partial transcripts before final pause are NOT translated or sent to peer (per DR §7 prosody finding).
- **FR-13: Convert Utterance audio to Source Text via on-device ASR.** Platform-appropriate, behind ASR Provider abstraction. *Consequences:* Android on-device ASR with `id-ID` + `en-US` locales, gated by runtime availability probe at launch; iOS on-device via bundled multilingual model (Apple APIs don't support `id-ID` on-device — see addendum); time-to-finalized Source Text <2s Android, <3s iOS after Utterance end (assumption: Whisper.cpp small on A17+; verify Week 1); empty/low-confidence ASR surfaces soft retry, never silent drop.
- **FR-14: Translate Source Text to Target Text via backend proxy.** Speaker's app POSTs Source Text to backend, which calls Translation Provider with v1 system prompt and returns Target Text. *Consequences:* POST round-trip <1.5s median when warm; backend enforces app-attestation per request, missing/invalid → HTTP 401; failed Translation Provider call (timeout/5xx/rate-limit) returns typed error, Caption renders with "translation unavailable" marker; direction set by user language preference at setup (Bania EN→ID, her ID→EN).
- **FR-15: Deliver Target Text to peer via Data Channel.** WebRTC peer-to-peer Data Channel Message. *Consequences:* speaker's-Target-Text→peer-render <300ms in same-region; reliable-ordered delivery; each message <1 KB normal Caption length.
- **FR-16: Render Caption on speaker's screen.** Speaker also sees own Source + Target Text so they can verify what was sent. *Consequences:* speaker sees own Source Text within 1s, own Target Text within 4s; failed translation → "translation unavailable" alongside Source Text so speaker knows partner did not receive translation.

#### §4.4 Captions UI (realizes UJ-2, UJ-3)

- **FR-17: Display a scrollable Caption history during the Call.** Lower portion of In-Call screen (assumption: lower 60%, UX to confirm). *Consequences:* visible for Call duration; Source vs Target visually differentiated; history scrollable independently of rest of In-Call screen; discarded on Call end unless FR-21 enabled.
- **FR-18: Render Partial Captions for in-progress speech.** Speaker's own caption row shows evolving Source Text in "in-progress" style. Partial Captions are local-only, NOT translated, NOT sent to peer (per FR-12 + DR §7). *Consequences:* updates at ASR partials cadence; on finalization transitions to finalized Caption with no row reorder or visual jump; peer never sees Partials.
- **FR-19: Auto-scroll Caption history to the newest Caption.** *Consequences:* new Caption appended → animates to bottom within 200ms; if user has manually scrolled up, auto-scroll suspended until they scroll back to bottom or tap "jump to latest" affordance.
- **FR-20: Visually distinguish translation failures.** *Consequences:* failed translations show "translation unavailable" marker; tapping marker shows brief explanation (e.g., "Network error" / "Translation service unavailable").

#### §4.5 Settings

- **FR-21: Opt in to per-device transcript history.** Toggle in Settings; default OFF. *Consequences:* when ON, all finalized Captions written to local encrypted storage on Call-end; when OFF, no captions persist beyond active Call; NEVER synced to server; user can view + delete entries from Settings.
- **FR-22: Toggle translation post-editor (optional Gemini reflow).** Toggle in Settings; default OFF. *Consequences:* when ON, adds ~400ms per-Utterance translation latency; reflow prompt authored during Phase 3 of build, informed by real-conversation evidence; v1 ships with the toggle + working reflow prompt OR FR-22 deferred to v1.1. [PM NOTE]: Settings copy must explain in plain language what the toggle does and what user is trading.
- **FR-23: Set custom display name.** Override default "Partner" shown to partner during Calls. *Consequences:* setting in Settings; new name appears on partner's screen on next Call; default "Partner" when never set; never asked during Pairing.
- **FR-24: View privacy summary.** One-screen plain-English summary of what data the app collects/where/what is never stored. *Consequences:* lists anonymous user identity, Pairing record, Caption text in transit, Crashlytics if opted in, transcript history if opted in; lists what is NEVER stored server-side (audio, Captions, conversation content); states Gemini AI Studio data-handling caveat per §6.1.
- *(FR-25 reserved — Unpair from current partner is FR-5, surfaced in Settings via that same FR.)*

**Total FRs:** 24 (FR-1 through FR-24; FR-25 intentionally reserved/aliased to FR-5).

### Non-Functional Requirements

#### Translation Quality Preservation Targets (§5.2 — load-bearing for v1 success per SM-2)

- **TQ-1. Indonesian discourse particles** (`lah`, `sih`, `kok`, `dong`, `deh`, `ya/iya`, `kan`, `nih`, `tuh`, `aja`, `gitu`, `udah`, `loh/lho`, `mah`). Particle loss = highest-impact quality regression.
- **TQ-2. Pronoun register signals.** `aku`/`kamu` (default intimate) vs `saya`/`Anda` (formal/cold) vs `gue`/`lo` (Jakarta playful).
- **TQ-3. Gender neutrality.** `dia` / `-nya` → singular "they" when ambiguous; never default "he"/"she".
- **TQ-4. Sundanese lexical insertions** (≥12 high-frequency tokens from DR §4); full SU clauses flagged `[su?]` rather than confabulated.
- **TQ-5. Honorifics directed at partner.** `mas`, `abang`, `kak` → "babe" or omit; never literalize as "older brother".
- **TQ-6. Religious expressions.** `insya Allah`, `alhamdulillah`, `astaghfirullah`, `masya Allah` preserved verbatim (optional first-use gloss).
- **TQ-7. Indirect refusals.** `nanti dulu` → "maybe later"; `mungkin` → "maybe (often soft no)"; `liat nanti` → "we'll see".
- **TQ-8. Gen-Z 2026 slang dictionary** (~20 high-frequency items from DR §3) mapped to natural English equivalents.

**TQ-AT (§5.3 Quality Acceptance Test):** 10 sample Calls across ≥3 sessions, each ≥20min, reviewed **together** by Bania + girlfriend, rating each Utterance ✅/⚠️/❌. Ship gate: ≥80% ✅ AND no Call has >2 ❌.

#### Privacy (§6.1)

- **NFR-P1.** No conversation content persists server-side (audio never stored; Source/Target Text in-memory on backend; not in logs beyond opaque request metadata).
- **NFR-P2.** Crashlytics opt-in, default off; conversation content NEVER in custom keys/logs; only Call IDs, language codes, error-type strings.
- **NFR-P3.** Transcript history local-only, per-device; never synced.
- **NFR-P4.** TLS 1.3 in transit on all client-backend and client-WebRTC connections.
- **NFR-P5.** WebRTC media client-encrypted to SFU; full client-to-client E2EE (Insertable Streams) deferred to v2.
- **NFR-P6.** Gemini AI Studio free-tier caveat (training-eligible inputs, global pool, no data-residency guarantee) accepted for v1; surfaced via FR-24.

#### Cost (§6.2)

- **NFR-C1.** $0/month operating cost is a HARD requirement for v1 at 2-user / ~30-min/day scale.
- **NFR-C2.** Cloud Billing budget alerts at $1 / $5 / $10; hard killswitch (detach billing) at $50.
- **NFR-C3.** No paid-tier substitution without explicit re-approval from Bania.

#### Safety / Reliability (§6.3)

- **NFR-R1.** Failed Translation Pipeline must NEVER silently drop a Caption; all failures surface as the "translation unavailable" indicator (per FR-20).
- **NFR-R2.** App works offline-degraded for ASR (on-device works without network); if offline, speaker sees Source Text + clear network-error indicator and no Target Text.
- **NFR-R3.** No third-party analytics SDK that touches conversation content. Crashlytics is the only allowed telemetry source.

#### Architecture (§6.4 — load-bearing for v2)

- **NFR-A1: Provider Abstraction.** System MUST expose `AsrProvider` and `TranslationProvider` interfaces; every FR using ASR or Translation routes through abstraction, never directly to vendor SDK. *Testable:* unit test confirms ASR call site is `AsrProvider` interface; unit test confirms translation call site is `TranslationProvider` interface; adding second `AsrProvider` (e.g., test stub) requires zero changes to §4.3 call sites.

#### Performance / Cold-Start (§6.5)

- **NFR-PF1: Warmup ping pattern.** Backend (min-instances=0) requires warmup. Client MUST send no-op warmup within 30s of app foreground, on Paired-home navigation, and on Call tap if no warmup in last 10min. Backend cold start: 500ms–2s typical.
- **NFR-PF2: E2E latency target.** Median Utterance-to-Caption <3.5s; p95 <5s on typical 4G (per SM-4). Architecture choices materially impacting latency must be documented in Addendum or TR before adoption.

#### Performance / Battery (§11 secondary)

- **NFR-PF3: Battery.** iPhone battery drain <30% per hour of Call (Whisper.cpp ceiling per TR).

#### Aesthetic / UX (§7)

- **NFR-U1.** Visual language: monochrome glass — pure B/W dominant, glassmorphism panels with backdrop blur, 1px low-opacity borders, subtle shadow. Color reserved for state signals only (translation-failure, mic-active dot), minimum saturation.
- **NFR-U2.** Layout: centered. Primary actions/content horizontally centered. Avoid left-aligned / asymmetric / feed-like.
- **NFR-U3.** Density: simplistic, no clutter. Generous whitespace. One primary action per screen wherever possible. No accessory chips/badges/dots unless functionally required.
- **NFR-U4.** Typography: system defaults (Roboto / SF), generously sized for Caption readability. Source vs Target via weight/opacity, NOT color.
- **NFR-U5.** Product voice: plain, kind, never apologetic-corporate. "Translation unavailable" beats "We're terribly sorry, an error occurred."
- **NFR-U6.** Anti-references explicitly forbidden: enterprise-y/productivity-y (chips, badges, accent colors, sidebars); AI-aesthetic clichés (gradients, sparkle iconography, "AI" branding, holographic chrome); busy/colorful/social-media-like; left-aligned top-heavy feed layouts.

#### Platform (§8)

- **NFR-PL1.** Android native, Compose, minimum API 33 (for on-device ASR APIs), target API 35.
- **NFR-PL2.** iOS native, SwiftUI, minimum iOS 17 (for newer ScrollView APIs), target iOS 26.
- **NFR-PL3.** Backend Cloud Run in `asia-southeast1` (Singapore), Node.js, min-instances=0 + warmup ping per NFR-PF1.
- **NFR-PL4.** Distribution v1 = personal sideload only. APK direct install on Android; TestFlight Ad Hoc on iOS. No App Store, no Play Store.

#### Success Metrics (§11)

- **SM-1 (Primary).** Bania uses ≥3x/week for first month after ship.
- **SM-2 (Primary).** ≥80% of Captions rated ✅ in TQ-AT.
- **SM-3 (Primary).** ≥5 successful Calls in first 2 weeks without bugs (no crashes, no failed Pairings, no missed-incoming).
- **SM-4 (Secondary).** Median E2E latency <3.5s; p95 <5s (mirrors NFR-PF2).
- **SM-5 (Secondary).** iPhone battery <30%/hour Call (mirrors NFR-PF3).
- **SM-6 (Secondary).** $0/month sustained for first 3 months (mirrors NFR-C1).
- **SM-7 (Qualitative).** Within first month, at least one ≥20-min Call where no miscommunication occurs and neither falls back to WhatsApp text translation or in-conversation clarification.
- **Counter-metrics (do NOT optimize):** SM-C1 (DAU), SM-C2 (latency-over-accuracy), SM-C3 (Caption volume).

**Total NFR-class items:** 8 TQ preservation targets + 6 Privacy + 3 Cost + 3 Reliability + 1 Architecture + 3 Performance + 6 Aesthetic + 4 Platform + 7 Success Metrics + 3 Counter-metrics = **44 distinct NFR/quality requirements**.

### Additional Requirements / Cross-Cutting

- **Non-Goals (§9) — canonical scope exclusions.** Forever-out: WhatsApp/FaceTime/Meet/3p-call-platform integration; group calls; public distribution (conditional on §10.4); conversation-content analytics; 3p integrations. v2-deferred: video; Sundanese full-clause translation; text chat; true E2EE between clients; cross-device transcript sync; multiple paired partners per user.
- **MVP Scope (§10.1).** Bidirectional ID↔EN live captioned voice Calls between two Paired Users; native Android+iOS per §7; anonymous Firebase Auth + 6-digit Pairing; WebRTC free tier; on-device ASR; backend translation proxy w/ app-attestation + §5.1 system prompt; Provider Abstraction per §6.4; inline Captions UI w/ Partial Captions + auto-scroll + failure indication; native incoming-Call UX; Settings (transcript history opt-in, post-editor toggle, display name, unpair, privacy summary); $0/month per §6.2.
- **Timeline (§10.3).** Ramp ~1 week; Build 4–6 weeks solo, 6 phases (foundations → basic Call → translation pipeline → bidirectional + peer display → polish & settings → testing/bugfix). Phase plan in TR §5.
- **System Prompt Versioning (§5.1).** Gemini system prompt is first-class versioned artifact in backend repo (`prompts/id-en-v1.md`, `prompts/en-id-v1.md`); two variants (ID→EN, EN→ID) sharing same caching context; iteration cadence = review after first 10 real Calls.

### Open Questions (§12 — 7 items)

1. Bania's Samsung Galaxy — does runtime probe confirm `id-ID` on-device? (Week 1; else cloud STT ~$15/mo, re-approve cost.)
2. Whisper.cpp battery drain on her specific iPhone model (Phase 3/6 measurement; >30%/hr → cloud STT fallback ~$15/mo).
3. VAD pause threshold (FR-12) shipping default 700ms — will need tuning during real-conversation testing.
4. Default Caption screen area % (FR-17) — assumption 60%, **to be confirmed in UX design**.
5. FR-22 Settings copy + reflow prompt — copy in UX; prompt in Phase 3.
6. Out-of-band Pairing Code delivery — assumption WhatsApp text; no in-app QR/deep-link v1.
7. In-app translation-quality rating tool (DR recommendation) — decide before Phase 5: v1 / v1.1 / skip.

### PRD Completeness Assessment

**Strengths (high readiness signal):**

1. **Requirement IDs are stable and globally numbered (FR-1 … FR-24, TQ-1 … TQ-8, NFR-class items implicit by §6 subsection, SM-1 … SM-7).** Traceability into epics will be straightforward.
2. **Every FR has named, testable Consequences** — not just "shall do X" but the acceptance signal. This is exactly the spec shape downstream Epic+Story work needs.
3. **Quality (TQ-*) targets are first-class** with the TQ-AT acceptance gate — load-bearing for SM-2.
4. **NFRs explicitly flagged as load-bearing** (Provider Abstraction §6.4 for v2; Warmup Ping §6.5 for cold-start). Easy to map to architecture decisions.
5. **Scope discipline is exemplary:** §9 canonical non-goals, §10.2 explicit v2 candidates with rationale, §11 counter-metrics block.
6. **Capability vs implementation discipline maintained** via Addendum; PRD readable as pure capability spec.

**Gaps / risks to flag for downstream alignment (Steps 3–6 will validate):**

1. **FR-25 marker is confusing.** Document says "FR-25 reserved — Unpair from current partner is FR-5". This is a parenthetical not a real FR. Recommend rephrasing or removing in next revision to avoid SM/Epic counting confusion. **Severity: cosmetic.**
2. **FR-22 conditional scope** ("v1 ships with toggle + working reflow prompt OR FR-22 deferred to v1.1"). Sprint planning must resolve this before stories are cut. **Severity: needs decision.**
3. **Open Question 7 (in-app rating tool)** — *"decide before Phase 5"*. If epics do not currently include a story or scope tag for "decision point", this is at risk of being missed.
4. **Open Question 4 (Caption screen %)** — UX should have resolved this; Step 4 will verify.
5. **NFR-class items are inline rather than separately numbered (NFR-1, NFR-2, …)**. PRD uses §6.x subsection identifiers and §5 TQ-* identifiers but no consolidated NFR registry. Acceptable, but epic-coverage traceability needs to handle this asymmetry explicitly.
6. **No requirement covers analytics opt-in UX itself** (Crashlytics §6.3 / §6.1 is named as opt-in but no FR specifies *how* the user opts in — Settings toggle? First-launch prompt?). Possible gap or implicit-in-FR-21/FR-24 — Step 3 should verify epic coverage.
7. **No requirement covers the partner display name set BY THE OTHER user.** FR-23 lets each user set their own display name shown to their partner. There is no explicit FR for receiving and rendering the partner's chosen display name — likely implicit but should be checked.

PRD analysis complete. Auto-proceeding to Epic Coverage Validation.

---

## Step 3 — Epic Coverage Validation

Epics document `epics.md` (1879 lines, marked `status: complete`, `stepsCompleted: [1,2,3,4]`) read in full. It contains a self-published FR Coverage Map (§"FR Coverage Map", lines 196–231), 8 epics with ~70 stories total (Stories 1.1–1.14, 2.1–2.11, 3.1–3.15, 4.1–4.9, 5.1–5.5, 6.1–6.8, 7.1–7.7, 8.1–8.10).

### 🚨 CRITICAL FINDING: PRD ↔ Epics Scope Drift

The epics document explicitly declares: *"Where PRD/UX file artifacts conflict with the Architecture document's SCOPE EXPANSION, Architecture wins (per architecture.md frontmatter). Architecture is the canonical glossary going forward."*

The epics define a **32-FR scope** (FR-1 … FR-32, with FR-14 superseded and FR-25 reserved), but the current `prd.md` (rev 2, final) only defines **FR-1 … FR-24**. The 7 added FRs (FR-26 → FR-32) and 6 relaxed/replaced NFRs do **not yet exist in the PRD file**.

The epics acknowledge this gap and assign reconciliation as **Story 1.14 ("PRD + UX-Spec Reconciliation Deliverables")** — but Story 1.14 is *itself part of the implementation work*, not pre-implementation alignment. This means **the PRD will be revised by an implementation story rather than before implementation begins**. That is a deliberate sequencing choice (the epics treat Architecture as the canonical source going forward), but it inverts the BMAD invariant that the PRD is the upstream contract.

**Implication for readiness:** Implementation can proceed *if* Story 1.14 is treated as a true predecessor for all other stories that depend on the new FRs / NFRs. If Stories 3.x, 5.x, 6.x, 7.x are picked up before Story 1.14 lands, downstream stories will reference FRs that don't exist in the upstream PRD — confusing for traceability, audits, and future agents.

### Coverage Matrix

**Legend:** ✅ Covered • ⚠️ Covered-but-drift • 🔄 Superseded • ⛔ Reserved (no story) • ❌ Missing

#### PRD-original FRs (24)

| FR | PRD Requirement | Epic Coverage | Status |
|---|---|---|---|
| FR-1 | Anonymous sign-in on first launch | Epic 1 — Story 1.8 | ✅ |
| FR-2 | Generate Pairing Code on demand | Epic 1 — Story 1.9 | ✅ |
| FR-3 | Enter partner's Pairing Code | Epic 1 — Story 1.10 | ✅ |
| FR-4 | Paired state persists across app restarts | Epic 1 — Story 1.11 | ✅ |
| FR-5 | Unpair from current partner | Epic 1 — Story 1.13 (shell); Epic 8 — Story 8.x (Settings UI) | ✅ |
| FR-6 | Place a Call to paired partner | Epic 2 — Story 2.3 | ✅ |
| FR-7 | Receive incoming Call notification | Epic 2 — Stories 2.4 (iOS), 2.5 (Android) | ✅ |
| FR-8 | Accept or reject incoming Call | Epic 2 — Story 2.6 | ✅ |
| FR-9 | End an active Call | Epic 2 — Story 2.8 | ✅ |
| FR-10 | Audio quality acceptance | Epic 2 — Story 2.10 | ✅ |
| FR-11 | Capture local mic audio | Epic 3 — Story 3.6 | ✅ |
| FR-12 | VAD Utterance boundaries | Epic 3 — Story 3.6 | ✅ |
| FR-13 | On-device ASR (ID + EN) | Epic 3 — Stories 3.4 (Android), 3.5 (iOS) | ✅ |
| FR-14 | Translate via backend proxy (Gemini) | Epic 3 — replaced by FR-31 (on-device translation) for v1 Plan A; Plan B path documented as fallback only | 🔄 Superseded |
| FR-15 | Deliver Target Text via Data Channel | Epic 4 — Story 4.2 | ✅ |
| FR-16 | Render Caption on speaker's screen | Epic 3 — Stories 3.12, 3.14 | ✅ |
| FR-17 | Scrollable Caption history | Epic 4 — Stories 4.3, 4.6 | ✅ |
| FR-18 | Partial Captions for in-progress speech | Epic 3 — Story 3.13 (speaker side, with Android-only honest asymmetry: iOS uses `SpeakingIndicator` per ADR-B5); Epic 4 — Story 4.3 (peer side) | ⚠️ Drift |
| FR-19 | Auto-scroll Caption history | Epic 4 — Stories 4.6, 4.7 | ✅ |
| FR-20 | Visually distinguish translation failures | Epic 3 — Story 3.15 (basic, speaker side); Epic 4 — Story 4.4 (full UX) | ✅ |
| FR-21 | Opt-in transcript history | Epic 8 — Story 8.6 | ✅ |
| FR-22 | Translation post-editor toggle | Epic 8 — Story 8.7 ("status resolved" — three-outcome decision deferred to after Story 3.9 model lock) | ⚠️ Conditional |
| FR-23 | Custom display name | Epic 8 — Story 8.5 | ✅ |
| FR-24 | Privacy summary | Epic 8 — Story 8.8 | ⚠️ Drift (post-SCOPE-EXPANSION posture, not PRD §6.1 content) |
| FR-25 | (Reserved — alias to FR-5) | — | ⛔ |

**PRD-FR coverage: 24/24 mapped (100%).** No PRD FR is orphaned.

#### Architecture SCOPE-EXPANSION FRs (in epics but NOT in PRD)

| FR | Requirement | Epic Coverage | Status in PRD |
|---|---|---|---|
| FR-26 | Audio Call vs Video Call selection on Paired home | Epic 2 (single button); Epic 6 — Story 6.1 (full `CallTypeSelector`) | ❌ Not in PRD §4 |
| FR-27 | Video pipeline + failure UX (360p × 30fps, `VideoPausedTile`, auto-retry) | Epic 6 — Stories 6.3, 6.4, 6.5, 6.6, 6.8 | ❌ Not in PRD §4; PRD §9 / §10.2 lists "video" as v2-only |
| FR-28 | Speaker/earpiece/Bluetooth audio-routing toggle | Epic 2 — Story 2.9 | ❌ Not in PRD §4 |
| FR-29 | E2EE setup + per-Call X25519 ECDH key exchange | Epic 1 — Story 1.12 (identity key); Epic 5 — Stories 5.1, 5.2, 5.3 | ❌ Not in PRD §4; PRD §6.1 / §9 / §10.2 lists "true E2EE between clients" as v2-only |
| FR-30 | Camera permission flow (lazy) | Epic 6 — Story 6.2 | ❌ Not in PRD §4 (implied by FR-27, but FR-27 not in PRD either) |
| FR-31 | On-device translation pipeline (replaces FR-14 cloud path) | Epic 3 — Stories 3.8, 3.9, 3.10 | ❌ Not in PRD §4; PRD §4.3 / Addendum specifies cloud Gemini |
| FR-32 | Leave-and-rejoin within 5-min window | Epic 7 — Stories 7.1–7.7 | ❌ Not in PRD §4 |

**Severity:** 🚨 **HIGH.** Seven FRs covering load-bearing v1 capabilities (video, E2EE, on-device translation, leave-and-rejoin) are fully storied but unrepresented in the upstream PRD. Any reader checking the PRD alone would conclude these are out-of-scope.

#### NFR Drift between PRD and Epics

| Topic | PRD says | Epics say | Severity |
|---|---|---|---|
| Translation quality target | SM-2: **≥80%** ✅ in TQ-AT | NFR-Quality: **≥60%** (relaxed; reverts to ≥80% under Plan B) | 🚨 HIGH |
| End-to-end latency | SM-4: median **<3.5s**, p95 <5s | NFR-Latency: median **<8s**, p95 <12s | 🚨 HIGH |
| Battery | SM-5: **<30%/hour** | NFR-Battery: best-effort, **50–70%/hour worst case acknowledged** | 🚨 HIGH |
| Backend | §8: Cloud Run in `asia-southeast1`, min-instances=0 + warmup ping §6.5 | AR-1/NFR-Cold-Start: **Oracle VM** (Ampere A1) always-on; **Cloud Run + warmup ping removed entirely** | 🚨 HIGH (architecture entirely swapped) |
| Translation Provider | §4.3 / Addendum: **Gemini 2.5 Flash** via Google AI Studio (free tier) | FR-31 + ADR-B1: **on-device NLLB-200 / MADLAD / Gemma 2B** (Plan A), Vertex AI Gemini fallback (Plan B) | 🚨 HIGH (provider entirely swapped) |
| Privacy posture | §6.1: media WebRTC-SFU (decrypts to forward); E2EE between clients deferred to v2; Gemini AI Studio free-tier caveat (training-eligible) | NFR-Privacy: **WhatsApp-equivalent or better — E2EE media + Data Channel via Insertable Streams**; translation never leaves device (Plan A); SFU sees only ciphertext | 🚨 HIGH (privacy promise elevated, but PRD still carries weaker claim) |
| App size | (not specified) | NFR-App-Size: +250 MB to ~1.5 GB acceptable for sideload | ⚠️ NEW |
| Cross-platform parity | (not specified) | NFR-Cross-Platform-Parity: "identical to the eye" | ⚠️ NEW |
| Observability | §6.1 / §6.3: Crashlytics opt-in, no conversation content | NFR-Observability: same posture + **SafeLog facade with `AllowedLogKey` enum + lint enforcement** as concrete mechanism | ✅ Mechanism added, not drift |
| Timeline | §10.3: **4–6 weeks** + 1 ramp | AR-23 / Story 1.14: revised to **~7–10 weeks + 1 ramp** | 🚨 HIGH |

#### Additional Coverage Observations

- **Provider Abstraction (PRD §6.4):** Covered by Story 3.3 (`AsrProvider` + `TranslationProvider` interfaces with cancellation contract). ✅
- **TQ-1 → TQ-8 (PRD §5.2):** Covered by Story 3.2 (`ParticleProcessor` + golden-file fixtures) + Story 3.7 (regression corpus with tags traceable to DR §1/§3/§4/§6). ✅
- **TQ-AT (PRD §5.3):** Covered conceptually by Story 3.1 (pre-validation conversation) + Story 3.9 (Week-1 validation gate) + Story 8.9 (post-Call QualityReviewRow). The paired ✅/⚠️/❌ acceptance test methodology itself is referenced but is not its own story. ⚠️ Minor — could be a dedicated v1-ship-gate runbook story.
- **PRD Open Question 7 (in-app rating tool):** Resolved by Story 8.9 (`QualityReviewRow`) — answered "yes, ships in v1." ✅
- **Architecture-driven AR-1 … AR-24 (24 additional requirements):** All mapped to epics per the epic-level "ARs covered" lines. ✅
- **UX-DR1 … UX-DR38 (38 UX design requirements):** All mapped to epics per the epic-level "UX-DRs covered" lines. Will be re-validated in Step 4 (UX Alignment). ✅
- **31-FR count claim in epics:** The epics doc states *"31 FRs total → all mapped."* This excludes FR-25 (reserved) but includes FR-1 through FR-32 minus FR-25 = 31. Counting is consistent.

### Missing Coverage

#### Critical Missing FRs (PRD → Epics)

**None.** Every PRD FR is mapped.

#### Critical Missing FRs (Epics → PRD)

| FR | Why this is critical | Recommendation |
|---|---|---|
| FR-26, FR-27, FR-30 (Video) | Video is now a v1 capability per Architecture but PRD §9 / §10.2 explicitly lists it as v2-deferred. Anyone reading the PRD will incorrectly conclude video is out-of-scope. | Story 1.14 must surface FR-26, FR-27, FR-30 in PRD §4; remove video from §9 / §10.2; update §10.1 MVP Scope. |
| FR-29 (E2EE) | E2EE between clients is now v1 per Architecture but PRD §6.1 / §9 / §10.2 explicitly lists it as v2-deferred. Privacy summary (FR-24) currently surfaces the weaker WebRTC-SFU promise. | Story 1.14 must rewrite PRD §6.1 to reflect WhatsApp-equivalent E2EE; surface FR-29 in §4; remove from §9 / §10.2. |
| FR-31 (on-device translation) | The entire translation pipeline architecture has swapped from cloud-Gemini to on-device. PRD §4.3 / Addendum still specifies the old Gemini-via-Cloud-Run path. | Story 1.14 must rewrite PRD §4.3 to describe on-device translation; surface FR-31; rewrite Addendum (or supersede it). |
| FR-32 (leave-and-rejoin) | New v1 capability not represented in PRD. Affects user-facing behavior (5-min reconnect window, `CallWaitingForPartnerState` overlay, `RejoinNotification`). | Story 1.14 must surface FR-32 in §4 with Description + Consequences. |

#### High Priority NFR Drift

- SM-2 / SM-4 / SM-5 targets in PRD §11 contradict NFR-Quality / NFR-Latency / NFR-Battery in epics. **Story 1.14 must rewrite §11 SM-2/4/5.**
- Cloud Run + warmup-ping NFR-PF1 in PRD §6.5 is obsolete (architecture swapped to Oracle VM). **Story 1.14 must rewrite §6.5 + §8 Platform.**
- Translation Provider in PRD §4.3 + Addendum is obsolete. **Story 1.14 must rewrite §4.3 FR-13/FR-14 + Addendum.**
- Privacy posture (§6.1) is now stronger than PRD claims. **Story 1.14 must rewrite §6.1 to reflect E2EE-by-default.**
- Timeline (§10.3) understated. **Story 1.14 must update §10.3.**

### Coverage Statistics

- **Total PRD FRs:** 24 (FR-1 … FR-24; FR-25 reserved)
- **Total Epics FRs:** 31 active (FR-1 … FR-32; FR-14 superseded, FR-25 reserved)
- **PRD FRs covered in epics:** 24 / 24 = **100%**
- **Epics FRs traced back to PRD:** 24 / 31 = **77%** (7 FRs added by Architecture, not yet reflected in PRD)
- **NFR drift count:** 6 quantitative targets relaxed/swapped; 2 new NFRs added without PRD representation; 1 entire architecture component (Cloud Run + warmup ping) obsoleted but still in PRD; 1 entire vendor (Gemini AI Studio) obsoleted but still in PRD/Addendum
- **Stories total:** ~70 across 8 epics
- **Reconciliation status:** Single dedicated story (Story 1.14) carries the entire PRD/UX rewrite load. **Not yet executed.**

Epic coverage validation complete. Auto-proceeding to UX Alignment (Step 4).

---

## Step 4 — UX Alignment Assessment

### UX Document Status

**Found.** `ux-design-specification.md` (128 KB, 1879 lines) — `Direction 1 + D4b` selected design direction. Includes Executive Summary, Core UX, Desired Emotional Response (with Anti-Emotions + Forbidden Strings audit list), Pattern Analysis, Design System Foundation, Defining Experience (Caption Loop in 9 Beats), Visual Design Foundation (Color/Typography/Spacing/Parity/Accessibility), Design Direction Decision, 7 User Journey Flows, 17 custom-component specs, UX Consistency Patterns, Responsive Design & Accessibility. Substantial and well-structured.

Secondary artifact: `ux-design-directions.html` (18 KB) — static mockup of canonical direction rendered across themes (currently has 3-theme rendering — see drift below). Non-authoritative per Step 1 selection; useful as visual reference only.

### UX ↔ PRD Alignment

#### Strengths

- **User Journeys traceable.** UJ-1 (Pairing), UJ-2 (First Call), UJ-3 (Deep Conversation) appear in both PRD §2.4 and UX §"User Journey Flows" with consistent identifiers and narrative.
- **Caption Loop (UX §"Defining Experience") aligns with FR-12 → FR-20 sequencing.** The 9-beat choreography maps directly onto PRD's per-Utterance pipeline.
- **TQ-1 / TQ-2 preservation surfaced as visual treatment** (UX "+0.3pt letter-spacing" treatment for emotional-weight whisper) — direct realization of PRD §5.2 TQ-1/TQ-2 in visual design.
- **Anti-Emotions framework** (UX §"Desired Emotional Response") and **Forbidden Strings audit** materially strengthen the PRD's §7 aesthetic constraints — UX is more rigorous here than PRD.
- **Accessibility (WCAG AAA primary contrast, Dynamic Type clamping)** exceeds PRD-stated requirements; PRD §7 mentions accessibility implicitly via "generously sized for Caption readability" but UX makes it concrete.

#### Gaps (UX → PRD)

- UX §"Defining Experience" introduces the explicit term *"Caption Loop"* and a 9-beat success-mechanic framing that PRD §4.3 does not name. Not a contradiction — UX enriches PRD — but downstream story authors should know the canonical term lives in UX, not PRD.
- UX §"Open Component Questions" (line ~1284) and §"Open Journey Questions" (line ~988) are unresolved at UX-spec close — they're explicitly forwarded to Architecture / Stories. Step 5 should check whether epics absorbed them.

### UX ↔ Architecture Alignment

#### 🚨 CRITICAL Drift (Same Root as PRD Drift)

The UX spec **predates** Architecture's SCOPE EXPANSION. Architecture §G ("Reconciliation Deliverables — CA's output to upstream artifacts") explicitly assigns UX rewrite as a CA deliverable, listing required edits. These rewrites have **not been applied** to `ux-design-specification.md`:

| UX-spec content | Architecture says | Status |
|---|---|---|
| §"Color System — Three Themes" — Theme A (Dark), **Theme B (Light high-contrast)**, Theme C (Image) | Architecture S3 decision: **Theme B removed**; `ThemePicker` becomes 2-option (Dark + Image) | 🚨 UX still has Theme B everywhere (lines 572, 682, 686, 702, 1206-1214 `ThemePicker` shows "Light high-contrast" as middle option, accessibility table cites Theme B contrast 14:1, "three themes" repeated 3× in heading text). Single-source-of-truth drift. |
| §"Spacing & Layout > In-Call screen vertical split" — only **Audio Call 40/60** described (line 634-637) | ADR-E1: **Audio 40/60 AND Video 50/50**, with explicit reject of backdrop-video-with-captions-overlay | 🚨 No Video layout in UX; Caption stack region under Theme C × Video documented in Architecture ADR-E2 but missing from UX |
| §"Custom Components" — 17 components (`MonochromeGlassPanel`, `CaptionStack`, `CaptionRow`, `PartialCaption`, `SpeakingIndicator`, `TranslationUnavailableMarker`, `SundanesePlaceholderRow`, `JumpToLatestPill`, `PairingCodeInput`, `PairingCodeDisplay`, `AudioLevelIndicator`, **`CallControlRow`** (old name), `QualityReviewRow`, `HerSideOneTapReaction`, `ThemePicker`, `BackgroundImagePicker`, `BackgroundImageOverlay`) | ADR-E5: **11 net-new components needed** (`CallTypeSelector`, `VideoTile`, `VideoPausedTile`, `VideoMutedTile`, `VideoCallControlRow`, `AudioRoutingToggle`, `CameraPermissionFlow`, `E2EEKeyExchangeIndicator`, `RejoinNotification`, `CallWaitingForPartnerState`) + **rename** `CallControlRow → AudioCallControlRow` to disambiguate from Video | 🚨 None of the 11 new components have UX specifications (Purpose / Anatomy / States / Variants / Accessibility / Interaction). Epics reference them by name (UX-DR17–DR22, DR24–DR26) but the canonical visual+interaction spec does not exist anywhere downstream of Architecture's name-and-purpose drop in ADR-E5. |
| §"AudioLevelIndicator" interaction notes (line 1158-1160) anti-evaluative constraint | Mirrored by Architecture Pattern §7 | ✅ Aligned |
| §"TranslationUnavailableMarker" — state-amber | Matches ADR-F3 + `state-amber` token | ✅ Aligned |
| §"SundanesePlaceholderRow" — `[Sundanese]` `text-tertiary` dim italic | Matches Architecture Pattern §7 `RenderMode.sundanesePlaceholder` | ✅ Aligned |
| §"Defining Experience — Caption Loop" — 9 beats | Matches Architecture Patterns §7 state-priority choreography + §11 ParticleProcessor + §9 Provider Abstraction surfaces | ✅ Aligned in concept; UX 9-beat narrative does not yet incorporate VideoPaused state, E2EEKeyExchange state, ModelLoading state which Architecture adds in §7 + ADR-F3 |
| §"Accessibility Considerations" (line 678-696) — Theme A/B high-contrast deepen | Architecture has no objection; Theme B removal makes this content half-obsolete | ⚠️ Recheck after Theme B removed |
| §"Phase-5 timing risk" note (line 1282) — fallback "ship v1 with Dark only, defer Light + Image" | Architecture S3 + AR-23 scope-cut runbook (Gap I.17) — already locked Theme B out and kept Theme C | ⚠️ UX risk-note framing is stale (suggests deferring Light, but Light is already cut) |

#### Component Inventory Reconciliation Math

- **UX spec custom components:** 17
- **Architecture ADR-E5 net-new components:** 10 (excluding rename)
- **Architecture rename:** 1 (`CallControlRow → AudioCallControlRow`)
- **Components fully spec'd post-reconciliation needed:** 27
- **Currently fully spec'd (Purpose/Anatomy/States/Variants/Accessibility/Interaction):** 17
- **Gap:** **10 components named-only in Architecture + Epics, but not spec'd in UX.** Implementation-readiness blocker if epic story authors are expected to consume UX-spec component definitions.

#### Architecture-Supports-UX Validation

- **Glass blur primitives (UX `MonochromeGlassPanel` thick/regular/thin):** Architecture confirms via `RenderEffect.createBlurEffect` (Android) + `Material` thicknesses (iOS) — ✅ supported.
- **Caption stack lazy rendering with stable identity:** Architecture confirms via `LazyColumn` + `rememberLazyListState` (Android) and `LazyVStack` + `ScrollViewReader` + `defaultScrollAnchor(.bottom)` (iOS), with `utterance_id` ULID as row key — ✅ supported.
- **Partial Caption Android-only / SpeakingIndicator iOS-only:** Architecture ADR-B5 explicitly enshrines this asymmetry — ✅ supported, and the architecture provides the rationale (Apple ASR can't stream `id-ID` on-device).
- **Auto-scroll suspend + JumpToLatestPill:** Architecture confirms `LaunchedEffect(captions.size)` + manual-scroll detection — ✅ supported.
- **Sundanese placeholder via ParticleProcessor.postProcess `RenderMode.sundanesePlaceholder`:** Architecture Pattern §11 + §7 — ✅ supported.
- **Animation timing (200-300ms primary, 150ms micro, cubic-bezier):** Architecture has no explicit timing pattern but does not contradict — ✅ implicit alignment via cross-platform parity NFR.
- **Theme C image background with adaptive overlay (0.40→0.55):** Architecture acknowledges (ADR-E2) and adds Video Call interaction; sandbox storage path implied — ✅ supported.
- **`E2EEKeyExchangeIndicator` one-time on first Call after pairing:** Architecture (FR-29 + Epic 5 Story 5.4) supports the mechanic; UX has no spec yet for the visual treatment.
- **Quality review surfaces (Bania-side per-Caption + her-side one-tap):** Architecture Pattern §13 + Epic 8 Stories 8.9 + 8.10 — ✅ supported architecturally; UX has detailed component specs ✅.
- **Cross-platform parity ("identical to the eye"):** Architecture §"Cross-Platform Parity" NFR + golden-file fixture harness — ✅ supported, mechanism in place.

### Alignment Issues

1. **🚨 [HIGH] Theme B drift.** UX still describes 3 themes; Architecture S3 + Epics UX-DR4 + Story 1.14 lock 2 themes. Affects: `ThemePicker` (renders 3 radio rows currently), entire §"Color System" subsection (Theme B token table), Phase-5 timing risk note, accessibility contrast table, `ux-design-directions.html` rendering. **Owner:** Story 1.14.
2. **🚨 [HIGH] Missing component specifications for 10 SCOPE-EXPANSION components.** Architecture ADR-E5 declared the names + purposes; UX must provide full Purpose / Anatomy / States / Variants / Accessibility / Interaction blocks before story implementation can proceed. Epics reference these components in UX-DR17 → UX-DR22, UX-DR24, UX-DR25, UX-DR26 but cite "UX-DR" identifiers that don't exist in the UX-spec file itself — they only exist in epics.md. **Owner:** Story 1.14.
3. **🚨 [HIGH] In-Call Video 50/50 layout missing from UX.** UX §"Spacing & Layout" specifies only Audio 40/60. Story 6.8 ("Video 50/50 In-Call Layout") in epics will need a UX-spec source-of-truth that doesn't exist. **Owner:** Story 1.14.
4. **⚠️ [MEDIUM] `CallControlRow` rename to `AudioCallControlRow` not yet applied to UX.** UX still uses old name everywhere; Architecture + Epics already use new name. Search-and-replace plus disambiguation entry needed. **Owner:** Story 1.14.
5. **⚠️ [MEDIUM] Caption Loop 9-beat narrative is incomplete vs. Architecture's failure-state taxonomy.** UX §"Defining Experience" describes 9 beats covering happy-path through translation-failure + Sundanese-placeholder; does not cover `videoPaused`, `e2eeKeyNotReady`, `modelLoading`, or `waitingForPartner` states which Architecture Pattern §7 adds. Epics' UX-DR31 says the loop renders "correctly" all 9 beats including these — but the canonical narrative document does not yet describe them. **Owner:** Story 1.14 (or follow-up UX iteration).
6. **⚠️ [MEDIUM] UX Open Component Questions (UX line ~1284) and Open Journey Questions (UX line ~988) appear unresolved.** Need to verify Step 5 whether epics absorbed answers or left them open.
7. **ℹ️ [LOW] `ux-design-directions.html` is a stale visual artifact** rendering 3 themes. Either regenerate after reconciliation, mark deprecated, or delete. Not a blocker for code work but a documentation-integrity item.

### Warnings

- **All three high-severity items above (Theme B, missing components, Video layout) trace back to a single root: the SCOPE EXPANSION reconciliation has not happened yet.** Story 1.14 is the designated mechanism. Mitigation = run Story 1.14 as a strict predecessor to ANY story that depends on the SCOPE-EXPANSION FRs/components (Stories 2.9, 5.x, 6.x, 7.x, 8.1 thru 8.4, 8.7, plus Story 6.1's `CallTypeSelector` evolution and UX-DR17 surfacing in Epic 2).
- The risk if Story 1.14 is *not* sequenced first: implementation stories will reference UX-DR identifiers that don't exist in the UX spec, FR identifiers (FR-26 → FR-32) that don't exist in the PRD, and component specifications that exist only as architectural names without visual+interaction definitions. Implementation agents would either invent specifications (drift inward) or block on missing context (drift outward).
- **Architecture's role-as-canonical:** Architecture's frontmatter (declared by epics) sets Architecture as the source-of-truth tiebreaker until reconciliation lands. This is unusual for BMAD (PRD is typically canonical) — downstream agents must be explicitly told this until Story 1.14 closes the gap.

UX alignment assessment complete. Auto-proceeding to Epic Quality Review (Step 5).

---

## Step 5 — Epic Quality Review

Applied `bmad-create-epics-and-stories` quality standards (user-value focus, epic independence, story sizing, no forward dependencies, BDD acceptance criteria, database/entity timing, starter-template handling).

### Epic-Level Findings

#### A. User Value Focus

| Epic | Title | User-Centric? | Notes |
|---|---|---|---|
| 1 | Foundation & Pairing | ✅ Yes | Pairing IS the user outcome. Includes 7 scaffolding stories (1.1-1.7) and 1 reconciliation story (1.14) alongside 6 user-facing pairing stories (1.8-1.13). Acceptable for greenfield Epic 1; long ramp before user value visible, but explicit in PRD §10.3 timeline. |
| 2 | Audio Calling | ✅ Yes | Place/receive/end native VoIP — directly delivers FR-6 through FR-10. |
| 3 | One-Direction Translation Pipeline | ⚠️ Borderline | Epic goal explicitly states: *"Proves the end-to-end translation pipeline works for one direction."* Speaker sees own captions, partner sees nothing. Degenerate user experience; Epic 3+4 together = user-shippable. Acceptable as a validation milestone given Epic 4 immediately follows and is structured for it. |
| 4 | Bidirectional Captions & Failure States | ✅ Yes | This is the actual UJ-3 delivery — both partners reading each other's translations. |
| 5 | End-to-End Encryption | ✅ Yes (privacy NFR) | E2EE is invisible to users except via the one-time `E2EEKeyExchangeIndicator`. Counts as user value because it realizes the WhatsApp-equivalent privacy promise (NFR-Privacy). |
| 6 | Video Calling | ✅ Yes | Direct FR-26 / FR-27 / FR-30 delivery. |
| 7 | Leave-and-Rejoin Resilience | ✅ Yes | Resilience IS user value when it kicks in. |
| 8 | Personalization, Settings & Post-Call Surfaces | ✅ Yes | Theming + Settings + post-Call quality review surfaces. |

**No "technical milestone" epics in the wrong sense.** Epic 1's scaffolding stories are appropriate for greenfield first-epic; not a violation.

#### B. Epic Independence (Epic N functions from Epic 1..N-1 outputs)

| Epic | Depends on | Forward dependency on later epic? |
|---|---|---|
| 1 | — (greenfield) | None |
| 2 | Epic 1 (sign-in, pairing, scaffold, App Check, Oracle VM) | None |
| 3 | Epic 1+2 (Calls established) | None |
| 4 | Epic 1+2+3 (translation pipeline) | None |
| 5 | Epic 1 (identity keypair from Story 1.12), Epic 2 (Calls), Epic 4 (Data Channel from Story 4.1 — must be wired before Insertable Streams encrypts it) | None |
| 6 | Epic 2 (Call mechanics), Epic 5 (`e2eeOptions.keyProvider` must already be wired for Video to also be E2EE) | None |
| 7 | Epic 2 (Call lifecycle), Epic 4 (Captions continue rendering for remaining side) | None |
| 8 | Epic 2 (Call lifecycle for post-Call sheets), Epic 4 (Captions for review), Epic 3 (Story 8.7 status of post-editor depends on Story 3.9 outcome) | None |

**No epic has a forward dependency on a later epic.** ✅ Clean DAG.

### Story-Level Findings

#### Acceptance Criteria Quality (~70 stories sampled)

- ✅ Every story uses **Given / When / Then BDD format** consistently.
- ✅ ACs are typically 5–12 per story with **measurable outcomes** (latency <3s, library version pin, specific SDK API name, error-code identifier, deterministic acceptance test).
- ✅ Error / edge cases included throughout (e.g., Story 1.10 covers 3 distinct input-error variants; Story 2.4 covers PushKit-failure silent-on-recipient edge; Story 3.4 covers ASR low-confidence + empty + permission denial).
- ✅ FR / NFR traceability cited inline (`(FR-6 acceptance)`, `(NFR-Quality)`, `(UX-DR…)`).

#### Story Sizing

- ✅ Single-deliverable stories throughout. No "Setup all models" type violations.
- ⚠️ **Story 1.5** wraps SafeLog + lint enforcement + ULID library wiring — three logically distinct concerns. Coherent as a single story (both are cross-cutting primitives) but could be split into 1.5a (SafeLog) and 1.5b (ULID) for cleaner tracking. **Severity: minor.**
- ⚠️ **Story 1.7** creates 7 shared-spec files (`canonical-names.md`, `error-codes.md`, `data-channel-schema-v1.json`, `auth-proxy-api.md`, `state-derivation.md`, `particle-rules-fixtures/`, `regression-corpus/`). Large but coherent. **Severity: minor.**
- 🟠 **Story 1.14** carries the entire PRD + UX-spec reconciliation: rewrites PRD §6.1, §10.2, §10.3, §11; surfaces FR-26 through FR-32; rewrites UX `ThemePicker`; adds 11 new UX component specs; rewrites In-Call layouts; documents Theme C × Video. **Effort is realistically multi-day for a single story.** Strong recommendation to split: Story 1.14a (PRD reconciliation) + Story 1.14b (UX reconciliation, including 11 new component specs with Purpose/Anatomy/States/Variants/Accessibility/Interaction blocks). **Severity: major.**
- ✅ All other stories appear individually sizable for solo-dev cadence.

#### Within-Epic Dependency Ordering

Sampled for all 8 epics:

- **Epic 1:** Stories 1.1, 1.2, 1.3, 1.4 (parallel scaffolds) → 1.5 (SafeLog + ULID) → 1.6 (CI/CD) → 1.7 (shared specs) → 1.8 (sign-in) → 1.9 (display code) → 1.10 (enter code) → 1.11 (persist) → 1.12 (identity key) → 1.13 (settings shell) → 1.14 (reconciliation). Linear and clean.
- **Epic 2:** 2.1 (auth-proxy) → 2.2 (Paired home + CallSession scaffolding) → 2.3 (place call) → 2.4 (iOS incoming) + 2.5 (Android incoming) → 2.6 (accept/reject) → 2.7 (In-Call layout) → 2.8 (end Call) → 2.9 (audio routing) → 2.10 (audio quality acceptance test) → 2.11 (failure-state seeds). Clean.
- **Epic 3:** 3.1 (pre-validation conversation) → 3.2 (ParticleProcessor) → 3.3 (interfaces) → 3.4/3.5 (ASR impls) → 3.6 (VAD) → 3.7 (regression corpus) → 3.8 (NLLB-200) → 3.9 (validation gate) → 3.10 (decorator) → 3.11 (debug tracing) → 3.12 (CaptionStack) → 3.13 (Partial/Speaking) → 3.14 (E2E speaker-side) → 3.15 (failure marker). Linear, each story builds on prior.
- **Epic 4:** 4.1 (Data Channel schema) → 4.2 (publish) → 4.3 (receive + render) → 4.4 (failure marker full) → 4.5 (Sundanese placeholder) → 4.6 (auto-scroll suspend) → 4.7 (JumpToLatestPill) → 4.8 (state-priority choreography) → 4.9 (E2E validation). Clean.
- **Epic 5:** 5.1 (per-call ephemeral key) → 5.2 (ECDH) → 5.3 (Insertable Streams wiring) → 5.4 (`E2EEKeyExchangeIndicator`) → 5.5 (privacy verification protocol). Clean.
- **Epic 6:** 6.1 (CallTypeSelector) → 6.2 (camera permission) → 6.3 (local video) → 6.4 (VideoTile) → 6.5 (VideoPausedTile) → 6.6 (VideoMutedTile) → 6.7 (VideoCallControlRow) → 6.8 (Video 50/50 layout). Clean.
- **Epic 7:** 7.1 (`empty_timeout: 300`) → 7.2 (LeaveAndRejoinManager) → 7.3 (CallWaitingForPartnerState overlay) → 7.4 (RejoinNotification) → 7.5 (rejoin flow) → 7.6 (after-5-min cleanup) → 7.7 (E2E validation). Clean.
- **Epic 8:** 8.1 (Theme C tokens + ThemePicker) → 8.2 (image picker) → 8.3 (image overlay) → 8.4 (Theme C × Video) → 8.5 (display name) → 8.6 (transcript history) → 8.7 (post-editor status — depends on 3.9) → 8.8 (privacy summary) → 8.9 (QualityReviewRow) → 8.10 (HerSideOneTapReaction). Clean.

**Story 8.7 backward dependency on Story 3.9** is the only cross-epic story-level dependency I found. Backward (8 > 3) so legal per BMAD rules. ✅

#### Forward Dependencies

Found **zero within-epic forward references**. Strong sequencing discipline throughout.

#### Database / Entity Creation Timing

- ✅ Firestore documents created where first needed: `/codes/{6digit}` in Story 1.9, `/pairs/{pairId}` in Story 1.10, `/users/{uid}/identityPub` in Story 1.12, `/calls/{callId}/ephemeralPub/{uid}` in Story 5.1, `/pairs/{pairId}/herReactionAggregate` in Story 8.10. No "Epic 1 Story 1 creates all tables" violation.
- ✅ Local DB schema `CallRecord` + `CaptionRecord` introduced in Story 8.6 (transcript history is the first feature that needs them). `QualityReviewRecord` in Story 8.9. `HerReactionRecord` in Story 8.10.
- ✅ Firestore rules in Story 1.4 (baseline) extended as new collections appear.

#### Starter Template Handling

- ✅ Architecture specifies "Per-Stack Native Scaffold Set" (not one starter, but four parallel scaffolds: Android Compose / Xcode SwiftUI / Oracle Docker Compose / Firebase init).
- ✅ Stories 1.1 (Android), 1.2 (iOS), 1.3 (Oracle VM), 1.4 (Firebase) cover all four scaffolds, each with version-pinning + manifest declarations + smoke test. Aligns with Architecture §"Selected Starter."
- ✅ CI/CD per stack early (Story 1.6).

### Findings by Severity

#### 🔴 Critical Violations

1. **Story 1.14 ("PRD + UX-Spec Reconciliation Deliverables") is the LAST story in Epic 1, but downstream Epic 2-8 stories reference its outputs as already-existing inputs.**

    Specific examples:
    - **Epic 2 Story 2.7** ("In-Call Screen — Audio 40/60") cites `UX-DR16` and `AudioCallControlRow` — both currently exist only in epics.md and Architecture, not in UX spec until 1.14 lands.
    - **Epic 2 Story 2.9** ("Audio Routing Toggle") cites `FR-28` and `AudioRoutingToggle` (`UX-DR20`) — `FR-28` not in PRD until 1.14; `AudioRoutingToggle` not spec'd in UX until 1.14.
    - **Epic 3 Stories 3.4 / 3.5** cite `FR-13 Android` / `FR-13 iOS` with relaxed targets that match epics NFR-Latency but contradict PRD SM-4 — readers reconciling against PRD would see contradiction until 1.14 lands.
    - **Epic 5 Story 5.4** references `E2EEKeyExchangeIndicator` (named in Architecture ADR-E5; no UX visual spec until 1.14).
    - **Epic 6 Stories 6.1, 6.5, 6.6, 6.7, 6.8** reference `FR-26`, `FR-27`, `FR-30` and components `CallTypeSelector`, `VideoTile`, `VideoPausedTile`, `VideoMutedTile`, `VideoCallControlRow` — none exist in PRD or UX until 1.14 lands.
    - **Epic 7 Stories 7.3, 7.4** reference `FR-32`, `CallWaitingForPartnerState`, `RejoinNotification` — same situation.
    - **Epic 8 Stories 8.1-8.4** reference 2-option `ThemePicker` (Theme C image-background only, Theme B removed) — UX still has 3-option ThemePicker until 1.14.

    **Remediation (choose one):**
    - **(a) PREFERRED:** Re-sequence Story 1.14 as Story 1.0 (the very first story of Epic 1) — even before scaffolding. The reconciliation is a documentation rewrite that does not depend on any scaffolding. This way, by the time any downstream story is picked up, PRD and UX already reflect the SCOPE EXPANSION.
    - **(b)** Split Story 1.14 into 1.14a (PRD reconciliation) + 1.14b (UX reconciliation) and run BOTH as first Epic-1 stories before any scaffolding.
    - **(c)** Declare Story 1.14 an explicit predecessor for every downstream story that cites a SCOPE-EXPANSION FR / component / target.

    **Severity: CRITICAL** — without this fix, ~25 downstream stories reference identifiers that don't yet exist in their authoritative source documents.

#### 🟠 Major Issues

2. **Story 1.14 is heavily overloaded** (see story-sizing notes above). Even with re-sequencing, this single story does too much to track as one deliverable. Recommend splitting per (b) above: 1.14a (PRD rewrites for §6.1, §10.2, §10.3, §11, §4 additions of FR-26-32) and 1.14b (UX rewrites: ThemePicker 2-option, 11 new component specs, In-Call Video layout, Theme C × Video).

3. **No dedicated story for the v1-ship-gate TQ-AT (PRD §5.3 / NFR-Quality).** The TQ-AT specifies: 10 sample Calls × ≥3 sessions × ≥20-min each, paired ✅/⚠️/❌ rating with ≥60% ✅ AND no Call >2 ❌. Story 4.9 covers Epic-4 completion validation (a 10-minute test); Story 8.9 builds the per-Caption review tool. Neither is the v1-ship-gate test itself. Recommend adding a **Story 8.11: Run TQ-AT v1 Ship Gate** as the final story before declaring v1 shippable.

4. **Epic 3 ships degenerate UX** (one-direction translation, partner sees nothing). Acknowledged trade-off but flags a real risk: if Epic 4 slips or is blocked, Bania has an "almost-but-not-actually-useful" build. Mitigation: ensure Epic 3 closure includes verification that demo + Epic 4 kickoff happen back-to-back; the Story 3.9 validation gate is a natural place to confirm this sequencing.

#### 🟡 Minor Concerns

5. **Story 1.5 wraps 3 distinct primitives** (SafeLog + lint + ULID). Coherent but could split for cleaner tracking. Not a blocker.
6. **Story 1.7 creates 7 shared-spec files** in one story. Coherent (cross-platform contracts) but heavy.
7. **NFR-Latency target (median <8s, p95 <12s)** is borderline-slow for conversational use. Documented as accepted trade-off in NFR-Latency + escape valve in Story 3.9 (Plan B). Not a quality violation — but worth noting that if real-conversation friction in Week 2-3 reveals the latency is felt as too slow, Plan B activation may be necessary even if Plan A quality bake-off passes.
8. **The `ux-design-directions.html` HTML mockup** is included in epics' frontmatter as an input document but renders 3 themes. After Story 1.14 lands, either regenerate or mark deprecated.

### Best Practices Compliance Checklist

| Standard | Status | Notes |
|---|---|---|
| Epic delivers user value | ⚠️ 7/8 fully; Epic 3 borderline (acknowledged) | |
| Epic can function independently | ✅ 8/8 | Clean DAG; no forward epic dependencies |
| Stories appropriately sized | ⚠️ 68/70 yes; Story 1.5 + Story 1.14 oversized | |
| No forward dependencies | ✅ Within-epic | Only one cross-epic dep (8.7 → 3.9, backward, legal) |
| Database tables created when needed | ✅ | Just-in-time schema introduction |
| Clear BDD acceptance criteria | ✅ | Given/When/Then throughout with measurable outcomes |
| Traceability to FRs / NFRs maintained | ✅ | All ~70 stories cite FR / NFR / UX-DR / AR identifiers inline |
| Starter-template usage | ✅ | Per-stack scaffold stories 1.1-1.4 explicit |
| Greenfield setup completeness | ✅ | Project setup + dev env + CI/CD all in Epic 1 |
| Privacy / security stories surface as explicit acceptance criteria | ✅ | NFR-Privacy verified in Stories 1.5 (SafeLog), 5.5 (packet-capture protocol), 8.6 (transcript-history local-only), 8.8 (Crashlytics gating), 8.10 (aggregated counts only) |

Epic quality review complete. Proceeding to Final Assessment (Step 6).

---

## Summary and Recommendations

### Overall Readiness Status

**🟡 NEEDS WORK — Ready-after-prerequisites**

The planning artifacts are individually high-quality and the epic-story structure is rigorously well-formed. The blocker is a **single-source-of-truth drift between Architecture (which underwent a SCOPE EXPANSION) and the upstream PRD + UX-spec (which were not yet rewritten to match)**. The epics correctly cover all 31 FRs and ~70 stories are well-sized, BDD-formatted, and dependency-clean, but ~25 downstream stories reference FR identifiers (FR-26 → FR-32), NFR-targets, and UX component specs that **do not yet exist in their authoritative source documents**.

The remediation is mechanical and well-scoped (Story 1.14, currently positioned as the LAST story of Epic 1 — see Critical Issue #1) but must run **before** any of the downstream stories that depend on its outputs.

### Critical Issues Requiring Immediate Action

1. **🚨 [CRITICAL] Re-sequence Story 1.14 before downstream stories that reference SCOPE-EXPANSION items.** Currently Story 1.14 is the last Epic-1 story, after all pairing UI stories — but at least 25 stories in Epics 2 / 5 / 6 / 7 / 8 reference FRs (FR-26 → FR-32), NFR targets, and UX components that only exist after 1.14 lands. Recommended fix: **make Story 1.14 the FIRST story of Epic 1** (run it before scaffolding — it's a pure documentation rewrite with no dependency on any code).

2. **🚨 [CRITICAL] PRD ↔ Architecture drift on 7 FRs.** PRD `prd.md` rev 2 contains FR-1 through FR-24 only. Architecture / epics add FR-26 (CallTypeSelector), FR-27 (Video), FR-28 (audio routing), FR-29 (E2EE), FR-30 (camera permission), FR-31 (on-device translation, replacing FR-14 cloud Gemini path), FR-32 (leave-and-rejoin). These are real v1 capabilities. PRD §9 / §10.2 explicitly *contradicts* this — still lists Video and true E2EE as v2-deferred.

3. **🚨 [CRITICAL] PRD ↔ Architecture drift on NFR targets.** PRD SM-2 (≥80% quality), SM-4 (median <3.5s latency), SM-5 (<30%/hour battery), §6.5 (Cloud Run + warmup ping), §8 (Cloud Run backend), §4.3 + Addendum (Gemini AI Studio), §10.3 (4-6 weeks) all contradict their epic-doc counterparts. All targets in epics are *relaxed* or *replaced* versus PRD; the entire backend architecture is swapped (Cloud Run → Oracle VM); the entire translation provider is swapped (Gemini cloud → on-device NLLB/MADLAD/Gemma with Vertex AI Gemini fallback).

4. **🚨 [CRITICAL] UX spec missing component specifications for 10 SCOPE-EXPANSION components.** Architecture ADR-E5 named `CallTypeSelector`, `VideoTile`, `VideoPausedTile`, `VideoMutedTile`, `VideoCallControlRow`, `AudioRoutingToggle`, `CameraPermissionFlow`, `E2EEKeyExchangeIndicator`, `RejoinNotification`, `CallWaitingForPartnerState` — but UX `ux-design-specification.md` has zero Purpose/Anatomy/States/Variants/Accessibility/Interaction blocks for them. UX still describes 3 themes (Theme B not removed per Architecture S3) and has no Video 50/50 layout (only Audio 40/60). Story 1.14b would close this.

5. **🟠 [MAJOR] Story 1.14 is heavily overloaded.** Splitting into Story 1.14a (PRD reconciliation) + Story 1.14b (UX reconciliation + 10 new component specs) is strongly recommended to make the work trackable as discrete deliverables.

6. **🟠 [MAJOR] No dedicated v1-ship-gate TQ-AT story.** PRD §5.3 specifies a 10-Call × ≥3-session × ≥20-min paired-review acceptance test with explicit ≥60% ✅ / no-Call->2-❌ threshold as the SHIP gate for SM-2. Story 4.9 covers Epic-4 completion (a single 10-min test), Story 8.9 builds the `QualityReviewRow` tool — but no story is "Run the TQ-AT and decide ship/no-ship." Recommend adding **Story 8.11: TQ-AT v1 Ship Gate Execution.**

### Recommended Next Steps (in order)

1. **Run Story 1.14a (PRD reconciliation) immediately.** Update `prd.md`:
    - §4: surface FR-26 → FR-32 with full Description + Consequences sections.
    - §4.3 / FR-13 / FR-14: rewrite translation pipeline to describe on-device path (Plan A) with Vertex AI fallback (Plan B); explicitly note FR-14 is superseded by FR-31.
    - §6.1: rewrite privacy posture to WhatsApp-equivalent E2EE; demote Gemini AI Studio caveat to Plan B-only note.
    - §6.5: remove "Warmup ping pattern"; replace with "Backend always-on (Oracle VM)" pattern.
    - §8: replace Cloud Run with Oracle VM (Ampere A1, Ubuntu 24.04 LTS ARM).
    - §9 / §10.2: remove Video and true E2EE from v2-deferral lists; surface in §10.1 as v1 in-scope.
    - §10.3: revise timeline to 7–10 weeks + 1 ramp.
    - §11: revise SM-2 (≥60%, or ≥80% under Plan B), SM-4 (median <8s, p95 <12s), SM-5 (best-effort).
    - Update decision log + frontmatter `updated: 2026-05-22` and `reconciled-with: architecture.md`.

2. **Run Story 1.14b (UX reconciliation) immediately after.** Update `ux-design-specification.md`:
    - §"Color System": remove Theme B section entirely; reduce "Three Themes" framing to "Two Themes"; update accessibility contrast table; remove Phase-5 risk-note about deferring Light theme (already cut).
    - §"Component Strategy" / §"Custom Components": add full Purpose/Anatomy/States/Variants/Accessibility/Interaction blocks for the 10 net-new components per ADR-E5; rename `CallControlRow → AudioCallControlRow` everywhere.
    - §"Defining Experience" / Caption Loop: extend 9-beat narrative to incorporate `videoPaused`, `e2eeKeyNotReady`, `modelLoading`, `waitingForPartner` states per Architecture Pattern §7.
    - §"Spacing & Layout": add Video Call 50/50 In-Call vertical split; document Theme C × Video interaction per ADR-E2.
    - §"ThemePicker" component: rewrite to 2 options (Dark + Image).
    - Either regenerate or mark deprecated: `ux-design-directions.html`.

3. **Then begin Epic 1 scaffolding (Stories 1.1, 1.2, 1.3, 1.4 in parallel)** with PRD + UX already reflecting the canonical scope.

4. **Consider splitting Story 1.5** (SafeLog + ULID) into 1.5a + 1.5b for cleaner tracking — minor, can wait.

5. **Add Story 8.11 (TQ-AT v1 Ship Gate)** to the end of Epic 8 as the explicit ship-gate runbook execution. Should reference PRD §5.3 acceptance criteria.

6. **Verify alignment after 1.14a/b lands:** re-run a focused diff between the new PRD/UX content and the epics' "Requirements Inventory" + "FR Coverage Map" sections. Any residual contradictions are bugs.

### Risk Acknowledgments (not blockers, but flagged)

- **NFR-Latency target (median <8s, p95 <12s)** is borderline-slow for conversational use. Acceptance gates and Plan B escape valve are in place. Real-conversation friction in Week 2-3 may force Plan B activation even if Plan A bake-off passes.
- **Epic 3 ships degenerate UX** (one-direction translation, partner sees nothing). Acceptable as a validation milestone but only if Epic 4 follows immediately without scope slip.
- **Single-failure-domain risk** (auth-proxy + LiveKit + Redis all on one Oracle Ampere A1 VM) is acknowledged in AR-19. Acceptable for personal-use scale but worth a backup-plan note in the runbook.
- **`project-context.md` absent.** Persistent-fact load returned empty — non-blocking but a fast win to generate (could be done after Story 1.14 lands, since it summarizes the now-stable canonical state).

### Counts

- **PRD FRs:** 24 (FR-1 → FR-24, FR-25 reserved) — **100% covered in epics.**
- **Epic FRs:** 31 active — **77% traceable back to PRD** (7-FR gap reconcilable via Story 1.14a).
- **NFR drift items:** 6 quantitative targets relaxed/swapped + 2 new NFRs added + 1 entire architecture component obsoleted + 1 entire vendor obsoleted — all reconcilable via Story 1.14a.
- **UX components specified:** 17 (covers PRD-original components). **10 SCOPE-EXPANSION components named but not spec'd** — reconcilable via Story 1.14b.
- **Epics:** 8. **Stories:** ~70 (well-sized, BDD-formatted, dependency-clean).
- **Critical issues:** 4. **Major issues:** 2. **Minor concerns:** 4.

### Final Note

This assessment identified **10 issues across 3 categories** (PRD/UX/Architecture drift, Story 1.14 sequencing, missing TQ-AT ship-gate story). All four CRITICAL items have a single mechanical remediation: **execute Story 1.14 (preferably split into 1.14a + 1.14b) as Epic 1's first deliverable, before any scaffolding work.** Once that reconciliation lands, the planning artifacts are coherent and implementation can proceed against a clean canonical baseline.

The underlying planning work — Architecture decisions, FR coverage, story structure, BDD acceptance criteria, dependency graph, error-handling discipline, privacy-safe observability, cross-platform parity discipline — is strong and ready. The gap is purely document-reconciliation. **Address Critical Issue #1 (re-sequence Story 1.14) and you can begin Epic 1 immediately.**

---

## Post-Assessment Reconciliation Execution Log (2026-05-22)

After the initial assessment completed, Bania granted autonomous execution authority ("You decide all these and work autonomously"). The reconciliation work was executed end-to-end in this same session. Below is the verified post-execution state.

### Updated Status: 🟢 READY FOR IMPLEMENTATION

All four CRITICAL issues and one of the two MAJOR issues from the original assessment have been resolved through direct file edits. The remaining MAJOR item (Story 8.11 TQ-AT ship gate) has been added to the backlog as a new story. The four MINOR concerns persist as low-priority items, none blocking.

### What Was Done

**1. ✅ Story 1.14 split into 1.14a (PRD) + 1.14b (UX) — both executed as Epic-1 prerequisites**

`epics.md` updated: Story 1.14 replaced with three stories at the original location:
- **Story 1.14a: PRD Reconciliation** — marked ✅ COMPLETED 2026-05-22; full acceptance criteria reflect what was done.
- **Story 1.14b: UX Spec Reconciliation** — marked ✅ COMPLETED 2026-05-22; full acceptance criteria reflect what was done.
- **Story 1.14c: Solo-Dev Scope-Cuts Runbook** — REMAINS PENDING; deferred until `/docs/` directory exists post-scaffolding. Low-effort (~30 min) follow-up.

Sequencing note added to both 1.14a and 1.14b explicitly stating they were executed as the FIRST Epic-1 deliverable (before any scaffolding), eliminating the Critical Issue #1 forward-dependency risk.

**2. ✅ PRD rewritten per Architecture §G (Critical Issues #2 + #3)**

`prd.md` rev 2 → **rev 3** (543 → 640 lines). Specifically:
- **Frontmatter:** `revision: 3`, `reconciled-with: architecture.md`.
- **§4.2 extended:** FR-26 (Audio vs Video selection), FR-28 (audio routing), FR-30 (lazy camera permission).
- **§4.3 rewritten:** on-device-first translation pipeline; FR-14 SUPERSEDED by FR-31 for v1 Plan A; FR-14 cloud-Gemini path retained as Plan B fallback only.
- **§4.5 FR-22:** NOTE-FOR-PM extended for Plan-A/B outcome dependency.
- **§4.6 / §4.7 / §4.8 NEW:** Video Pipeline (FR-27), End-to-End Encryption (FR-29), Resilience (FR-32).
- **§6.1 fully rewritten:** WhatsApp-equivalent E2EE; Gemini AI Studio caveat demoted to Plan-B-only note.
- **§6.5:** Warmup ping pattern removed; replaced with always-on Oracle VM.
- **§8 Platform:** Cloud Run replaced with Oracle Ampere A1 VM + Caddy + Node.js auth-proxy + Docker Compose; xaeryx.com domain added.
- **§9 + §10.2:** Video and "true E2EE between clients" removed from v2-deferred lists.
- **§10.1 in-scope:** 4 new bullets added; backend-proxy bullet replaced.
- **§10.3 Timeline:** revised to 7–10 weeks + 1 ramp.
- **§11 SM-2 / SM-4 / SM-5:** targets relaxed per on-device inference reality; Plan B revert paths documented.
- **§12 Open Questions 4 + 7:** marked RESOLVED.
- **§13 Assumptions Index:** appended with 10 reconciliation-resolution entries.

**3. ✅ UX spec rewritten per Architecture §G + ADR-E1 through ADR-E5 (Critical Issue #4)**

`ux-design-specification.md` (1565 → 1743 lines, +178 lines, +22 KB). Specifically:
- **Frontmatter:** `reconciled-with`, `reconciled-date`, `reconciliation-notes` added.
- **Theme B removed comprehensively** — 16 sites touched (Color System heading, Theme B token table deleted, Typography + Spacing headings updated, accessibility contrast bullets, parity contract example, Forward-to-Step-9, Design Directions intro + row #4, Implementation Roadmap Phase-5 risk note, Testing Strategy matrix, ThemePicker a11y label, Customization Strategy table).
- **In-Call vertical split rewritten:** Audio 40/60 + Video 50/50 table + explicit-reject note for backdrop-video-with-captions-overlay.
- **NEW subsection "Theme C × Video Interaction"** added per ADR-E2.
- **`CallControlRow` → `AudioCallControlRow`** renamed throughout (component spec + all inline references).
- **`ThemePicker` component spec** rewritten to 2-option (Dark + Image).
- **10 NEW component specifications added** in §"Custom Components" with full Purpose / Anatomy / States / Variants / Accessibility / Interaction blocks per existing template, plus per-component "*Implementation: Epic X Story Y.Z*" forward-references:
  1. `CallTypeSelector` (~1.1 KB) — Epic 6 Story 6.1
  2. `AudioRoutingToggle` (~1.2 KB) — Epic 2 Story 2.9
  3. `VideoCallControlRow` (~1.3 KB) — Epic 6 Story 6.7
  4. `CameraPermissionFlow` (~1.5 KB) — Epic 6 Story 6.2
  5. `VideoTile` (~1.1 KB) — Epic 6 Story 6.4
  6. `VideoPausedTile` (~1.3 KB, **neutral grey NOT amber** per ADR-F3) — Epic 6 Story 6.5
  7. `VideoMutedTile` (~1.1 KB) — Epic 6 Story 6.6
  8. `E2EEKeyExchangeIndicator` (~1.3 KB) — Epic 5 Story 5.4
  9. `RejoinNotification` (~1.2 KB, local notification NOT CallKit re-ring) — Epic 7 Story 7.4
  10. `CallWaitingForPartnerState` (~1.5 KB) — Epic 7 Story 7.3
- **Caption Loop extended** with "Additional failure-state beats (per Architecture §7)" sub-section listing all 8 priority-ordered states (`e2eeKeyNotReady` → `modelLoading` → `waitingForPartner` → `networkDropped` → `translationFailed` → `videoPaused` → `sundanesePlaceholder` → `asrLowConfidence`) + one-banner-at-a-time rule.
- **Phase-2 Implementation Roadmap row** updated to include `AudioRoutingToggle` + `CallTypeSelector`.
- **Components-NOT-in-v1 list** updated (Video tile + E2EE indicator removed — now v1).

**4. ✅ Story 8.11 (TQ-AT v1 Ship Gate Execution) added (Major Issue #2)**

`epics.md` extended with new Story 8.11 at end of Epic 8 — full BDD acceptance criteria for the PRD §5.3 TQ-AT execution:
- 10 sample Calls × ≥3 sessions × ≥20-min each, paired review (Bania + girlfriend together).
- Per-TQ-category breakdown (TQ-1 → TQ-8 traceability).
- Ship thresholds explicit per Plan A (≥60%, no Call >2 ❌) and Plan B (≥80%, no Call >2 ❌).
- Three-outcome decision (PASS / FAIL-on-Plan-A → activate Plan B / FAIL-on-both → scope-cut decision).
- SM-7 qualitative win-condition verified alongside.
- `HerSideOneTapReaction` aggregate (Story 8.10) reviewed as corroborating signal.

**5. ✅ PRD Addendum marked as superseded-by-architecture**

`addendum.md` frontmatter updated: `status: companion` → `status: superseded-by-architecture`; `reconciled-with: architecture.md` added. A prominent ⚠️ SCOPE EXPANSION NOTICE added at the top declaring which sections remain valid (FR-1 → FR-5, FR-6 → FR-9, FR-10, FR-11 + FR-13, FR-15, FR-17 → FR-20, FR-21, FR-24) and which are superseded (FR-14 backend Cloud Run + Gemini AI Studio; §6.5 warmup; FR-22 reflow chain — all retained as Plan-B-only reference).

### Remaining Minor Concerns (not blocking)

- **Story 1.5 wraps SafeLog + lint + ULID** — splittable but coherent. Not addressed; not a blocker.
- **Story 1.7 creates 7 shared-spec files** — large but coherent. Not addressed; not a blocker.
- **NFR-Latency target (median <8s)** — borderline-slow; acceptable trade-off documented with Plan B escape valve. Watch for real-conversation friction in Week 2-3 of build.
- **`ux-design-directions.html` HTML mockup** — still renders 3 themes; flagged in UX spec but not regenerated. Optional follow-up.
- **Story 1.14c (Solo-dev scope-cuts runbook)** — deferred until `/docs/` exists post-scaffolding. ~30-min follow-up.

### Verification: PRD ↔ Epics ↔ UX Coverage After Reconciliation

| Item | Pre-Reconciliation | Post-Reconciliation |
|---|---|---|
| **PRD FR count** | 24 (FR-1 → FR-24) | **31 active** (FR-1 → FR-32; FR-14 superseded, FR-25 reserved) |
| **PRD ↔ Epics FR coverage** | 24/24 PRD (100%); 24/31 epics (77%) | **31/31 (100%) bidirectional** |
| **PRD NFR targets matching epics** | 6 drift items | **0 drift** |
| **PRD Translation Provider** | Gemini AI Studio (free) | **On-device (Plan A) + Vertex AI Gemini (Plan B)** |
| **PRD Backend** | Cloud Run + warmup ping | **Oracle Ampere A1 VM (always-on)** |
| **PRD Privacy posture** | WebRTC SFU sees plaintext; E2EE deferred to v2 | **WhatsApp-equivalent E2EE; translation never leaves device (Plan A)** |
| **PRD Timeline** | 4-6 weeks + 1 ramp | **7-10 weeks + 1 ramp** |
| **UX themes** | 3 (Dark + Light + Image) | **2 (Dark + Image)** |
| **UX component count** | 17 spec'd | **27 spec'd** (17 original + 10 new per ADR-E5; `CallControlRow` renamed to `AudioCallControlRow`) |
| **UX In-Call layout** | Audio 40/60 only | **Audio 40/60 + Video 50/50** |
| **UX Caption Loop coverage** | 9 happy-path beats | **9 happy-path beats + 8 failure-state beats with priority order** |
| **Epics story count** | ~70 | **~71** (Story 1.14 split into 1.14a/b/c, Story 8.11 added — net +2 stories) |
| **Story 1.14 sequencing risk** | LAST Epic-1 story (forward-dependency risk on ~25 downstream stories) | **Executed FIRST as Epic-1 prerequisite** (no longer a risk) |
| **TQ-AT v1 ship gate** | Implicit only (no dedicated story) | **Explicit (Story 8.11)** |
| **PRD Addendum status** | Carried unmarked-as-stale references to Cloud Run + Gemini AI Studio | **Marked `superseded-by-architecture` with Plan-B-only scope notice** |

### Final State

**🟢 READY FOR IMPLEMENTATION.**

All planning artifacts are now mutually coherent. Bania can begin Epic 1 implementation work (Stories 1.1 → 1.13) immediately. The originally-blocking Story 1.14 reconciliation has been completed in this same session via autonomous execution; downstream Epic 2-8 stories now reference FR identifiers, NFR targets, and UX components that exist in their authoritative source documents.

**Recommended first implementation moves:**
1. Begin Stories 1.1, 1.2, 1.3, 1.4 in parallel (Android scaffold / iOS scaffold / Oracle VM + Docker Compose + domain / Firebase init).
2. After Story 1.1 lands `/docs/` directory, knock out Story 1.14c (Solo-dev scope-cuts runbook, ~30 min) for completeness.
3. Proceed to Stories 1.5 → 1.13 per the linear sequencing already in the epic.

The reconciliation work is durably captured in three places: the rewritten artifacts themselves, the Story 1.14a/b/c records in epics.md, and this execution log section of the readiness report.

---

## Post-Assessment Implementation Progress Log (2026-05-22, continued)

After the reconciliation completed, Bania said "Go." Continued autonomous execution focused on the credential-free / external-account-free work in Epic 1.

### Stories Completed Autonomously

| Story | Status | Deliverables |
|---|---|---|
| 1.7 (Shared Specs Directory + Cross-Platform Fixtures) | ✅ COMPLETED 2026-05-22 | 7 files under `/shared/` — see Story 1.7 status block in epics.md |
| 1.14c (Solo-Dev Scope-Cuts Runbook) | ✅ COMPLETED 2026-05-22 | `/docs/runbooks/solo-dev-scope-cuts.md` — 5 week-by-week triggers + single-failure-domain risk doc + architectural lock-ins + append-only log |

### Files Created (13 total)

```
shared/
├── canonical-names.md                                              (3.6 KB)
├── error-codes.md                                                  (3.4 KB)
├── data-channel-schema-v1.json                                     (3.6 KB, valid JSON Schema 2020-12)
├── auth-proxy-api.md                                               (4.4 KB)
├── state-derivation.md                                             (5.2 KB)
├── particle-rules-fixtures/
│   ├── README.md                                                   (3.6 KB — fixture file contract, metadata schema, anti-patterns)
│   └── particles/loh/case_001/
│       ├── source.txt                                              ("Aku kangen kamu loh")
│       ├── expected_processed.txt                                  ("Aku kangen kamu [PARTICLE:loh]")
│       ├── expected_target.txt                                     ("I miss you, you know")
│       └── metadata.json                                           (validated against the implied schema)
└── regression-corpus/
    └── README.md                                                   (4.5 KB — corpus.jsonl format, tagging conventions, bake-off harness output spec, kill-criteria reference)

docs/
└── runbooks/
    └── solo-dev-scope-cuts.md                                      (5.8 KB)
```

All files validated against their canonical sources:
- `canonical-names.md` ↔ Architecture §1, §3, §4
- `error-codes.md` ↔ Architecture §10
- `data-channel-schema-v1.json` ↔ Architecture §5 (snake_case wire format, ULID pattern, BCP 47 pattern, schema-version-1 const, enum constraints, required-field list, schema-version policy in description)
- `auth-proxy-api.md` ↔ Architecture ADR-C1 + ADR-C2 + Story 2.1 acceptance criteria
- `state-derivation.md` ↔ Architecture Pattern §13 + ADR-A6
- `particle-rules-fixtures/` ↔ Architecture Pattern §11 + Story 3.2 acceptance criteria
- `regression-corpus/` ↔ Architecture ADR-B1 + Gap I.14 + Story 3.7 acceptance criteria
- `solo-dev-scope-cuts.md` ↔ Architecture Gap I.17 + I.19 + the 5 epic-level triggers identified in this assessment

### Stories Blocked on Bania's External Inputs

The remaining first-pass Epic 1 stories all require credentials, accounts, or platform-specific tooling that I cannot autonomously provide. Listed in suggested do-order:

| Story | Blocker(s) on Bania | What I can do once unblocked |
|---|---|---|
| **1.3** (Oracle VM + LiveKit Docker Compose + Domain) | Oracle Cloud account creation; Cloudflare Registrar account + ~$10/year domain payment for `xaeryx.com`; SSH keypair generation | Write the `/infra/docker-compose.yml`, `/infra/livekit.yaml`, `/infra/Caddyfile`, `/infra/scripts/provision-oracle-vm.sh`, `/infra/scripts/deploy.sh` for you to apply — these are pure code/config; provisioning the VM itself + DNS config requires your cloud-console access. |
| **1.4** (Firebase Init + Firestore Rules + App Check) | Google account for Firebase project creation; iOS DeviceCheck registration + Android Play Integrity registration | Write `/firebase/firebase.json`, `/firebase/firestore.rules` (Architecture-canonical schema), `/firebase/appcheck/{android,ios}-providers.md` setup notes — but the `firebase init` interactive flow + provider registration are yours. |
| **1.1** (Android Project Scaffold) | Android Studio installed; physical Galaxy device with USB debugging enabled for the hello-world smoke test | Generate the entire Android project skeleton (Gradle Kotlin DSL, `libs.versions.toml`, `AndroidManifest.xml`, `Application` class, `MainActivity` Compose, `Theme.kt` with Theme A tokens, `MonochromeGlassPanel.kt` with `RenderEffect` blur) as a directory of files for you to open in Android Studio. Cannot autonomously verify it builds. |
| **1.2** (iOS Project Scaffold) | **Mac with Xcode** (you're on Windows 11 — Xcode does not run on Windows); Apple Developer account for the TestFlight Ad Hoc cert | I can generate the Swift source files (`@main App`, `Info.plist`, `TranslatorRep.entitlements`, `TranslatorRepStyle.swift`, `MonochromeGlassPanel.swift` with `Material` variants) as a directory of files for you OR your mac-having collaborator to open in Xcode. Cannot autonomously create the `.xcodeproj` itself (Xcode-only) or run a smoke test. |
| **1.5** (SafeLog + Lint + ULID) | Depends on 1.1 + 1.2 existing | Generate `SafeLog.kt` + `SafeLog.swift` + detekt rule + SwiftLint rule + ULID wrappers + test vectors once scaffolds exist. |
| **1.6** (CI/CD Per Stack) | GitHub repo created + GitHub Actions enabled + secrets configured (Firebase token, TestFlight API key, Oracle SSH key) | Write all three `.github/workflows/*.yml` files; cannot push secrets / verify CI runs. |
| **1.8 → 1.13** (Pairing/identity user flows) | Depends on 1.1, 1.2, 1.3, 1.4 existing | Generate all Kotlin + Swift source files once scaffolds exist. |

### Recommended Sequence For You

Given the blockers, the highest-leverage move for you right now is to **stand up Story 1.3 (Oracle VM + domain)** because it unblocks Story 2.1 (auth-proxy) which is the load-bearing backend piece, and it forces a real-money commitment (~$10/year domain) early enough to surface budget objections cleanly.

Suggested order over the next few days:
1. **You:** Register Cloudflare account + buy `xaeryx.com` (~$10/year, includes WHOIS privacy free).
2. **You:** Sign up Oracle Cloud (always-free); provision Ampere A1 VM (4 OCPU / 24 GB RAM); SSH keypair.
3. **Me, autonomously:** Write `/infra/` Docker Compose + LiveKit + Caddy config files; you `ssh + docker compose up`.
4. **You:** Create Firebase project; download `google-services.json` + `GoogleService-Info.plist`.
5. **Me, autonomously:** Write `/firebase/firestore.rules` + setup notes; you `firebase deploy`.
6. **You:** Install Android Studio (if not already).
7. **Me, autonomously:** Generate all of `/android/` as a directory of files; you open in Android Studio + run.
8. **(You/collaborator with Mac):** Open the `/ios/` files I'll have generated alongside in Xcode + run.

I can begin step 3, 5, and 7 right now if you give me a single "Go" — the files will be ready for you to apply when you've done your external-account steps.

### Files Ready For Your Immediate Use

These three files I just created have content you should review now because they encode decisions:

1. **[shared/auth-proxy-api.md](shared/auth-proxy-api.md)** — proposes endpoint path `POST /v1/token`, deterministic `roomName` derivation, rate-limit policy (10/min/UID), error code shape. Confirm or revise before Story 2.1.
2. **[shared/data-channel-schema-v1.json](shared/data-channel-schema-v1.json)** — locks the wire format including `additionalProperties: true` (forward-compat) and field constraints. Any change to required-field set means a `schema_version: 2` bump.
3. **[docs/runbooks/solo-dev-scope-cuts.md](docs/runbooks/solo-dev-scope-cuts.md)** — pre-thought scope-cut decisions for Weeks 1/3/5/7/9. Read this NOW (not at trigger time, by definition) so you've already pre-agreed with yourself about the cuts.







