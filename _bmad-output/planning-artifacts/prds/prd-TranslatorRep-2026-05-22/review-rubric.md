# PRD Quality Review — TranslatorRep

## Overall verdict

This is a strong PRD for the shape it's targeting: a personal-use, multi-stakeholder consumer app with explicit polish bar and a load-bearing translation-quality thesis. The thesis is clear, the personas are real and used, the FRs almost all carry testable consequences, scope honesty is unusually high (the Sundanese §10.2 callout is exemplary), and the bespoke §5 Translation Quality section earns its place. What's at risk is mostly downstream-facing: a handful of FRs lean on adjectives ("acceptable," "clean speech," "comparable to WhatsApp") that will create story-creation drift, several `[ASSUMPTION]` thresholds in latency-critical FRs need real targets before engineers can build against them, and FR-23 is a stub. None of this threatens the PRD's overall direction — it threatens the precision with which "done" can be verified.

## Decision-readiness — strong

The PRD makes decisions and stands behind them. The Sundanese omission at §10.2 is a model of decision-readiness: it names the gap ("This is a real and visible gap"), names the user-visible consequence ("those phrases will be garbled"), notes that DR's softer framing was rejected on purpose, and includes a `[NOTE FOR PM]` instructing future planning to preserve the framing. That's a real trade-off held in tension, not smoothed.

§6.1 takes a clear-eyed privacy position: the PRD names the SFU-decrypts-to-forward limitation rather than handwaving "secure." §10.3 explicitly anti-defines "Not a privacy-perfect product" — "Bania has accepted this tradeoff in exchange for $0/month and quality." That's a decision, named as one.

Open Questions §12 are mostly genuine — items 1 (Galaxy `id-ID` on-device availability) and 2 (Whisper.cpp battery on her iPhone) are real risks with named fallbacks and re-approval gates ("re-approve cost"). Items 3-6 are tuning-level, appropriate for v1 entry.

Counter-metrics (SM-C1–C3) explicitly state what *not* to optimize, including the load-bearing "latency vs accuracy" tension. This is rare and good.

### Findings
- **low** Open Question 5 partly answered inline (§ §12) — Q5 cites the `[ASSUMPTION]` answer already chosen ("off by default, 'Polish translations (slower)'"). It's listed as open but reads as resolved-pending-UX-confirmation. *Fix:* either move to Assumptions Index as resolved-pending-copy-review, or reframe Q5 as "Final UX copy and toggle placement" so it's genuinely open.

## Substance over theater — strong

Two personas, both load-bearing. Bania's persona drives the EN-source direction of FR-14 and the "no Indonesian beyond a few words" framing that makes the §5 preservation targets matter. His girlfriend's persona drives the §5 register/honorific/Sundanese rules and the §5.3 paired quality-acceptance test ("her input is essential — she will catch register and tone errors that Bania as a non-Indonesian-speaker cannot evaluate"). Neither is furniture.

§1 Vision is product-specific, not swap-in-anywhere: the second paragraph specifically names *why this exists* — "every 'use existing call app + add translation overlay' path is technically impossible" — which is a thesis grounded in TR Findings, not a vision-statement cliché. The third paragraph commits to "tool, not product" framing, which the rest of the PRD honors.

§5 Translation Quality is the opposite of NFR theater: every bullet is product-specific with concrete tokens (`lah`, `sih`, `kok`, `aku`/`kamu`, `insya Allah`). §7 Aesthetic is also specific, with named anti-references ("AI-aesthetic clichés (glowing gradients, sparkle iconography)") and visual touchstones (iOS 26 Control Center, Bear, Linear-but-quieter).

§6 Constraints carries product-specific thresholds: "Cloud Billing budget alerts at $1 / $5 / $10, killswitch at $50" rather than "must be cost-effective."

### Findings
- (none)

## Strategic coherence — strong

