# Story 1.6: CI/CD Per Stack

Status: ready-for-dev

<!-- Created 2026-05-23 by bmad-create-story workflow (run inline). Validation optional; run bmad-create-story:validate before bmad-dev-story if desired. -->

## Story

As a solo developer,
I want a GitHub Actions Android CI workflow that runs detekt + unit tests on every PR (and stubs for iOS + infra workflows so the wire-up is obvious when their unblocking stories land),
so that regressions in the Android stack are caught before merge — and so the iOS + infra workflows can be lit up with zero spec drift the moment Story 1.2 + Story 1.3 close out.

## Acceptance Criteria

**Given** the scaffolds + lint primitives exist (Story 1.1 ✅, Story 1.5 ✅),
**When** a PR opens against `main` that touches `android/**` or `shared/**`,
**Then** `.github/workflows/android-ci.yml` runs and:

1. **AC-1 (Android CI workflow exists, path-filtered):** `.github/workflows/android-ci.yml` exists, triggered on `pull_request` and `push` to `main`, filtered to `android/**` and `shared/**` (per architecture §"CI/CD Per Stack").
2. **AC-2 (Android CI runs detekt):** the workflow runs `./gradlew :app:detekt` and fails if any issues are reported. The existing detekt config (`android/detekt-config.yml`) is the source of truth — the workflow does not re-define rules.
3. **AC-3 (Android CI runs unit tests):** the workflow runs `./gradlew :app:testDebugUnitTest` and fails on any test failure. Story 1.5's 7 unit tests must pass on the CI runner.
4. **AC-4 (Android CI assembles a debug APK as a workflow artifact):** the workflow runs `./gradlew :app:assembleDebug` and uploads the resulting APK as a workflow artifact (retention 7 days). The architecture spec asks for `assembleRelease` — scoped DOWN to `assembleDebug` because `release` requires a signing config that this story does not yet land (signing-config provisioning is deferred per `docs/runbooks/solo-dev-scope-cuts.md` if that document covers it, or to a follow-up story 1.6d otherwise).
5. **AC-5 (CI catches a deliberately-broken commit):** a deliberately-broken Android commit (e.g., adding `import android.util.Log` to `MainActivity.kt`) confirms the workflow fails the build. Documented in the dev notes; the verification commit is reverted before this story closes.
6. **AC-6 (Workflow fits within GitHub Actions free-tier minute budget at solo-dev volume):** the Android workflow's first-run wall-clock time is recorded in dev notes; warm runs (with Gradle cache) should complete in < 10 minutes. (2,000 minutes/month free tier — solo-dev PR volume is < 30/month, so headroom is ample even at 10 min/run.)
7. **AC-7 (iOS CI workflow stubbed, NOT executed until Story 1.2 unblocks):** `.github/workflows/ios-ci.yml` exists with the trigger filter (`ios/**` or `shared/**`) and a single job whose step is `echo "iOS CI deferred to Story 1.6b once Story 1.2 lands Xcode project"`. Story 1.2's close-out adds the real SwiftLint + xcodebuild test + snapshot-test + Ad-Hoc-archive steps. Keeps the path-filter contract present in the repo so a future iOS PR doesn't accidentally trigger no workflow.
8. **AC-8 (Infra CI workflow stubbed, NOT executed until Story 1.3 unblocks):** `.github/workflows/infra-ci.yml` exists with the trigger filter (`infra/**`) and a stub job that `echo`s a deferral message pointing at Story 1.6c. Story 1.3's close-out fills in the real yamllint + `docker compose config` + tag-triggered SSH-deploy steps.
9. **AC-9 (CI documentation):** `docs/runbooks/ci-stack-overview.md` exists and documents (a) what each workflow does today, (b) what steps are deferred to Story 1.6b / 1.6c, (c) how to trigger a workflow manually (`workflow_dispatch`), (d) how to debug a failing run locally with the same JDK + Gradle versions, (e) the rationale for the `assembleDebug` vs `assembleRelease` scope cut (AC-4).

## Tasks / Subtasks

### Android tasks (real work — this is the bulk of Story 1.6)

