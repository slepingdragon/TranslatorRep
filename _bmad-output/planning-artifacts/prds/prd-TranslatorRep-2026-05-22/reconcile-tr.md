# TR Reconciliation — TranslatorRep PRD

## Overall verdict

The PRD is broadly faithful to the TR's locked Path-B1 architecture and consistently reflects the four scope pivots (project shape, Indonesian-only v1, custom call app, free-tier-first). Functional requirements are buildable under LiveKit Cloud + on-device ASR + Cloud Run + Gemini AI Studio + Firebase, and the FR-13/14/15 latency budget is plausibly within the TR's estimated end-to-end window. However, there are several places where PRD numbers, defaults, or framings diverge from what the TR actually established — most notably the latency math, the iOS battery target, and an implicit assumption that the Cloud Run cold-start risk doesn't affect FR-13/14's stated medians.

## Contradictions or buildability concerns

- **FR-13 time-to-first-partial on iOS (<1.5 s) versus TR's actual basis.** PRD §4.3 FR-13 asserts "<1.5s on iOS [ASSUMPTION: Whisper.cpp small model on A17+]". The TR never benchmarks Whisper.cpp time-to-first-partial; it labels Whisper.cpp accuracy *and* battery as **High-severity, unmeasured** Week-1/Phase-3 risks (TR §"Top Risks and Open Questions", §"Risk Assessment"). Whisper.cpp is also fundamentally **utterance-level**, not streaming — the TR's design implies partials come from VAD-windowed transcription, not true streaming partials. The "<1.5s to first partial" target is therefore a fresh assumption not grounded in TR.

- **FR-13 time-to-final (<2 s after utterance end) vs FR-14 (<1 s POST round-trip) vs SM-4 (median <2.5 s end-to-end).** Adding the PRD's own numbers (ASR final ≤2 s + Cloud Run round-trip ≤1 s + data-channel ≤0.3 s) gives ~3.3 s, exceeding SM-4's <2.5 s median target. TR's own budget (§"Scalability and Performance") allocates ~500-1000 ms for STT *streaming* finals on `chirp_3` — which the PRD has substituted away from. With Whisper.cpp utterance-level inference plus a cold-startable Cloud Run hop, the median budget is tight; the PRD numbers don't add up against themselves.

- **FR-14 Cloud Run cold start unacknowledged.** PRD §4.3 FR-14 specifies "POST round-trip <1s under typical conditions (median)" with no carve-out for cold start. TR §"Implementation Specifics" and §"Deployment" mandate **min-instances=0 with a warmup-ping pattern** to stay $0/month, and acknowledges 500ms-2s typical cold start. The PRD never references the warmup ping requirement, so the first-call-of-a-session FR-14 contract is effectively unbuildable under the locked free-tier config.

- **FR-15 reliable-ordered data channel claim.** PRD asserts "Messages are reliable-ordered (SCTP reliable channel)." This is buildable but depends on the SDK's data-channel mode being explicitly set; the TR's example code shows `publishData(byteArray, reliable = true)` for LiveKit Android but does not commit to ordered delivery semantics. Minor — likely true, but the PRD is stating an SDK-level guarantee as a requirement.

- **SM-5 iPhone battery target (<30%/30-min) tighter than TR's reference.** TR §"Success Metrics and KPIs" and §"Risk Assessment" both state "<30% per *hour*" (per 60-min call) as the Whisper.cpp acceptability ceiling. PRD SM-5 cuts this in half to "<30% per *30-minute* call". That's a 2× tightening of an already-flagged High-severity unmeasured risk. If Bania is going to use a number, the TR's hour-basis number is the one backed by the research; the half-hour basis appears to be an unintentional change.

- **FR-7 push-delivery <2 s SLO on PushKit/FCM.** Buildable but optimistic. TR cites APNs/FCM "high priority" delivery patterns but never commits to a specific delivery-latency SLO. APNs VoIP push under throttle or background-state conditions can be much slower than 2 s; this is a number that should be flagged as aspirational.

- **§4.5 FR-22 "Polish translations (slower) ~+400 ms" matches TR but only describes a *single* Gemini call as the baseline.** TR §"Scalability and Performance" describes Gemini-as-post-editor as additive (NMT then Gemini reflow). The PRD's locked v1 stack uses Gemini for the *primary* translation (no NMT), so a "post-editor" toggle that "runs Gemini twice" is a new construct not directly in the TR — buildable, but the PRD is inventing rather than reflecting.

## TR constraints the PRD doesn't acknowledge

- **Week-1 probe: `SpeechRecognizer.checkRecognitionSupport()` for `id-ID` on Bania's Galaxy.** The PRD §12 Open Question 1 captures this *partially* — it names the probe but doesn't surface it as a v1 architectural gate. TR labels it **High severity**; if absent, the locked $0/month plan breaks (~$15/mo Cloud STT fallback for his side). The PRD treats this as a notes-level open question rather than a constraint on FR-13's buildability.

- **Whisper.cpp programmatic-streaming-partials limitation.** Whisper.cpp processes audio in chunks/windows, not true streaming with continuous partials like Google chirp_3 or Android's on-device SpeechRecognizer. FR-18 (Partial Captions "at ~300-500ms cadence") will look very different on iOS than on Android. The PRD treats partial-result rendering as symmetric across platforms; the TR's stack choice forces an asymmetry.

