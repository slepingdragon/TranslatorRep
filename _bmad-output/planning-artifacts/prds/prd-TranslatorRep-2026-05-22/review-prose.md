# Prose Review — TranslatorRep PRD

## Overall prose verdict

The prose is mostly tight, direct, and aligned with the stated "plain, kind, decisive" voice. The weakest seams are (1) terminology drift between casual/formal forms ("Call" vs "call", "Caption" vs "caption", "his girlfriend" vs "girlfriend"), (2) a handful of run-on sentences in §1, §2.4, and §10.2 that buckle under their own clauses, and (3) "Realizes UJ-x" tag noise that pads every FR header. Nothing is broken; the doc reads well aloud. The fixes below are surface-level polish, not structural.

## Findings by severity

### Critical / High

- **High** Terminology drift on `Call` / `Caption` / `Utterance` (throughout) — The Glossary mandates capitalized defined terms ("Downstream workflows and readers must use these terms exactly"), but the doc itself violates this. Examples: §4.2 "place or receive a Call" (correct) vs §6.3 "silently drop a Caption" (correct) vs §10.1 "voice calls between two paired users" (lowercase — should be `Calls` and `Paired Users`); §11 SM-3 "Calls" capitalized but "pairings" lowercase; §10.2 "voice calls" lowercase. *Fix:* sweep the entire doc post-Glossary and capitalize all Glossary terms consistently — `Call`, `Caption`, `Utterance`, `Paired Users`, `Pairing Code`, `Source Text`, `Target Text`, `Translation Pipeline`, `Partial Caption`, `Data Channel Message`, `Translation Provider`, `ASR Provider`.

- **High** Run-on sentence opening §1 (§1 ¶1, "They speak in their own languages; both see the other's speech as captions on screen within ~2 seconds, translated with attention to the discourse particles, slang, and code-switching patterns that carry the emotional content of conversational Indonesian.") — Three independent ideas chained by a semicolon and a trailing participial phrase; the referent of "translated" is ambiguous (captions? speech?). *Fix:* split into two sentences. "They speak in their own languages and see the other's speech as captions within ~2 seconds. The translation preserves the discourse particles, slang, and code-switching patterns that carry the emotional content of conversational Indonesian."

- **High** "Realizes UJ-x" tag is overused and adds no per-FR information (every FR header in §4) — The tag appears on 24 FRs, often listing the same journeys (UJ-2, UJ-3) verbatim. It reads like ceremony, not signal, and clashes with the stated anti-padding voice. *Fix:* either move the journey mapping to a single table at the top of §4, or drop the line from FRs where it's obvious from the section header (e.g., §4.2 Calling FRs obviously realize UJ-2).

- **High** Run-on with unclear referent in §2.4 UJ-3 (§2.4 ¶3, "There's a moment of vulnerability about her family that would have been clunky through her old self-translation; this time it lands.") — "this time it lands" is evocative but the antecedent of "it" (the moment? the conversation? the translation?) is unclear, and "her old self-translation" is a noun phrase the doc hasn't established. *Fix:* "She has a moment of vulnerability about her family. With her old workaround — translating for herself in real time — it would have come out clunky. This time it lands cleanly."

- **High** Voice shift mid-paragraph in §10.2 Sundanese bullet (§10.2, "**This is a real and visible gap.** v2 closes it using chirp_2 chunked Recognize + Google NMT for SU. Given her actual switching pattern, v2 is not optional — just sequenced after v1 ships.") — The paragraph mixes user-prose voice ("real and visible gap"), implementation-spec voice ("chirp_2 chunked Recognize + Google NMT"), and product-strategy voice ("v2 is not optional — just sequenced") in three consecutive sentences. The reader has to context-switch twice. *Fix:* separate the strategy claim from the implementation hint: "**This is a real and visible gap.** v2 is not optional — just sequenced after v1 ships. (Technical path: chirp_2 chunked Recognize + Google NMT for SU.)"

### Medium / Low

- **Medium** "His girlfriend" used inconsistently (§2.1 header is bold "**His girlfriend**", §1 uses "his girlfriend" mid-sentence, §5.3 uses "**Bania and his girlfriend together**", §11 SM-2 uses "Bania and his girlfriend rating together"). The referent is always clear, but the lack of a consistent capitalization or naming convention reads slightly sloppy. *Fix:* the resolution note at §13 says "kept generic" — fine, but pick one form and stick to it. Recommend always lowercase "his girlfriend" in prose, no bolding outside the §2.1 persona header.

- **Medium** §1 ¶2 awkward phrasing ("every 'use existing call app + add translation overlay' path is technically impossible"). The quoted phrase reads like a slide title. *Fix:* "every path that combines an existing call app with a translation overlay is technically impossible."

- **Medium** §1 ¶3 "stop having quiet miscommunication undercurrents" — noun-pile ("quiet miscommunication undercurrents") forces the reader to parse three modifiers. *Fix:* "stop carrying quiet undercurrents of miscommunication."

- **Medium** Passive where active is clearer — §4.3 FR-11 "Audio is tapped from the LiveKit local audio track" (twice in this FR alone, also FR-15 "Messages are reliable-ordered"). *Fix:* "The app taps audio from the LiveKit local audio track." For FR-15: "The data channel sends messages reliably and in order (SCTP reliable channel)."

- **Medium** Hedging in FR-1 / FR-3 / FR-8 success criteria — "within 3 seconds", "within 2 seconds", "within 5 seconds" inconsistently formatted vs §11's "<2.5s", "<4s". *Fix:* pick one form (recommend `<Ns` since it's more compact and matches the latency section).

- **Medium** §3 Glossary "Utterance" definition ("Roughly one sentence.") — fragment, and "roughly" softens a load-bearing definition. *Fix:* "Roughly one sentence in length." or fold into the preceding sentence: "...as a single ASR + translation request — roughly one sentence."