- [ ] **Task 1: Create `.github/workflows/` directory + scaffolding** (AC-1)
  - [ ] 1.1 Create directory `.github/workflows/` at the repo root.
  - [ ] 1.2 Add a top-of-file comment in every workflow file naming the source-of-truth architecture section (§"CI/CD Per Stack") + the story that wired it (this story for android-ci.yml; Story 1.6b for ios-ci.yml; Story 1.6c for infra-ci.yml) so future readers can trace decisions.
- [ ] **Task 2: Implement `android-ci.yml` — checkout + JDK + Gradle cache** (AC-1, AC-2, AC-3, AC-4, AC-6)
  - [ ] 2.1 Trigger block: `on: { pull_request: { paths: ['android/**', 'shared/**'] }, push: { branches: ['main'], paths: ['android/**', 'shared/**'] }, workflow_dispatch: {} }`. The `workflow_dispatch` lets the dev run it manually for debugging.
  - [ ] 2.2 `jobs.android-ci.runs-on: ubuntu-latest`. ubuntu-22.04 is the current ubuntu-latest; the Android tooling is fully supported there and minute consumption is 1× (vs 10× macOS or 2× Windows) — solo-dev free-tier budget demands ubuntu.
  - [ ] 2.3 Step: `actions/checkout@v4` (latest stable major).
  - [ ] 2.4 Step: `actions/setup-java@v4` with `distribution: temurin`, `java-version: 17`. Mirrors the dev's local Adoptium Temurin JDK 17 setup from Story 1.5's dev notes — bit-for-bit compatible Gradle behavior.
  - [ ] 2.5 Step: `gradle/actions/setup-gradle@v4` (the modern wrapper-aware action; replaces deprecated `gradle/gradle-build-action`). This installs Gradle from the wrapper-pinned `gradle-8.10.2` and configures the build cache.
  - [ ] 2.6 Set `working-directory: android` at the job level via `defaults.run.working-directory: android` so subsequent `./gradlew` calls don't need explicit paths.
- [ ] **Task 3: Wire detekt + tests + assembleDebug** (AC-2, AC-3, AC-4)
  - [ ] 3.1 Step (after Gradle setup): `name: detekt; run: ./gradlew :app:detekt --no-daemon`. `--no-daemon` is the CI convention — no warm daemon to reuse across PRs, so the daemon is just overhead.
  - [ ] 3.2 Step: `name: unit-tests; run: ./gradlew :app:testDebugUnitTest --no-daemon`. Story 1.5's 7 tests must pass here.
  - [ ] 3.3 Step: `name: assemble-debug; run: ./gradlew :app:assembleDebug --no-daemon`. Produces `android/app/build/outputs/apk/debug/app-debug.apk`.
  - [ ] 3.4 Step: `actions/upload-artifact@v4` with `name: app-debug-apk-${{ github.sha }}`, `path: android/app/build/outputs/apk/debug/*.apk`, `retention-days: 7`. Short retention keeps GitHub free-tier storage from filling up.
- [ ] **Task 4: Wire test-report annotations** (AC-3 supporting)
  - [ ] 4.1 Step (always-runs, even on failure): publish JUnit XML test reports via `mikepenz/action-junit-report@v4` with `report_paths: 'android/app/build/test-results/**/*.xml'`. Renders failing tests inline in the PR's Checks tab — strictly nicer than scrolling logs.
  - [ ] 4.2 The `if: always()` condition is critical — without it, the report only publishes on the green path which is the opposite of useful.
- [ ] **Task 5: Smoke-test the workflow via deliberately broken commits** (AC-5)
  - [ ] 5.1 Add `import android.util.Log` to `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt`, commit on a throwaway branch, open a PR → expect detekt to fail with the SafeLog ForbiddenImport message.
  - [ ] 5.2 Revert (or close PR), confirm a clean rerun passes.
  - [ ] 5.3 Repeat with a deliberately-broken unit test (e.g., `assertEquals(1, 2)` in a temp test) → expect testDebugUnitTest to fail; revert.
  - [ ] 5.4 Document the smoke-test commits in dev notes (with the throwaway branch name + the wall-clock duration observed for each run — used as the AC-6 baseline).
