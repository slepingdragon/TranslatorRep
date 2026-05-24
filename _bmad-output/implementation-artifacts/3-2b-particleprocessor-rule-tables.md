# Story 3.2b: ParticleProcessor Rule Tables — Expansion of Story 3.2's loh-only Scope

Status: in-progress

<!-- Created 2026-05-24 (Phase 1 of N) on feature/3-2b-tq1-kan-sih-dong-deh.

     Multi-PR rollup story. Phases 1-3 (landed 2026-05-24): TQ-1 COMPLETE
     (all 14 Indonesian discourse particles, 42 fixtures, 5 helpers).
     Phase 4 (this PR, 2026-05-24): TQ-3 gender-neutral `dia → they` + TQ-6
     6 religious-expression verbatim rules. 2 new helpers. 21 new fixtures
     (now 63 total). AC-1 + AC-2 + AC-6 + AC-8 + AC-9 + AC-10 ✅. AC-3, AC-4,
     AC-5, AC-7 deferred to Phase 5 (need Story 3.1 linguistic input).

     iOS Swift parity is Story 3.2c (Mac/iOS Claude session). -->

## Story

As a solo developer extending the ParticleProcessor pattern proven out by Story 3.2,
I want all 14 TQ-1 Indonesian discourse particles + the TQ-3/4/5/6/7/8 rule categories populated incrementally on the existing harness,
so that the on-device translation pipeline can preserve cultural-pragmatic context across the full domain-research surface before Story 3.7's regression-corpus bake-off + Story 3.9's model validation gate.

## Acceptance Criteria

**Given** Story 3.2 landed the ParticleProcessor module structure + harness + one fully-functional rule (`loh`),
**When** this story's phases land cumulatively,
**Then:**

1. **AC-1 (TQ-1: 14 Indonesian discourse particles fully implemented):** All 14 `ParticleRule` entries registered. Phase 1+2 landed 8 sentence-final particles via `sentenceFinalParticle` helper. **Phase 3 added the remaining 6** via 4 new helpers (`formalQuestionSuffix` for kah; `sentenceInitialDeictic` for nih+tuh; `pronounConcessive` for mah; `midSentenceAlso` for juga+also). **✅ 14 of 14 done.**
2. **AC-2 (Per-rule ≥3 golden-file fixtures):** **Phase 3 added 18 new fixtures** (6 particles × 3 cases). **✅ 42 of 42 done.**
3. **AC-3 (TQ-8: ≥20 Gen-Z slang items):** `GenZSlang.kt` populated; ≥20 slang items with their target equivalents + ≥3 fixtures each. **0 of 20 done.**
4. **AC-4 (TQ-4: ≥12 Sundanese lexical insertions):** `SundaneseInsertions.kt` populated as a side-channel processor (renders `RenderMode.SUNDANESE_PLACEHOLDER` rather than injecting an equivalent). **0 of 12 done.**
5. **AC-5 (TQ-5: partner honorifics + strip rules):** `HonorificStripping.kt` implements detect + strip/preserve per intimate-vs-formal register decisions from DR §6. **Not started.**
6. **AC-6 (TQ-6: religious-expression verbatim preservation):** `verbatimReligious` helper in `ParticleRules.kt` registers 6 Arabic-origin religious expressions (`alhamdulillah`, `insyaallah`, `bismillah`, `subhanallah`, `astaghfirullah`, `masyaallah`); preProcess tags each, postProcess substitutes marker → same lowercase term verbatim. 18 fixtures (6 × 3) — all with VERIFY-WITH-GIRLFRIEND notes. Multi-word variants (`insya Allah` etc.) deferred. **✅ Phase 4.** (The `ReligiousExpressions.kt` stub file is kept for future expansion + iOS parity scaffolding.)
7. **AC-7 (TQ-7: indirect refusals):** `IndirectRefusals.kt` detects + annotates indirect refusal patterns per DR §6. **Not started.**
8. **AC-8 (TQ-3: gender-neutral `dia → they` default):** `genderNeutralPronoun` helper in `ParticleRules.kt` registers `dia` (placed AFTER `mah` so `<pronoun> mah` collisions resolve correctly). Target equivalent = `"they"` (singular-they default per architecture §11 / DR §6.3); inject conservatively replaces stray `he/she` from NMT. 3 fixtures (subject + object + preference). Object/possessive forms (`him`/`her`/`his`/`-nya`) deferred pending Story 3.1 conversational evidence. **✅ Phase 4.**
9. **AC-9 (Generalized fixture test):** `ParticleProcessorFixtureTest.kt` iterates every `particles/<rule>/case_NNN/` dir without hardcoded rule names — Phase 1 ✅.
10. **AC-10 (Multi-punct cleanup):** `ParticleProcessor.postProcess` collapses duplicated terminal punctuation (`??` → `?`, `!!` → `!`) so rule equivalents containing `?` (e.g., `kan` → `, right?`) don't leave artifacts when injected before an existing source-side `?` — Phase 1 ✅.

