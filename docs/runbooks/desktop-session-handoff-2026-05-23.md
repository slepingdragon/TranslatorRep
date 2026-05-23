# Desktop Session Handoff — 2026-05-23

Paste the block below as your **first message** to Claude Code after pulling
the latest `main` on your desktop. It primes Claude with `bmad-help` and a
status briefing so the session orients itself without trial-and-error.

After pulling, also run `cd android && .\gradlew.bat :app:detekt :app:testDebugUnitTest`
once to warm the Gradle cache on desktop — the Story 1.5 build needs detekt 1.23.7
and `com.aallam.ulid:ulid-kotlin:1.3.0` which the desktop hasn't downloaded yet.

---

## Copy-paste prompt

```
bmad-help

Where we are (Story 1.5 just hit `review`):

Epic 1 — Foundation & Pairing — in-progress:
- ✅ Done: 1.1 (Android scaffold, verified on S24 Ultra), 1.7 (shared specs),
  1.14a/b/c (PRD + UX + scope-cuts reconciliation)
- 🔍 In review: 1.5 (SafeLog facade + detekt/SwiftLint enforcement + ULID lib)
  — fully implemented + 7/7 tests pass + detekt clean + lint smoke-test fires
  on synthetic violation. Committed + pushed.
- ⏸ Blocked on external inputs (cannot do autonomously):
  - 1.2 (iOS scaffold) — sequenced on Mac, see docs/runbooks/ios-setup-on-mac.md
  - 1.3 (Oracle VM + LiveKit + domain) — needs Oracle Cloud + Cloudflare accounts
  - 1.4 (Firebase Init + Firestore rules + App Check) — needs Google + Firebase project
- 📋 Next on Windows after 1.5 approved: 1.6 (CI/CD per stack)

Authoritative files to read first:
- _bmad-output/implementation-artifacts/sprint-status.yaml — current sprint state
- _bmad-output/implementation-artifacts/1-5-safelog-lint-ulid.md — the story
  currently in review, with full Dev Agent Record + File List + Change Log
- _bmad-output/planning-artifacts/epics.md — all epic and story definitions
- shared/canonical-names.md — cross-platform contracts (recently updated with
  Story 1.5's locked ULID test vector: 01KS7ZDFMA041061050R3GG28A for
  timestamp_ms=1779458031242 + random=0102030405060708090A)

Recommended next move: run `bmad-code-review` against the current branch
(in a fresh context, ideally a different LLM than the one that wrote Story 1.5
per BMAD convention). If approved → auto-marks Story 1.5 done. If issues →
adds [AI-Review] follow-up tasks and cycles back to bmad-dev-story.

What I want you to do: run bmad-help first to confirm you're oriented, then
launch bmad-code-review on Story 1.5.
```

---

## Why this format

`bmad-help` is a meta-skill that reads `_bmad/_config/bmad-help.csv` and the
current sprint-status to figure out what phase Claude is in and what the
recommended next skill is. Starting with it means Claude reads the catalog +
sprint state before doing anything else — no risk of duplicating work or
running the wrong skill.

The status briefing in the prompt is a defensive backstop in case Claude
misreads the sprint-status file. If memory or the CSV change, the briefing
still gives Claude the right ground truth.