- [ ] **Task 6: Capture wall-clock baselines** (AC-6)
  - [ ] 6.1 After the first clean PR-trigger run finishes, record wall-clock duration in dev notes. Note the cold-cache run (first ever, no Gradle cache) separately from the warm-cache run (subsequent runs against the same `gradle-8.10.2` cache key).
  - [ ] 6.2 Confirm warm runs are < 10 min; if cold runs blow past 15 min that's a smell — investigate whether the Gradle cache is being saved correctly (the `gradle/actions/setup-gradle@v4` action handles this by default; misconfiguration manifests as every run being a cold run).

### iOS + infra tasks (stubs only — execution deferred)

- [ ] **Task 7: Implement `ios-ci.yml` stub** (AC-7)
  - [ ] 7.1 Trigger block: `on: { pull_request: { paths: ['ios/**', 'shared/**'] }, push: { branches: ['main'], paths: ['ios/**', 'shared/**'] }, workflow_dispatch: {} }`.
  - [ ] 7.2 Single job `ios-ci-stub` on `macos-latest` (so the runner type is locked in for when Story 1.6b lights it up) with one step: `echo "iOS CI deferred to Story 1.6b — Xcode project lands in Story 1.2"; exit 0`. Exit 0 keeps PRs unblocked.
  - [ ] 7.3 Top-of-file comment explicitly names the deferred steps in the order Story 1.6b will add them: SwiftLint → xcodebuild test → snapshot tests → archive (Ad Hoc on tag) per architecture §"CI/CD Per Stack" row 2.
- [ ] **Task 8: Implement `infra-ci.yml` stub** (AC-8)
  - [ ] 8.1 Trigger block: `on: { pull_request: { paths: ['infra/**'] }, push: { branches: ['main'], paths: ['infra/**'] }, workflow_dispatch: {} }`. Note no `shared/**` — infra has no shared-spec dependency.
  - [ ] 8.2 Single job `infra-ci-stub` on `ubuntu-latest` with one step echoing the deferral message + `exit 0`.
  - [ ] 8.3 Top-of-file comment names the deferred steps: `yamllint livekit.yaml + Caddyfile` → `docker compose config` → on tag → `ssh + deploy.sh on Oracle VM` per architecture §"CI/CD Per Stack" row 3.

### Documentation tasks

- [ ] **Task 9: Write `docs/runbooks/ci-stack-overview.md`** (AC-9)
  - [ ] 9.1 Section "Current state (2026-05-23)" — table of the three workflows, current scope, owning story.
  - [ ] 9.2 Section "Manual trigger" — how to use `workflow_dispatch` from the GitHub Actions UI.
  - [ ] 9.3 Section "Local equivalents" — the exact `./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug` command sequence and the JDK 17 (Temurin) + Gradle 8.10.2 versions used.
  - [ ] 9.4 Section "Why `assembleDebug` not `assembleRelease`" — explicit scope-cut rationale (signing-config TBD).
  - [ ] 9.5 Section "Deferred — Story 1.6b iOS CI / Story 1.6c infra CI" — checklist of what each follow-up story needs to land, mirroring the stub-file comments so future readers can cross-reference.
- [ ] **Task 10: Update sprint-status.yaml + this story file** (post-implementation)
  - [ ] 10.1 Move `1-6-cicd-per-stack` from `ready-for-dev` → `review` after tasks 1–9 complete + the smoke-test PR has fired both green and red. Add follow-up entries `1-6b-ios-ci-flesh-out` (sequenced after Story 1.2) and `1-6c-infra-ci-flesh-out` (sequenced after Story 1.3) — both initial status `backlog`.
- [ ] **Task 11: Run code-review (CR) checklist for canonical-name + path-filter compliance** (architecture §16 "Code-review agent checks")
  - [ ] 11.1 Confirm no forbidden synonyms appear in workflow YAML or runbook prose (per canonical-names.md §1).
  - [ ] 11.2 Confirm path filters match the architecture spec exactly (`android/**` + `shared/**` for Android, `ios/**` + `shared/**` for iOS, `infra/**` for infra). A typo here means the workflow silently never fires.

