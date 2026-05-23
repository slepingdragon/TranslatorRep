# Solo-Dev Scope-Cut Runbook

> **Purpose:** Pre-thought scope-cut decisions for the 7-10-week TranslatorRep v1 build, so morale-collapse triggers during the build are reactive against a plan rather than improvised under stress.
>
> **Authority:** Architecture Gap I.17 ("Solo-dev scope-cut criteria") + Gap I.19 ("Single-failure-domain risk acceptance documentation"). Story 1.14c (Implementation Readiness 2026-05-22).
> **Audience:** Bania (solo dev + product owner).
> **Updated:** 2026-05-22

---

## How to Use This Runbook

When morale dips, deadlines slip, or a Week-N trigger fires:

1. **Don't decide under stress.** Re-read this document.
2. **Match the situation to a trigger.** If a trigger fires, follow the pre-decided cut.
3. **Document the actual cut.** Append to the "Actual cuts taken" log at the bottom of this file so the v1 retrospective (epic-completion Retrospective story) has the evidence.

The pre-decided cuts here are *defaults*, not contracts. If the situation looks different from the trigger description, override the default — but write down *why* in the log.

---

## Week-by-Week Cut Triggers (Defaults)

### Week 1 — Translation-model bake-off (Story 3.9 Gate)

**Trigger:** All Plan A candidates (NLLB-200 → MADLAD-400 → Gemma 2B) fail the kill criteria.

