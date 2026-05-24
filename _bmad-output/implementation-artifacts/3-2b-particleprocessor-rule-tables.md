# Story 3.2b: ParticleProcessor Rule Tables — Expansion of Story 3.2's loh-only Scope

Status: in-progress

<!-- Created 2026-05-24 (Phase 1 of N) on feature/3-2b-tq1-kan-sih-dong-deh.

     Multi-PR rollup story. Phase 1 (this PR): landed kan + sih + dong + deh
     (4 of 13 remaining TQ-1 particles). Subsequent phases add more particles +
     other TQ categories. Story flips to `review` → `done` when the FULL original
     Story 3.2 AC (14 TQ-1 + TQ-3/4/5/6/7/8 categories) is populated.

     iOS Swift parity is Story 3.2c (Mac/iOS Claude session). -->

## Story

As a solo developer extending the ParticleProcessor pattern proven out by Story 3.2,
I want all 14 TQ-1 Indonesian discourse particles + the TQ-3/4/5/6/7/8 rule categories populated incrementally on the existing harness,
so that the on-device translation pipeline can preserve cultural-pragmatic context across the full domain-research surface before Story 3.7's regression-corpus bake-off + Story 3.9's model validation gate.

## Acceptance Criteria

**Given** Story 3.2 landed the ParticleProcessor module structure + harness + one fully-functional rule (`loh`),
**When** this story's phases land cumulatively,
**Then:**

1. **AC-1 (TQ-1: 14 Indonesian discourse particles fully implemented):** All 14 `ParticleRule` entries registered in `ParticleRules.kt` using the `sentenceFinalParticle` (or analogous) helper. Phase 1 landed `loh + kan + sih + dong + deh` = **5 of 14 done; 9 to go** (`kok`, `ya`, `lah`, `kah`, `nih`, `tuh`, `mah`, `juga`, `also`).
2. **AC-2 (Per-rule ≥3 golden-file fixtures):** Every TQ-1 rule has ≥3 fixtures under `shared/particle-rules-fixtures/particles/<name>/case_NNN/`. Phase 1 covered: `loh` (3 from Story 3.2), `kan` (3), `sih` (3), `dong` (3), `deh` (3) = **15 of 42 done; 27 to go**.
3. **AC-3 (TQ-8: ≥20 Gen-Z slang items):** `GenZSlang.kt` populated; ≥20 slang items with their target equivalents + ≥3 fixtures each. **0 of 20 done.**
4. **AC-4 (TQ-4: ≥12 Sundanese lexical insertions):** `SundaneseInsertions.kt` populated as a side-channel processor (renders `RenderMode.SUNDANESE_PLACEHOLDER` rather than injecting an equivalent). **0 of 12 done.**
5. **AC-5 (TQ-5: partner honorifics + strip rules):** `HonorificStripping.kt` implements detect + strip/preserve per intimate-vs-formal register decisions from DR §6. **Not started.**
6. **AC-6 (TQ-6: religious-expression verbatim preservation):** `ReligiousExpressions.kt` dictionary of Arabic-origin religious expressions; postProcess preserves them verbatim. **Not started.**
7. **AC-7 (TQ-7: indirect refusals):** `IndirectRefusals.kt` detects + annotates indirect refusal patterns per DR §6. **Not started.**
8. **AC-8 (TQ-3: gender-neutral `dia → they` default):** New rule that handles Indonesian gender-neutral pronouns, defaulting to English singular-they (not he/she). Currently inlined in `loh/case_003` metadata as an observation only — needs a code rule. **Not started.**
9. **AC-9 (Generalized fixture test):** `ParticleProcessorFixtureTest.kt` iterates every `particles/<rule>/case_NNN/` dir without hardcoded rule names — Phase 1 ✅.
10. **AC-10 (Multi-punct cleanup):** `ParticleProcessor.postProcess` collapses duplicated terminal punctuation (`??` → `?`, `!!` → `!`) so rule equivalents containing `?` (e.g., `kan` → `, right?`) don't leave artifacts when injected before an existing source-side `?` — Phase 1 ✅.

**Done criteria:** Story 3.2b flips to `review` when AC-1, AC-2, AC-3, AC-4, AC-5, AC-6, AC-7, AC-8 are all ✅. AC-9 + AC-10 are infrastructure ACs satisfied by Phase 1.

## Tasks / Subtasks

### Phase 1 — TQ-1 expansion: kan + sih + dong + deh (this PR, 2026-05-24)

- [x] **Refactor `ParticleRules.kt` to extract `sentenceFinalParticle(name, englishEquivalent)` helper.** Replaces hand-rolled rule construction with one-line entries; new TQ-1 particles drop in trivially. The existing `loh` rule now uses the helper too (functional equivalent; cleaner).
- [x] **Add 4 new TQ-1 rules via the helper.** `kan → ", right?"`, `sih → ", though"`, `dong → ", please"`, `deh → ", then"`. Helper uses `Regex.escape(name)` for defense-in-depth even though current particle names are all alpha-lowercase.
- [x] **Harden `ParticleProcessor.postProcess` cleanup pass.** Added multi-punct collapse (`?` and `!` runs collapse to single) so the `kan` equivalent's trailing `?` doesn't double when injected before a source-side `?`. The detekt LoopWithTooManyJumpStatements rule stays satisfied because cleanup is a series of simple `.replace()` calls, not a loop.
- [x] **Generalize `ParticleProcessorFixtureTest.kt`.** Single test method iterates every `particles/<rule>/case_NNN/` directory; rule name from dir name doubles as the expected particle token. Removes loh-specific hardcoding from Story 3.2. AC-9 ✅.
- [x] **Write 12 new fixtures.** 3 cases each for kan, sih, dong, deh — all with explicit `VERIFY WITH GIRLFRIEND` notes in metadata.json. Test count: 12 → 12 (still 1 test method, now running 15 fixture cases internally).
- [x] **Local validation.** `./gradlew :app:detekt :app:testDebugUnitTest` 16s warm-cache; 0 detekt smells; all 12 unit-test methods pass (1 fixture method × 15 cases internally).