## Dev Notes

### Why this story matters now

Story 1.5 just landed the SafeLog facade + detekt ForbiddenImport rule + 7 unit tests. The rule and the tests are only useful if CI runs them on every PR — otherwise a future PR can silently bypass the SafeLog gate by deleting the import (or by ignoring a failing local detekt). This story closes that loop for Android.

iOS and infra are stubbed (not implemented) because:

- **iOS** needs the Xcode project from Story 1.2 (sequenced on a Mac per `docs/runbooks/ios-setup-on-mac.md`). Without an `.xcodeproj`, `xcodebuild test` has nothing to build.
- **Infra** needs the Oracle VM + `infra/` directory from Story 1.3 (sequenced on Oracle Cloud + domain purchase). Without `infra/livekit.yaml` and friends, there's nothing to `yamllint` or `docker compose config`.

Landing stub workflows now (instead of waiting) accomplishes two things:

1. **Path-filter contract is committed.** Any future iOS PR that lands ON or AFTER Story 1.2 will at least trigger _some_ workflow (the stub), which gives Story 1.6b an explicit hook to replace rather than a blank file to create from scratch.
2. **Scope debt is visible.** A stub workflow with a top-of-file `# DEFERRED to Story 1.6b` comment shows up in the GitHub Actions UI as a "passed" check that explicitly says "I'm not real yet" — much better than silently no checks at all.

### Architecture references