**Done criteria:** Story 3.2b flips to `review` when AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8 are all ✅. AC-9 + AC-10 are infrastructure ACs satisfied by Phase 1. **Post-Phase-4 status:** AC-1, AC-2, AC-6, AC-8, AC-9, AC-10 ✅; AC-3 (TQ-8 slang), AC-4 (TQ-4 Sundanese), AC-5 (TQ-5 honorifics), AC-7 (TQ-7 refusals) remain — Phase 5 (needs Story 3.1 input).

## Tasks / Subtasks

### Phase 1 — TQ-1 expansion: kan + sih + dong + deh (this PR, 2026-05-24)

- [x] **Refactor `ParticleRules.kt` to extract `sentenceFinalParticle(name, englishEquivalent)` helper.** Replaces hand-rolled rule construction with one-line entries; new TQ-1 particles drop in trivially. The existing `loh` rule now uses the helper too (functional equivalent; cleaner).
- [x] **Add 4 new TQ-1 rules via the helper.** `kan → ", right?"`, `sih → ", though"`, `dong → ", please"`, `deh → ", then"`. Helper uses `Regex.escape(name)` for defense-in-depth even though current particle names are all alpha-lowercase.
- [x] **Harden `ParticleProcessor.postProcess` cleanup pass.** Added multi-punct collapse (`?` and `!` runs collapse to single) so the `kan` equivalent's trailing `?` doesn't double when injected before a source-side `?`. The detekt LoopWithTooManyJumpStatements rule stays satisfied because cleanup is a series of simple `.replace()` calls, not a loop.
- [x] **Generalize `ParticleProcessorFixtureTest.kt`.** Single test method iterates every `particles/<rule>/case_NNN/` directory; rule name from dir name doubles as the expected particle token. Removes loh-specific hardcoding from Story 3.2. AC-9 ✅.
- [x] **Write 12 new fixtures.** 3 cases each for kan, sih, dong, deh — all with explicit `VERIFY WITH GIRLFRIEND` notes in metadata.json. Test count: 12 → 12 (still 1 test method, now running 15 fixture cases internally).
- [x] **Local validation.** `./gradlew :app:detekt :app:testDebugUnitTest` 16s warm-cache; 0 detekt smells; all 12 unit-test methods pass (1 fixture method × 15 cases internally).

### Phase 2 — TQ-1 sentence-final remainder: kok + ya + lah (this PR, 2026-05-24)

- [x] **`kok`** (sentence-final defensive clarification): `sentenceFinalParticle("kok", ", honestly")`. Scoped to the sentence-final use only; the polysemous sentence-INITIAL use ("Kok kamu telat?" = "How come you're late?") is deferred to Phase 3 with a separate rule design — that's why the original Phase-2-was-9-particles plan got split into Phase 2 (3 sentence-final particles) + Phase 3 (6 particles with different shapes).
- [x] **`ya`** (sentence-final confirmation tag): `sentenceFinalParticle("ya", ", yeah?")`. Disambiguated from kan by register: kan = presupposed-knowledge check ("right?"); ya = comprehension/instruction soft-confirm ("yeah?"). The standalone "Ya" / "yes" word doesn't trigger the rule because the regex requires preceding whitespace.
- [x] **`lah`** (sentence-final emphasis): `sentenceFinalParticle("lah", ", obviously")`. "Duh" is more accurate but reads flippant in some registers; "obviously" is the safer mid-register choice.
- [x] **9 new fixtures.** 3 per particle (kok/ya/lah). All with VERIFY WITH GIRLFRIEND notes.
- [x] **Test infrastructure unchanged.** Generalized fixture test auto-picked up the 3 new dirs — delivers the "zero test changes" promise from Phase 1.
- [x] **Local validation.** 10s warm-cache; 0 detekt smells; 12 unit-test methods pass; 1 fixture method now iterates 24 cases (8 particles × 3 fixtures).

