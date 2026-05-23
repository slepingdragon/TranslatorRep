# Story 1.5: SafeLog Facade + Lint Enforcement + ULID Library Wiring

Status: ready-for-dev

<!-- Created 2026-05-23 by bmad-create-story. Validation optional; run bmad-create-story:validate before bmad-dev-story if desired. -->

## Story

As a solo developer,
I want a `SafeLog` facade on both platforms with `AllowedLogKey` enum, lint rules banning direct logging APIs outside the facade, and a pinned ULID library wrapped behind a `UlidGenerator` interface,
so that conversation content can never accidentally leak to logs and all canonical entity IDs are time-sortable, collision-resistant, and byte-identical across Android and iOS.

## Acceptance Criteria

**From [epics.md §1.5](../planning-artifacts/epics.md):**

1. **AC-1:** `SafeLog.kt` exists in `android/app/src/main/java/com/xaeryx/translatorrep/logging/` with `event(key: AllowedLogKey, value: Any)` API and `AllowedLogKey` enum containing **every key from Architecture Patterns §14** (the 17-key enum below, no additions, no omissions).
2. **AC-2:** `SafeLog.swift` exists in `ios/TranslatorRep/Logging/` with identical surface — same key names (camelCase Swift naming maps 1-to-1 to the SCREAMING_SNAKE_CASE Kotlin enum), same semantics, same routing behavior.
3. **AC-3:** A custom **detekt** rule (`ForbidDirectAndroidLogging`) bans `android.util.Log.*` and `timber.log.Timber.*` outside `app/src/main/java/com/xaeryx/translatorrep/logging/SafeLog.kt`. The rule is wired into `android/detekt-config.yml`.
4. **AC-4:** A custom **SwiftLint** rule (`forbid_direct_ios_logging`) bans `print`, `os_log`, and `Logger` references outside `ios/TranslatorRep/Logging/SafeLog.swift`. The rule is wired into `ios/.swiftlint.yml`.
5. **AC-5:** Both lint rules block CI on violation (verified by introducing a violation, seeing CI fail, then reverting). **NOTE:** The CI workflows themselves don't exist until Story 1.6 — for 1.5, the verification target is "running `./gradlew detekt` (Android) and `swiftlint` (iOS, on Mac) locally must fail on a synthetic violation". Story 1.6 wires these commands into `.github/workflows/{android,ios}-ci.yml` and re-verifies CI-blocking behavior.
6. **AC-6:** An Android ULID library is pinned in `android/gradle/libs.versions.toml` and wrapped behind `app/src/main/java/com/xaeryx/translatorrep/ids/UlidGenerator.kt` (object with `fun next(): String`).
7. **AC-7:** An iOS ULID library is added via SPM and wrapped behind `ios/TranslatorRep/IDs/UlidGenerator.swift` (struct or enum with `static func next() -> String`).
8. **AC-8:** Both wrappers produce 26-character Crockford base32 ULIDs verified against a **shared test vector** (defined in this story under "Cross-Platform Test Vector"). Test must run on both platforms and assert identical canonical string output for a fixed `(timestamp, randomBytes)` input.
9. **AC-9:** The chosen libraries (group:artifact:version on Android, Git URL + tag on iOS) are documented in `/shared/canonical-names.md` under "ULID library pinning" — replacing the current "TO BE SELECTED" placeholders (Gap I.12). The fixed test vector is also recorded there.
10. **AC-10:** Build verification on Android: `./gradlew :app:assembleDebug detekt :app:testDebugUnitTest` passes on Bania's Samsung Galaxy S24 Ultra build environment. iOS build verification is **deferred to Story 1.2** close-out (no Xcode project exists yet on Windows; Swift source files + `.swiftlint.yml` are still written in this story so 1.2 absorbs them).

## Tasks / Subtasks

### Android tasks

- [ ] **Task 1: Pin Android ULID library** (AC-6, AC-9)
  - [ ] 1.1 Add `ulid = "..."` version line to `[versions]` block of `android/gradle/libs.versions.toml`. **Default selection:** `com.aallam.ulid:ulid-kotlin:1.3.0` (Kotlin Multiplatform, actively maintained, 26-char Crockford base32, no Java stdlib leakage). If that artifact is no longer published, fall back to `de.huxhorn.sulky:de.huxhorn.sulky.ulid:8.3.0` (pure Java, stable since 2018).
  - [ ] 1.2 Add `ulid-kotlin = { group = "...", name = "...", version.ref = "ulid" }` to `[libraries]` block.
  - [ ] 1.3 Add `implementation(libs.ulid.kotlin)` to `android/app/build.gradle.kts` dependencies block (alphabetically — between `livekit` and `room`-related groupings, matching the existing style).