**Pre-decided cut:** Activate **Plan B** (Vertex AI Gemini 2.5 Flash in `asia-southeast1`).
- Cost: +~$5/month at 2-user / ~30-min/day scale; Cloud Billing budget alerts already at $1/$5/$10/$50.
- Privacy posture: downgrades from "translation never leaves device" to "Vertex AI Gemini sees translation text but does not train on it." Documented in PRD §6.1 Plan-B note.
- Quality threshold: SM-2 reverts to ≥80% (vs Plan A's ≥60%) — higher bar but cloud LLM should clear it.
- Re-approval required from Bania before Plan B activates.

**Alternative cut (if Plan B is rejected):** Defer captions entirely to v1.1, ship Audio Calling alone (Epics 1+2+5+6+7 only — drop Epics 3, 4, 8 partially). Last-resort; this loses the whole point of v1.

### Week 3 — Reality check on translation latency

**Trigger:** Real-conversation testing reveals median Utterance-to-Caption latency is felt as "too slow" by both Bania and girlfriend across multiple sessions, even though SM-4 (median <8s) is technically met.

**Pre-decided cut:** Activate **Plan B** for translation (cloud Gemini latency typically 1-2s round-trip vs on-device 5-8s).
- Same cost / re-approval / privacy-downgrade trade as Week 1 trigger.

**Alternative cut:** Activate **cloud STT** (Google STT streaming) for one or both sides. Cost ~$15/month per platform. Re-approval required.

### Week 5 — Personalization scope check

**Trigger:** Epics 1-4 + 5 + 6 + 7 are landed but Week 5 is closing and Epic 8 has 10+ stories remaining (theming, post-Call surfaces, settings).

**Pre-decided cut:** **Defer `QualityReviewRow` (Story 8.9) and `HerSideOneTapReaction` (Story 8.10) to v1.1.**
- Rationale: SM-2 acceptance can still happen via Story 8.11 TQ-AT ship gate using a quick-and-dirty paired review session (10 sample Calls reviewed together with a notes file, no `QualityReviewRow` UI required). The proper in-app rating UI is a polish item, not a ship-blocker.
- Story 8.11 (TQ-AT Ship Gate) STAYS — it's the v1 ship gate.

**Alternative cut:** Defer Theme C (`BackgroundImagePicker`, `BackgroundImageOverlay`, Theme C × Video interaction — Stories 8.1-8.4) to v1.1, ship with Dark theme only.

### Week 7 — Theme C late slippage

**Trigger:** Week 7 arrives, audio + bidirectional captions + E2EE + video + leave-and-rejoin all working, but Theme C component work has not started and Phase 5 polish is squeezed.

**Pre-decided cut:** **Ship v1 with Dark theme only; defer Theme C entirely to v1.1.**
- Rationale: UX explicitly anticipated this — see UX spec §"Implementation Roadmap > Phase-5 timing risk" (post-reconciliation note). Theme C is bonus polish, not core experience.
- ThemePicker (Story 8.1) simply doesn't appear in Settings v1; re-appears in v1.1 as a 2-option picker.

### Week 9 — Hard ship-gate decision

**Trigger:** Story 8.11 TQ-AT runs. Result is FAIL on Plan A (under ≥60%) AND Plan B re-run also fails (under ≥80%).

**Pre-decided cut:** **Iterate the on-device `ParticleProcessor` rules.** Most TQ failures historically trace to rule gaps rather than model failures — DR §1 might have particles or registers not yet in the rule table. Schedule a 3-day iteration: review the failing utterances, identify rule gaps, extend `ParticleRules.kt` / `ParticleRules.swift`, re-run regression corpus + TQ-AT. THEN ship if pass.

**Alternative cut (if iteration also fails):** Soft-launch v1 to Bania + girlfriend ONLY (which is the v1 audience anyway), defer "ship v1 to App/Play Stores" decision (which was never v1 anyway per PRD §9). Continue iterating in production-of-two.

---

## Single-Failure-Domain Accepted Risk (Gap I.19)

**Fact:** The Oracle Ampere A1 VM hosts all of: `livekit-server`, `redis`, `caddy`, `auth-proxy`. If the VM dies, the entire backend dies.

**Why this is accepted for v1:**

1. **Personal-use scale.** Two users, ~30-min/day. The blast radius of a backend outage is "Bania and his girlfriend cannot place a translated call until the VM is back up" — they fall back to WhatsApp (untranslated, exactly what they're using today). Not a business-impacting failure.
2. **Oracle Always-Free tier SLO.** The Always-Free tier is best-effort; no SLA. But empirically, Ampere A1 instances are stable for months at a time. Failure mode is more likely "instance reclaimed for non-use" than "instance crashed mid-call."
3. **Mitigations in place:**
   - Caddy auto-renews Let's Encrypt; no manual cert rotation needed after first deploy.
   - LiveKit + Redis run as Docker services; `docker compose up -d` brings them back.
   - `/infra/scripts/provision-oracle-vm.sh` + `/infra/scripts/deploy.sh` are idempotent (Story 1.3 AC). A fresh VM is ~30 min from "git clone" to "running."
   - Backend code + configs live in git; nothing valuable is local-only to the VM.
4. **What goes wrong if the VM dies in the middle of a call:** the LiveKit room destroys; both apps observe `participantDisconnected` and `RoomState.ended`; users return to Paired home with "Call ended unexpectedly." Captions captured during the call are lost (in-flight) unless transcript history was on (FR-21), in which case the locally-persisted Captions remain on each device.

**Future mitigation (v2 candidate, NOT v1):** Two-VM HA with shared Cloudflare DNS round-robin. Cost: $0 (second VM also Always-Free). Effort: 1-2 days. Defer unless real outages happen.

---

## Cuts NOT Available (Architectural Lock-Ins)

The following are NOT scope-cuttable without a full architecture re-think:

- **NFR-Privacy:** WhatsApp-equivalent E2EE via Insertable Streams. Cannot defer to v1.1 because cutting it after rolling out v1 with the privacy promise is a trust-breaker. If E2EE work slips, the cut is "defer v1 ship by N days" not "ship v1 without E2EE."
- **NFR-Provider-Abstraction:** `AsrProvider` + `TranslationProvider` interfaces. Cannot cut because v2 Sundanese support and Plan B activation both depend on it as a config-swap rather than a rewrite.
- **NFR-Reliability silent-drop ban:** Translation failures MUST surface as `TranslationUnavailableMarker`. Cannot cut to "just drop failed captions" because it would erode the trust signal SM-7 measures.
- **`SafeLog` facade + lint enforcement:** Privacy-safe observability. Cannot cut because the consequence is an accidental conversation-content log that violates §6.1.

---

## Actual cuts taken (log — append-only)

| Date | Trigger | Cut taken | Rationale | Restored in v1.1? |
|---|---|---|---|---|
| (none yet) | | | | |

---

## Cross-references

- PRD §10.3 Timeline (revised 7-10 weeks + 1 ramp).
- Architecture Gap I.17 (origin of this runbook).
- Architecture Gap I.19 (single-failure-domain risk acceptance).
- Story 1.14c (the implementation-readiness story that created this runbook).
- Story 3.9 (Week-1 validation gate — primary upstream trigger).
- Story 8.11 (TQ-AT v1 ship gate — primary downstream trigger).
- UX spec §"Implementation Roadmap > Phase-5 timing risk" (Theme C deferral).
