# Deferred Work

Items raised during code review that were classified as `defer` (real but not actionable now — pre-existing, out of current story scope, or low-risk theoretical). Each entry names the originating review + date so they can be re-raised when the relevant story area is touched.

---

## Deferred from: code review of 1-6-cicd-per-stack (PR #3) (2026-05-23)

**Scope note:** PR #3 bundled Story 1.5 (SafeLog + lint + ULID) commits along with Story 1.6 (CI/CD) because `origin/main` was 3 commits behind local `main` at PR time. Most defer items below are Story 1.5 follow-ups that the review surfaced because the 1.5 diff slice was in PR #3. These should be picked up the next time someone is editing the named file or area — not lined up as a discrete story unless they cluster.

### Android / Kotlin

**All 6 items below LANDED 2026-05-23 in branch `chore/1-5-cr-defer-fixes-android`.** Kept here struck-through for traceability; remove on next clean-up pass.

- ~~**`SafeLog.kt` Crashlytics route: `runCatching { }` catches every `Throwable`, comment says only `IllegalStateException`.**~~ ✅ Fixed: narrowed to `catch (e: RuntimeException)` with explicit "availability > visibility" rationale comment; `OutOfMemoryError` / `StackOverflowError` propagate normally.
- ~~**`SafeLog.event` doesn't guard `value.toString()` throws.**~~ ✅ Fixed: introduced `stringifyDefensively(value)` private helper that returns `"<toString-failed:<ExceptionClass>>"` on `RuntimeException` so a buggy caller-`toString()` can't turn the logging path into a fault source.
- ~~**`UlidGenerator.next()` doesn't guard `System.currentTimeMillis()` returning negative on bizarre device clocks.**~~ ✅ Fixed: `coerceAtLeast(0L)` on the timestamp argument with rationale comment.
- ~~**`UlidGeneratorTest` 50ms-sleep + strict `<` assertion is theoretically flaky under NTP step.**~~ ✅ Fixed (same edit as below): relaxed assertion to `<=` which matches `next()`'s "monotonic" (not "strictly monotonic") docstring + the assertion-message text. NTP-rewind during the sleep would still fail `<=`.
- ~~**`UlidGeneratorTest` test message says `<=` but assertion uses strict `<`.**~~ ✅ Fixed (combined with above): assertion now uses `<=`, so message text matches semantics.
- ~~**`encodeCanonical` `MAX_TIMESTAMP_MS` upper-bound + negative-timestamp paths are not exercised by tests.**~~ ✅ Fixed: added 4 boundary tests — `rejects negative timestamp`, `rejects timestamp exceeding 48 bits`, `accepts MAX_TIMESTAMP_MS upper boundary`, `accepts zero timestamp lower boundary`. Test count: 7 → 11.

### iOS / Swift

- **`UlidGenerator.next()` uses `#if canImport(ULID)` and `preconditionFailure` on the else branch — release build without SPM wired compiles cleanly and crashes at first ID generation.** A `#error` directive would catch the misconfiguration at build time instead. Currently latent because Xcode project hasn't landed (Story 1.2). — [ios/TranslatorRep/IDs/UlidGenerator.swift]
- **`encodeCanonical` uses `precondition` for argument validation — crashes the user's app in release builds.** Diverges from Kotlin which throws `IllegalArgumentException`. Cross-platform parity argues for `guard ... else { throw }` instead. Latent until iOS callers exist. — [ios/TranslatorRep/IDs/UlidGenerator.swift]
- **`1 << 48` literal will overflow on a hypothetical 32-bit target (watchOS armv7k).** Not a concern today (no 32-bit targets), but the fix is trivial: `Int64(1) << 48`. — [ios/TranslatorRep/IDs/UlidGenerator.swift]
- **`SafeLog` `OSLog` subsystem captures `Bundle.main.bundleIdentifier` once at type-initialization.** In xctest contexts, this captures the xctest tool's bundle identifier — log filtering by subsystem misses test-emitted events. Use `Bundle(for: SafeLogMarker.self).bundleIdentifier` instead. — [ios/TranslatorRep/Logging/SafeLog.swift]
- **`SafeLog` `String(describing: value)` will crash if `value`'s `description` throws.** Wrap defensively. — [ios/TranslatorRep/Logging/SafeLog.swift]
- **iOS `SafeLog` Crashlytics nested `#if !DEBUG` + `#if canImport(FirebaseCrashlytics)` behavior is asserted ("safe no-op in Obj-C msgSend semantics") without runtime verification.** Code-comment claim that Swift cannot trap NSException is correct but the trust in Firebase's no-op behavior is implicit. Worth a runtime smoke test in the first iOS CI run. — [ios/TranslatorRep/Logging/SafeLog.swift]

### Lint config

- **SwiftLint custom regex matches code-shaped text inside comments + doc strings.** False-positive lint errors on innocent `Logger` mentions in comments. SwiftLint custom_rules has no built-in comment exclusion. — [ios/.swiftlint.yml]
- **SwiftLint regex method-style alternation `\.(log|debug|info|notice|warning|error|critical|fault|trace)\s*\(` matches non-logger types** like `Result.error(...)`, `Future.error(...)`, Combine `.debug(...)`. Constrain to `(logger|log|os)\.<method>\(`. — [ios/.swiftlint.yml]
- **SwiftLint `identifier_name` `min_length: 1` is codebase-wide.** The ULID encoder loop legitimately uses `bit` and 1-char loop indices, but applying `min_length: 1` everywhere removes the guardrail. An `excluded:` list scoped to encoder identifiers would be tighter. — [ios/.swiftlint.yml]

### Documentation

- **Story 1.5 file still names `oherrala/swift-ulid` as the iOS ULID "default pick" in the Library Research section.** Code-review-fix #2 verified that URL is a 404 and replaced with `yaslab/ULID.swift` in `PACKAGES.md` + `canonical-names.md` + `UlidGenerator.swift`, but the originating Library Research prose was not updated. Cosmetic doc drift. — [_bmad-output/implementation-artifacts/1-5-safelog-lint-ulid.md, Library Research → iOS ULID section]

---

## How to use this file

When picking up a story that touches one of the files listed above, scan this document for deferred items in that file and consider folding them into the new story. After landing a deferred fix, strike the bullet (or remove it entirely) and add a Change Log note in the story that addressed it.
