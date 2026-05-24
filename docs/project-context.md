---
project_name: TranslatorRep
user_name: Bania
generated: 2026-05-23
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
---

# Project Context for AI Agents

> **Read this first.** Distilled rules + landmines for any AI agent (Claude Code, BMad skills, code review) doing work in this repo. Optimized for LLM context: facts and pointers, not prose. If a rule is obvious from a quick file scan, it's not here — only things you'd otherwise have to learn the hard way.

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
├── .gitattributes         # Scoped LF rule for android/gradlew (Story 1.6 CR)
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

### Code

- **`SafeLog` facade is the ONLY logging surface.** Direct `android.util.Log.*` or `timber.log.Timber.*` outside `android/app/src/main/java/com/xaeryx/translatorrep/logging/SafeLog.kt` is a detekt `ForbiddenImport` error. The rule is the SafeLog gate per architecture §14. iOS equivalent: `SafeLog.swift` + SwiftLint custom rule banning `print|os_log|NSLog|debugPrint|dump|Logger`.
- **SafeLog forbids 3 value classes.** Never log: (1) conversation content (`source_text`, `target_text`, `caption_text`), (2) PII (`participant_name`, `display_name`), (3) Flores-200 codes (`ind_Latn`, `eng_Latn`, `sun_Latn`). BCP 47 codes (`id-ID`, `en-US`) are fine. No runtime enforcement — code review (§16) catches this.
- **ULID library coordinate confusion.** Android: Maven coordinate is `com.aallam.ulid:ulid-kotlin:1.3.0` but the **Kotlin package** is just `ulid` — `import ulid.ULID` (NOT `import com.aallam.ulid.ULID`). Always wrap behind `ids/UlidGenerator.kt`; production code never imports the library directly.
- **Cross-platform ULID test vector (locked at Story 1.5).** `(timestamp_ms=1779458031242, random_bytes=0102030405060708090A) → "01KS7ZDFMA041061050R3GG28A"`. Both `UlidGenerator.encodeCanonical(...)` implementations (Kotlin + Swift) MUST produce byte-identical output for this input. See `shared/canonical-names.md` §3.
- **`UlidGenerator.next()` coerces `System.currentTimeMillis() ≥ 0L`** to guard a misconfigured device clock. Don't remove the coerce — the library throws `IAE` on negative timestamps.
- **`SafeLog.event(value)` defends `value.toString()`** via `stringifyDefensively()` — buggy caller `toString()` returns `"<toString-failed:...>"` instead of crashing the logging path.

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
| `infra-ci.yml` | `infra/**`, `.github/workflows/infra-ci.yml` | stub on ubuntu-latest | Story 1.6c |

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
| `1-3-oracle-vm-livekit-...` | Requires Oracle Cloud account + Cloudflare Registrar + `xaeryx.com` domain (~$10/yr) + SSH keypair | Bania completes manually |
| `1-4-firebase-init-...` (Android) | Phase 0 (manual Firebase console setup) per `docs/runbooks/firebase-setup-android.md` | **Prep PR landed 2026-05-23** (story file + runbook + `firebase/` rules + Kotlin stub). `ready-for-dev`. Activation PR waits on Bania's Phase 0. |
| `1-4b-ios-firebase-init` | Story 1.2 + 1.4 | Future iOS Claude session |
| `1-4c-play-store-internal-testing-android` | Story 1.4 + Story 1.6d | Wires CI → signed AAB → Play Console Internal Testing track → opt-in URL (QR-able). Gives the "scan code, install via Play Store" workflow. **Renamed 2026-05-24** from `firebase-app-distribution` — Internal Testing is cleaner for native Android + validates Play Integrity end-to-end. Bania has Play Console dev account confirmed. |
| `1-8` through `1-13` (pairing arc) | All gated on Story 1.4 (Firebase) | Cascading block — unblocks as soon as 1.4 lands |
| `1-6b-ios-ci-flesh-out` | Story 1.2 | iOS CI flesh-out |
| `1-6c-infra-ci-flesh-out` | Story 1.3 | infra CI flesh-out |
| `1-6d-android-ci-flesh-out` | Available now; not picked up yet | Compose UI tests + Roborazzi + assembleRelease + signing-config |

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

1. Either re-invoke the skill (interactive), OR hand-edit and bump the frontmatter `generated:` date.
2. Sections must stay LEAN — agents load this on every BMad skill activation via the `persistent_facts` glob `{project-root}/**/project-context.md`.
3. If a rule is in `architecture.md` and easy to find there, link don't duplicate.
4. New landmines (a future "Story 1.X Bug #N") MUST be added to §4 — that section earns its keep by preventing repeat incidents.