- **[architecture.md §"CI/CD Per Stack"](../planning-artifacts/architecture.md#cicd-per-stack)** (lines 1547–1555) — the table that defines all three workflows, their triggers, and their steps. This story implements row 1 in full and stubs rows 2–3.
- **[architecture.md §"Repo Shape — Monorepo, Per-Stack Roots"](../planning-artifacts/architecture.md#repo-shape--monorepo-per-stack-roots)** (lines 1186–1212) — confirms `.github/workflows/` at repo root is the canonical location.
- **[architecture.md §16 "Enforcement Guidelines"](../planning-artifacts/architecture.md#16-enforcement-guidelines)** — code-review checklist that this CI workflow makes mechanically enforceable.
- **[/docs/runbooks/solo-dev-scope-cuts.md](../../docs/runbooks/solo-dev-scope-cuts.md)** (Story 1.14c) — the scope-cut rationale baseline. AC-4's `assembleDebug` (vs `assembleRelease`) is consistent with the solo-dev scope-cut pattern.

### Previous story intelligence (Story 1.5)

Story 1.5's dev notes recorded these landmines that Story 1.6's CI workflow needs to dodge:

1. **JDK version sensitivity.** Story 1.5 needed JDK 17 (Adoptium Temurin); the local dev session for the code-review fixes used Android Studio's bundled JBR 21 successfully because Gradle's `jvmToolchain(17)` provisions the right JDK at compile time. CI must use `actions/setup-java@v4` with `temurin 17` explicitly — relying on Gradle toolchain auto-provisioning on a fresh ubuntu-latest runner without a pre-existing JDK will download JDK 17 mid-build (slow first run; fine subsequently).
2. **Gradle wrapper pinned to 8.10.2.** Don't try to use `gradle/gradle-build-action@v3` or override the version — the wrapper is the contract.
3. **No `kspDebugUnitTestKotlin` or other surprise tasks.** Story 1.5's `:app:testDebugUnitTest` graph executed `kspDebugKotlin`, `compileDebugKotlin`, `compileDebugUnitTestKotlin` cleanly; if CI fails earlier than expected check whether KSP regenerated unexpectedly.
4. **`com.aallam.ulid:ulid-kotlin:1.3.0` resolves cleanly from Maven Central.** No proprietary repos to register; default `mavenCentral() + google()` repos in `settings.gradle.kts` cover everything.
5. **Detekt config is at `android/detekt-config.yml`, NOT `android/app/detekt-config.yml`.** The `detekt { config.setFrom(files("$rootDir/detekt-config.yml")) }` block in `android/app/build.gradle.kts` resolves `$rootDir` to `android/`. CI must run gradle from the `android/` directory (handled by `defaults.run.working-directory: android` per Task 2.6).

### Library / action research — GitHub Actions

| Action | Pin | Why |
|---|---|---|
| `actions/checkout@v4` | latest stable major | Repo checkout; required first step. |
| `actions/setup-java@v4` | `distribution: temurin`, `java-version: 17` | JDK 17 matches local dev. v4 is stable as of late 2024; v3 is deprecated but still works. |
| `gradle/actions/setup-gradle@v4` | latest stable major | Successor to `gradle/gradle-build-action@v3` (which is now deprecated). v4 reads the wrapper, configures the Gradle build cache, and writes the cache back automatically — no manual cache step needed. |
| `actions/upload-artifact@v4` | latest stable major | v3 was deprecated in late 2024 (sunset Nov 2024). Use v4 for new workflows. APK artifact upload + 7-day retention. |
| `mikepenz/action-junit-report@v4` | latest stable major | Inline test-failure annotations in the PR Checks tab. Far better DX than digging through raw Gradle logs. |

**Rejected alternatives:**

- `setup-java@v3` — works, but v4 is stable and preferred for new workflows.
- `gradle/gradle-build-action@v3` — deprecated; do NOT use even though it still functions.
- Custom cache via `actions/cache@v4` keyed on `**/*.gradle*` — unnecessary; `gradle/actions/setup-gradle@v4` handles this with better defaults.

### Source-tree placement

```
.github/workflows/
├── android-ci.yml         # Full implementation (this story)
├── ios-ci.yml             # Stub (this story); Story 1.6b lights it up
└── infra-ci.yml           # Stub (this story); Story 1.6c lights it up

docs/runbooks/
└── ci-stack-overview.md   # New runbook documenting current state + deferred work
```

### `android-ci.yml` shape — reference (do not commit this verbatim; it's a guide for the dev agent)

```yaml
# .github/workflows/android-ci.yml
# Source of truth: architecture.md §"CI/CD Per Stack" row 1.
# Owning story: 1-6-cicd-per-stack.
name: android-ci

on:
  pull_request:
    paths:
      - 'android/**'
      - 'shared/**'
  push:
    branches: [main]
    paths:
      - 'android/**'
      - 'shared/**'
  workflow_dispatch: {}

defaults:
  run:
    working-directory: android

jobs:
  android-ci:
    runs-on: ubuntu-latest
    timeout-minutes: 20
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - uses: gradle/actions/setup-gradle@v4

      - name: detekt
        run: ./gradlew :app:detekt --no-daemon

      - name: unit-tests
        run: ./gradlew :app:testDebugUnitTest --no-daemon

      - name: assemble-debug
        run: ./gradlew :app:assembleDebug --no-daemon

      - name: publish-junit-report
        if: always()
        uses: mikepenz/action-junit-report@v4
        with:
          report_paths: 'android/app/build/test-results/**/*.xml'

      - name: upload-debug-apk
        uses: actions/upload-artifact@v4
        with:
          name: app-debug-apk-${{ github.sha }}
          path: android/app/build/outputs/apk/debug/*.apk
          retention-days: 7
```

### `ios-ci.yml` stub shape

```yaml
# .github/workflows/ios-ci.yml
# DEFERRED to Story 1.6b — depends on Story 1.2 (Xcode project on Mac).
# Source of truth: architecture.md §"CI/CD Per Stack" row 2.
# When 1.6b lights this up, replace the stub job with:
#   - SwiftLint (uses .swiftlint.yml from Story 1.5)
#   - xcodebuild test (TranslatorRep + TranslatorRepTests schemes)
#   - snapshot tests (point-free or yaslab pattern)
#   - archive (TestFlight Ad Hoc on tag push)
name: ios-ci

on:
  pull_request:
    paths:
      - 'ios/**'
      - 'shared/**'
  push:
    branches: [main]
    paths:
      - 'ios/**'
      - 'shared/**'
  workflow_dispatch: {}

jobs:
  ios-ci-stub:
    runs-on: macos-latest  # locked in for 1.6b's xcodebuild
    timeout-minutes: 5
    steps:
      - run: |
          echo "iOS CI deferred to Story 1.6b — Xcode project lands in Story 1.2."
          echo "Stub passes so iOS PRs aren't blocked. See docs/runbooks/ci-stack-overview.md."
```

### `infra-ci.yml` stub shape

```yaml
# .github/workflows/infra-ci.yml
# DEFERRED to Story 1.6c — depends on Story 1.3 (Oracle VM + infra/ directory).
# Source of truth: architecture.md §"CI/CD Per Stack" row 3.
# When 1.6c lights this up, replace the stub job with:
#   - yamllint infra/livekit.yaml + infra/Caddyfile
#   - docker compose -f infra/docker-compose.yml config (validation only, no up)
#   - on tag push: ssh oracle-vm && ./infra/scripts/deploy.sh
name: infra-ci

on:
  pull_request:
    paths:
      - 'infra/**'
  push:
    branches: [main]
    paths:
      - 'infra/**'
  workflow_dispatch: {}

jobs:
  infra-ci-stub:
    runs-on: ubuntu-latest
    timeout-minutes: 5
    steps:
      - run: |
          echo "Infra CI deferred to Story 1.6c — Oracle VM + infra/ directory land in Story 1.3."
          echo "Stub passes so infra PRs aren't blocked. See docs/runbooks/ci-stack-overview.md."
```

### Testing standards

- **CI workflow itself is its own test surface.** AC-5 (deliberately broken commit) is the smoke test — there is no separate unit test for the workflow YAML.
- **The yaml files MUST be valid GitHub Actions syntax.** GitHub will refuse to register a malformed workflow; the smoke test of pushing a branch with the workflow file is itself the validation.
- **No flakiness budget.** If the Android CI workflow ever flakes (passes on rerun without code changes), that's a Story 1.6 regression and must be investigated before next story closes.

### Project Structure Notes

- Repo root gains `.github/workflows/` (3 files) + `docs/runbooks/ci-stack-overview.md` (1 file). No source code changes in `android/`, `ios/`, `shared/`, or `infra/`.
- The `defaults.run.working-directory: android` job-level setting (Task 2.6) is required because the Gradle wrapper lives at `android/gradlew`, not at repo root.

### References

- [architecture.md §"CI/CD Per Stack"](../planning-artifacts/architecture.md#cicd-per-stack)
- [architecture.md §"Repo Shape — Monorepo, Per-Stack Roots"](../planning-artifacts/architecture.md#repo-shape--monorepo-per-stack-roots)
- [architecture.md §16 "Enforcement Guidelines"](../planning-artifacts/architecture.md#16-enforcement-guidelines)
- [/_bmad-output/implementation-artifacts/1-5-safelog-lint-ulid.md](./1-5-safelog-lint-ulid.md) — Story 1.5 dev notes (JDK + Gradle versions, detekt config path, ULID lib coordinates)
- [/docs/runbooks/desktop-session-handoff-2026-05-23.md](../../docs/runbooks/desktop-session-handoff-2026-05-23.md) — confirms Story 1.6 is the planned next-on-Windows move after 1.5
- [actions/setup-java@v4 docs](https://github.com/actions/setup-java/blob/main/README.md)
- [gradle/actions/setup-gradle@v4 docs](https://github.com/gradle/actions/blob/main/setup-gradle/README.md)

## Dev Agent Record

### Agent Model Used

_(filled in by dev-story at implementation time)_

### Debug Log References

_(filled in by dev-story — record any unexpected CI failures + their resolutions; the JDK/Gradle version mismatch from Story 1.5 dev notes is the canonical example of what to write here)_

### Completion Notes List

_(filled in by dev-story — must record: cold-cache + warm-cache wall-clock baselines for android-ci.yml; smoke-test throwaway-branch name; observed action versions if any were bumped past the recommended ones in this file)_

### File List

_(filled in by dev-story — comprehensive list of files created / modified)_

### Change Log

- 2026-05-23 — Story 1.6 created (status `ready-for-dev`). Scope: Android CI workflow + iOS/infra stubs. iOS + infra full implementations split into follow-up stories 1-6b and 1-6c sequenced after Story 1.2 and Story 1.3 respectively.