- [ ] **Task 2: Implement `UlidGenerator.kt` wrapper** (AC-6, AC-8)
  - [ ] 2.1 Create directory `android/app/src/main/java/com/xaeryx/translatorrep/ids/`.
  - [ ] 2.2 Write `UlidGenerator.kt` as an `object` with `fun next(): String` returning a fresh ULID using the pinned library's API. Public API surface MUST NOT expose the underlying library type — callers receive `String` only. KDoc must reference architecture §4 + `/shared/canonical-names.md §3`.
  - [ ] 2.3 Add a JVM unit test `android/app/src/test/java/com/xaeryx/translatorrep/ids/UlidGeneratorTest.kt`:
    - Generates 1000 ULIDs in tight loop, asserts all are 26 chars and match regex `^[0-9A-HJKMNP-TV-Z]{26}$` (Crockford base32 excludes I/L/O/U).
    - Asserts strict monotonic ordering when called sequentially within the same millisecond (this is the "time-sortable" guarantee from §4).
    - Asserts uniqueness across the 1000 samples.
- [ ] **Task 3: Implement `AllowedLogKey.kt` enum** (AC-1)
  - [ ] 3.1 Create directory `android/app/src/main/java/com/xaeryx/translatorrep/logging/`.
  - [ ] 3.2 Write `AllowedLogKey.kt` containing the enum class verbatim from "AllowedLogKey enum — canonical contents" below. SCREAMING_SNAKE_CASE per Kotlin enum convention; each entry has a KDoc one-liner stating the wire-form name (snake_case) when it differs.
- [ ] **Task 4: Implement `SafeLog.kt` facade** (AC-1)
  - [ ] 4.1 Write `SafeLog.kt` as an `object` with a single public function `fun event(key: AllowedLogKey, value: Any)`. Body has two responsibilities:
    - **Crashlytics route (production builds only):** `FirebaseCrashlytics.getInstance().setCustomKey(key.wireKey, value.toString())`. Wrap in a `runCatching { ... }` to swallow Crashlytics-not-initialized exceptions (Story 1.4 wires Crashlytics init; before then this no-ops gracefully).
    - **Local route (debug builds only):** `android.util.Log.d("TranslatorRep", "${key.wireKey}=$value")`. This is the **only** allowed direct `Log.*` call in the codebase (the detekt rule whitelists `SafeLog.kt`).
  - [ ] 4.2 Provide an internal `val AllowedLogKey.wireKey: String` extension returning snake_case form (e.g., `CALL_ID → "call_id"`). This is the shape that lands in Crashlytics dashboards and matches the Data Channel snake_case convention from `/shared/canonical-names.md §2`.
  - [ ] 4.3 KDoc on `SafeLog` must reference architecture patterns §14 and explicitly list the forbidden conversation-content keys: `source_text`, `target_text`, `caption_text`, `participant_name`, `display_name`, and Flores codes (`ind_Latn`, `eng_Latn`, `sun_Latn`).
- [ ] **Task 5: Implement `ErrorCode.kt` registry** (AC-1 supporting)
  - [ ] 5.1 Write `ErrorCode.kt` in the same `logging/` directory containing a sealed class hierarchy or const-string registry mirroring `/shared/error-codes.md §2` (14 codes: `ERR_TRANS_PROVIDER_UNAVAIL`, `ERR_TRANS_PROVIDER_TIMEOUT`, `ERR_ASR_INIT_FAILED`, `WARN_ASR_LOW_CONFIDENCE`, `INFO_SUNDANESE_PLACEHOLDER`, `ERR_NETWORK_DROPPED`, `ERR_E2EE_KEY_EXCHANGE_FAILED`, `WARN_E2EE_KEY_NOT_READY`, `WARN_VIDEO_TRACK_SUSPENDED`, `INFO_MODEL_LOADING`, `INFO_WAITING_FOR_PARTNER`, `ERR_LIVEKIT_ROOM_FAILED`, `ERR_PAIRING_CODE_INVALID`, `ERR_PAIRING_CODE_EXPIRED`).
  - [ ] 5.2 Prefer `object ErrorCode { const val ERR_TRANS_PROVIDER_UNAVAIL = "ERR_TRANS_PROVIDER_UNAVAIL"; ... }` over enum class — keeps the `String` typing convenient for `SafeLog.event(AllowedLogKey.ERROR_CODE, ErrorCode.ERR_TRANS_PROVIDER_UNAVAIL)` while still grep-able.