- **Medium** §4.1 ¶2 "Subsequent launches go directly to the Paired home screen (with the Call button) and skip the pairing UI entirely." — the parenthetical interrupts the flow; "entirely" is padding. *Fix:* "Subsequent launches go directly to the Paired home screen and skip pairing UI."

- **Medium** §4.5 FR-22 NOTE FOR PM uses second person ("Don't ship this as a cryptic toggle") in a doc that's otherwise third-person or imperative-to-system. *Fix:* either accept the shift (PRD-to-PM annotations can be conversational) or reframe: "This must not ship as a cryptic toggle."

- **Medium** §5.3 ✅/⚠️/❌ emoji rating scheme is used in §5.3 and §11 SM-2 but nowhere else; reads inconsistently against the otherwise text-only document. *Fix:* fine to keep, but introduce the legend once at first use ("rated as ✅ accurate-and-natural, ⚠️ accurate-but-awkward, or ❌ wrong / lost meaning") and reference it briefly in SM-2 instead of repeating "✅ accurate-and-natural".

- **Medium** §6.1 mixed punctuation in bullets — some bullets end with periods, some don't (e.g., "**TLS 1.3 in transit** on all client-Cloud-Run and client-LiveKit connections." vs "**WebRTC end-to-end via SFU**: media is client-encrypted..."). *Fix:* uniform — every bullet ends with a period.

- **Medium** §7 Aesthetic bullets mix sentence fragments and full sentences within the same bullet ("**Density: simplistic, no clutter.** Generous whitespace. One primary action per screen wherever possible."). Reads like a brainstorm dump. *Fix:* either fragments throughout for punchy bullets, or full sentences throughout. Recommend trimming to: "**Density:** simplistic. Generous whitespace. One primary action per screen."

- **Medium** §10.2 "Sundanese support" bullet uses present tense for v1 ("v1's translation pipeline cannot reliably handle Sundanese") then future ("will be garbled") then present ("is a real and visible gap") then future ("v2 closes it") — tense soup. *Fix:* future for v2 outcomes, present for v1 limitations, no shifts mid-paragraph.

- **Low** §2.3 "v1 is `id` ↔ `en` only" — the rest of the doc uses "Indonesian ↔ English" or "ID/EN" in caps; lowercase code-format here is inconsistent. *Fix:* "Indonesian ↔ English only" or "ID ↔ EN only".

- **Low** §3 Glossary "Pairing Code" definition has a parenthetical ("e.g., WhatsApp text") that becomes a load-bearing assumption later (§12 Q6). *Fix:* keep the parenthetical here but treat the WhatsApp dependency as an explicit assumption in §13 (it already is — good).

- **Low** §4.1 FR-2 "A code remains valid until used in a successful pairing or the user explicitly regenerates it." — "or the user explicitly regenerates it" is awkward; "regenerate" doesn't take an object here. *Fix:* "...until used in a successful pairing or until the user requests a new one."

- **Low** §4.2 FR-7 "FCM high-priority data message delivers within 2 seconds" — "delivers" is intransitive here; reads odd. *Fix:* "arrives within 2 seconds" or "is delivered within 2 seconds."

- **Low** §4.4 FR-19 arrow notation "New Caption appended → list animates" mixes formal prose with arrow notation that appears nowhere else in the doc. *Fix:* "When a new Caption is appended, the list animates to the bottom within 200ms."

- **Low** §5.2 "Particle loss = highest-impact quality regression." — `=` sign in prose context. *Fix:* "Particle loss is the highest-impact quality regression."

- **Low** §6.2 "**$0/month operating cost is a hard requirement** for v1 at 2-user / ~30-min/day scale." — "2-user / ~30-min/day scale" is dense shorthand. *Fix:* "for v1 at two users averaging ~30 minutes/day."

- **Low** §8 "Target API: 35 (current)" and "Target iOS: latest (26 as of 2026)" — parenthetical timestamps will rot. *Fix:* drop the parentheticals, or move to a single "Versions accurate as of 2026-05-22" note at section head.

- **Low** §10.1 "Native Android + iOS apps with polished, native UI." — "native" used twice in eight words. *Fix:* "Native Android + iOS apps with polished, platform-idiomatic UI."

- **Low** §11 SM-1 "Validates the entire product hypothesis (FR-6, FR-7, FR-8, all Captions FRs)." — "all Captions FRs" is informal shorthand against the precise references preceding it. *Fix:* "(FR-6, FR-7, FR-8, FR-17 through FR-20)."

- **Low** §11 SM-7 "Validates the brief's qualitative success criterion." — singular; the brief likely has more than one, and the doc just said "the success signal from the brief" in §2.4 UJ-3. *Fix:* "Validates the deep-conversation success signal from the brief."

- **Low** §12 Q1 "If absent, fall back to cloud STT for his side (~$15/month) and re-approve cost." — "his side" is informal; the doc usually says "Bania's side" or "his app". *Fix:* "fall back to cloud STT for Bania's device".

- **Low** §13 final bullet group uses "— resolved" suffix on every item, which is consistent but reads ritualistic. *Fix:* fine as-is; if trimming, consider grouping under a "Resolved" subheading and dropping the per-line suffix.

- **Low** §0 "BMAD workflows (UX, Architecture, Epics & Stories)" — acronym `BMAD` used without expansion. *Fix:* expand on first use, or accept as internal jargon and add to Glossary.

- **Low** §0 "Functions as the implicit 'tech addendum' for this PRD." — scare quotes around "tech addendum" suggest the term isn't a real one in this codebase. *Fix:* drop the quotes; it's a clear enough phrase: "Functions as the tech addendum for this PRD."
