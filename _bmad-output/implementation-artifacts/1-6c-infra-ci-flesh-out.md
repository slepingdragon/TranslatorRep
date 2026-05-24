# Story 1.6c: Infra CI Flesh-Out — yamllint + shellcheck + compose/caddy validate + auth-proxy test/build

Status: review

<!-- Created 2026-05-24 (autonomous session, post-PR-#18/#19 merge).
     Spun out of Story 1.6 as the deferred infra-CI follow-up (sibling of 1-6b/1-6d).
     SCOPE NOTE 2026-05-24: the architecture spec (row 3) prescribed a third step —
     tag-triggered `ssh + deploy.sh` on the Oracle VM. That step is DEFERRED here
     because (a) Story 1.3 Phase 2 hasn't run (no VM to deploy to) and (b) Bania
     deprioritized Oracle pending the Oracle-vs-LiveKit-Cloud hosting decision. The
     lint/test/build value lands now; the deploy job waits for that decision. -->

## Story

As a solo developer with real `infra/` content now in the repo (LiveKit + Caddy + docker-compose deploy stack, the auth-proxy TypeScript service + its 23-test vitest suite, and the Oracle capacity-poll script),
I want `infra-ci.yml` to actually lint, type-check, test, and build that content on every infra PR,
so that a regression in the auth-proxy (or a malformed compose/Caddy/LiveKit config, or a CRLF-broken shell script) is caught in CI instead of at deploy time — closing the gap where the auth-proxy's 23 tests were green locally but **no CI ran them**.

## Acceptance Criteria

**Given** Story 1.6 (CI per stack) is `done` (the `infra-ci.yml` stub exists and passes) and Story 1.3 Phases 1a+1b landed real `infra/` content,
**When** this story lands,
**Then:**

1. **AC-1 (config-lint job):** `.github/workflows/infra-ci.yml` has a `config-lint` job that runs, in order, all passing: `yamllint` (on `infra/livekit.yaml` + `infra/docker-compose.yml`, via `infra/.yamllint.yml`), `shellcheck` (on `infra/deploy.sh` + `infra/scripts/*.sh`), `docker compose config -q` (schema/interpolation validation, no `up`), and `caddy validate` (Caddyfile, via the official `caddy:2-alpine` image).
2. **AC-2 (auth-proxy job):** an `auth-proxy` job on Node 22 (matching `package.json` engines + the Dockerfile build stage) runs `npm ci → npm run typecheck → npm test → npm run build`, all green — the 23-test vitest suite now runs in CI.
3. **AC-3 (auth-proxy-docker job):** an `auth-proxy-docker` job runs `docker build` on the multi-stage distroless `infra/auth-proxy/Dockerfile` (no push), proving the image builds — catches Dockerfile / `npm prune` / distroless-copy breakage the source-level test job can't.
4. **AC-4 (shell scripts pinned LF):** `.gitattributes` pins `*.sh` (and the auth-proxy `Dockerfile`) to `eol=lf` so a Windows checkout (`core.autocrlf=true`) can't re-introduce CRLF that would break `bash`/`shellcheck` (SC1017) on Linux runners + the VM. Committed bytes are already LF; this is the working-tree guard.
5. **AC-5 (Oracle deploy step deferred, documented):** the tag-triggered `ssh + deploy.sh` step (architecture row 3 step 3) is NOT wired; the workflow header + `ci-stack-overview.md` §5 document why (no VM yet + open Oracle-vs-LiveKit-Cloud decision) and what to add when it resolves.
6. **AC-6 (path-filtered + self-validating):** the workflow triggers only on `infra/**` + its own path (no wasted minutes on Android/iOS PRs); editing the workflow itself triggers it (self-validation). Existing `android-ci.yml` / `ios-ci.yml` are unchanged.

**Done criteria:** Story 1.6c flips to `review` when all 6 ACs ✅ and the workflow runs green on its own PR → CR pass → `done`.

## Tasks / Subtasks

### Phase 1 — Workflow

- [x] **1.1** Replace the `infra-ci-stub` job in `.github/workflows/infra-ci.yml` with three parallel jobs: `config-lint`, `auth-proxy`, `auth-proxy-docker`. Keep the existing `on:` path filters, `concurrency`, and `workflow_dispatch`.
- [x] **1.2** `config-lint`: yamllint (python `-m`, PATH-safe) → shellcheck (`--severity=warning`, pre-installed on the runner) → `docker compose config -q` → `caddy validate` via `caddy:2-alpine`.
- [x] **1.3** `auth-proxy`: `actions/setup-node@v4` (node 22 + npm cache keyed on the lockfile) → `npm ci` → typecheck → test → build, with `working-directory: infra/auth-proxy`.
- [x] **1.4** `auth-proxy-docker`: `docker build` the image, no push.
- [x] **1.5** Document the deferred tag-deploy step in the workflow header (no VM + open hosting decision).

### Phase 2 — Supporting config

- [x] **2.1** Add `infra/.yamllint.yml` (extends `relaxed`; line-length max 120 warning; new-lines unix).
- [x] **2.2** Extend `.gitattributes`: `*.sh text eol=lf` + `infra/auth-proxy/Dockerfile text eol=lf`.
- [x] **2.3** Fix the stale auth-proxy TODO comment in `infra/docker-compose.yml` (the service is implemented, not a TODO).

### Phase 3 — Docs + tracking

- [x] **3.1** Update `docs/runbooks/ci-stack-overview.md`: move infra-ci from "Stub" to "Full" in §1; mark §5's 1.6c checklist done + record the deploy-step deferral.
- [x] **3.2** Update `sprint-status.yaml`: 1-6c `backlog` → `review`; bump `last_updated`.
- [x] **3.3** Update `docs/project-context.md` §10 — 1-6c row.

## Dev Notes

### Why this is worth doing even with Oracle deprioritized

The auth-proxy mints LiveKit JWTs after verifying Firebase Auth + App Check. That responsibility exists **regardless** of whether the SFU is self-hosted on Oracle or rented from LiveKit Cloud — the proxy is containerized and run somewhere either way. So its CI (typecheck + the 23-test suite + the distroless image build) is not Oracle-coupled and is not wasted if the hosting decision flips to LiveKit Cloud. Only the *deploy* step (ssh to the Oracle VM) is Oracle-coupled, and that's the one step deferred.

The concrete gap closed: PR #19 landed the auth-proxy with 23 green vitest tests, but `infra-ci.yml` was a stub that echoed a message. Any later edit to the proxy could break tests and CI would stay green. This story makes CI honest.

### Why `--severity=warning` on shellcheck

`deploy.sh` triggers three SC2029 (info) findings — "unescaped, this expands on the client side." That expansion is **intentional**: `deploy.sh` constructs a command string locally (`$COMPOSE_CMD`, `$REMOTE_DIR`) and passes it to `ssh` to run on the VM, so client-side expansion is the desired behavior. `--severity=warning` filters info-level noise while still failing on real warnings/errors. `oracle-capacity-loop.sh` is clean at all severities.

### Why `caddy validate` runs via Docker

There's no `caddy` binary on the ubuntu runner. The official `caddy:2-alpine` image provides it. `validate --adapter caddyfile` parses the Caddyfile, adapts it to JSON, and provisions modules to check structural validity. It does **not** perform ACME or reach the network, so it's safe and deterministic in CI. (`docker compose config` similarly validates without `up`.)

### Why pin `*.sh` to LF in `.gitattributes`

The repo index already stores LF for the shell scripts (verified via `git ls-files --eol`). But Bania's machine has `core.autocrlf=true`, so the **working tree** holds CRLF — local `shellcheck` flagged SC1017 (literal carriage return) on every line. CI is unaffected (Linux checkout = LF), but pinning `*.sh eol=lf` keeps any Windows checkout from materializing CRLF, so local linting matches CI and the deploy/poll scripts stay POSIX-runnable. This extends the same belt-and-suspenders pattern Story 1.6 used for `android/gradlew`.

### Job topology

Three independent jobs (no `needs:`) run in parallel for fast wall-clock feedback and isolated failure surfaces: a YAML typo fails `config-lint` without obscuring an auth-proxy test failure. `auth-proxy` (source tests, ~1 min) is separate from `auth-proxy-docker` (image build, ~2-3 min) so a quick test failure surfaces before the slower build finishes.

### Library + tooling references

- [yamllint configuration](https://yamllint.readthedocs.io/en/stable/configuration.html) — `extends: relaxed`, `line-length`, `new-lines`.
- [ShellCheck severity levels](https://github.com/koalaman/shellcheck/wiki/Optionsseverity) — `error > warning > info > style`.
- [`docker compose config`](https://docs.docker.com/reference/cli/docker/compose/config/) — render + validate without running.
- [`caddy validate`](https://caddyserver.com/docs/command-line#caddy-validate) — config validation, no side effects.
- [actions/setup-node caching](https://github.com/actions/setup-node#caching-global-packages-data) — `cache: npm` + `cache-dependency-path`.

### Source-tree placement

```
.github/workflows/
└── infra-ci.yml                 # MODIFIED: stub → config-lint + auth-proxy + auth-proxy-docker
infra/
├── .yamllint.yml                # NEW: yamllint config for the deploy-stack YAML
└── docker-compose.yml           # MODIFIED: stale auth-proxy TODO comment → implemented note
.gitattributes                   # MODIFIED: *.sh + Dockerfile pinned eol=lf
```

### Testing standards

- **CI is the test.** The workflow self-validates on this PR (its own path is in the trigger filter). Green = AC-1..AC-3 + AC-6 satisfied.
- **Local pre-validation performed:** auth-proxy `npm ci + typecheck + test (23 green) + build` on Node 24; `shellcheck` 0.11.0 on LF copies (oracle script clean, deploy.sh only SC2029 info); `yamllint` 1.38.0 with `infra/.yamllint.yml` on LF copies (exit 0). `docker compose config` / `caddy validate` / `docker build` could not be pre-run locally (no Docker on the Windows dev box) — validated on the CI runner.

### References

- [Story 1.6](./1-6-cicd-per-stack.md) — parent CI story (stub created here).
- [Story 1.6d](./1-6d-android-ci-flesh-out.md) — sibling Android CI flesh-out (pattern mirror).
- [Story 1.3](./1-3-oracle-vm-livekit-docker-compose-stack-domain.md) — landed the `infra/` content this CI exercises.
- [CI Stack Overview runbook](../../docs/runbooks/ci-stack-overview.md) — §1 current-state table + §5 deferred-work.
- [architecture.md §"CI/CD Per Stack"](../planning-artifacts/architecture.md) — row 3 source of truth.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-PR-#18/#19 merge)

### Debug Log References

- auth-proxy local validate (Node 24): `npm ci` clean → `npm run typecheck` clean → `vitest run` 4 files / 23 tests passed → `tsc` build clean. CI pins Node 22 (engines target).
- shellcheck 0.11.0 (via `shellcheck-py`) on LF-normalized copies: `oracle-capacity-loop.sh` exit 0; `deploy.sh` 3× SC2029 (info only) → filtered by `--severity=warning`. On the raw working-tree files shellcheck reported SC1017 (CRLF) — a local autocrlf artifact, not present on the LF index / CI checkout; addressed defensively by AC-4.
- yamllint 1.38.0 with `infra/.yamllint.yml` on LF copies of `livekit.yaml` + `docker-compose.yml`: exit 0 (the `relaxed`-preset `new-lines` error seen on raw CRLF files vanishes on LF, confirming the CI checkout passes).

### Completion Notes List

- Replaced the single-echo `infra-ci-stub` with three parallel jobs. No `needs:` between them — parallel for fast feedback + isolated failure surfaces.
- yamllint invoked as `python -m yamllint` (python pre-installed on the runner) to dodge the `pip install` user-bin-PATH trap; `shellcheck` is pre-installed on `ubuntu-latest` so it's called directly.
- Oracle SSH-deploy step intentionally NOT wired (AC-5). Documented in the workflow header + runbook §5 with the exact gating condition (`refs/tags/infra-v*` + `ORACLE_VM_SSH_KEY` secret) to add when the hosting decision resolves.
- Fixed a stale comment in `docker-compose.yml` that still called the auth-proxy a TODO needing implementation — it shipped in PR #19.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-6c-infra-ci-flesh-out.md` — this file
- `infra/.yamllint.yml` — yamllint config for deploy-stack YAML

**Modified:**
- `.github/workflows/infra-ci.yml` — stub job replaced with config-lint + auth-proxy + auth-proxy-docker
- `.gitattributes` — `*.sh` + `infra/auth-proxy/Dockerfile` pinned `eol=lf`
- `infra/docker-compose.yml` — stale auth-proxy TODO comment → "implemented" note
- `docs/runbooks/ci-stack-overview.md` — §1 table infra-ci → Full; §5 1.6c checklist done + deploy deferral
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-6c backlog → review; last_updated bump
- `docs/project-context.md` §10 — 1-6c row updated

### Change Log

- 2026-05-24 — Story 1.6c file created (`review`). Fleshed out `infra-ci.yml` from stub to real lint/test/build across the LiveKit/Caddy/compose configs + the auth-proxy TS service + its Docker image. Tag-triggered Oracle SSH-deploy step deferred pending Story 1.3 Phase 2 + the Oracle-vs-LiveKit-Cloud hosting decision.