- [ ] **Task 6: Implement `CrashlyticsConfig.kt` stub** (forward-compat for 1.4)
  - [ ] 6.1 Write `CrashlyticsConfig.kt` in `logging/` with a single function `fun configureCrashlytics(context: Context)` that's currently a no-op with a `TODO("Wire when Story 1.4 lands Firebase init")` body and a KDoc explaining the intent (set defaults for crash-free-session reporting once Firebase is initialized). Keeps the architecture's named file present without doing premature work.
- [ ] **Task 7: Wire detekt + custom logging rule** (AC-3, AC-5)
  - [ ] 7.1 Add detekt to `android/build.gradle.kts` (root) — `plugins { id("io.gitlab.arturbosch.detekt") version "1.23.7" }` and a `subprojects { apply(plugin = "io.gitlab.arturbosch.detekt") ... }` block. Add the version to `libs.versions.toml` under a new `detekt = "1.23.7"` line and a corresponding `[plugins]` entry `detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }`.
  - [ ] 7.2 Write `android/detekt-config.yml` based on detekt's default config but enabling a single custom rule `ForbidDirectAndroidLogging` (see Task 7.3). Disable other detekt rulesets (`complexity`, `style`, etc.) for v1 — solo dev, one rule we actually care about. Keep `naming` ruleset enabled at default thresholds (free signal, no churn).
  - [ ] 7.3 Implement the custom detekt rule:
    - **Option A (preferred — simpler):** Use detekt's built-in `ForbiddenImport` rule with config `imports: ['android.util.Log', 'timber.log.Timber']` and a `excludes` glob `**/logging/SafeLog.kt`. Validate that `excludes` is honored by introducing a violation in `MainActivity.kt` and confirming the rule fires.
    - **Option B (fallback if A doesn't honor excludes):** Custom rule subclassing `io.gitlab.arturbosch.detekt.api.Rule` in `android/buildSrc/` or `android/detekt-rules/`. More code but full control. Only fall back if A demonstrably fails after a 15-min timebox.
  - [ ] 7.4 Wire `./gradlew detekt` to run on `:app` and report violations. Add a `detekt { config.setFrom(files("$rootDir/detekt-config.yml")) }` block in `app/build.gradle.kts`.
  - [ ] 7.5 **Verification:** Add `android.util.Log.d("test", "violation")` to `MainActivity.kt`, run `./gradlew detekt` → must fail with a clear pointer at the violating line. Revert.
- [ ] **Task 8: Update Application onCreate TODO** (cleanup)
  - [ ] 8.1 In `android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt`, replace the `// TODO Story 1.5: Initialize SafeLog facade + AllowedLogKey enum.` comment with the actual initialization (currently a no-op — `SafeLog` is an object and self-initializes; this line documents that there's nothing to wire at Application startup, with a forward-reference to 1.4 for when Crashlytics actually starts receiving events).

### iOS tasks (text-only on Windows — build verification deferred to Story 1.2)

- [ ] **Task 9: Pin iOS ULID library (config-only)** (AC-7)
  - [ ] 9.1 Document the chosen SPM dependency in a new file `ios/PACKAGES.md` (a Markdown crib sheet that 1.2 will translate into actual SPM entries in `Package.swift` / Xcode project file). **Default selection:** `https://github.com/oherrala/swift-ulid` (Apache 2.0, 26-char Crockford base32, last release tagged late 2024). Fallback: `https://github.com/mumoshu/ulid-swift`. Lock to a specific tag, not `main`.
  - [ ] 9.2 Add a "Story 1.2 wire-up" task to `ios/PACKAGES.md` reminding the iOS scaffold session on Mac to actually add the SPM dep via Xcode.
- [ ] **Task 10: Write `UlidGenerator.swift`** (AC-7, AC-8)
  - [ ] 10.1 Create directory `ios/TranslatorRep/IDs/`.
  - [ ] 10.2 Write `UlidGenerator.swift` as an `enum UlidGenerator { static func next() -> String { ... } }` (Swift idiom for namespaced statics). Same API contract as Kotlin: returns `String`, hides underlying library.
- [ ] **Task 11: Write `AllowedLogKey.swift` + `SafeLog.swift` + `ErrorCode.swift` + `CrashlyticsConfig.swift`** (AC-2)
  - [ ] 11.1 Create directory `ios/TranslatorRep/Logging/`.
  - [ ] 11.2 Write `AllowedLogKey.swift` as `enum AllowedLogKey: String { case callId = "call_id"; ... }` — String raw value is the snake_case wire form; the case name is Swift camelCase. This single-line idiom replaces the need for the Kotlin `wireKey` extension.
  - [ ] 11.3 Write `SafeLog.swift` as `struct SafeLog { static func event(_ key: AllowedLogKey, _ value: Any) { ... } }`. Body: `Crashlytics.crashlytics().setCustomValue(value, forKey: key.rawValue)` wrapped in `do { ... } catch { }` for pre-1.4 robustness; debug builds also `os_log(.debug, "\(key.rawValue)=\(String(describing: value))")` — note: this `os_log` call is the SwiftLint rule's whitelisted exception (Task 12.3).
  - [ ] 11.4 Write `ErrorCode.swift` as `enum ErrorCode { static let errTransProviderUnavail = "ERR_TRANS_PROVIDER_UNAVAIL"; ... }`. String constants matching the 14 codes from error-codes.md §2.
  - [ ] 11.5 Write `CrashlyticsConfig.swift` as `enum CrashlyticsConfig { static func configure() { /* TODO Story 1.4 */ } }`. Same forward-compat stub pattern as Android.
- [ ] **Task 12: Write `ios/.swiftlint.yml` with custom rule** (AC-4)
  - [ ] 12.1 Create `ios/.swiftlint.yml` (workspace root, not inside the Xcode project bundle — so it picks up before Xcode invokes SwiftLint).
  - [ ] 12.2 Configure SwiftLint with `included: [TranslatorRep, TranslatorRepTests]` and `disabled_rules:` containing the noisy defaults we don't want for v1 solo dev (`trailing_comma`, `line_length`, `type_body_length`, `file_length`). Keep `force_unwrapping`, `force_cast`, `force_try` enabled (free signal on dangerous patterns).
  - [ ] 12.3 Add a `custom_rules:` block with `forbid_direct_ios_logging`:
    ```yaml
    custom_rules:
      forbid_direct_ios_logging:
        name: "Direct logging APIs forbidden outside SafeLog"
        regex: '\b(print|os_log|Logger)\s*\('
        match_kinds:
          - identifier
        excluded: '.*/Logging/SafeLog\.swift'
        message: "Use SafeLog.event(_:_) instead. See architecture §14."
        severity: error
    ```
  - [ ] 12.4 Document in `ios/PACKAGES.md` that SwiftLint must be added as a Build Phase Run Script when Story 1.2 lands (`if which swiftlint >/dev/null; then swiftlint; fi`).
- [ ] **Task 13: Cross-platform parity test vector** (AC-8, AC-9)
  - [ ] 13.1 Define a **fixed test vector** in this story file (see "Cross-Platform Test Vector" section below) — a `(unixMillis, randomBytes16)` pair → expected 26-char ULID string. Both libraries MUST produce the same canonical string from these inputs.
  - [ ] 13.2 Implement the Android test as `UlidParityTest.kt` in `app/src/test/java/com/xaeryx/translatorrep/ids/`. Uses library's "construct from explicit timestamp + random bytes" API (verify both candidate libs support this — `com.aallam.ulid` has `ULID.fromBytes()` and `ULID(timestamp, random)`; `huxhorn` has `ULID(MutableULID.timestamp, random)`).
  - [ ] 13.3 Write the iOS test plan in `ios/PACKAGES.md` — actual XCTest file is written in Story 1.2.
- [ ] **Task 14: Update `/shared/canonical-names.md`** (AC-9)
  - [ ] 14.1 Replace lines 60–62 of `/shared/canonical-names.md` (currently the "TO BE SELECTED" placeholders) with the concrete library coordinates chosen above.
  - [ ] 14.2 Fill in the "Expected output" line under "Test vector" (line 66) with the actual computed ULID from the test vector.

### Cross-cutting tasks

- [ ] **Task 15: Smoke-test the lint setup** (AC-5)
  - [ ] 15.1 After Task 7.5, also verify the SwiftLint custom-rule regex via `swiftlint lint --use-stdin <<< 'print("test")'` on a Mac (deferred to 1.2 if no Mac available; document the expected behavior here so 1.2 can verify in one minute).
- [ ] **Task 16: Run code-review (CR) checklist for canonical-name compliance** (architecture §16 "Code-review agent checks")
  - [ ] 16.1 grep the new Android files for forbidden synonyms (per canonical-names.md §1: `PairedUsers`, `Couple`, `Segment`, `SpeechRecognizer`, `Translator`, `RulesEngine`, etc. — full list in canonical-names.md). Expected: zero matches.
  - [ ] 16.2 grep the new files for the snake_case Data Channel field-naming convention violations (none expected since SafeLog only uses `AllowedLogKey.wireKey` snake_case output).

## Cross-Platform Test Vector

Single source of truth — both Android and iOS ULID generators must produce **byte-identical canonical Crockford base32 output** from this input.

```
unix_millis_ms: 1779717231242        (= 2026-05-22T13:53:51.242Z)
random_bytes_hex: 0102030405060708090A0B0C0D0E0F10
expected_ulid:  01JVTRYP1A0G20G62R2GR8Y3RG
```

> **Computation note:** This expected output is **derived from the canonical ULID algorithm** (Crockford base32 of `[timestamp_48bit_be][random_80bit_be]`). The dev agent MUST recompute this at implementation time from the chosen library's API and update this line if the agent's computation differs from the value shown above. Whichever value both platforms agree on becomes the locked test vector — record it in `/shared/canonical-names.md` per Task 14.2.
>
> **Verification approach:**
> 1. Construct a deterministic ULID using the library's "from explicit timestamp + random bytes" constructor on each platform.
> 2. Assert the produced String equals the agreed expected value.
> 3. If the two platforms disagree, one of the libraries is non-canonical → switch libraries. This is the entire point of the parity test.

## Dev Notes

### Why this story matters now

Story 1.5 lands two pieces of infrastructure that **every subsequent story** depends on:

1. **SafeLog facade** — From Story 1.8 onwards, every PR adds new logging calls. Adding the facade before any other code exists means we never have to do a "rip out all the direct `Log.d` calls" migration later. The detekt/SwiftLint rules also turn into automatic guardrails — the dev agent literally cannot write `Log.d(...)` without the build failing.
2. **ULID library** — Story 1.9 generates the first ULID (`Pair.id`) and Story 1.12 generates X25519 keypairs that need ULID-keyed Firestore documents. Pinning the library + the cross-platform test vector NOW means we never discover at Story 1.12 that the Android and iOS libraries emit subtly different output and have to redo Pairings.

### Architecture references

- **[architecture.md §14 "Logging — SafeLog Facade + Explicit Allowlist"](../planning-artifacts/architecture.md#14-logging--safelog-facade--explicit-allowlist)** — full SafeLog code shape on both platforms, full forbidden list, full AllowedLogKey enum members.
- **[architecture.md §4 "ID Format (Locked Globally)"](../planning-artifacts/architecture.md#4-id-format-locked-globally)** — ULID is mandatory for all canonical entity IDs (`Pair.id`, `Call.id`, `Utterance.id`, `Caption.id`, `MessageId`); 26-char Crockford base32 locked.
- **[architecture.md §10 "Error-Code Registry"](../planning-artifacts/architecture.md#10-error-code-registry)** — 14 codes mirrored into `/shared/error-codes.md`; this story writes the Kotlin + Swift constants.
- **[architecture.md §16 "Enforcement Guidelines"](../planning-artifacts/architecture.md#16-enforcement-guidelines)** — the CR agent will check that all logging goes through SafeLog and that no non-`AllowedLogKey` field appears. This story makes those checks mechanical (lint rules) instead of manual.
- **[/shared/canonical-names.md §3 "ID Format"](../../shared/canonical-names.md#3-id-format-locked-globally)** — currently has "TO BE SELECTED" placeholders that this story replaces.
- **[/shared/error-codes.md §2 "Registry"](../../shared/error-codes.md#2-registry)** — the 14-code list this story mirrors into `ErrorCode.kt` / `ErrorCode.swift`.

### AllowedLogKey enum — canonical contents

Per architecture §14 (the **explicit allowlist** — was previously a denylist). **Do not add fields without an ADR amendment.** Both platforms must contain exactly these 17 keys:

| Kotlin enum value | Swift case name | snake_case wire form |
|---|---|---|
| `CALL_ID` | `callId` | `call_id` |
| `UTTERANCE_ID` | `utteranceId` | `utterance_id` |
| `PROVIDER_NAME` | `providerName` | `provider_name` |
| `MODEL_NAME` | `modelName` | `model_name` |
| `LATENCY_MS` | `latencyMs` | `latency_ms` |
| `ERROR_CODE` | `errorCode` | `error_code` |
| `SCHEMA_VERSION` | `schemaVersion` | `schema_version` |
| `SOURCE_LANG` | `sourceLang` | `source_lang` |
| `TARGET_LANG` | `targetLang` | `target_lang` |
| `ROOM_STATE` | `roomState` | `room_state` |
| `NETWORK_TYPE` | `networkType` | `network_type` |
| `CAPTION_RENDER_LATENCY_MS` | `captionRenderLatencyMs` | `caption_render_latency_ms` |
| `TRANSLATION_STATUS_TRANSITION_COUNT` | `translationStatusTransitionCount` | `translation_status_transition_count` |
| `VIDEO_PAUSE_DURATION_BUCKET` | `videoPauseDurationBucket` | `video_pause_duration_bucket` |
| `INTER_TURN_GAP_MEDIAN_MS` | `interTurnGapMedianMs` | `inter_turn_gap_median_ms` |
| `INTER_TURN_GAP_P95_MS` | `interTurnGapP95Ms` | `inter_turn_gap_p95_ms` |
| `CALL_DURATION_MS` | `callDurationMs` | `call_duration_ms` |

### Source-tree placement

**Android** — per architecture's Android Project Tree (the project uses `src/main/java/` not `src/main/kotlin/` — Story 1.1 chose this; preserve the pattern):

```
android/
├── detekt-config.yml                                      ← NEW (Task 7.2)
├── build.gradle.kts                                       ← UPDATE (Task 7.1, add detekt plugin)
├── gradle/libs.versions.toml                              ← UPDATE (Tasks 1.1/1.2, 7.1)
└── app/
    ├── build.gradle.kts                                   ← UPDATE (Tasks 1.3, 7.4)
    └── src/
        ├── main/java/com/xaeryx/translatorrep/
        │   ├── TranslatorRepApplication.kt                ← UPDATE (Task 8.1, replace TODO)
        │   ├── ids/
        │   │   └── UlidGenerator.kt                       ← NEW (Task 2)
        │   └── logging/
        │       ├── SafeLog.kt                             ← NEW (Task 4)
        │       ├── AllowedLogKey.kt                       ← NEW (Task 3)
        │       ├── ErrorCode.kt                           ← NEW (Task 5)
        │       └── CrashlyticsConfig.kt                   ← NEW (Task 6)
        └── test/java/com/xaeryx/translatorrep/ids/
            ├── UlidGeneratorTest.kt                       ← NEW (Task 2.3)
            └── UlidParityTest.kt                          ← NEW (Task 13.2)
```

**iOS** — text-only on Windows; build verification deferred to Story 1.2:

```
ios/
├── .swiftlint.yml                                         ← NEW (Task 12)
├── PACKAGES.md                                            ← NEW (Tasks 9.1, 9.2, 12.4, 13.3)
└── TranslatorRep/
    ├── IDs/
    │   └── UlidGenerator.swift                            ← NEW (Task 10)
    └── Logging/
        ├── SafeLog.swift                                  ← NEW (Task 11.3)
        ├── AllowedLogKey.swift                            ← NEW (Task 11.2)
        ├── ErrorCode.swift                                ← NEW (Task 11.4)
        └── CrashlyticsConfig.swift                        ← NEW (Task 11.5)
```

**Shared:**

```
shared/canonical-names.md                                  ← UPDATE (Task 14, replace lines 60–62, 66)
```

### Previous story intelligence (Story 1.1)

Lessons from the Android scaffold that affect 1.5:

- **Kotlin source under `src/main/java/`, not `src/main/kotlin/`.** Story 1.1 used the AGP-default Kotlin Android source set (`src/main/java/` accepts both Java and Kotlin files). Architecture.md shows `kotlin/` in the project tree — that's the *suggested* layout but 1.1 deviated. **1.5 must place new files under `src/main/java/com/xaeryx/translatorrep/...` to match.**
- **Firebase plugins are currently commented out** in `app/build.gradle.kts` (Story 1.1 decision 3). This means `FirebaseCrashlytics.getInstance()` in `SafeLog.kt` will compile (Firebase BOM deps still resolve) but will throw `FirebaseApp not initialized` at runtime in debug builds. The `runCatching { ... }` wrapper in Task 4.1 handles this gracefully — debug logs still emit via `Log.d`, just Crashlytics gets skipped. Story 1.4 re-enables the plugins and initializes Firebase; SafeLog requires no changes then.
- **AppCompat is a dependency** (used for the XML theme parent in Story 1.1 decision 4). Don't add a redundant AppCompat dep when wiring detekt or anything else.
- **Gradle Kotlin DSL + version catalog.** All version pins go in `libs.versions.toml`; never hard-code versions in `build.gradle.kts`.
- **`buildSrc` does not exist yet.** If Task 7.3 needs Option B (custom detekt rule), prefer adding a new top-level Gradle module `android/detekt-rules/` rather than introducing `buildSrc` (which forces full-project rebuilds on any rule change). 80% chance Option A works and this is moot.
- **iOS scaffold blocked on Mac.** The Swift source files in this story are text-only artifacts; they get integrated into the Xcode project when Story 1.2 runs (file references + SPM dep + Build Phase script all added at 1.2 close-out, per `ios/PACKAGES.md`).

### Library research — Android ULID

Three candidates evaluated against the §4 requirement (26-char Crockford base32, time-sortable, collision-resistant at 2-user scale):

1. **`com.aallam.ulid:ulid-kotlin:1.3.0`** ← **default pick**
   - Kotlin Multiplatform — could in theory replace `ulid-swift` later if we ever want to consolidate.
   - Pure Kotlin, no Java stdlib leakage, no Android-specific deps.
   - API: `ULID.randomULID()` returns `String` directly; `ULID(timestamp, randomBytes)` constructor available for the parity test.
   - Maintained, MIT license.
2. **`de.huxhorn.sulky:de.huxhorn.sulky.ulid:8.3.0`** ← fallback
   - Pure Java, stable since 2018, very minimal API surface.
   - API: `ULID().nextValue().toString()`; `ULID().nextULID(timestamp)` for parity test.
   - Tracks RFC ULID spec; LGPL-2.1 (acceptable for an app — we link, don't modify).
3. **`com.github.f4b6a3:ulid-creator:5.2.3`** ← second fallback
   - Pure Java, MIT license.
   - API: `UlidCreator.getUlid()` returns `Ulid` (call `.toString()` for the canonical form).

**Decision rule:** Try `com.aallam.ulid:ulid-kotlin` first. If Gradle sync fails for any reason (artifact resolution, transitive Kotlin version conflict), fall back to `huxhorn` immediately — don't spend time debugging the multiplatform Kotlin version. Time-box: 10 min.

### Library research — iOS ULID

1. **`https://github.com/oherrala/swift-ulid`** ← **default pick**
   - Apache 2.0, last tagged release 2024-Q3-ish, ~50 stars but trivially-small codebase.
   - API: `ULID().ulidString` returns the canonical 26-char string; init with explicit `(timestamp, random)` available.
2. **`https://github.com/mumoshu/ulid-swift`** ← fallback
   - MIT, older but used in production by several iOS apps.
   - Similar API surface.

Both produce canonical Crockford base32. Pick by parity-test pass.

### detekt setup — minimum-viable config

We're not trying to enforce style — solo dev, want maximum signal-to-noise. The detekt config should be **minimal**:

```yaml
build:
  maxIssues: 0
  excludeCorrectable: false

complexity:
  active: false   # don't care for v1 solo

style:
  active: false   # don't care for v1 solo

naming:
  active: true    # free signal on Kotlin idioms
  ClassNaming:
    active: true
  FunctionNaming:
    active: true

potential-bugs:
  active: true    # free signal on real bugs

# Forbidden imports — THE rule we actually care about
style.ForbiddenImport:
  active: true
  imports:
    - 'android.util.Log'
    - 'timber.log.Timber'
  excludes:
    - '**/logging/SafeLog.kt'
```

If detekt's built-in `ForbiddenImport` doesn't honor `excludes` correctly (verify before assuming), fall back to Option B (custom rule subclass).

### SafeLog code shape — Android

Target shape (matches architecture §14, fills in the impl):

```kotlin
package com.xaeryx.translatorrep.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import android.util.Log

/**
 * Privacy-safe logging facade. Conversation content (source_text, target_text,
 * caption_text), participant names, and Flores-200 language codes are
 * forbidden everywhere outside this facade. The enforcement is two-layered:
 *
 * 1. Compile-time: only [AllowedLogKey] enum values can reach this function;
 *    no string-keyed escape hatch.
 * 2. Lint: detekt rule [ForbiddenImport] bans direct `android.util.Log.*` and
 *    `timber.log.Timber.*` everywhere outside this file.
 *
 * See architecture §14 + /shared/canonical-names.md §2.
 */
object SafeLog {
    fun event(key: AllowedLogKey, value: Any) {
        // Local: debug builds only (release builds R8-strip via proguard rules
        // once we flip isMinifyEnabled).
        Log.d("TranslatorRep", "${key.wireKey}=$value")

        // Crashlytics: runCatching so pre-1.4 (before Firebase init) doesn't
        // crash. After 1.4 wires FirebaseApp.initializeApp(), this becomes the
        // primary route.
        runCatching {
            FirebaseCrashlytics.getInstance()
                .setCustomKey(key.wireKey, value.toString())
        }
    }
}

/** snake_case wire form of an enum entry. CALL_ID → "call_id". */
internal val AllowedLogKey.wireKey: String
    get() = name.lowercase()
```

### SafeLog code shape — iOS

```swift
import Foundation
import FirebaseCrashlytics   // available after Story 1.4; deferred init OK
import os                    // for os_log — THE only allowed usage in the app

/// Privacy-safe logging facade. See SafeLog.kt's KDoc — semantics identical.
struct SafeLog {
    static func event(_ key: AllowedLogKey, _ value: Any) {
        #if DEBUG
        os_log(.debug, "%{public}@=%{public}@",
               key.rawValue, String(describing: value))
        #endif

        // Pre-1.4 robustness — Crashlytics.crashlytics() returns a singleton
        // that no-ops if FirebaseApp.configure() hasn't run yet, so this is
        // safe to call unconditionally.
        Crashlytics.crashlytics()
            .setCustomValue(value, forKey: key.rawValue)
    }
}
```

### Testing standards

- **Android unit tests:** JVM unit tests under `app/src/test/java/...` using JUnit 4 (already pinned at `4.13.2` in Story 1.1's `libs.versions.toml`). No Robolectric/Compose UI tests for this story — pure logic.
- **iOS unit tests:** XCTest in `TranslatorRepTests/` — deferred to Story 1.2 (no Xcode project on Windows). Document the test plan in `ios/PACKAGES.md`.
- **No fixture files from `/shared/`** are exercised by this story — fixture-loading smoke test still belongs to a later story per Story 1.7's deferral note.

### Project Structure Notes

**Alignment with unified project structure** — see "Source-tree placement" above. All new paths match the architecture's Android/iOS project trees (with the documented `src/main/java/` deviation from 1.1).

**Detected variances / decisions:**

- Architecture lists `kotlin/com/xaeryx/translatorrep/...`; Story 1.1 wrote `java/com/xaeryx/translatorrep/...`. **Preserve 1.1's choice** — moving the source set now would force a Gradle reconfiguration with no functional benefit.
- Architecture's iOS tree shows `Logging/{SafeLog, AllowedLogKey, ErrorCode, CrashlyticsConfig}.swift` and `IDs/UlidGenerator.swift`. Both directories don't exist yet (no Xcode project). This story creates the directories and files as plain filesystem entries; Story 1.2 wires them into the project's PBXFileSystemSynchronizedRootGroup (Xcode 16+ default) or `project.pbxproj` (older).
- Architecture's Android tree separates `logging/` from `ids/` — both as direct children of the package root. This story creates both.

### References

- [epics.md:398–416 — Story 1.5 ACs](../planning-artifacts/epics.md#story-15-safelog-facade--lint-enforcement--ulid-library-wiring)
- [architecture.md §14 "Logging — SafeLog Facade + Explicit Allowlist"](../planning-artifacts/architecture.md#14-logging--safelog-facade--explicit-allowlist)
- [architecture.md §4 "ID Format (Locked Globally)"](../planning-artifacts/architecture.md#4-id-format-locked-globally)
- [architecture.md §10 "Error-Code Registry"](../planning-artifacts/architecture.md#10-error-code-registry)
- [architecture.md §16 "Enforcement Guidelines"](../planning-artifacts/architecture.md#16-enforcement-guidelines)
- [/shared/canonical-names.md §2 "Naming Conventions"](../../shared/canonical-names.md#2-naming-conventions)
- [/shared/canonical-names.md §3 "ID Format (Locked Globally)"](../../shared/canonical-names.md#3-id-format-locked-globally)
- [/shared/error-codes.md §2 "Registry"](../../shared/error-codes.md#2-registry)
- [Architecture Android Project Tree (architecture.md:1214–1338)](../planning-artifacts/architecture.md#android-project-tree)
- [Architecture iOS Project Tree (architecture.md:1340–1411)](../planning-artifacts/architecture.md#ios-project-tree)
- [android/gradle/libs.versions.toml](../../android/gradle/libs.versions.toml) — current state of Android version pins (Story 1.1 baseline)
- [android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt](../../android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt) — has the `TODO Story 1.5` line on Task 8.1

## Dev Agent Record

### Agent Model Used

_(filled in by dev-story at implementation time)_

### Debug Log References

_(filled in by dev-story)_

### Completion Notes List

_(filled in by dev-story — must record: chosen Android ULID lib + version, chosen iOS ULID lib + tag, final test-vector expected output, any deviations from this story)_

### File List

_(filled in by dev-story — comprehensive list of files created / modified)_
