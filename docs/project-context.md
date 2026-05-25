---
project_name: TranslatorRep
user_name: Bania
generated: 2026-05-23
last_updated: 2026-05-24
generated_by: bmad-generate-project-context
sections_completed:
  - identity_and_authority
  - tech_stack
  - repo_shape
  - critical_landmines
  - conventions
  - bmad_workflow
  - safelog_and_canonical_names
  - testing
  - ci_cd
  - externally_blocked_work
  - pointers
  - update_protocol
  - remaining_sessions_to_ship_v1
---

# Project Context for AI Agents

> **Read this first.** Distilled rules + landmines for any AI agent (Claude Code, BMad skills, code review) doing work in this repo. Optimized for LLM context: facts and pointers, not prose. If a rule is obvious from a quick file scan, it's not here — only things you'd otherwise have to learn the hard way.

> **Session handoff (2026-05-24):** Today landed 9 PRs in the morning (Stories 1.6 done, 3.2 done, 3.2b TQ-1 complete: 14 Indonesian discourse particles + 42 golden-file fixtures), then **Session 7 in the afternoon** landed Story 3.2b Phase 4: TQ-3 (`dia → they`) + TQ-6 (6 religious-expression verbatim rules) — 21 new fixtures + 5 existing-fixture updates → 63 total. Story 1.4 (Firebase Android) is `ready-for-dev` awaiting Bania's manual Phase 0. See §13 below for the full remaining-session map.

---

## 1. Identity & authority

- **App:** `TranslatorRep` — bilingual real-time call + translation app (Indonesian Bahasa ↔ English, Sundanese-aware). 2-user pairing scale.
- **Owner:** Brady J Bania (`@slepingdragon` on GitHub). Solo-dev cadence.
- **Hosting:** `xaeryx.com` subdomain model (see global `~/.claude/CLAUDE.md`).
- **Source of truth hierarchy** (when docs conflict, this wins):
  1. `_bmad-output/planning-artifacts/architecture.md` (1800+ lines — most decisions)
  2. `shared/canonical-names.md` (concept names + forbidden synonyms — enforced by code review)
  3. `_bmad-output/planning-artifacts/epics.md` (story definitions)
  4. `docs/runbooks/*.md` (operational + scope-cut context)

**Never invent a fact about the project.** If you can't find it in those four sources, ask Bania or check `git log`.

---

## 2. Tech stack (exact)

| Layer | Tech | Pin | Where configured |
|---|---|---|---|
| Android language | Kotlin | per Gradle TOML | `android/gradle/libs.versions.toml` |
| Android UI | Jetpack Compose (BOM-managed) | per TOML | `android/app/build.gradle.kts` |
| Android JDK | **Adoptium Temurin 17** | — | `android/app/build.gradle.kts` `jvmToolchain(17)` |
| Android Gradle wrapper | **8.10.2** | wrapper-locked | `android/gradle/wrapper/gradle-wrapper.properties` |
| Android min SDK / target | 33 / 35 | locked | `android/app/build.gradle.kts` |
| Android lint | detekt 1.23.7 + custom `ForbiddenImport` rule | TOML | config at `android/detekt-config.yml` (NOT `android/app/`) |
| Android tests | JUnit 4 | `libs.junit` | `org.junit.Assert.assertEquals` style — see §8 |
| ULID (Kotlin) | `com.aallam.ulid:ulid-kotlin:1.3.0` | locked Story 1.5 | wrapped behind `android/.../ids/UlidGenerator.kt` |
| iOS language | Swift / SwiftUI | TBD Story 1.2 | — |
| iOS lint | SwiftLint + custom rules | — | `ios/.swiftlint.yml` |
| ULID (Swift) | **`yaslab/ULID.swift`** ≥ 1.3.1 (NOT `oherrala/swift-ulid` — that URL is 404) | — | `ios/PACKAGES.md` |
| WebRTC media | LiveKit Android/iOS SDKs + LiveKit Server | per TOML | architecture §A |
| Backend auth | Custom Express auth-proxy on Oracle VM (deferred Story 1.3) | — | `shared/auth-proxy-api.md` |
| Identity store | Firebase Anonymous Auth + Firestore + App Check | BOM | gated on Story 1.4 |
| CI | GitHub Actions, ubuntu-latest, JDK 17 Temurin | per-stack workflows | `.github/workflows/{android,ios,infra}-ci.yml` |
| BMad Method | 6.7.1 | — | `_bmad/_config/bmad-help.csv` |

**Firebase plugins are commented-out in `android/app/build.gradle.kts`** — uncomment the `alias(libs.plugins.google.services)` + `alias(libs.plugins.firebase.crashlytics)` lines explicitly when Story 1.4 lands `google-services.json` at `app/google-services.json`. The Firebase BOM dependencies still resolve fine; they're just inert until then.

---

## 3. Repo shape