The thesis is explicit and stated up front: own the call to bypass OS-sandbox restrictions, then make translation quality the differentiator. Every feature serves that thesis:
- §4.1 Pairing exists because you can't piggyback on an existing app's contact graph.
- §4.2 Calling exists because of TR Findings A/B/F (third-party-call-audio-tap is impossible).
- §4.3 Translation Pipeline + §5 Quality Requirements are the differentiator.
- §4.4 Captions UI is how the differentiator manifests to users.
- §4.5 Settings is intentionally minimal — "no clutter," explicitly aligned with the §7 aesthetic thesis.

Success Metrics validate the thesis, not activity. SM-1 (real adoption ≥3x/week) and SM-7 (the deep-conversation signal) directly mirror the §1 vision phrase "the long deep conversations they already have." SM-2 (≥80% accurate-and-natural) validates §5. SM-C1 explicitly rules out DAU as a counter-metric — "never optimize for DAU."

MVP scope kind is "experience" — sequenced around the quality bar (§10.2 Sundanese is the load-bearing v2 sequence, not a leftover bucket).

### Findings
- (none)

## Done-ness clarity — adequate

The FR consequences are mostly testable and unusually rigorous for a personal-use PRD. FR-1 ("`FirebaseAuth.currentUser` non-null within 3 seconds"), FR-3 ("Invalid code shows inline error within 2 seconds"), FR-6 ("Tap-to-call-ringing latency <3 seconds on a typical 4G connection"), FR-13 ("Time-to-first-partial: <500ms on Android; <1.5s on iOS"), FR-15 ("Send latency… <300ms in same-region peer connection") — all directly testable.

But several load-bearing FRs lean on adjectives or `[ASSUMPTION]` placeholders that need real targets before engineers can verify "done":

- **FR-10 ("Audio quality is acceptable for conversation")** uses "comparable to a WhatsApp voice call," "sufficient bitrate for clean speech," "audible at network conditions down to 4G with 200ms latency." None of these are testable as written — "comparable to WhatsApp" requires a comparison protocol; "sufficient" and "clean" are adjectives.
- **FR-12 (VAD)** carries two `[ASSUMPTION]` thresholds (~700ms pause, ~15s max utterance) with no shipping target — Open Question 3 acknowledges this but the FR has no fallback bound the engineer can implement against.
- **FR-22 reflow prompt** is `[ASSUMPTION: separate reflow prompt to be defined in implementation; placeholder for now]` — this is an unbuilt feature with no spec.
- **FR-23 ("Unpair from current partner. Settings exposes the unpair action defined in FR-5.")** has no Consequences (testable) block at all. It's a stub pointer; that's fine, but it leaves §4.5 Settings without testable bounds for the Settings surface itself.
- **FR-17 caption area "lower 60%"** is `[ASSUMPTION]`-tagged with no design rationale or fallback bound.

### Findings
- **high** FR-10 audio quality is unverifiable as written (§ §4.2 FR-10) — "comparable to WhatsApp," "sufficient bitrate for clean speech," "audible at… 4G with 200ms latency" cannot be tested without a protocol. *Fix:* state a concrete acceptance protocol (e.g., "MOS ≥ 3.5 in a paired listening test across 3 sessions" or "10-minute call recorded at 4G with 200ms latency must be rated ✅ by both users for audibility") and a concrete Opus bitrate floor (e.g., ≥24kbps).
- **medium** FR-12 VAD thresholds carry no shipping target (§ §4.3 FR-12) — the `[ASSUMPTION]` is acknowledged in OQ-3 but no engineer-buildable default exists. *Fix:* commit to a v1 default (e.g., "Ship 700ms pause / 15s max; expose internal debug flag for tuning during quality test") so the build can proceed; tuning is downstream of shipping the default.
- **medium** FR-22 reflow prompt is an unspecified placeholder (§ §4.5 FR-22) — feature is buildable only after the prompt exists. *Fix:* either move FR-22 to v2 candidates, or commit to authoring the reflow prompt before the §5.3 quality test and reference it from §5.1's versioned-artifact discipline.
- **low** FR-23 has no testable consequences (§ §4.5 FR-23) — relies entirely on FR-5. *Fix:* add a one-line consequence ("Unpair entry point appears in Settings and triggers FR-5 flow") or delete FR-23 and reference FR-5 from §4.5's description.
- **low** FR-17 60% caption area lacks rationale (§ §4.4 FR-17) — `[ASSUMPTION]` flagged in OQ-4 but the implication for the upper 40% (who/them indicator? mute button? call duration?) isn't specified. *Fix:* either inventory what occupies the upper 40% (mic toggle, end-call button, partner name, duration timer) or defer the percentage and define the caption-area constraint as "primary visual mass; controls fit above without crowding."

