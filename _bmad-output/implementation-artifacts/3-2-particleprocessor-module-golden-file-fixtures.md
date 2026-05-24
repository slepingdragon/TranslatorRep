# Story 3.2: ParticleProcessor Module + Golden-File Fixtures (Android, TQ-1 `loh` only)

Status: review

<!-- Created 2026-05-24 by feature/3-2-particleprocessor-android branch.
     Story scope cut to Android-only + TQ-1 single particle (`loh`) per
     Bania's "Android first; do something autonomously" direction.

     iOS Swift parity              → Story 3-2c-particleprocessor-ios-parity
     Remaining TQ-1 particles +
       TQ-3 + TQ-4 + TQ-5 +
       TQ-6 + TQ-7 + TQ-8 rules   → Story 3-2b-particleprocessor-rule-tables -->

## Story

As a solo developer implementing the rules-based particle preservation strategy (ADR-B3),
I want a working `ParticleProcessor` module on Android with the structural pattern proved out by one fully-implemented rule (TQ-1 `loh`) + cross-platform golden-file fixture harness,
so that subsequent rule additions (Story 3.2b) drop into a known shape and the harness validates them on every CI run.

## Acceptance Criteria

**Given** Story 1.7 scaffolded `shared/particle-rules-fixtures/` with the contract README + starter `loh/case_001/` fixture, and architecture §11 specifies the 7-file ParticleProcessor module layout,
**When** the module is implemented (Android Kotlin scope),
**Then:**

1. **AC-1 (Module layout per architecture §11 file list):** `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/` contains all 7 files mandated by architecture §11: `ParticleProcessor.kt`, `ParticleRules.kt`, `SundaneseInsertions.kt`, `HonorificStripping.kt`, `IndirectRefusals.kt`, `GenZSlang.kt`, `ReligiousExpressions.kt`. The 5 sibling rule files are stubs with explicit `TODO Story 3.2b` markers; `ParticleProcessor.kt` + `ParticleRules.kt` are functional.
2. **AC-2 (`ParticleProcessor` API matches Architecture Patterns §9 symmetric surface):** Exposes `preProcess(text: String, sourceLang: String, targetLang: String): ProcessedText` and `postProcess(rawTarget: String, originalSource: String, sourceLang: String, targetLang: String): PostProcessed`. iOS parity (Story 3-2c) will mirror the identical signatures in Swift.
3. **AC-3 (TQ-1 `loh` rule fully implemented):** `ParticleRules.allRules` includes the `loh` rule with detect (sentence-final position with whitespace lookbehind + optional terminal-punctuation lookahead), `targetEquivalents = { "en-US": ", you know" }`, and an `inject` function that places the equivalent immediately before any trailing punctuation (or at end of string). Preserves the existing `loh/case_001` semantics from Story 1.7's starter fixture.
4. **AC-4 (≥3 fixtures for `loh`):** `shared/particle-rules-fixtures/particles/loh/` contains `case_001/` (pre-existing from Story 1.7), `case_002/` (this PR — value-judgement assertion), `case_003/` (this PR — information-sharing + TQ-3 gender-neutral `dia → they`).
5. **AC-5 (Fixture-test harness):** `ParticleProcessorFixtureTest.kt` discovers all `loh/case_NNN/` directories, runs preProcess + postProcess against each, asserts: (a) `preProcess(source) == expected_processed`, (b) `preProcess.particles == ["loh"]`, (c) `postProcess(expected_target, source)` is idempotent (returns expected_target unchanged), (d) `postProcess.particlesPreserved` contains every `expected_particles` entry from the rule. Test fails if fewer than 3 cases are found.
6. **AC-6 (Build integration):** `app/build.gradle.kts` passes `repo.root` system property to `tasks.withType<Test>` so the fixture loader resolves `shared/particle-rules-fixtures/` regardless of Gradle test working directory.
7. **AC-7 (Local + CI green):** `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug --no-daemon` passes locally; android-ci.yml on PR run passes (path filter `android/**` + `shared/**` matches both source + fixture changes).

**Note:** Cross-platform parity test (Android + iOS must produce byte-identical outputs on the shared fixture set) is **deferred to Story 3-2c** since iOS implementation doesn't exist yet. Until 3-2c, the Android test is the only validator.

## Tasks / Subtasks

### Android implementation (this PR)

