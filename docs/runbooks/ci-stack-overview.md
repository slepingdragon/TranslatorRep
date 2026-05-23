# CI Stack Overview

> **Authority:** This runbook documents the state of `.github/workflows/` as of Story 1.6 (2026-05-23). Source-of-truth: [architecture.md §"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack).

---

## 1. Current state (2026-05-23)

| Workflow file | Trigger paths | Today's scope | Owning story | Follow-up story |
|---|---|---|---|---|
| `.github/workflows/android-ci.yml` | `android/**`, `shared/**` | **Full:** checkout → JDK 17 (Temurin) → Gradle 8.10.2 (wrapper) → `detekt` → `testDebugUnitTest` → `assembleDebug` → JUnit XML annotations → APK artifact upload (7-day retention). | 1-6-cicd-per-stack | — (signing-config / `assembleRelease` deferred to potential Story 1.6d) |
| `.github/workflows/ios-ci.yml` | `ios/**`, `shared/**` | **Stub.** Single `echo` step on `macos-latest`. Exits 0 to keep PRs unblocked. | 1-6-cicd-per-stack | 1-6b-ios-ci-flesh-out (sequenced after Story 1.2 lands the Xcode project on Mac) |
| `.github/workflows/infra-ci.yml` | `infra/**` | **Stub.** Single `echo` step on `ubuntu-latest`. Exits 0 to keep PRs unblocked. | 1-6-cicd-per-stack | 1-6c-infra-ci-flesh-out (sequenced after Story 1.3 lands the Oracle VM + `infra/` directory) |

Path filters are intentional: an Android-only PR will not trigger iOS or infra workflows, and vice-versa. This keeps the GitHub Actions free-tier minute budget lean and isolates failure surfaces per stack.

---

## 2. Manual trigger (`workflow_dispatch`)

All three workflows include `workflow_dispatch: {}` so they can be triggered manually from the GitHub UI for debugging or one-off runs.

1. Open `https://github.com/slepingdragon/TranslatorRep/actions`.
2. Click the workflow name in the left sidebar (e.g., `android-ci`).
3. Click **Run workflow** (top-right of the runs list).
4. Pick the branch (defaults to `main`) → **Run workflow**.

Useful when you want to verify a workflow change without producing a PR, or to re-run a job that exited cleanly but you suspect a flake.

---

## 3. Local equivalents

The Android CI workflow runs three Gradle tasks. To reproduce the CI build locally — same JDK, same Gradle version — from the repo root:

```bash
cd android
./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

### Required local toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | **17** (Adoptium Temurin recommended) | The Gradle build's `jvmToolchain(17)` will auto-provision JDK 17 if it can't find one. Pre-installing avoids a slow first-run download. |
| Gradle | **8.10.2** (wrapper-pinned) | The repo's `android/gradle/wrapper/gradle-wrapper.properties` pins the version. Never invoke a system-installed `gradle` — always use `./gradlew`. |
| Android SDK | API 34+ | Provisioned by Android Studio or `sdkmanager`. CI runners (`ubuntu-latest` with `actions/setup-java@v4` + `gradle/actions/setup-gradle@v4`) handle this transparently; locally, Android Studio Iguana+ is the easiest path. |

If `./gradlew :app:detekt` fails locally but passes on CI (or vice-versa), check JDK version first (`java -version`). JDK version drift is the #1 cause of CI vs. local divergence on this project — see Story 1.5's dev notes for the canonical incident.

---

## 4. Why `assembleDebug`, not `assembleRelease`?

The architecture spec ([§"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack)) prescribes `assembleRelease` for the Android workflow. Story 1.6 scopes this **down to `assembleDebug`** for the following reason:

- `assembleRelease` requires a signing configuration (keystore + alias + key + store passwords) that this story does not yet provision.
- Provisioning a release signing config involves: (a) generating a keystore, (b) storing the keystore + passwords as GitHub Actions secrets, (c) wiring `android/app/build.gradle.kts` to read the secrets at build time, (d) deciding upload-keystore-vs-app-signing-by-Google-Play strategy.
- That's a non-trivial story on its own and is **explicitly deferred** to follow-up Story 1.6d (or rolled into the first store-upload story, TBD).
- Until then, `assembleDebug` produces a valid APK artifact that is sideloadable on a dev device — sufficient for Epic 1's pairing-and-call validation flow, which doesn't depend on a release build.

This scope-cut is consistent with the [solo-dev scope-cuts pattern](./solo-dev-scope-cuts.md).

---

## 5. Deferred work — what 1.6b and 1.6c need to land

### Story 1.6b — `ios-ci.yml` flesh-out (sequenced after Story 1.2)

Replace the stub job in `.github/workflows/ios-ci.yml` with these steps, in order (per architecture.md §"CI/CD Per Stack" row 2):

- [ ] **SwiftLint** — uses `.swiftlint.yml` from Story 1.5. Action: `norio-nomura/action-swiftlint@3.2.1` or a `brew install swiftlint && swiftlint` run-step.
- [ ] **`xcodebuild test`** — TranslatorRep + TranslatorRepTests schemes. Requires the Xcode project from Story 1.2.
- [ ] **Snapshot tests** — pattern decision (point-free `swift-snapshot-testing` vs. yaslab pattern) made in Story 1.6b itself.
- [ ] **Archive (TestFlight Ad Hoc)** — gated on `push` of a tag (e.g., `ios-v*`). Requires signing-config provisioning analogous to Android's deferred 1.6d.

### Story 1.6c — `infra-ci.yml` flesh-out (sequenced after Story 1.3)

Replace the stub job in `.github/workflows/infra-ci.yml` with these steps, in order (per architecture.md §"CI/CD Per Stack" row 3):

- [ ] **`yamllint`** — lint `infra/livekit.yaml` and `infra/Caddyfile`. Action: `ibiqlik/action-yamllint@v3`.
- [ ] **`docker compose config`** — validation-only (`-f infra/docker-compose.yml config`), no `up`. Catches malformed compose files before deploy.
- [ ] **SSH deploy on tag push** — `appleboy/ssh-action@v1.0.3` (or equivalent) executing `./infra/scripts/deploy.sh` on the Oracle VM. Requires `ORACLE_VM_SSH_KEY` GitHub Actions secret.

Both follow-up stories should mirror this runbook's "Current state" table by adding their own row(s) at completion.

---

## 6. Debugging a failing CI run

When a workflow run fails, the diagnosis order is:

1. **Open the failed run** in the GitHub Actions UI. Failing steps are highlighted red.
2. **Check the JUnit-report annotation** in the PR's Checks tab — `mikepenz/action-junit-report@v4` renders individual failing tests inline (no need to scroll Gradle logs for test failures).
3. **For detekt failures:** the log will name the rule that fired (e.g., `ForbiddenImport`) and the source file. Fix locally with `cd android && ./gradlew :app:detekt`.
4. **For test failures:** reproduce locally with `cd android && ./gradlew :app:testDebugUnitTest --tests 'com.xaeryx.translatorrep.<TestClass>'`.
5. **For `assembleDebug` failures:** typically a Kotlin compilation error or a missing manifest entry. Reproduce with `cd android && ./gradlew :app:assembleDebug`.
6. **If the workflow runs but a step exits 0 unexpectedly (silent pass):** check the path-filter — a workflow with a misconfigured `paths:` block can be triggered but skip every step. Confirm `.github/workflows/android-ci.yml` lines for `paths:` match `android/**` + `shared/**`.

If a run fails on CI but passes locally with the same commit, suspect:

- JDK version drift (see §3).
- Gradle cache corruption — the `gradle/actions/setup-gradle@v4` action handles cache persistence. A misconfigured cache key manifests as every run being a cold-cache run (warm-cache should land in < 10 min per AC-6).
- Newly-introduced dependency that resolved locally from a cached repo but fails on CI's fresh Maven Central pull.

---

## 7. References

- [architecture.md §"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack) — table of all three workflows + scope.
- [architecture.md §"Repo Shape — Monorepo, Per-Stack Roots"](../../_bmad-output/planning-artifacts/architecture.md#repo-shape--monorepo-per-stack-roots) — confirms `.github/workflows/` lives at repo root.
- [Story 1.6 — CI/CD Per Stack](../../_bmad-output/implementation-artifacts/1-6-cicd-per-stack.md) — the story that wired this.
- [Story 1.5 — SafeLog + lint + ULID](../../_bmad-output/implementation-artifacts/1-5-safelog-lint-ulid.md) — JDK 17 + Gradle 8.10.2 baseline that this CI must match.
- [solo-dev scope-cuts runbook](./solo-dev-scope-cuts.md) — pattern justifying `assembleDebug` over `assembleRelease`.
- [actions/setup-java@v4 docs](https://github.com/actions/setup-java/blob/main/README.md)
- [gradle/actions/setup-gradle@v4 docs](https://github.com/gradle/actions/blob/main/setup-gradle/README.md)