```
TranslatorRep/
├── .agents/skills/        # BMad skills (bmad-dev-story, bmad-code-review, etc.)
├── .github/workflows/     # CI per stack (android-ci.yml, ios-ci.yml, infra-ci.yml)
├── .gitattributes         # LF rules: android/gradlew (1.6 CR) + *.sh & auth-proxy Dockerfile (1.6c)
├── _bmad/                 # BMad config + scripts (bmad-help.csv, customization resolver)
├── _bmad-output/
│   ├── planning-artifacts/    # architecture.md, epics.md, prds/, ux-design-spec, briefs/
│   └── implementation-artifacts/  # story files {N-M-slug}.md + sprint-status.yaml + deferred-work.md
├── android/               # Kotlin/Compose app — single Gradle module today (:app)
├── ios/                   # Swift/SwiftUI app — Xcode project lands Story 1.2 (Mac-side)
├── infra/                 # Docker Compose stack — lands Story 1.3 (Oracle VM-side)
├── shared/                # Cross-platform contracts (canonical-names, schemas, fixtures)
└── docs/
    ├── project-context.md     # this file
    └── runbooks/              # operational + scope-cut runbooks
```

**Per-stack roots are sacrosanct.** Architecture §"Repo Shape — Monorepo, Per-Stack Roots". An iOS file in `android/` (or vice versa) is a code-review reject.

---

## 4. Critical landmines (read every one)

These are documented bugs / footguns that have actually bitten the project. Each cost real time when they first surfaced.

### Build / toolchain