- [x] **Task 1: Create `translation/` package + supporting enums.** `RenderMode.kt` + `TranslationStatus.kt` mirror canonical-names.md §1 + architecture §F enum specs. wireValue extensions match the snake_case/camelCase wire forms specified in canonical-names.md.
- [x] **Task 2: ParticleProcessor.kt — preProcess + postProcess two-pass design.** Detect particles via `ParticleRules.applicableRules(sourceLang, targetLang)`; tag with `[PARTICLE:<name>]` in preProcess; substitute markers OR inject equivalents in postProcess; final-pass whitespace cleanup. Idempotency check: skip injection if equivalent already present in rawTarget.
- [x] **Task 3: ParticleRules.kt — registry + `ParticleRule` data class.** Single `loh` rule implemented; 13 TQ-1 placeholders documented as `TODO Story 3.2b`. `ParticleRule` exposes the four operations the processor needs: detect, sourceLang filter, targetEquivalents map, inject function.
- [x] **Task 4: 5 sibling stub files.** `SundaneseInsertions.kt`, `HonorificStripping.kt`, `IndirectRefusals.kt`, `GenZSlang.kt`, `ReligiousExpressions.kt` — each documents its TQ-N category + Story 3.2b TODO + the rendering/injection strategy that follow-up will implement.
- [x] **Task 5: Fixture-test harness.** `ParticleProcessorFixtureTest.kt` walks `particles/loh/case_NNN/` dirs, runs both passes, asserts. Uses `repo.root` system property to locate `/shared/` directory at the repo root.
- [x] **Task 6: Build config.** `tasks.withType<Test>().configureEach { systemProperty("repo.root", rootProject.projectDir.parentFile.absolutePath) }` in `app/build.gradle.kts`.
- [x] **Task 7: 2 new fixtures.** `loh/case_002` (value-judgement, "Itu enak banget loh") + `loh/case_003` (information-sharing + gender-neutral dia, "Dia datang besok loh"). Each has source.txt, expected_processed.txt, expected_target.txt, metadata.json.
- [x] **Task 8: Local validation.** `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug` green. Test count 11 → 12 (+1 ParticleProcessorFixtureTest method).
- [x] **Task 9: Sprint tracking + project-context.** Add 3-2b + 3-2c backlog entries; flip 3-2 → review; project-context §10 updated.

### Out of scope (deferred to sub-letter follow-ups)