### Phase 2 — TQ-1 completion: remaining 9 particles (future PR)

- [ ] **`kok`** (mild surprise): probably `sentenceFinalParticle("kok", ", how come?")` — but kok has many uses (sentence-initial "kok, ...", mid-sentence emphasis). May need a separate helper for kok's polysemy. Verify shape with girlfriend before implementation.
- [ ] **`ya`** (confirmation): `sentenceFinalParticle("ya", ", yeah?")` — careful, "ya" also means "yes" as a standalone word + appears in mid-sentence as filler. Pattern matching must be precise.
- [ ] **`lah`** (emphasis): `sentenceFinalParticle("lah", ", indeed")` or `, of course`. Register varies wildly.
- [ ] **`kah`** (formal question marker): special case — typically DROP in target (English doesn't need a marker for questions; word order does the work). Helper variant: `sentenceFinalParticleDropInTarget("kah")`.
- [ ] **`nih`** (proximal "this here"): different pattern — sentence-initial or post-noun, not sentence-final. Will need a `proximalDeictic` helper or hand-rolled rule.
- [ ] **`tuh`** (distal "that there"): mirror of nih; same pattern variation.
- [ ] **`mah`** (concession): mid-sentence "as for X", different position. Hand-roll likely.
- [ ] **`juga`** (also): "saya juga" (me too), "dia juga datang" (they came too) — position varies. Mid-sentence detect + position-aware inject.
- [ ] **`also`** (Indonesian loanword): same as juga; could share rule entry or duplicate.

### Phase 3 — TQ-8 Gen-Z slang dictionary (future PR)

- [ ] Populate `GenZSlang.kt` with ≥20 contemporary slang items + their target equivalents. Examples: `gabut` (bored), `mager` (lazy), `cuy` (mate/dude), `bestie`, `gass` (let's go), `santuy` (chill), `mantap` (awesome), `auto` (automatically), `wkwkwk` (haha), `sotoy` (know-it-all), `kepo` (nosy), `gws` (get well soon), `btw`, `omg`, `pengen` (want), `bgt` (very), `udh` (already), `gak` (no/not), `nyariin` (looking for), `lemes` (weak/tired).
- [ ] ≥3 fixtures per slang item. Position-aware inject (slang substitutes the source token, doesn't append at end).
- [ ] Defer-decision: Phase 3 might warrant its own helper `wordSubstitutionRule(sourceToken, targetEquivalent)` since the substitution semantics differ from sentence-final discourse particles.

### Phase 4 — TQ-3/4/5/6/7 categories (future PRs)

- [ ] **TQ-3 gender-neutral `dia → they` rule.** Verify integration with existing rules — `dia datang besok loh` (loh/case_003) already hand-codes the gender-neutral choice in the expected_target; the new rule should make that automatic.
- [ ] **TQ-4 Sundanese spans:** side-channel processor in `SundaneseInsertions.kt`; emits `RenderMode.SUNDANESE_PLACEHOLDER` rather than equivalent injection. Different `PostProcessed` shape consumer.
- [ ] **TQ-5 honorifics:** `HonorificStripping.kt` — register-aware (intimate → strip, formal → preserve).
- [ ] **TQ-6 religious verbatim:** `ReligiousExpressions.kt` — dictionary lookup, marker substitution returns same token in target (no translation).
- [ ] **TQ-7 indirect refusals:** `IndirectRefusals.kt` — pragmatic-marker injection.

### Phase 5 — Done criteria + 3.2b → review

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

- Phase 1 of 4-5 phases. 5 of 14 TQ-1 particles done.
- Generalized test method handles all particle dirs without modification — future phases need fixture additions only, not test changes.
- Multi-punct cleanup is defensive infrastructure that costs nothing for non-punctuation rules.
- 12 new fixtures all carry explicit VERIFY-WITH-GIRLFRIEND notes; expected_target choices are conscious hypotheses, not assumptions.

### File List

**Created:**

- `_bmad-output/implementation-artifacts/3-2b-particleprocessor-rule-tables.md` (this file)
- `shared/particle-rules-fixtures/particles/kan/case_{001,002,003}/source.txt|expected_processed.txt|expected_target.txt|metadata.json` (12 files)
- `shared/particle-rules-fixtures/particles/sih/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/dong/case_{001,002,003}/…` (12 files)
- `shared/particle-rules-fixtures/particles/deh/case_{001,002,003}/…` (12 files)

**Modified:**

- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleRules.kt` (helper extraction + 4 new rules)
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessor.kt` (multi-punct cleanup added)
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessorFixtureTest.kt` (generalized to iterate all rule dirs)
- `_bmad-output/implementation-artifacts/sprint-status.yaml` (3-2b → in-progress; last_updated bump)
- `docs/project-context.md` §10 (status note on 3-2b)

### Change Log

- 2026-05-24 — Story 3.2b created (status `in-progress`). Phase 1 landed kan + sih + dong + deh (4 of 13 remaining TQ-1 particles); helper-extracted ParticleRules; generalized fixture test; multi-punct cleanup. Phases 2-5 add the remaining TQ-1 particles + TQ-3/4/5/6/7/8 categories incrementally.
