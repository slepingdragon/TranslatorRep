# Structural Review — TranslatorRep PRD

## Overall structural verdict

The PRD is unusually well-architected for a personal-use app — clear linear flow from Vision → Personas → Glossary → Features → Quality → Constraints → Aesthetic → Platform → Non-Goals → Scope → Metrics → Open Questions → Assumptions. Section sizing is mostly proportionate to the stakes (translation quality and pipeline get the most ink, which is correct). The main structural issues are localized: a few redundancies between sections (Non-Goals vs. Out-of-Scope vs. Non-Users; FR-5 vs. FR-23; assumptions duplicated between §12 and §13), and one ordering wobble where §7 Aesthetic sits between hard requirements (§6 Constraints) and infra realities (§8 Platform) instead of clustering with content-level concerns. No critical structural defects; the document would survive downstream BMAD handoff as-is.

## Findings by severity

### Critical / High

- **High** — Triple-redundant scope-exclusion content (§2.3 Non-Users vs. §9 Non-Goals vs. §10.3 Out of Scope Entirely). Three sections answer overlapping versions of "who/what this isn't for": §2.3 excludes user types (third parties, other languages, video, store distribution); §9 restates the same exclusions in product-feature framing (not group, not video, not store, not chat); §10.3 again lists impossible integrations and analytics. A reader hits "no video" three times and "not for the Play Store" twice. *Fix:* consolidate to a single §9 Non-Goals (kept where it sits — it reads well there), reduce §2.3 to a one-line pointer ("see §9 Non-Goals"), and fold §10.3's two items into §9. Total cut: ~15 lines.

- **High** — FR-5 (Unpair) and FR-23 (Unpair via Settings) are functionally the same requirement split across two Features sections. FR-23 reads "Settings exposes the unpair action defined in FR-5" — a pure pointer that doesn't earn its FR number, but does inflate the FR index. *Fix:* delete FR-23, renumber subsequent FRs (FR-24 → FR-23), and add a one-line cross-reference under §4.5 Settings: "Unpair: see FR-5."

- **High** — §13 Assumptions Index duplicates content already inline-tagged `[ASSUMPTION]` in earlier sections. Each assumption now exists twice: once at the point of use, once in the appendix. This doubles maintenance load and risks drift on edit. *Fix:* either (a) keep inline `[ASSUMPTION]` tags and reduce §13 to a one-line pointer with grep instructions, or (b) strip the inline tags and centralize in §13. Recommend (a) — preserves locality of reading.

### Medium / Low

- **Medium** — §7 Aesthetic and Tone is positioned between §6 Constraints and §8 Platform, breaking the flow of "what must be true" → "what stack realizes it." Aesthetic is content/UX-shaped, not infra-shaped. *Fix:* swap §7 and §8 so Platform sits adjacent to Constraints (both are non-negotiable infra/policy), and Aesthetic sits adjacent to Features/UX concerns (move §7 to follow §4 or §5). Low-impact, improves reading flow.

- **Medium** — §5 Translation Quality Requirements is structurally orphaned: it asserts in its own preamble that it "doesn't fit neatly under any single Feature." It actually pairs tightly with FR-14 (the translation call) and the Captions FRs. Considered keeping as a sibling section, but its preservation list (§5.2) reads like detailed acceptance criteria for FR-14. *Fix:* leave §5 where it is (it earns its place — quality is load-bearing), but add explicit cross-refs from FR-14 → §5.2 and from §5.3 → SM-2 to bind the orphan back into the FR graph.

- **Medium** — §4.3 (Translation Pipeline) section has 6 FRs (FR-11 through FR-16) while §4.5 Settings has 4 (FR-21–FR-24) of which one is a pointer. Settings is appropriately small, but §4.3 lacks a closing "render-on-peer" FR — there is FR-16 (render on speaker's screen) but no symmetric "FR-X: Render Caption on listener's screen" requirement. The peer-render is mentioned in §4.3's prose but never given testable consequences. *Fix:* add FR-17 (peer-render) before the current FR-17 (caption history), renumber. Symmetric with FR-16 — easy fill.

- **Medium** — Cross-reference precision: FR-21 says "see FR-5" via the unpair pointer but §4.5's description doesn't reference §5 Translation Quality even though FR-22 (post-editor) directly affects translation pipeline quality. FR-22's "[NOTE FOR PM]" is structurally awkward — meta-commentary embedded mid-FR. *Fix:* move PM notes to a dedicated "Implementation Notes" subsection or a footnote block; keeps the FR list scannable.

- **Low** — Heading hierarchy is consistent (H1 title, H2 numbered sections, H3 subsections, H4 for FRs). One inconsistency: §2.1, §2.2, §2.3, §2.4 use H3, but §10.1, §10.2, §10.3 also use H3 — good. However §11's "Primary / Secondary / Qualitative win-condition / Counter-metrics" use H3 with non-numbered titles, breaking the numbered-subsection pattern used elsewhere. *Fix:* either number them (§11.1 Primary, §11.2 Secondary, §11.3 Qualitative, §11.4 Counter) or accept the inconsistency as intentional for the metrics section. Low-impact.

- **Low** — UJ-1, UJ-2, UJ-3 are defined in §2.4 but referenced extensively in FRs ("Realizes UJ-1"). Cross-refs all resolve correctly. SM-1 through SM-7 and SM-C1 through SM-C3 all defined in §11 — no broken refs. FR-1 through FR-24 all defined sequentially — no gaps, no broken refs. Cross-ref hygiene is clean.

- **Low** — List vs prose balance: §2.4 User Journeys correctly uses dense prose (narrative is the point), §4.x FRs correctly use bullet lists for testable consequences. §6 Constraints uses bullets where short paragraphs might read better for the privacy section — the bullet-of-prose pattern feels slightly compressed for the nuance involved. Low-impact, defensible either way.

- **Low** — §0 Document Purpose is appropriately short (10 lines) and earns its place by anchoring upstream refs. §3 Glossary is appropriately list-shaped. §12 Open Questions and §13 Assumptions Index are both appropriately concise.

- **Low** — Missing section candidate: no explicit "Dependencies" section calling out upstream artifacts beyond §0's mentions (DR, TR, Brief). The DR reflow prompt and the Cloud Run repo are both referenced but never enumerated as v1 dependencies. *Fix:* optional — for a 2-user personal app this is gold-plating. Skip unless downstream BMAD workflows complain.

- **Low** — Document length (~488 lines) is appropriate for the stakes. Translation Pipeline (§4.3) and Translation Quality (§5) earn their disproportionate share; Settings (§4.5) and Platform (§8) are appropriately terse. No section is bloated relative to its contribution. No section is anemic relative to its importance.