- ❌ **TQ-1 remaining 13 particles** (`kan`, `sih`, `dong`, `deh`, `kok`, `ya`, `lah`, `kah`, `nih`, `tuh`, `mah`, `juga`, `also`) — each ≥3 fixtures → **Story 3-2b** (~42 new fixtures alone)
- ❌ **TQ-8 Gen-Z slang dictionary** (≥20 items × 3 fixtures) — **Story 3-2b**
- ❌ **TQ-4 Sundanese lexical insertions** (≥12 × 3) — **Story 3-2b**
- ❌ **TQ-5 honorifics + strip rules** — **Story 3-2b**
- ❌ **TQ-6 religious-expression verbatim preservation** — **Story 3-2b**
- ❌ **TQ-7 indirect refusals** — **Story 3-2b**
- ❌ **TQ-3 gender-neutral `dia → they`** as its own rule (currently inlined as part of fixture case_003's metadata, not a code rule yet) — **Story 3-2b**
- ❌ **iOS Swift parity** — **Story 3-2c** (Mac/iOS session)
- ❌ **Cross-platform parity assertion test** — depends on Story 3-2c
- ❌ **`raw_target.txt` injection-test variant** — current harness tests idempotency only; the harder "naive NMT output → postProcess injects equivalent" path needs fixtures to include the naive-NMT input. Track as deferred-work item.
- ❌ **metadata.json-driven test parameterization** — current test hardcodes `loh` expectations. Story 3.2b switches to metadata-driven so adding rules doesn't require editing the test class.

## Dev Notes

### Why scope-cut to one particle

The original epics.md AC for Story 3.2 enumerates 7 rule categories × ≥3 fixtures each = 100+ fixtures minimum, plus iOS parity. That's a multi-day story.

This PR ships the **structural pattern** + **end-to-end harness** + **one fully-validated rule**. Sub-letter follow-ups become "fill in the rule table" stories that have:
- ✅ no architectural decisions to make
- ✅ proven structural pattern to copy
- ✅ working test harness to add fixtures to
- ✅ small, reviewable PR-sized chunks

This matches the established sub-letter pattern (Story 1.6 → 1.6b/c/d, Story 1.4 → 1.4b/c). The CR will flag any architectural deficiency now rather than after 100+ fixtures are written against a flawed pattern.

### Why idempotent-postProcess test rather than naive-NMT injection test

The README at `/shared/particle-rules-fixtures/README.md` says the post-processor test should assert `postProcess(rawTarget, ...) == expected_target`. The rawTarget isn't separately specified in the fixture — it implicitly comes from what the NMT would have produced.

For this PR, "rawTarget = expected_target" is the **idempotency case**: confirms postProcess doesn't double-inject when the equivalent is already present in the NMT output (the realistic case where the NMT happens to produce ", you know" naturally from "loh"). This catches a real class of bug — over-injection — without needing a new fixture field.

The **harder injection test** ("naive NMT output without the equivalent → postProcess adds the equivalent") is genuinely useful but needs each fixture to include `raw_target.txt` with the naive-NMT-shaped input. That's a fixture-format extension — track as `deferred-work.md` item and address in Story 3.2b.

### `ParticleRule` design — open-closed extension

The `ParticleRule` data class is closed-over four operations: `detect`, `targetEquivalents`, `inject`, `name + sourceLang`. Adding a new particle is one entry in `ParticleRules.allRules`. The `ParticleProcessor` never changes.

This pays off in Story 3.2b: 13+ new entries, zero changes to `ParticleProcessor.kt`. The 5 stub files (SundaneseInsertions et al.) will end up either as additional `ParticleRule` entries (slang, honorifics, religious — all word-substitution patterns) or as side-channel processors that operate alongside ParticleProcessor (Sundanese spans, indirect refusals — both need different semantics).

### Verify-with-girlfriend notes

Both new fixtures (`case_002`, `case_003`) have **VERIFY WITH GIRLFRIEND** notes in their metadata.json. The English equivalents I chose ("That's really delicious, you know" / "They're coming tomorrow, you know") are plausible but might not match the partner pair's actual conversational register. The fixtures are version-controlled — easy to update after Story 3.1's pre-validation conversation provides real-evidence corrections.

### Library / API research

- **No new dependencies.** Everything uses Kotlin stdlib + existing JUnit 4 (`org.junit.Test`, `org.junit.Assert.assertEquals`, `org.junit.Assert.assertTrue`).
- **`org.json` deliberately avoided.** Android's mock SDK stubs `org.json.*` in unit tests (returns "Method not mocked" without Robolectric). Adding Robolectric or `org.json:json` to satisfy metadata-parsing is out of scope; hardcoded loh-specific expectations cover this PR. Story 3.2b is where metadata-driven parameterization becomes necessary (it'll motivate the JSON-parsing decision then).
- **`File` access in unit tests** works fine without instrumentation — JVM-side `java.io.File` reads the actual filesystem, and the `repo.root` system property points at the repo root via `rootProject.projectDir.parentFile`.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/translation/
├── RenderMode.kt                       # NEW — enum (architecture §F)
├── TranslationStatus.kt                # NEW — enum (architecture §7)
└── particles/
    ├── ParticleProcessor.kt            # NEW — entry point (functional)
    ├── ParticleRules.kt                # NEW — registry + loh rule (functional)
    ├── SundaneseInsertions.kt          # NEW — STUB (Story 3.2b)
    ├── HonorificStripping.kt           # NEW — STUB (Story 3.2b)
    ├── IndirectRefusals.kt             # NEW — STUB (Story 3.2b)
    ├── GenZSlang.kt                    # NEW — STUB (Story 3.2b)
    └── ReligiousExpressions.kt         # NEW — STUB (Story 3.2b)

android/app/src/test/java/com/xaeryx/translatorrep/translation/particles/
└── ParticleProcessorFixtureTest.kt     # NEW — fixture-driven test (functional)

android/app/build.gradle.kts            # MODIFIED — added Test systemProperty block

shared/particle-rules-fixtures/particles/loh/
├── case_001/                            # PRE-EXISTING (Story 1.7)
├── case_002/                            # NEW (this story) — value-judgement
│   ├── source.txt, expected_processed.txt, expected_target.txt, metadata.json
└── case_003/                            # NEW (this story) — info-sharing + dia
    ├── source.txt, expected_processed.txt, expected_target.txt, metadata.json