## Scope honesty — strong

§9 Non-Goals does real work. "Not a privacy-perfect product" is a courageous inclusion — most PRDs would bury that. "Not a transcription / dictation app" forecloses a plausible scope-creep direction. §10.3 "Out of Scope Entirely" distinguishes "won't do v1" from "won't ever do," which is a useful axis the rubric doesn't even require.

§10.2 Sundanese is the standout: the harder-framing decision is documented in the decision log AND preserved inline with a `[NOTE FOR PM]` to prevent future softening. The de-scoping is honest ("This is a real and visible gap"), the v2 sequencing is explicit ("not optional — just sequenced after v1 ships"), and the user-visible failure mode is named ("garbled, mistranslated, or flagged as uncertain").

§13 Assumptions Index roundtrips cleanly — every inline `[ASSUMPTION]` I can find is indexed. Resolved-during-review block preserves the audit trail.

Open-items density is appropriate for the stakes: 6 Open Questions + 10 inline assumptions on a green-light-to-build personal-app PRD with two known unknowns (Galaxy on-device ASR, Whisper battery) is reasonable, not blocking.

### Findings
- **low** §6.1 mentions Crashlytics is opt-in but no FR governs the opt-in UX (§ §6.1 / §4.5) — privacy-summary FR-24 lists Crashlytics, but no FR defines where/how the user opts in. *Fix:* add a one-line FR in §4.5 ("Crashlytics opt-in toggle in Settings, default off") so the constraint has a buildable surface.

## Downstream usability — adequate

Glossary §3 is present, specific, and honored throughout. Domain nouns (Paired Users, Pairing Code, Call, Utterance, Translation Pipeline, Caption, Source Text, Target Text, Partial Caption, Data Channel Message) are used identically in §4 FRs and §5 Quality Requirements. Capitalization is consistent.

FR IDs are contiguous (FR-1 through FR-24), unique, and Realizes-UJ references resolve cleanly. UJ-1/UJ-2/UJ-3 each name a persona by exact label ("Bania," "his girlfriend"). SMs cross-reference FRs by ID (SM-1 → FR-6/7/8/all-Captions; SM-2 → FR-13/14/15; SM-4 → FR-13/14/15) — these resolve.

Cross-references to upstream docs (TR, DR, brief) use relative paths and survive source-extraction; the §3 mention "TR Findings A, B, F" in §10.3 is a load-bearing claim that depends on TR's section IDs holding stable — worth verifying once at architecture handoff.

Two friction points for downstream story creation:

1. **FR-5 and FR-23 are duplicates** — FR-23 says "Settings exposes the unpair action defined in FR-5." Either consolidate or make FR-23 the surface-exposure spec for the action FR-5 defines.
2. **§5 Translation Quality preservation targets are not FRs** — they're product requirements without IDs. A story-creator will need to either invent IDs ("TQ-1," "TQ-2") or stretch FR-14 to cover all of §5.2. The §5.3 quality acceptance test is referenced by SM-2 but isn't itself an FR or assigned an ID.

