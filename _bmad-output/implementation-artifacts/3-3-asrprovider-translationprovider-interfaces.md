# Story 3.3: AsrProvider + TranslationProvider Interfaces — Symmetric Surface + Cancellation Contract (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session). Android-only; iOS protocols are a future story.
     The Provider Abstraction NFR (PRD §6.4 / Patterns §9). Buildable + JVM-testable with no
     LiveKit / model / device — chosen while the calling path waits on Brady's LiveKit Cloud setup. -->

## Story

As a solo developer enforcing the Provider Abstraction NFR (PRD §6.4 / Architecture Patterns §9),
I want both platforms to expose `AsrProvider` and `TranslationProvider` interfaces with identical method names, parameter shapes, and a cross-platform-testable cancellation contract,
so that v2 Sundanese, Plan B Gemini, and any future provider can swap behind the abstraction without rewriting call sites.

## Acceptance Criteria

(From epics.md Story 3.3.)

**Given** Architecture Patterns §9 defines the interface shapes,
**When** I add the interfaces,
**Then:**

1. **AC-1 (AsrProvider):** `translation/asr/AsrProvider.kt` with `val supportsStreamingPartials: Boolean`, `fun start(language: LanguageCode): Flow<AsrEvent>`, `fun stop()`.
2. **AC-2 (TranslationProvider):** `translation/nmt/TranslationProvider.kt` with `suspend fun translate(sourceText, sourceLang, targetLang): TranslationResult`.
3. **AC-3 (TranslationResult):** identical fields — `targetText`, `particlesPreserved: List<String>`, `status: TranslationStatus`, `renderMode: RenderMode`, `confidence: Double`.
4. **AC-4 (test doubles):** `FakeAsrProvider` + `FakeTranslationProvider` exist for caption/CallSession tests without real ASR/model cost.
5. **AC-5 (cancellation contract):** `stop()` MUST terminate the stream + release all resources within **500 ms**, verified by a unit test asserting no allocated resources remain 500 ms after `stop()`.
6. **AC-6 (LanguageCode):** a `LanguageCode` wrapper enforces BCP 47 (`id-ID`, `en-US`, `su-ID`); Flores-200 codes appear ONLY inside `TranslationProvider.translate()` → model call, never elsewhere (Patterns §3).
7. **AC-7 (UI never calls providers):** all flows go through `CallSession` (Patterns §13).

**Out of scope / deferred:**
- **iOS** `AsrProvider`/`TranslationProvider` protocols + Swift fakes → future iOS story (Android-first).
- **The detekt rule** that flags direct Provider calls from View code (AC-7 enforcement) → **deferred** (documented below): it needs a custom per-package detekt rule; for now the convention is structurally enforced (everything routes through `CallSession`) + CR, matching how the project handles its other conventions. There are no View-layer provider call sites yet.