```

### References

- [architecture.md §11 "ParticleProcessor Module"](../planning-artifacts/architecture.md#11-particleprocessor-module-named-peer-to-providers) — module file layout + fixture format
- [architecture.md §B "Translation Stack"](../planning-artifacts/architecture.md#b-translation-stack) — ADR-B3 rules-based preservation strategy
- [shared/particle-rules-fixtures/README.md](../../shared/particle-rules-fixtures/README.md) — fixture contract
- [shared/canonical-names.md §1](../../shared/canonical-names.md) — `RenderMode` + `TranslationStatus` enum specs + wire forms
- [Story 1.7](./1-7-shared-specs-directory-cross-platform-fixtures.md) — scaffolded `/shared/` + starter loh fixture
- [Story 3.1](../planning-artifacts/epics.md#story-31-pre-validation-conversation-with-girlfriend) — pre-validation conversation that will inform fixture content corrections
- Domain Research §1 (Indonesian discourse particles) — source of truth for particle semantics (TODO: link to DR doc once located)

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24 while Bania doing Phase 0 for Story 1.4)

### Debug Log References

- **Detekt initial failure on Pass 2 loop**: `LoopWithTooManyJumpStatements` at `ParticleProcessor.kt:102`. Refactored the early-continue chain to a nested-if structure (one explicit conditional, zero `continue` statements). No behavior change; reads slightly worse but stays inside detekt's complexity rule. Considered `@Suppress("LoopWithTooManyJumpStatements")` but chose refactor since the post-refactor code is genuinely fine.
- No other build issues.

### Completion Notes List

- ParticleProcessor module structure landed; one functional rule (TQ-1 `loh`) + 5 stubs documenting Story 3.2b expectations.
- Test harness loads fixtures via `repo.root` system property — pattern reusable for future shared/-fixture tests in any subsystem.
- 3 fixtures total for `loh` (case_001 pre-existed; case_002 + case_003 added). Both new fixtures include explicit `VERIFY WITH GIRLFRIEND` notes in metadata.json so corrections are obvious targets.
- Test count: 11 → 12 (+1 new test method that iterates over all 3 fixtures).
- Build wall-clock locally: detekt + tests + assembleDebug in **24s** (warm cache). CI verification pending push.

### File List

**Created:**

- `android/app/src/main/java/com/xaeryx/translatorrep/translation/RenderMode.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/TranslationStatus.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessor.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ParticleRules.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/SundaneseInsertions.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/HonorificStripping.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/IndirectRefusals.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/GenZSlang.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/particles/ReligiousExpressions.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/particles/ParticleProcessorFixtureTest.kt`
- `shared/particle-rules-fixtures/particles/loh/case_002/source.txt`
- `shared/particle-rules-fixtures/particles/loh/case_002/expected_processed.txt`
- `shared/particle-rules-fixtures/particles/loh/case_002/expected_target.txt`
- `shared/particle-rules-fixtures/particles/loh/case_002/metadata.json`
- `shared/particle-rules-fixtures/particles/loh/case_003/source.txt`
- `shared/particle-rules-fixtures/particles/loh/case_003/expected_processed.txt`
- `shared/particle-rules-fixtures/particles/loh/case_003/expected_target.txt`
- `shared/particle-rules-fixtures/particles/loh/case_003/metadata.json`
- `_bmad-output/implementation-artifacts/3-2-particleprocessor-module-golden-file-fixtures.md` (this file)

**Modified:**

- `android/app/build.gradle.kts` — added `tasks.withType<Test>` block to pass `repo.root` system property
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 3-2 entry flipped + 3-2b + 3-2c added
- `docs/project-context.md` — §10 updated with 3-2b + 3-2c sub-letter rows

### Change Log

- 2026-05-24 — Story 3.2 created + implemented + landed for review on `feature/3-2-particleprocessor-android`. Scope cut to Android-only + TQ-1 `loh` single rule per Bania's autonomous-while-Phase-0 direction. Pattern established for sub-letter follow-ups (3.2b = remaining rules; 3.2c = iOS parity). 9 source files + 1 test file + 2 fixtures × 4 files + build config + tracking docs. Local build 24s warm-cache; detekt clean (zero smells); 12 tests pass.