### Findings
- **medium** FR-5 / FR-23 duplicate the unpair capability (§ §4.1 FR-5, §4.5 FR-23) — confusing for story creation. *Fix:* keep FR-5 as the capability spec, replace FR-23 with a one-line cross-reference in §4.5's description (no FR-23 entry), and renumber subsequent FRs OR keep IDs and explicitly note FR-23 as a surface-exposure pointer.
- **medium** §5.2 preservation targets and §5.3 acceptance test lack IDs (§ §5) — SM-2 references "§5 Translation Quality Requirements" but story creation will want explicit hooks. *Fix:* assign IDs to each §5.2 bullet (TQ-1 particles, TQ-2 pronoun register, TQ-3 gender, TQ-4 Sundanese lexical, TQ-5 honorifics, TQ-6 religious, TQ-7 indirect refusals, TQ-8 Gen-Z slang) and one to the §5.3 protocol (e.g., TQ-AT). Reference from SM-2.
- **low** TR Findings A/B/F reference in §10.3 depends on TR section IDs (§ §10.3) — if TR is later restructured, this breaks. *Fix:* on architecture handoff, verify TR finding IDs are stable, or quote the finding inline.

## Shape fit — strong

This is a consumer multi-stakeholder app with polish-bar requirements. The PRD shape matches:
- **UJs are present and load-bearing** (UJ-1 first-time pairing, UJ-2 first call, UJ-3 the deep conversation). Each has a stated Climax / Resolution / Edge case structure. UJ-3 in particular ties directly to the §1 vision and SM-7 — that's the right kind of UJ load-bearing.
- **Personas are present, exactly two, both used.** Not over-formalized.
- **Aesthetic + Tone section is present and specific** because polish-from-day-one is a brief requirement. The PRD knew to include this and earned it.
- **Translation Quality §5 is custom** — the PRD explicitly justifies why it's bespoke ("This section is custom for TranslatorRep because translation quality is the load-bearing v1 success criterion and is governed by an artifact (the Gemini system prompt) that doesn't fit neatly under any single Feature"). That's exactly the right move for shape fit.
- **Enterprise sections are correctly absent** — no stakeholders matrix, no ROI, no operational SLA, no monetization. Decision log records the drops.

§7 calibration is right: a hobby/solo PRD without polish bar wouldn't need this section; this PRD does because the brief calls for polish from day one. The section is product-specific (monochrome glass, centered, simplistic) rather than generic ("the app should look polished").

### Findings
- (none)

## Mechanical notes

- **Glossary drift:** None detected. "Caption" / "Captions" pluralization is consistent. "Paired Users" / "paired partner" — "paired partner" appears in FR-4, FR-5, FR-6 as a relational descriptor; doesn't conflict with the Glossary's "Paired Users" term but worth noting that "paired partner" isn't itself glossed. Low impact.
- **ID continuity:** FR-1 through FR-24 contiguous, no gaps, no duplicates. UJ-1/2/3 contiguous. SM-1/2/3/4/5/6/7 contiguous + SM-C1/C2/C3 counter-metrics. Clean.
- **Cross-references:** Realizes-UJ tags resolve. SM → FR references resolve. §4.5 FR-23 → FR-5 resolves. §10.3 → TR Findings A/B/F is the only external-ID dependency (see Downstream finding).
- **Assumptions Index roundtrip:** Every inline `[ASSUMPTION]` I found appears in §13. §13 entries all trace back to inline tags. The "Resolved during PRD review" subsection is a nice discipline.
- **UJ persona linkage:** UJ-1, UJ-2, UJ-3 all name "Bania" and "his girlfriend" by exact label from §2.1. No floating UJs.
- **Required sections for stakes + product type:** Vision, Personas, UJs, Glossary, Features (with FRs), Translation Quality, Constraints, Aesthetic, Platform, Non-Goals, MVP Scope, Success Metrics, Open Questions, Assumptions Index — all present and appropriate for a multi-stakeholder consumer hobby app with polish bar. Decision log records the deliberate drops (enterprise, monetization, why-now).
