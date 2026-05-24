# CI Stack Overview

> **Authority:** This runbook documents the state of `.github/workflows/` as of Story 1.6 (2026-05-23). Source-of-truth: [architecture.md §"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack).

---

## 1. Current state (2026-05-23)

| Workflow file | Trigger paths | Today's scope | Owning story | Follow-up story |
|---|---|---|---|---|
| `.github/workflows/android-ci.yml` | `android/**`, `shared/**`, `.github/workflows/android-ci.yml` | **Full:** checkout → JDK 17 (Temurin) → Gradle 8.10.2 (wrapper) → `detekt` → `testDebugUnitTest` → `assembleDebug` → JUnit XML annotations → APK artifact upload (7-day retention). | 1-6-cicd-per-stack | 1-6d-android-ci-flesh-out (adds Compose UI tests + Roborazzi screenshot diff + `assembleRelease` with signing-config) |
| `.github/workflows/ios-ci.yml` | `ios/**`, `shared/**`, `.github/workflows/ios-ci.yml` | **Stub.** Single `echo` step on `ubuntu-latest` (runner will flip to `macos-latest` in 1.6b when `xcodebuild` lands). Exits 0 to keep PRs unblocked. | 1-6-cicd-per-stack | 1-6b-ios-ci-flesh-out (sequenced after Story 1.2 lands the Xcode project on Mac) |
| `.github/workflows/infra-ci.yml` | `infra/**`, `.github/workflows/infra-ci.yml` | **Full (Story 1.6c, 2026-05-24).** Three parallel jobs: **config-lint** (`yamllint` livekit.yaml + docker-compose.yml via `infra/.yamllint.yml` → `shellcheck --severity=warning` deploy.sh + scripts/*.sh → `docker compose config -q` → `caddy validate` via `caddy:2-alpine`); **auth-proxy** (Node 22: `npm ci` → typecheck → vitest (23 tests) → `tsc` build); **auth-proxy-docker** (`docker build` the distroless image, no push). **Deferred:** tag-triggered `ssh + deploy.sh` on the Oracle VM — see §5. | 1-6-cicd-per-stack | — (deploy step deferred pending Oracle-vs-LiveKit-Cloud decision) |

Path filters are intentional: an Android-only PR will not trigger iOS or infra workflows, and vice-versa. This keeps the GitHub Actions free-tier minute budget lean and isolates failure surfaces per stack. Each workflow's own YAML path is also included so edits to the workflow itself trigger the workflow on the PR that introduces them (self-validation).

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

## 4. Scope cuts vs. architecture spec — what 1.6 deferred to 1.6d

The architecture spec ([§"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack)) prescribes a longer Android CI chain than Story 1.6 implemented. Three steps are deferred to follow-up **Story 1.6d** so they can land together with the signing-config work:

| Step | Spec wants | 1.6 ships | Why deferred to 1.6d |
|---|---|---|---|
| **APK target** | `assembleRelease` | `assembleDebug` | Release builds need a signing config (keystore + GitHub Actions secrets + `build.gradle.kts` wiring). Non-trivial — deserves its own story alongside store-upload prep. `assembleDebug` is sideloadable on dev devices, which covers Epic 1's pairing-and-call validation flow. |
| **Compose UI tests** | `./gradlew :app:connectedDebugAndroidTest` | Not implemented | No Compose UI tests are written yet — they'll arrive with the stories that introduce real UI (pairing screen onward, Epic 1 stories 1.9+). Wiring the gradle task before the tests exist would just slow CI for zero signal. |
| **Roborazzi screenshot diff** | `./gradlew :app:verifyRoborazziDebug` | Not implemented | Roborazzi needs reference images committed to source control. Establishing those baselines is its own decision (which Compose preview surfaces to baseline, on which Android API level). Belongs in 1.6d with the UI tests it depends on. |

This scope-cut is consistent with the [solo-dev scope-cuts pattern](./solo-dev-scope-cuts.md) — defer infrastructure that has no current production code to exercise it. Story 1.6d will close the architecture-spec gap when the prerequisite work (UI components, signing config, Roborazzi baselines) is ready.

---

## 5. Deferred work — what 1.6b and 1.6c need to land

### Story 1.6b — `ios-ci.yml` flesh-out (sequenced after Story 1.2)

Replace the stub job in `.github/workflows/ios-ci.yml` with these steps, in order (per architecture.md §"CI/CD Per Stack" row 2):

- [ ] **SwiftLint** — uses `.swiftlint.yml` from Story 1.5. Action: `norio-nomura/action-swiftlint@3.2.1` or a `brew install swiftlint && swiftlint` run-step.
- [ ] **`xcodebuild test`** — TranslatorRep + TranslatorRepTests schemes. Requires the Xcode project from Story 1.2.
- [ ] **Snapshot tests** — pattern decision (point-free `swift-snapshot-testing` vs. yaslab pattern) made in Story 1.6b itself.
- [ ] **Archive (TestFlight Ad Hoc)** — gated on `push` of a tag (e.g., `ios-v*`). Requires signing-config provisioning analogous to Android's deferred 1.6d.

### Story 1.6c — `infra-ci.yml` flesh-out — ✅ DONE (2026-05-24)

Landed in Story 1.6c. The stub was replaced with three parallel jobs (see §1 table for the full step list). Implementation notes that diverge from the original plan above:

- **`yamllint`** — lints `infra/livekit.yaml` + `infra/docker-compose.yml` (NOT the Caddyfile — it isn't YAML; Caddy syntax is validated separately by `caddy validate`). Invoked as `python -m yamllint -c infra/.yamllint.yml` (python is pre-installed on the runner) rather than a third-party action — fewer supply-chain surfaces, PATH-independent.
- **`shellcheck`** — added beyond the original plan: lints `infra/deploy.sh` + `infra/scripts/*.sh` at `--severity=warning` (info-level SC2029 in deploy.sh is intentional client-side expansion). Pre-installed on `ubuntu-latest`.
- **`docker compose config -q`** — as planned (validation-only, no `up`).
- **`caddy validate`** — added beyond the original plan: validates the Caddyfile via the official `caddy:2-alpine` image (no caddy binary on the runner).
- **auth-proxy test + build** — added beyond the original plan (the auth-proxy didn't exist when row 3 was written): `npm ci → typecheck → vitest → tsc` on Node 22, plus a `docker build` of the distroless image. This is the story's highest-value addition — it closes the gap where the proxy's 23 tests had no CI.

#### Still deferred — SSH deploy on tag push

**NOT wired.** Reason: Story 1.3 Phase 2 hasn't run (no Oracle VM exists to deploy to) **and** the Oracle-vs-LiveKit-Cloud hosting decision is open (Bania deprioritized Oracle 2026-05-24). When that resolves and a VM (or equivalent host) exists, add a `deploy` job:

- Gate: `if: startsWith(github.ref, 'refs/tags/infra-v')` (push of an `infra-v*` tag).
- Action: `appleboy/ssh-action` (SHA-pinned) executing `./infra/deploy.sh` on the host.
- Secret: `ORACLE_VM_SSH_KEY` (private key) — `gh secret set ORACLE_VM_SSH_KEY < ~/.ssh/oracle-translatorrep`.

If the decision flips to LiveKit Cloud, the auth-proxy still needs a host (Fly.io / Render / Cloud Run); the same tag-gated deploy job applies with a different target.

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

## 7. Known trade-offs (current scope)

These are deliberate posture choices made by Story 1.6 + its CR. They are not bugs and they are not on a near-term roadmap to change — they're noted here so a future contributor doesn't have to re-derive the reasoning.

- **No fork-PR guard on `android-ci.yml`.** The job grants `checks: write` + `pull-requests: write` permissions, which would also flow to PRs opened from forks (GitHub withholds repo secrets from fork PRs, but workflow-permissions still apply). `TranslatorRep` is a solo-dev repo on `slepingdragon` with no fork PRs expected. **If the repo opens to outside contributors,** add `if: github.event.pull_request.head.repo.full_name == github.repository` to the `android-ci` job to skip fork PRs (or migrate to the `pull_request_target` pattern with a manual approval gate).
- **Third-party action `mikepenz/action-junit-report` SHA-pinned; first-party `actions/*` + `gradle/*` pinned to major.** First-party GitHub-org actions have stronger supply-chain guarantees; the SHA-pin discipline is concentrated where the risk is. If a major bump is needed (`v4` → `v5`), update the SHA + the `# v4` annotation deliberately.
- **`--no-daemon` on every Gradle invocation.** CI runners are ephemeral — the Gradle build daemon (which is in-process JVM reuse across tasks within a job) saves nothing across jobs/PRs. The `gradle/actions/setup-gradle@v4` action handles cache persistence at the Gradle-home level, not at the daemon level. `--no-daemon` is the standard CI convention.
- **`workflow_dispatch: {}` on all three workflows.** Lets you manually fire a run from the GitHub UI without a PR. Useful for debugging or for re-running the iOS/infra stubs as a sanity check.

---

## 8. References

- [architecture.md §"CI/CD Per Stack"](../../_bmad-output/planning-artifacts/architecture.md#cicd-per-stack) — table of all three workflows + scope.
- [architecture.md §"Repo Shape — Monorepo, Per-Stack Roots"](../../_bmad-output/planning-artifacts/architecture.md#repo-shape--monorepo-per-stack-roots) — confirms `.github/workflows/` lives at repo root.
- [Story 1.6 — CI/CD Per Stack](../../_bmad-output/implementation-artifacts/1-6-cicd-per-stack.md) — the story that wired this.
- [Story 1.5 — SafeLog + lint + ULID](../../_bmad-output/implementation-artifacts/1-5-safelog-lint-ulid.md) — JDK 17 + Gradle 8.10.2 baseline that this CI must match.
- [solo-dev scope-cuts runbook](./solo-dev-scope-cuts.md) — pattern justifying `assembleDebug` over `assembleRelease`.
- [actions/setup-java@v4 docs](https://github.com/actions/setup-java/blob/main/README.md)
- [gradle/actions/setup-gradle@v4 docs](https://github.com/gradle/actions/blob/main/setup-gradle/README.md)