**Done criteria:** flips to `review` when AC-1..AC-6 ✅ + AC-7 convention documented, and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`.

## Tasks / Subtasks

- [x] **1.1** `translation/LanguageCode.kt` — `@JvmInline value class` over a BCP 47 string, `init`-validated to `xx-XX`; `INDONESIAN`/`ENGLISH`/`SUNDANESE` constants. Keeps Flores codes off the public surface (Patterns §3).
- [x] **1.2** `translation/asr/AsrEvent.kt` — sealed `Partial(text)` / `Final(text)` / `Error(errorCode)` (errorCode = an `ErrorCode` constant; text is conversation content, never logged).
- [x] **1.3** `translation/asr/AsrProvider.kt` + `translation/nmt/TranslationProvider.kt` interfaces (symmetric with the iOS protocols to come).
- [x] **1.4** `translation/TranslationResult.kt` — the 5-field result (reuses existing `TranslationStatus` + `RenderMode`).
- [x] **2.1** `FakeAsrProvider` (test) — scripted events + a `resourcesAllocated` flag + an open stream held until `stop()`; `FakeTranslationProvider` (test) — canned result + call recorder.
- [x] **3.1** `LanguageCodeTest` (3) — constants, well-formed accept, malformed/Flores reject. `AsrProviderCancellationTest` (2) — the 500 ms stop-terminates-and-releases contract (+ idempotent stop). `FakeTranslationProviderTest` (1).
- [x] **4.1** Story file + `sprint-status.yaml` (epic-3 → in-progress; 3-3 → review) + `docs/project-context.md`.

## Dev Notes

### The cancellation contract test (AC-5), without coroutines-test

`FakeAsrProvider.start` emits its scripted events then parks on a `CompletableDeferred` (modeling a live engine holding resources); `stop()` completes it → the flow's `finally` clears `resourcesAllocated` → the stream terminates. The test collects with `CoroutineStart.UNDISPATCHED` (so emission happens inline before the assertions), calls `stop()`, then `withTimeout(500) { job.join() }` proves termination within budget and asserts `resourcesAllocated == false`. This works deterministically under plain `runBlocking` (the toolchain has no `kotlinx-coroutines-test`).

### Why 3.10 is NOT in this PR (was paired in the request)

Story 3.10 (`RuleBasedTranslationProvider` decorator) composes `ParticleProcessor` with the **locked `RawTranslationProvider`** — and that provider is the *outcome of Story 3.9* (the Week-1 on-device model bake-off), which hasn't run. So 3.10 is genuinely blocked on 3.9; building it now would hardcode a guess at the locked model. 3.3 (the interfaces these providers implement) is the unblocked, foundational piece. 3.10 follows once 3.9 locks the model.

### AC-7 detekt rule deferred (not skipped)

A custom detekt rule banning Provider references in `ui/`/View code is a mini-project (a per-package rule, unlike the existing import-based `ForbiddenImport`). Deferred with this note; the architecture *structurally* enforces it (UI → `CallSession` → providers; `CallSession` is the only holder), and CR catches violations — consistent with how other conventions are enforced. Worth adding when the first real provider call sites exist (Story 3.4+).

### Test-double location

The fakes live in `src/test/` (used by this story's tests + future CaptionStack/CallSession unit tests) so they don't bloat the release APK. If an `androidTest` (instrumented) suite ever needs them, promote to a `testFixtures` source set.

### Library references

- [Architecture Patterns §3 / §9 / §13](../planning-artifacts/architecture.md) — language-code boundary, provider/cancellation contract, "UI never calls providers directly".
- [Kotlin inline value classes](https://kotlinlang.org/docs/inline-classes.html) — `LanguageCode`.
- [BCP 47](https://www.rfc-editor.org/rfc/rfc5646) — language tags.

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/translation/
├── LanguageCode.kt                 # NEW (BCP 47 value class)
├── TranslationResult.kt            # NEW
├── asr/
│   ├── AsrEvent.kt                 # NEW
│   └── AsrProvider.kt              # NEW (interface)
└── nmt/
    └── TranslationProvider.kt      # NEW (interface)
android/app/src/test/java/com/xaeryx/translatorrep/translation/
├── LanguageCodeTest.kt             # NEW (3)
├── asr/
│   ├── FakeAsrProvider.kt          # NEW (test double)
│   └── AsrProviderCancellationTest.kt  # NEW (2 — the 500ms contract)
└── nmt/
    ├── FakeTranslationProvider.kt  # NEW (test double)
    └── FakeTranslationProviderTest.kt  # NEW (1)
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); the cancellation contract is verified without instrumented tests. Local validate (JDK 17): `:app:detekt` 0 smells; `LanguageCodeTest` 3/3 + `AsrProviderCancellationTest` 2/2 + `FakeTranslationProviderTest` 1/1 (**53 unit tests total app-wide green**); `:app:assembleDebug` green.

### References

- [epics.md Story 3.3](../planning-artifacts/epics.md) — AC source.
- [Story 3.4/3.5 (next ASR impls)](../planning-artifacts/epics.md) — implement `AsrProvider`.
- [Story 3.8/3.9/3.10](../planning-artifacts/epics.md) — NMT providers, model bake-off, the rules decorator.
- existing `translation/TranslationStatus.kt` + `RenderMode.kt` — reused by `TranslationResult`.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24)

### Debug Log References

- Local validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`LanguageCodeTest` 3/3, `AsrProviderCancellationTest` 2/2, `FakeTranslationProviderTest` 1/1; 53 total); `:app:assembleDebug` BUILD SUCCESSFUL.

### Completion Notes List

- Defined the symmetric provider surface (`AsrProvider`, `TranslationProvider`, `AsrEvent`, `TranslationResult`, `LanguageCode`) + reference fakes.
- The §9 500 ms cancellation contract is captured as a deterministic JVM test against `FakeAsrProvider` (UNDISPATCHED collect + `withTimeout` join).
- `LanguageCode` keeps Flores-200 codes off the public surface (Patterns §3); reused existing `TranslationStatus`/`RenderMode`/`ErrorCode`.
- 3.10 (decorator) intentionally NOT built — blocked on Story 3.9's locked `RawTranslationProvider`. AC-7 detekt rule deferred (documented; convention structurally enforced).

### File List

**Created (main):**
- `_bmad-output/implementation-artifacts/3-3-asrprovider-translationprovider-interfaces.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/LanguageCode.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/TranslationResult.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/asr/AsrEvent.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/asr/AsrProvider.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/translation/nmt/TranslationProvider.kt`

**Created (test):**
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/LanguageCodeTest.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/asr/FakeAsrProvider.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/asr/AsrProviderCancellationTest.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/nmt/FakeTranslationProvider.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/translation/nmt/FakeTranslationProviderTest.kt`

**Modified:**
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — epic-3 → in-progress; 3-3 → review; last_updated bump
- `docs/project-context.md` — Epic-3 status note

### Change Log

- 2026-05-24 — Story 3.3 implemented (Android). Symmetric `AsrProvider` + `TranslationProvider` interfaces, `AsrEvent`, `TranslationResult`, BCP-47 `LanguageCode`, + reference fakes; the §9 500 ms cancellation contract verified by a JVM test. 6 unit tests; detekt clean; assembleDebug green. 3.10 deferred (blocked on 3.9); AC-7 detekt rule deferred (documented). Status → `review`.