### Phase 3 — 6 remaining TQ-1 particles via 4 new helpers (this PR, 2026-05-24)

- [x] **`kah`** (formal-question SUFFIX): `formalQuestionSuffix("kah", listOf("apa", "siapa", "mana", "bila", "berapa", "kapan", "mengapa", "bagaimana"))`. Detects suffix on closed-list of stems; preProcess strips suffix + marks; postProcess silently removes marker (English doesn't need a question particle).
- [x] **`nih`** + **`tuh`** (sentence-initial deictics): `sentenceInitialDeictic("nih", "Here, ")` and `sentenceInitialDeictic("tuh", "There, ")`. Detects particle at text start; inject prepends equivalent.
- [x] **`mah`** (concessive after subject pronoun): `pronounConcessive("mah")`. Closed-list of pronouns (aku/saya/kamu/dia/kami/kita/mereka/ia). NULL target (NMT produces "as for X" naturally from pronoun + clause).
- [x] **`juga`** + **`also`** (mid-sentence "also/too"): `midSentenceAlso("juga")` and `midSentenceAlso("also")`. "also" is the Indonesian loanword variant — rare in real conversation, registered to satisfy AC-1's 14-particle enumeration.
- [x] **4 new helpers added to `ParticleRules.kt`** with clear design notes per helper.
- [x] **`ParticleProcessor.kt` refactored** for null-target rules: Pass 1 silently strips markers with no equivalent + still adds to preserved; Pass 2 adds to preserved on detect even if equivalent is null.
- [x] **`applicableRules` filter updated** to allow rules with empty `targetEquivalents` map (was filtering them out).
- [x] **18 new fixtures.** 3 per particle × 6 particles. Each fixture has explicit VERIFY-WITH-GIRLFRIEND notes; `also` fixtures additionally flagged with rare-usage caveat.
- [x] **Local validation.** `./gradlew :app:detekt :app:testDebugUnitTest --rerun-tasks` passes; 0 detekt smells; fixture-test method now iterates 42 cases internally (14 particles × 3 fixtures).

**TQ-1 IS NOW COMPLETE.** Phase 4+ shifts to other TQ categories.

**Caveat on the `--rerun-tasks` requirement:** Gradle doesn't track `/shared/particle-rules-fixtures/` as test inputs (the directory is outside the Android module). Adding new fixtures locally won't trigger test re-run via standard `./gradlew :app:testDebugUnitTest` — must use `--rerun-tasks` or modify a Kotlin source file to bust the cache. CI is unaffected (fresh runner = always re-runs). Worth tracking as a deferred-work item to add `inputs.dir("$rootDir/../shared/particle-rules-fixtures")` to the test task wiring.

### Phase 4 — TQ-3 gender-neutral `dia` + TQ-6 religious verbatim (this PR, 2026-05-24)

The "easier" half of the remaining AC surface — no native-speaker linguistic
input required (architecture §11 + DR §6.3 are clear specs; honorifics/slang/
Sundanese/refusals are the harder set deferred to Phase 5 pending Story 3.1).
Per `docs/project-context.md` §13 this is "Session 7 — Particle rules — easier".

- [x] **TQ-3 `genderNeutralPronoun` helper + `dia` rule.** Detects standalone `dia` (subject or object position) as a whole word case-insensitively; target equivalent = `"they"` (singular-they default per architecture §11 / DR §6.3). Registered AFTER `mah` in `allRules` to keep `dia mah` collisions tagged by `mah` first (mah's pronoun list includes `dia`). Inject is conservative — replaces the first `\b(he|she|He|She)\b` in `current` with `they/They` if present; no-op if not. Object/possessive forms (`him`/`her`/`his`/`-nya`) are deferred pending real-conversation evidence from Story 3.1.
- [x] **TQ-6 `verbatimReligious` helper + 6 rules.** `alhamdulillah`, `insyaallah`, `bismillah`, `subhanallah`, `astaghfirullah`, `masyaallah`. Target equivalent = the same lowercase term — the English-receiving partner is expected to recognize these; literal translations like "thank God" / "God willing" would flatten the cultural-pragmatic register per DR §6 / architecture §11. Multi-word variants (`insya Allah`, `masya Allah`) + Arabic-English transliterations (`inshallah`, `mashallah`) + long-form (`bismillahirrahmanirrahim`, `astaghfirullahaladzim`) deferred — v1 covers single-word Indonesian spellings only.
- [x] **5 existing fixtures updated** to reflect the new `dia` rule's tagging: `also/case_002`, `juga/case_002`, `kah/case_002`, `loh/case_003`, `mah/case_002` all contained `dia` as a standalone word in their source. Each had `[PARTICLE:dia]` added to `expected_processed.txt` + `"dia"` appended to `metadata.json` `expected_particles`. `expected_target.txt` for all 5 already used singular `they/them/their` (the convention was already in place; this PR makes it enforced by the code path).
- [x] **21 new fixtures.** 3 cases each for `dia`, `alhamdulillah`, `insyaallah`, `bismillah`, `subhanallah`, `astaghfirullah`, `masyaallah` (7 × 3 = 21). All carry explicit VERIFY-WITH-GIRLFRIEND notes in metadata; religious fixtures also flag multi-word variants and register-fit questions (e.g., subhanallah vs masyaallah for human-vs-natural admiration).
- [x] **Test assertion relaxed.** `ParticleProcessorFixtureTest.kt` previously asserted `processed.particles == listOf(ruleName)` (strict equality) — multi-particle fixtures (which now exist post-Phase-4) make that wrong. Switched to `processed.particles.contains(ruleName)` with an explicit comment that the full per-fixture particle set is still strictly enforced by the `expected_processed.txt` equality check. A future enhancement could parse `metadata.json`'s `expected_particles` for set-equality assertion (needs a JSON parser; deferred per the original Story 3.2 reasoning).
- [x] **Local validation.** `./gradlew :app:detekt :app:testDebugUnitTest --rerun-tasks` (per the documented `--rerun-tasks` landmine for fixture changes) — 16s warm-cache, 0 detekt smells, all 63 fixture cases pass (42 prior + 21 new). Fixture-test method now iterates 63 cases internally.

**TQ-3 + TQ-6 ACs COMPLETE.** AC-6 + AC-8 ✅. Story 3.2b stays `in-progress` because AC-3 (TQ-8 slang), AC-4 (TQ-4 Sundanese), AC-5 (TQ-5 honorifics), AC-7 (TQ-7 indirect refusals) remain — Phase 5.

### Phase 5 — TQ-4/5/7/8 categories (future PR — needs Bania's girlfriend's linguistic input per Story 3.1)

- [ ] **TQ-8 Gen-Z slang.** Populate `GenZSlang.kt` with ≥20 contemporary slang items + their target equivalents. Examples: `gabut` (bored), `mager` (lazy), `cuy` (mate/dude), `bestie`, `gass` (let's go), `santuy` (chill), `mantap` (awesome), `auto` (automatically), `wkwkwk` (haha), `sotoy` (know-it-all), `kepo` (nosy), `gws` (get well soon), `btw`, `omg`, `pengen` (want), `bgt` (very), `udh` (already), `gak` (no/not), `nyariin` (looking for), `lemes` (weak/tired). ≥3 fixtures per slang item. May warrant a `wordSubstitutionRule` helper since semantics differ from sentence-final discourse particles.
- [ ] **TQ-4 Sundanese spans.** Side-channel processor in `SundaneseInsertions.kt`; emits `RenderMode.SUNDANESE_PLACEHOLDER` rather than equivalent injection. Different `PostProcessed` shape consumer. Needs Story 3.1 input on which Sundanese lexical insertions actually appear in the partner conversation.
- [ ] **TQ-5 honorifics.** `HonorificStripping.kt` — register-aware (intimate → strip, formal → preserve). Per architecture §11 line 431-432: `mas → babe` or omit for intimate-partner register.
- [ ] **TQ-7 indirect refusals.** `IndirectRefusals.kt` — pragmatic-marker injection.

### Phase 6 — Done criteria + 3.2b → review

- [ ] All AC-1 through AC-8 ✅.
- [ ] Story 3.2b flips to `review`; CR pass; flip to `done`.
- [ ] iOS parity Story 3.2c can then start (Mac/iOS session).

## Dev Notes

### Phase 1 design decisions

**Helper-extraction was the right call.** Pre-helper, each rule was ~15 lines of construction. Post-helper, each rule is one line. The 4 new rules added 4 lines instead of ~60. When Phase 2's nine more particles land, that's 9 lines added instead of ~135. Compounding payoff.

**Helper accepts an `englishEquivalent` string parameter rather than a `Map<targetLang, String>`** — current scope is en-US only; multi-language equivalents become relevant only when the spec covers languages beyond English (PRD scope is id-ID ↔ en-US for v1; Sundanese is render-mode only). When that day comes, the helper signature evolves; until then, single-equivalent is honest about the current scope.

**Multi-punct cleanup needed for `kan` specifically.** The `kan` rule's `, right?` injects before any trailing source `?`, producing `??`. Final-pass `Regex("""([.!?])\1+""")` → `"$1"` collapses to a single. Same protection covers `!` runs from any future emphatic particle. The cleanup runs once at the end of `postProcess`; idempotent.

**Generalized test means new particles need ZERO test changes.** Adding 9 more particles in Phase 2 = 9 directories + 27 fixtures only. No test class edits. This is the maintainability payoff the harness was supposed to deliver.

### Why not implement all 9 remaining TQ-1 particles in this PR

Three reasons:

1. **Different particles have different shapes.** `kok`/`ya`/`lah`/`kah` are sentence-final like Phase 1's; the helper covers them. But `nih`/`tuh` are deictics (sentence-initial or post-noun), `mah` is concessive (mid-sentence), `juga`/`also` are "also" (position-variable). Each non-sentence-final particle needs design thought + possibly a new helper. Mixing those decisions in with Phase 1's straightforward expansion would slow Phase 1 down + risk getting the harder cases wrong without your linguistic review.
2. **Reviewable PR size.** Phase 1 is already ~70 files (4 rule entries + 48 fixture files + test refactor + story + tracking). Doubling the surface would make CR harder.
3. **Real-evidence dependency.** The harder particles (kok, ya, mah) have polysemy that depends on conversational context. Story 3.1's pre-validation conversation is the right time to pin down which usages matter most for the partner pair, then implement those specifically.

### Verify-with-girlfriend list (Phase 1 fixtures)

All 12 new fixtures have explicit notes in their metadata.json. Summary of what to check:

| Particle | Fixture | Question for verification |
|---|---|---|
| `kan` | case_001 | Is "right?" the right register for casual coffee preference? Or "isn't it?" / "don't you?" |
| `kan` | case_002 | "right?" vs "aren't they?" for perfect-aspect check |
| `kan` | case_003 | "right?" vs "is that OK?" / "do you mind?" for permission-seeking |
| `sih` | case_001 | "though" vs "a bit" / "kind of" for self-disclosure-with-reservation |
| `sih` | case_002 | "though" vs "I guess" / "sort of" for lukewarm-opinion |
| `sih` | case_003 | Subject elision — should it be "I haven't gotten an answer, though" or "we haven't ..." or just elided? |
| `dong` | case_001 | "please" vs "come on" for affectionate-impatient hurry-up |
| `dong` | case_002 | "please" vs "would you" / "come on, teach me" for favor-request |
| `dong` | case_003 | "please" vs "mind" / "could you" for polite-action-request |
| `deh` | case_001 | ", then" vs "I guess I'll" / "I think I'll" for deliberative self-decision |
| `deh` | case_002 | ", then" vs "alright" / "okay" for inclusive-we joint decision |
| `deh` | case_003 | ", then" vs ", alright" for closing-instruction |

If any of these land wrong with your girlfriend, the fix is editing the `expected_target.txt` (test will then fail until either the fixture OR the rule's `targetEquivalent` is updated to match). Story 3.2b Phase 1 doesn't lock these in stone — they're explicit hypotheses to validate.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/
├── ParticleProcessor.kt     # MODIFIED (multi-punct cleanup)
├── ParticleRules.kt          # MODIFIED (helper extraction + 4 new rules)
└── … (5 stubs unchanged)

android/app/src/test/java/com/xaeryx/translatorrep/translation/particles/
└── ParticleProcessorFixtureTest.kt  # MODIFIED (generalized to iterate all rule dirs)

shared/particle-rules-fixtures/particles/
├── loh/case_{001,002,003}/         # PRE-EXISTING (Story 3.2)
├── kan/case_{001,002,003}/         # NEW (this PR)
├── sih/case_{001,002,003}/         # NEW
├── dong/case_{001,002,003}/        # NEW
└── deh/case_{001,002,003}/         # NEW
```

### References

- [Story 3.2](./3-2-particleprocessor-module-golden-file-fixtures.md) — pattern established in
- [architecture.md §11 "ParticleProcessor Module"](../planning-artifacts/architecture.md#11-particleprocessor-module-named-peer-to-providers)
- [shared/particle-rules-fixtures/README.md](../../shared/particle-rules-fixtures/README.md) — fixture contract
- [Story 3.1](../planning-artifacts/epics.md#story-31-pre-validation-conversation-with-girlfriend) — pre-validation conversation; the fixtures' VERIFY notes will turn into corrections after this story

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-PR #8-merge)

### Debug Log References

- No build issues. Helper-extraction + test refactor + 12 new fixtures all compiled + passed in one shot. Local `detekt + testDebugUnitTest` in 16s warm-cache.

### Completion Notes List

- **Phase 4 (latest):** TQ-3 (`dia → they`) + TQ-6 (6 religious-expression verbatim rules) landed. 7 helpers total (5 previous + 2 new). 63 fixture cases total (42 previous + 21 new). All 5 existing fixtures that contained `dia` in source were updated; no expected_target changes were needed (the singular-they convention was already in place).
- Phase 1: 5 of 14 TQ-1 particles done. Phase 2: 8 of 14. Phase 3: 14 of 14 (TQ-1 complete). Phase 4: TQ-3 + TQ-6 complete. Phase 5 remaining: TQ-4/5/7/8 (need Story 3.1 GF input).
- Generalized test method continues to handle all particle dirs without modification — Phase 4 needed test changes ONLY to relax the strict per-fixture `processed.particles == listOf(ruleName)` assertion to `contains(ruleName)`, because multi-particle fixtures now exist. The full per-fixture particle set is still strictly enforced by `expected_processed.txt` equality.
- Multi-punct cleanup (Phase 1) is defensive infrastructure that costs nothing for non-punctuation rules.
- All 21 Phase-4 fixtures + 5 updated fixtures carry explicit VERIFY-WITH-GIRLFRIEND notes; expected_target choices are conscious hypotheses, not assumptions. Religious-fixture notes flag multi-word variants (`insya Allah`, `mashallah`, etc.) + register-fit questions (subhanallah vs masyaallah for human-vs-natural admiration).

### File List

**Created (Phase 4):**

- `shared/particle-rules-fixtures/particles/dia/case_{001,002,003}/source.txt|expected_processed.txt|expected_target.txt|metadata.json` (12 files)
- `shared/particle-rules-fixtures/particles/alhamdulillah/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/insyaallah/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/bismillah/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/subhanallah/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/astaghfirullah/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/masyaallah/case_{001,002,003}/…` (12 files)

**Created (Phase 1):**

- `_bmad-output/implementation-artifacts/3-2b-particleprocessor-rule-tables.md` (this file)
- `shared/particle-rules-fixtures/particles/{kan,sih,dong,deh,kok,ya,lah,kah,nih,tuh,mah,juga,also}/case_{001,002,003}/…` (Phases 1-3, 13 dirs × 12 files = 156 files)

**Modified (Phase 4):**

- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleRules.kt` (2 new helpers `genderNeutralPronoun` + `verbatimReligious` + 7 new rule entries + KDoc updated to mention 6 helpers total)
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessorFixtureTest.kt` (relaxed strict particle-list assertion to `contains` for multi-particle fixtures)
- `shared/particle-rules-fixtures/particles/also/case_002/{expected_processed.txt,metadata.json}` (added `[PARTICLE:dia]` tag + `dia` to expected_particles)
- `shared/particle-rules-fixtures/particles/juga/case_002/{expected_processed.txt,metadata.json}` (same)
- `shared/particle-rules-fixtures/particles/kah/case_002/{expected_processed.txt,metadata.json}` (same)
- `shared/particle-rules-fixtures/particles/loh/case_003/{expected_processed.txt,metadata.json}` (same)
- `shared/particle-rules-fixtures/particles/mah/case_002/{expected_processed.txt,metadata.json}` (same)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (Phase 4 progress note; last_updated bump)
- `docs/project-context.md` §10 (3-2b status note) + §13 (Session 7 → DONE) + session handoff line

**Modified (Phases 1-3, prior PRs):**

- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleRules.kt` (helper extractions + 14 TQ-1 rules)
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessor.kt` (multi-punct cleanup + null-target support)

### Change Log

- 2026-05-24 — Story 3.2b created (status `in-progress`). Phase 1 landed kan + sih + dong + deh (4 of 13 remaining TQ-1 particles); helper-extracted ParticleRules; generalized fixture test; multi-punct cleanup. Phases 2-5 add the remaining TQ-1 particles + TQ-3/4/5/6/7/8 categories incrementally.
- 2026-05-24 (later) — **Phase 2** landed kok + ya + lah (3 more sentence-final particles). TQ-1 progress: 8 of 14 done. The remaining 6 (kah/nih/tuh/mah/juga/also) all need NEW helpers because they don't fit the sentence-final pattern — split out into Phase 3 with explicit design notes (suffix-detect for kah; deictic-position for nih/tuh; mid-sentence position-aware for mah/juga/also). Phase numbers 3–5 renumbered to 4–6 to accommodate the new Phase 3. Test infrastructure unchanged — the generalized fixture test auto-picked up the 3 new fixture dirs without any code changes (delivers the "phase 2+ needs zero test changes" promise from Phase 1).
- 2026-05-24 (even later) — **Phase 3** landed kah + nih + tuh + mah + juga + also via 4 new helpers (`formalQuestionSuffix`, `sentenceInitialDeictic`, `pronounConcessive`, `midSentenceAlso`). Required null-target support in `ParticleProcessor.postProcess` (Pass 1 strips markers with no equivalent; Pass 2 adds to preserved on detect even when equivalent is null) + `applicableRules` filter update to allow rules with empty `targetEquivalents`. **TQ-1 IS COMPLETE: 14 of 14 particles + 42 of 42 fixtures.** AC-1 + AC-2 ✅. Story 3.2b stays `in-progress` because TQ-3/4/5/6/7/8 (AC-3..AC-8) are still pending Phases 4-5.
- 2026-05-24 (Phase 4) — **TQ-3 + TQ-6 LANDED.** 2 new helpers (`genderNeutralPronoun`, `verbatimReligious`) + 7 new rule entries (`dia` + 6 religious terms). 21 new fixtures (3 each for dia/alhamdulillah/insyaallah/bismillah/subhanallah/astaghfirullah/masyaallah). 5 existing fixtures updated to add `[PARTICLE:dia]` tag + metadata (also/case_002, juga/case_002, kah/case_002, loh/case_003, mah/case_002 — all already used singular `they/them` in expected_target so no target changes needed). Test assertion relaxed from `processed.particles == listOf(ruleName)` (strict equality) to `processed.particles.contains(ruleName)` since multi-particle fixtures now exist; the full per-fixture set is still strictly enforced by `expected_processed.txt` equality. **63 of 63 fixture cases pass.** AC-6 + AC-8 ✅. Remaining: AC-3 (TQ-8 slang), AC-4 (TQ-4 Sundanese), AC-5 (TQ-5 honorifics), AC-7 (TQ-7 refusals) — Phase 5 (needs Story 3.1 input). Per `docs/project-context.md` §13 this was "Session 7 — Particle rules — easier" (no native-speaker input required for the chosen categories).