- **Gemini AI Studio "no data residency" / training-on-free-tier-inputs caveat.** TR §"Cost-Optimization Addendum" notes "Gemini AI Studio has no data residency" and §"Free-Tier Validation" notes "free-tier inputs may be used for Google training (acceptable for personal use)." The PRD §6.1 privacy section says "TLS 1.3 in transit" and "audio is never stored" but never acknowledges that **finalized utterance text** flows through AI Studio under terms that may include training. This is the single biggest privacy delta from the TR that the PRD soft-pedals.

- **Cloud Run warmup-ping pattern.** The TR's `$0/month` plan explicitly requires the client to fire `GET /healthz` at app launch / call-start. PRD §8 says "min-instances=0 with warmup ping pattern" but no FR captures this; FR-6 (Place a Call) and FR-7 (Receive Call) don't include the warmup obligation, so downstream stories may omit it and silently degrade FR-14.

- **LiveKit Cloud Build tier headroom (36% of participant-min/mo).** PRD §6.2 says "$0/month sustained" but doesn't mention the participant-minute ceiling (5000/mo) or the documented escape (self-host on Oracle ARM A1). At ~30 min/day × 2 participants × 30 days = 1800 participant-min (the TR's exact estimate), they're at 36% — fine, but the PRD has no monitoring requirement to detect approaching the ceiling.

- **Translation Provider abstraction is not surfaced as an FR.** TR §"Design Principles" makes provider abstraction a *load-bearing* design principle ("v2 Sundanese expansion is a config swap, not a rewrite"). The PRD §3 Glossary defines `Translation Provider` and `ASR Provider` but no FR requires the abstraction. Downstream Architecture/Stories could ship without it; v2 Sundanese plan in §10.2 then becomes a rewrite, not a config swap.

- **Crashlytics conversation-content guardrail.** TR §"Implementation Specifics" explicitly states "**Never log caption / transcript text** via `setCustomKey` or `log()`." PRD §6.1 echoes this in a constraints bullet but never elevates it to a testable FR. For a privacy-load-bearing requirement, that's surprisingly weak.

- **Sundanese gap framing matches TR but downplays v2 ASR transport.** PRD §10.2 says v2 closes the gap "using chirp_2 chunked Recognize + Google NMT for SU." Correct per TR Finding D/E, but doesn't carry forward TR's acknowledgement that this introduces ~5–15 s perceived latency on SU utterances — meaning v2 will have an asymmetric UX (fast ID, slow SU) that PRD §10.2 doesn't preview.

- **Pairing collision handling depth.** TR doesn't deeply analyze the 6-digit pairing-code architecture; PRD §4.1 FR-2 invents "1M code space with collision check at generation." Acceptable but is PRD-introduced policy, not TR-backed.

## Capability/implementation boundary issues

- **FR-11 SDK-specific callback names in the contract.** "tapped from the LiveKit local audio track via `AudioBufferCallback` (Android) / `AudioCustomProcessingDelegate` (iOS)" — this is implementation detail copied verbatim from TR §"Implementation Specifics". PRD should state the capability ("local audio is captured for ASR while WebRTC continues to publish it"); SDK callback names belong in the Architecture document.

- **FR-12 specific VAD threshold (~700 ms) and max-utterance (~15 s).** Reasonable starting numbers but they're tuning parameters, not capability statements. Belongs in CA or an addendum, with FR-12 stating the capability ("system segments continuous mic audio into Utterances at natural speech pauses").

- **FR-13 names exact APIs (`createOnDeviceSpeechRecognizer`, "Whisper.cpp small multilingual").** These are the TR's locked choices; restating them in an FR conflates capability with mechanism. If TR ever needs to substitute (e.g., quality-fallback to cloud STT — which PRD §10.2 lists as a v2 path), FR-13 has to change. PRD should reference "on-device ASR Provider per the TR" and let the Architecture doc bind the specific API.

- **FR-15 names WebRTC data channel and exact JSON payload schema.** Payload schema (`{utterance_id, source_lang, source_text, target_lang, target_text, timestamp_start}`) is design — belongs in CA/Stories. PRD-level capability is "translated utterances are delivered to the peer with low latency over the existing call connection."

- **FR-14 specifies Firebase App Check 401 on missing/invalid token.** This is the TR's auth pattern, not a product capability. PRD-level capability is "translation endpoint rejects unauthenticated requests."

- **§4.1 FR-3 Firestore document schema (`/pairs/{pairId}: {memberA, memberB, createdAt}`).** Data-model detail — belongs in CA. PRD-level: "successful pairing is durably recorded and visible to both apps."

- **§8 Platform section technically restates TR's stack.** Mostly appropriate as a platform-baseline declaration, but "min-instances=0 with warmup ping pattern" is implementation guidance that belongs in the warmup-ping FR (which doesn't exist — see TR constraints section above).

- **FR-21 names "Room w/ SQLCipher" and "Keychain-encrypted SwiftData".** Library and crypto choices belong in CA. PRD-level capability is "transcript history is per-device, encrypted at rest, never synced."