- **JDK version sensitivity.** Local Gradle MUST run on JDK 17 (Adoptium Temurin OR Android Studio's bundled JBR). A system Java 8 (often on Windows `PATH` at `C:\Program Files (x86)\...\java8path\java.exe`) will crash with `Invalid maximum heap size: -Xmx4096m`. Fix locally: `JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH" ./gradlew ...`. CI is unaffected (uses `actions/setup-java@v4 temurin 17`).
- **`android/gradlew` executable bit.** Tracked as `100755` in git index. Windows contributors who delete + recreate the file may re-strip to `100644`, causing CI `./gradlew: Permission denied`. Belt-and-suspenders: (a) `.gitattributes` rule for LF, (b) `chmod +x ./gradlew` step in android-ci.yml. Fix incident: Story 1.6 Bug #1.
- **Detekt config path.** Lives at `android/detekt-config.yml`, NOT `android/app/detekt-config.yml`. The `detekt { config.setFrom(files("$rootDir/detekt-config.yml")) }` block resolves `$rootDir` to `android/` (because Gradle is invoked from `android/`). CI must `cd android` first — handled by `defaults.run.working-directory: android` in android-ci.yml.
- **JUnit assertion imports.** Use `import org.junit.Assert.assertEquals` (JUnit 4 style, message-first signature: `assertEquals("msg", expected, actual)`). `kotlin.test.assertEquals` is NOT on the classpath. Caught the hard way during Story 1.6 smoke testing.
- **`--no-daemon` everywhere on CI.** Standard CI convention — daemon doesn't persist across ephemeral runners. The `gradle/actions/setup-gradle@v4` cache is at Gradle-home level, not daemon level. Don't "fix" this.
- **`/shared/particle-rules-fixtures/` is NOT a Gradle test input.** When adding new fixtures locally, `./gradlew :app:testDebugUnitTest` will be UP-TO-DATE without re-running. Use `--rerun-tasks` OR touch a Kotlin source file to bust the cache. CI is unaffected (fresh runner re-runs everything). Caught during Story 3.2b Phase 3. **Fix opportunity:** add `inputs.dir("$rootDir/../shared/particle-rules-fixtures")` to the test task wiring in `app/build.gradle.kts` (track as deferred work).

### Code

- **`SafeLog` facade is the ONLY logging surface.** Direct `android.util.Log.*` or `timber.log.Timber.*` outside `android/app/src/main/java/com/xaeryx/translatorrep/logging/SafeLog.kt` is a detekt `ForbiddenImport` error. The rule is the SafeLog gate per architecture §14. iOS equivalent: `SafeLog.swift` + SwiftLint custom rule banning `print|os_log|NSLog|debugPrint|dump|Logger`.
- **SafeLog forbids 3 value classes.** Never log: (1) conversation content (`source_text`, `target_text`, `caption_text`), (2) PII (`participant_name`, `display_name`), (3) Flores-200 codes (`ind_Latn`, `eng_Latn`, `sun_Latn`). BCP 47 codes (`id-ID`, `en-US`) are fine. No runtime enforcement — code review (§16) catches this.
- **ULID library coordinate confusion.** Android: Maven coordinate is `com.aallam.ulid:ulid-kotlin:1.3.0` but the **Kotlin package** is just `ulid` — `import ulid.ULID` (NOT `import com.aallam.ulid.ULID`). Always wrap behind `ids/UlidGenerator.kt`; production code never imports the library directly.
- **Cross-platform ULID test vector (locked at Story 1.5).** `(timestamp_ms=1779458031242, random_bytes=0102030405060708090A) → "01KS7ZDFMA041061050R3GG28A"`. Both `UlidGenerator.encodeCanonical(...)` implementations (Kotlin + Swift) MUST produce byte-identical output for this input. See `shared/canonical-names.md` §3.
- **`UlidGenerator.next()` coerces `System.currentTimeMillis() ≥ 0L`** to guard a misconfigured device clock. Don't remove the coerce — the library throws `IAE` on negative timestamps.
- **`SafeLog.event(value)` defends `value.toString()`** via `stringifyDefensively()` — buggy caller `toString()` returns `"<toString-failed:...>"` instead of crashing the logging path.
- **`ParticleProcessor` has null-target rules** (`kah`, `mah`). They participate in preProcess marker-tagging but emit nothing in postProcess (English doesn't need a question marker / NMT produces "as for X" naturally from pronoun + clause). `ParticleRules.applicableRules` filter ALLOWS rules with `emptyMap()` targetEquivalents through — don't tighten the filter back. Both `postProcess` passes handle the null-equivalent case explicitly.
- **`ParticleProcessor` is variant-source-set-free** for now but `FirebaseBootstrap` is NOT — `AppCheckFactoryProvider` lives in `src/debug/` AND `src/release/` separately because `DebugAppCheckProviderFactory` is `debugImplementation` only. A naive `if (BuildConfig.DEBUG)` branch would NoClassDefFoundError at release-compile time. Pattern lives in `android/app/src/{debug,release}/java/.../firebase/AppCheckFactoryProvider.kt`.

### Convention

- **Story 1.5 detekt config can't be applied via top-level `subprojects {}`** because the project is single-module today. When a second Gradle module lands (`:detekt-rules`, `:shared-kmp`, or any LiveKit fork), that module's `build.gradle.kts` MUST add `alias(libs.plugins.detekt)` + its own `detekt { config.setFrom(files("$rootDir/detekt-config.yml")) }` block — otherwise the ForbiddenImport gate has a silent backdoor.

---

## 5. Conventions

### Naming

- Kotlin/Swift types: `PascalCase`. Functions/vars/params: `camelCase`.
- File names: match the contained primary type (`CaptionRow.kt` / `CaptionRow.swift`).
- Data Channel JSON wire fields: **snake_case** (locked).
- Firestore document fields: **camelCase** (Firebase convention).
- Language codes: **BCP 47** externally (`id-ID`, `en-US`, `su-ID`); **Flores-200** (`ind_Latn`, `eng_Latn`) only inside `TranslationProvider.translate()` at the model-call boundary, never elsewhere.
- Logging keys: `snake_case`, must be from `AllowedLogKey` enum.
- Error codes: `ERR_<DOMAIN>_<CONDITION>` SCREAMING_SNAKE_CASE — see `shared/error-codes.md`.
- IDs: **26-char ULID Crockford base32** universally (`Pair.id`, `Call.id`, `Utterance.id`, `Caption.id`, `MessageId`).

### Git

- **Branches:** `feature/{slug}` for new stories; `chore/{slug}` for maintenance; `smoke/{slug}` for throwaway smoke-test branches.
- **PRs:** squash-merge + `--delete-branch`. PR title becomes the squash commit subject; PR description becomes the body.
- **Commits:** body explains *why* (the *what* is in the diff). Co-author Claude attribution per `~/.claude/CLAUDE.md`.
- **Smoke-test branches** (e.g., `smoke/1-6-detekt-break`): deliberately broken commits to validate CI fails for the right reason. PRs closed without merge; branches deleted local + remote after capture.

### Branch protection / merging

- No CODEOWNERS or required reviewers currently. Bania self-merges via `gh pr merge --squash --delete-branch`.
- **After `gh pr merge` on a PR whose local main was ahead of `origin/main`,** local main will diverge (its commits are now inside the squash). Resolve with `git fetch origin && git reset --hard origin/main` — the "destroyed" commits' content is in the squash. See PR #3 handoff notes for the incident.

---

## 6. BMad workflow (read before invoking any `bmad-*` skill)

### Story file lifecycle

- Story files: `_bmad-output/implementation-artifacts/{N-M-slug}.md`, where `N`=epic, `M`=story-or-substory (e.g., `1-6`, `1-6b`).
- Sprint tracking: `_bmad-output/implementation-artifacts/sprint-status.yaml` — single YAML with `development_status:` map.
- **Status flow:** `backlog → ready-for-dev → in-progress → review → done` (with `optional` for retrospectives).
- The dev-story workflow flips status; the code-review workflow flips review → done.

### Story-naming sub-letters

When the original story is split into follow-ups:

- `{N-M}b`, `{N-M}c`, `{N-M}d` — same N-M number with letter suffix indicates a deliberate scope-cut follow-up sequenced after some prerequisite. E.g., Story 1.6 deferred iOS to `1-6b-ios-ci-flesh-out` (after Story 1.2), infra to `1-6c-infra-ci-flesh-out` (after Story 1.3), and Compose UI tests + Roborazzi + signing-config to `1-6d-android-ci-flesh-out`.

### Cycle skills

- `bmad-create-story` (CS) → `bmad-create-story:validate` (VS, optional) → `bmad-dev-story` (DS) → `bmad-code-review` (CR) → loop back to DS if findings, else next CS or `bmad-retrospective` (ER) if epic complete.
- **Always run each skill in a fresh context window** when possible — the dev-story workflow explicitly says so. The Code Review tip recommends a *different* LLM than the one that implemented the story.

### Files agents may modify in a story file

Per the dev-story workflow critical rules: ONLY these sections of the story file may be modified during implementation:

- `Tasks/Subtasks` checkboxes
- `Dev Agent Record` (Debug Log, Completion Notes, File List)
- `Change Log`
- `Status` field

Never edit ACs, the Story statement, or Dev Notes — those are spec.

### Defer items

- `_bmad-output/implementation-artifacts/deferred-work.md` — items raised in CR that are real-but-not-actionable-now. Each entry names the originating review + date. When picking up a story that touches one of the listed files, scan defer items for that file and consider folding.

### Sprint-status YAML format

- 2-space indent inside `development_status:` block.
- `last_updated` is hand-edited — no timezone (project pattern; don't introduce TZs without backfilling).
- Status enum strictly from the file's STATUS DEFINITIONS comment block.

---

## 7. SafeLog & canonical names (enforcement layer)

### Canonical-name enforcement

- `shared/canonical-names.md` §1 is the **glossary + forbidden-synonyms table**. Examples: use `Pair` not `Couple`/`Bond`; use `Call` not `Session`/`Conversation`; use `CaptionStack` not `CaptionList`. Full list in the file.
- Code review rejects: (a) single-platform renames, (b) any new synonym for a glossary concept, (c) inline ad-hoc renames without ADR provenance in `architecture.md`.
- The code-review workflow grep-scans diffs for forbidden synonyms before approval.

### SafeLog (architecture §14)

```
SafeLog.event(key: AllowedLogKey, value: Any)
```

- `key` is from a closed enum (`AllowedLogKey`); adding a new key requires ADR + simultaneous Android+iOS PR.
- `value` is stringified defensively (buggy `toString()` → marker string).
- Two routes: Crashlytics custom-key (release, gated on `!BuildConfig.DEBUG`) + `Log.d` (debug). R8 strips `Log.d` in release once minification is on (post Epic 4).
- Crashlytics call is wrapped `try/catch(RuntimeException)` — pre-init `IllegalStateException` AND any other Crashlytics misbehavior no-op gracefully. `OutOfMemoryError` / `StackOverflowError` propagate.

---

## 8. Testing

### Where + how

- Android unit tests: `android/app/src/test/java/com/xaeryx/translatorrep/...`. Run: `cd android && ./gradlew :app:testDebugUnitTest --no-daemon`.
- Current test count: **11** (7 from Story 1.5 + 4 boundary tests from Story 1.5 CR cleanup).
- JUnit 4 imports only: `org.junit.Test`, `org.junit.Assert.assertEquals`, etc.
- Test message conventions: `assertEquals("descriptive failure message", expected, actual)` — message is FIRST arg.

### Cross-platform parity tests

- `UlidParityTest.kt` (Android) + Swift equivalent (Story 1.2) lock the same `encodeCanonical(...)` test vector from `shared/canonical-names.md` §3. Both must produce `"01KS7ZDFMA041061050R3GG28A"` for the canonical input.

### Local-vs-CI parity

Run before pushing:
```bash
cd android
./gradlew :app:detekt :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

If local fails but CI passes (or vice-versa), suspect (in order): JDK drift → gradlew exec bit → Gradle cache corruption. See [`docs/runbooks/ci-stack-overview.md`](runbooks/ci-stack-overview.md) §6.

---

## 9. CI / CD (per-stack)

| Workflow | Trigger paths | Today's scope | Follow-up |
|---|---|---|---|
| `android-ci.yml` | `android/**`, `shared/**`, `.github/workflows/android-ci.yml` | detekt → testDebugUnitTest → assembleDebug → JUnit annotations → APK 7-day artifact | Story 1.6d adds Compose UI tests + Roborazzi + assembleRelease+signing |
| `ios-ci.yml` | `ios/**`, `shared/**`, `.github/workflows/ios-ci.yml` | stub on **ubuntu-latest** (flips to macos-latest in 1.6b when xcodebuild lands) | Story 1.6b |
| `infra-ci.yml` | `infra/**`, `.github/workflows/infra-ci.yml` | **Full (1.6c):** config-lint (yamllint + shellcheck + `docker compose config` + `caddy validate`) ‖ auth-proxy (Node 22 npm ci+typecheck+vitest(23)+build) ‖ auth-proxy-docker (distroless image build) | Oracle ssh-deploy step deferred (open hosting decision) |

### Workflow hardening (per Story 1.6 CR — keep these)

- All workflows: `concurrency: { group: '<name>-${{ github.ref }}', cancel-in-progress: true }`.
- All workflows: path filter includes the workflow's own YAML for self-validation.
- Android: SHA-pinned `mikepenz/action-junit-report@db71d41eb79864e25ab0337e395c352e84523afe` (v4); first-party `actions/*` + `gradle/*` pinned to major.
- Android: `permissions: { contents: read, checks: write, pull-requests: write }` at job level (required for JUnit annotations).
- Android: `gradle/actions/setup-gradle@v4` with `validate-wrappers: true` + `cache-read-only: ${{ github.ref != 'refs/heads/main' }}`.
- Android: `upload-artifact@v4` with `name: app-debug-apk-${{ github.sha }}-${{ github.run_attempt }}` + `if-no-files-found: error`.
- Android: explicit `chmod +x ./gradlew` step (belt-and-suspenders for the exec bit).

### Wall-clock baselines

- **Cold-cache:** ~4 min (full chain: detekt + tests + assembleDebug + APK upload).
- **Warm-cache:** ~1m30s (gradle cache primed). AC-6 target was <10min — massive headroom.

### Solo-dev free-tier budget

GitHub Actions free tier = 2000 minutes/month. Solo-dev PR volume <30/month × ~3min/run = ~90 min/month. Concurrency control + macos-latest avoidance (stubs on ubuntu) keep this in budget.

---

## 10. Externally-blocked work (DO NOT START without checking)

| Story | Blocker | Status / sub-letter scheme |
|---|---|---|
| `1-2-ios-project-scaffold` | Requires Mac + Xcode | Deferred to Mac session per `docs/runbooks/ios-setup-on-mac.md` |
| `1-3-oracle-vm-livekit-...` | Requires Oracle Cloud account + Cloudflare Registrar + `xaeryx.com` domain (~$10/yr) + SSH keypair | **Phase 0 runbook + Phase 1a infra config + Phase 1b auth-proxy TypeScript ALL landed 2026-05-24** ([`docs/runbooks/oracle-vm-setup.md`](runbooks/oracle-vm-setup.md), [`infra/`](../infra/), [`infra/auth-proxy/`](../infra/auth-proxy/)). `ready-for-dev`. Phase 1b: 10 src files + 4 test files (23 unit tests green). POST /v1/token + GET /v1/healthz fully implemented. Dockerfile multi-stage → distroless. Local validate clean. **Only Phase 2 remaining**: Bania finishes Oracle Phase 0 (async), then `./infra/deploy.sh` from laptop + smoke test via `curl /v1/healthz`. Unblocks all of Epic 2 (audio calling). |
| `1-4-firebase-init-...` (Android) | ~~Phase 0 (manual Firebase console setup)~~ | **DONE → `review` 2026-05-24.** Phase 0 manual setup landed (Project ID `translatorrep-8d773`, Jakarta Firestore, Play Integrity linked, rules deployed). Phase 1 code wiring landed same day (plugins activated, `FirebaseBootstrap.init()` wired, `FirebaseSmokeTest` implemented + intent-extra triggered from MainActivity). Only Bania's Phase 3 device smoke test remains for `done` flip — cascades-unblocks Stories 1.8-1.13 pairing arc + entire Epic 2. |
| `1-4b-ios-firebase-init` | Story 1.2 + 1.4 | Future iOS Claude session |
| `1-4c-play-store-internal-testing-android` | Story 1.4 + Story 1.6d | Wires CI → signed AAB → Play Console Internal Testing track → opt-in URL (QR-able). Gives the "scan code, install via Play Store" workflow. **Renamed 2026-05-24** from `firebase-app-distribution` — Internal Testing is cleaner for native Android + validates Play Integrity end-to-end. Bania has Play Console dev account confirmed. |
| `1-8-anonymous-sign-in` (Android) | Story 1.4 (Firebase) ✅ | **LANDED → `review` 2026-05-24.** Anonymous sign-in on first launch (FR-1). App-scoped `AnonymousAuthRepository` (`StateFlow<AuthState>`), `FirebaseAuthGateway` seam (unit-testable under JUnit4-only toolchain — 8 tests green), state-gated `MainActivity` (loading/ready/retry; NO login UI). X25519 keypair deferred to 1.12. detekt clean + assembleDebug green. First story of the pairing arc. |
| `1-9-display-own-pairing-code` (Android) | Story 1.8 ✅ | **LANDED → `review` 2026-05-24.** Own 6-digit code → `/codes/{code}` (collision-checked, reused-on-return, regenerable); `PairingCodeDisplay` (UX-DR14) on the D4b Paired-Empty home (UX-DR15); tap-copy + long-press-regenerate. Core = PairingCodeGenerator + CodeStore seam + PairingFirestoreRepository + PairingCodeAllocator + PairingViewModel; 8 unit tests. **Uses `ownerId` per deployed firestore.rules (not spec's `ownerUid`).** detekt clean + assembleDebug green. |
| `1-10-enter-partners-pairing-code-to-pair` (Android) | Story 1.9 ✅ | **LANDED → `review` 2026-05-24.** Interactive `PairingCodeInput` (UX-DR13) → lookup `/codes/{code}` → create `/pairs/{pairId}` (ULID, memberA=me) + write OWN `/users/{uid}.pairId` → Paired-home placeholder. `PairingCoordinator` (NotFound/OwnCode/Expired/Success) + `PairStore` seam; 5 unit tests. **Reconciled w/ deployed rules:** keep `ownerId`; initiator writes only its own side — partner discovers via a `/pairs`-membership listener in 1.11 (rules forbid writing partner's `/users`). detekt 0 + assembleDebug green. |
| `1-11-paired-state-persists-across-app-restarts` (Android) | Story 1.10 ✅ | **LANDED → `review` 2026-05-24.** App-wide `PairingStatusRepository` = **first Room DB** (single-row pair mirror, offline-first) + live `/pairs`-membership listener (`Filter.or`, `callbackFlow`). Partner-side discovery writes its OWN `pairId` (completes 1.10's model). MainActivity routes on status (Unknown/Unpaired/Paired); listener also drives the post-pair transition. **3rd rules conflict:** partner name defaults to "Partner" — can't read partner's `/users` (owner-only); name-sharing → Story 8.5 via `/pairs`. 5 unit tests; detekt 0 + assembleDebug green. |
| `1-12-x25519-identity-keypair-generate-publish` (Android) | Story 1.11 ✅ | **LANDED → `review` 2026-05-24.** X25519 long-term identity keypair (ADR-A2) via **Google Tink** (new dep; raw 32-byte, cross-platform parity, Epic-5 ECDH-ready). Private key in EncryptedSharedPreferences (`secure/SecureStorage`) — never logged/networked; public key → `/users/{uid}.identityPub` Blob. `e2ee/` pkg + 6 unit tests. Per-call ephemeral ECDH/HKDF = Epic 5. detekt 0 + assembleDebug green. |
| `1-13-settings-sheet-shell-with-unpair` (Android) | Story 1.11/1.12 ✅ | **LANDED → `review` 2026-05-24 — CLOSES THE PAIRING ARC.** Settings gear → `ModalBottomSheet` (UX-DR35) + two-tap-confirm Unpair → delete `/pairs/{pairId}` + clear Room mirror + own `pairId`; partner flips to Unpaired via the existing `/pairs` listener (no new partner code). Added `material-icons-core`. 45 unit tests; detekt 0 + assembleDebug green. **Next: Story 2.2 (real Paired home + Call button) / Epic 2 — gated on hosting (Oracle OUT → LiveKit Cloud).** |
| `1-6b-ios-ci-flesh-out` | Story 1.2 | iOS CI flesh-out |
| `1-6c-infra-ci-flesh-out` | Story 1.3 | infra CI flesh-out |
| `1-6d-android-ci-flesh-out` | ~~Available now~~ | **LANDED → `review` 2026-05-24.** Signing config (reads `app/keystore.properties` OR debug fallback) + `assembleRelease` task working locally + Compose UI instrumented-test scaffold (compiles; CI deferred — needs emulator runner) + `docs/runbooks/release-keystore-setup.md`. Roborazzi screenshot tests SPLIT OUT to new sub-story `1-6e` (Windows Skia native-graphics risk + irrelevant to QR-install path). CI release-build (real keystore via GH secret) deferred to Story 1.4c. |
| `1-6e-roborazzi-screenshot-tests` | NEW 2026-05-24 — split from 1-6d | Roborazzi Compose screenshot testing in CI. Deferred until clear ROI (e.g., flaky Compose UI bug). Setup risk on Windows (Robolectric NATIVE graphics mode + Skia native lib loading) — fine on Ubuntu CI but local Windows dev may need extra config. |
| `3-2b-particleprocessor-rule-tables` | Available; in-progress | **TQ-1 + TQ-3 + TQ-6 COMPLETE (Phase 1+2+3+4 landed 2026-05-24): 14 Indonesian discourse particles + gender-neutral `dia → they` + 6 Arabic-origin religious-expression verbatim rules, 63 golden-file fixtures via 7 helpers** (`sentenceFinalParticle`, `formalQuestionSuffix`, `sentenceInitialDeictic`, `pronounConcessive`, `midSentenceAlso`, `genderNeutralPronoun`, `verbatimReligious`). Phase 5 (TQ-4 Sundanese, TQ-5 honorifics, TQ-7 indirect refusals, TQ-8 slang) remains — gated on Story 3.1 GF linguistic input. Story flips to review when AC-3+AC-4+AC-5+AC-7 populated. |
| `3-2c-particleprocessor-ios-parity` | Story 1.2 | iOS Swift mirror of `translation/particles/` (all 7 helpers + 22 rules + 63 fixtures) + cross-platform parity test |

**If an agent is asked to start one of these, check current state in `sprint-status.yaml` first** — many have prep PRs already landed that just need activation, not from-scratch start.

**Mac-side work waits until Windows-side Epic 1 is fully done** (Bania's explicit decision 2026-05-23 — "Android all first, then a separate Claude session for iOS"). This means iOS sub-letters (1-2, 1-4b, 1-6b, etc.) accumulate as backlog while Android-side Stories 1.4 → 1.8 → 1.9 → ... → 1.13 progress.

---

## 11. Pointers (load only when needed)

- **Architecture decisions:** `_bmad-output/planning-artifacts/architecture.md` (1800+ lines, sectioned — use `grep -n "^##"` to navigate; never load whole)
- **Story definitions + ACs:** `_bmad-output/planning-artifacts/epics.md`
- **UX spec:** `_bmad-output/planning-artifacts/ux-design-specification.md` + `ux-design-directions.html`
- **PRDs:** `_bmad-output/planning-artifacts/prds/`
- **Research / briefs:** `_bmad-output/planning-artifacts/{research,briefs}/`
- **Cross-platform contracts:** `shared/canonical-names.md`, `shared/data-channel-schema-v1.json`, `shared/auth-proxy-api.md`, `shared/state-derivation.md`, `shared/error-codes.md`
- **CI runbook:** `docs/runbooks/ci-stack-overview.md`
- **Solo-dev scope-cuts pattern:** `docs/runbooks/solo-dev-scope-cuts.md`
- **iOS Mac-side handoff:** `docs/runbooks/ios-setup-on-mac.md`
- **Defer items log:** `_bmad-output/implementation-artifacts/deferred-work.md`
- **BMad skill catalog:** `_bmad/_config/bmad-help.csv` (use this — never invent skill names)
- **BMad customization resolver:** `_bmad/scripts/resolve_customization.py --skill <path> --key workflow`

---

## 12. Update protocol

This file is generated by `bmad-generate-project-context`. To add or change rules:

1. Either re-invoke the skill (interactive), OR hand-edit and bump the frontmatter `last_updated:` date.
2. Sections must stay LEAN — agents load this on every BMad skill activation via the `persistent_facts` glob `{project-root}/**/project-context.md`.
3. If a rule is in `architecture.md` and easy to find there, link don't duplicate.
4. New landmines (a future "Story 1.X Bug #N") MUST be added to §4 — that section earns its keep by preventing repeat incidents.
5. When story status materially changes (done, in-progress, new sub-letter added), update §10 and §13 in the SAME PR as the story file change.

---

## 13. Remaining sessions to ship v1 (as of 2026-05-24)

Started: 9 stories done (1.1, 1.5, 1.6, 1.7, 1.14a/b/c, 3.2) + 3.2b in-progress (TQ-1 + TQ-3 + TQ-6 landed; TQ-4/5/7/8 remain for Phase 5). Remaining: ~79 stories in original spec + sub-letters. Estimate: **~40 Claude Code sessions** (28 Android + ~11 iOS + 1 final ship gate) plus user-side manual tasks. **Android Session 7 done 2026-05-24.**

### Android track (Windows) — sessions in dependency order

| # | Session | Stories | Dependency |
|---|---|---|---|
| 1 | Firebase Phase 1 wiring | 1.4 | Bania Phase 0 done |
| 2 | Oracle infra | 1.3 | Bania Oracle Cloud setup done |
| 3 | Pairing arc — sign-in + code exchange | 1.8 **DONE**, 1.9 **DONE**, 1.10 **DONE** (2026-05-24) | Session 1 |
| 4 | Pairing arc — persistence + identity | 1.11 **DONE**, 1.12 **DONE**, 1.13 **DONE** (2026-05-24) — **ARC COMPLETE** | Session 3 |
| 5 | Release config + Play Store Internal Testing | 1.6d + 1.4c | Session 1 |
| 6 | Infra CI flesh-out | 1.6c | **DONE 2026-05-24** — decoupled from Session 2 (Oracle): lint/test/build only; ssh-deploy step deferred |
| 7 | Particle rules completion — easier | 3.2b Phase 4 (TQ-3 + TQ-6) | **DONE 2026-05-24** |
| 8 | Particle rules completion — harder | 3.2b Phase 4b (TQ-4 + TQ-5 + TQ-7 + TQ-8) | **Bania's girlfriend's linguistic input** for slang/Sundanese |
| 9 | Audio calling — auth + call placement | 2.2 **DONE**; **2.1 DONE** (2026-05-24 — backend LIVE: LiveKit Cloud + Render auth-proxy `translatorrep-auth-proxy.onrender.com`, free + UptimeRobot keep-alive); **2.3 wired** (real call: token→room.connect→mic→ACTIVE) | epic-2 **in-progress**; **2.3 `review` — needs Brady's 2-device call test to flip `done`** |
| 10 | Audio calling — Android FCM + lifecycle | 2.5, 2.6, 2.7, 2.8 | Session 9 |
| 11 | Audio calling — finish + taxonomy | 2.9, 2.10, 2.11 | Session 10 |
| 12 | Translation — interfaces + Android ASR + VAD | 3.3 **DONE** (2026-05-24, provider interfaces + cancellation contract + fakes), 3.4 (Android ASR, needs device), 3.6 (VAD) | epic-3 **in-progress** |
| 13 | Translation — corpus + model | 3.7, 3.8 | Session 12 |
| 14 | **⚠️ Validation gate (project's biggest risk)** | 3.9 | Session 13 + **Bania's Story 3.1 real-conversation evidence** |
| 15 | Translation — decorator + tracing + captions | 3.10, 3.11, 3.12, 3.13 | Session 14 verdict |
| 16 | Translation — end-to-end speaker-side | 3.14, 3.15 | Session 15 |
| 17 | Bidirectional captions — data channel + publish/receive | 4.1, 4.2, 4.3, 4.4 | Session 16 |
| 18 | Bidirectional captions — Sundanese + scroll + choreography + e2e | 4.5–4.9 | Session 17 |
| 19 | E2EE — keypair + key derivation | 5.1, 5.2 | Session 18 |
| 20 | E2EE — LiveKit Insertable Streams (hard) | 5.3 | Session 19 |
| 21 | E2EE — indicator + privacy verification | 5.4, 5.5 | Session 20 |
| 22 | Video calling — selector + capture + render | 6.1, 6.2, 6.3, 6.4 | Session 21 |
| 23 | Video calling — failure tiles + controls + layout | 6.5, 6.6, 6.7, 6.8 | Session 22 |
| 24 | Resilience — config + state machine | 7.1, 7.2 | Session 23 |
| 25 | Resilience — UX + notification + flow + e2e | 7.3–7.7 | Session 24 |
| 26 | Personalization — theme + background | 8.1, 8.2, 8.3, 8.4 | Session 25 |
| 27 | Personalization — settings + transcript + editor + privacy | 8.5, 8.6, 8.7, 8.8 | Session 26 |
| 28 | Personalization — quality review + reactions + **ship gate** | 8.9, 8.10, 8.11 | Session 27 |

### iOS track (separate Claude sessions on Mac)

| # | Session | Stories | Notes |
|---|---|---|---|
| α | iOS scaffold | 1.2 | Xcode project, SwiftUI, mirror Android design tokens |
| β | iOS Firebase + CI | 1.4b + 1.6b | `GoogleService-Info.plist` + DeviceCheck + xcodebuild test in ios-ci.yml |
| γ | iOS Whisper.cpp ASR | 3.5 | Compile whisper.cpp XCFramework + Swift integration |
| δ | iOS ParticleProcessor parity | 3.2c | Mirror all 5 helpers + 14 rules + 42 fixtures in Swift + cross-platform parity test |
| ε | iOS pairing arc (mirror Android 1.8–1.13) | Swift halves of 1.8–1.13 | Anon sign-in + paircode + X25519 + settings |
| ζ | iOS audio calling — PushKit/CallKit | 2.4 + Swift halves of Epic 2 | iOS-specific PushKit + CallKit (different APIs than Android FCM/ConnectionService) |
| η | iOS translation pipeline | Swift halves of Epic 3 | After Android 3.9 verdict; mirror chosen Plan A/B/C |
| θ | iOS bidirectional captions | Swift halves of Epic 4 | Caption UI + state-priority choreography |
| ι | iOS E2EE | Swift halves of Epic 5 | LiveKit Insertable Streams in Swift |
| κ | iOS video calling | Swift halves of Epic 6 | SwiftUI VideoTile + camera + controls |
| λ | iOS resilience + personalization | Swift halves of Epic 7 + 8 | LeaveAndRejoinManager + theme/settings/transcript |

### User manual tasks (not Claude sessions)

- **Phase 0 Firebase** (in progress) → unblocks Android Session 1
- **Oracle Cloud setup + xaeryx.com domain** → unblocks Android Session 2 → all of Epic 2
- **Story 3.1 — real conversation with girlfriend** → feeds Android Session 14 (validation gate) + corrects the 39 VERIFY-WITH-GIRLFRIEND fixtures
- **Periodic linguistic review** of fixtures' `metadata.json` notes
- **TQ-AT v1 ship gate execution** (within Android Session 28) → final go/no-go

### Session pattern for fresh sessions

Each fresh Claude Code session should:

1. Read this file's §13 to find what's next per the dependency chain.
2. Read `_bmad-output/implementation-artifacts/sprint-status.yaml` for current state truth.
3. If story file exists, read it; if not, create it via `bmad-create-story` (or write manually following the Story 1.6 / 3.2b template).
4. Implement → local validate → commit → push → PR → watch CI → pause for Bania's merge call.
5. When Bania merges, reconcile local + report next-step options.

Don't load this whole file's §13 mid-session — it's reference. The session-specific story file + sprint-status are the active surfaces.
