# Translation Provider Regression Corpus

> **Purpose:** A tagged corpus of ~200 utterances used for the Week-1 translation-model bake-off harness (Story 3.7) and for re-validation on every model swap or `ParticleProcessor` rule change. Distinct from `../particle-rules-fixtures/`: those fixtures isolate post-processor behavior; this corpus exercises **end-to-end translation provider quality** (NMT + ParticleProcessor combined).
>
> **Authority:** Architecture ADR-B1 (model bake-off process), Architecture Gap I.14 (regression corpus requirement), DR §1/§3/§4/§6 (source linguistic categories).
> **Updated:** 2026-05-22 (Story 1.7 scaffolding — corpus contents to be populated in Story 3.7)

---

## Corpus Status

- **Target size:** ~200 utterances at first population (Story 3.7).
- **Current status:** SCAFFOLDED ONLY. The directory exists with this README; corpus utterances will be added in Story 3.7 ("Regression Corpus + Bake-Off Harness").
- **Balance target:** roughly 50/50 ID→EN and EN→ID directions.

---

## Corpus File Format

The corpus is a single file (`corpus.jsonl`) — JSON Lines (one JSON object per line) — for streaming-friendliness. Each line:

```json
{"id": "01HKZN...", "source_lang": "id-ID", "source_text": "Aku kangen kamu loh", "target_lang": "en-US", "expected_target_text": "I miss you, you know", "tags": ["particle:loh", "register:aku", "intimacy", "TQ-1"], "expected_particles": ["loh"], "dr_section_ref": "DR §1"}
```

| Field | Notes |
|---|---|
| `id` | ULID; stable identifier for tracking across bake-off runs. |
| `source_lang` | BCP 47. |
| `source_text` | The utterance to feed to the `TranslationProvider`. |
| `target_lang` | BCP 47. |
| `expected_target_text` | The ideal translation (used for fuzzy-score / exact-match metrics). Acknowledged that NMT outputs vary — `expected_target_text` is one acceptable target; the bake-off ratings allow human review for "accurate-but-different-wording" cases. |
| `tags` | TQ-category tags (`TQ-1` through `TQ-8`) + thematic tags (`register:aku`, `intimacy`, `particle:loh`, `su:urang`, etc.). |
| `expected_particles` | List of particle tokens that `TranslationResult.particlesPreserved` should contain. |
| `dr_section_ref` | Provenance — which DR section justifies the example. |

---

## Tagging Conventions

| Tag prefix | Meaning |
|---|---|
| `TQ-1` … `TQ-8` | TranslationQuality preservation target this utterance exercises. |
| `particle:<name>` | Specific particle being tested (`particle:loh`, `particle:kan`, …). |
| `register:<word>` | Pronoun register being tested (`register:aku`, `register:saya`, `register:gue`). |
| `su:<word>` | Sundanese insertion being tested (`su:urang`, `su:atuh`, `su:mah`). |
| `gender:dia` | Gender-neutrality cases (TQ-3). |
| `religious:<phrase>` | Religious expression preservation (TQ-6). |
| `honorific:<term>` | Honorific stripping (TQ-5). |
| `refusal:indirect` | Indirect refusal (TQ-7). |
| `slang:<term>` | Gen-Z slang mapping (TQ-8). |
| `intimacy` | High emotional-load examples (relationship vulnerability, etc.). |
| `multi-particle` | Two or more particles in one Utterance — stress test. |

A single utterance often carries 3–5 tags. Filtering the corpus by tag enables per-TQ-category pass-rate computation.

---

## Bake-Off Harness Output

The bake-off harness (Story 3.7) runs a candidate `TranslationProvider` against the corpus and produces a results CSV at `/docs/runbooks/week-1-validation-results-<provider>.csv`:

| Column | Notes |
|---|---|
| `utterance_id` | Corpus ULID. |
| `model_run_id` | ULID generated per bake-off run; ties all rows in one CSV together. |
| `provider_name` | e.g., `nllb-200-distilled-600m-q4`. |
| `raw_target` | NMT output before `ParticleProcessor.postProcess`. |
| `post_processed_target` | After post-processing. |
| `expected_target` | From corpus. |
| `particles_preserved` | Comma-separated list. |
| `expected_particles` | Comma-separated list from corpus. |
| `exact_match` | Boolean: `post_processed_target == expected_target`. |
| `fuzzy_score` | 0.0–1.0 string similarity (Levenshtein or BLEU — pick one in Story 3.7). |
| `particle_preservation_rate` | Fraction of `expected_particles` actually preserved. |
| `latency_ms` | Translation call duration. |
| `qa_rating` | OPTIONAL human ✅/⚠️/❌ post-pass, populated after Bania + girlfriend review. |

---

## Kill Criteria (set in Story 3.7 / Story 3.9)

The Week-1 validation gate (Story 3.9) uses bake-off results against pre-defined kill criteria documented in `/docs/runbooks/week-1-validation.md`. Example criteria shape:

- If NLLB-200 fuzzy-score median < 0.55 OR particle-preservation rate on TQ-1 cases < 0.50, fall to MADLAD-400.
- If MADLAD-400 fails the same criteria, fall to Gemma 2B.
- If all Plan A candidates fail, activate Plan B (Vertex AI Gemini per Story 3.9 Outcome C).

Final thresholds are set during Story 3.1 (pre-validation conversation with girlfriend) — see runbook.

---

## CI Integration

The harness runs on every PR touching `translation/` paths on either platform (architecture §"CI/CD per stack" + Story 1.6). A regression beyond a tolerance threshold blocks merge.

---

## Adding to the Corpus

1. Identify which TQ category / linguistic phenomenon the new utterance exercises.
2. Generate a ULID for the `id` field.
3. Add a single line to `corpus.jsonl` with all required fields.
4. Run the bake-off harness locally and verify the existing locked-Plan-A model still produces acceptable output.
5. CR agent verifies: valid JSON, BCP 47 codes, plausible `expected_target_text`, appropriate tags.

## Anti-Patterns

- Don't add multi-sentence utterances — Utterances are VAD-bounded single units per FR-12.
- Don't add utterances that the v1 system cannot reasonably handle (e.g., long Sundanese clauses, mixed-language code-switching beyond TQ-4 lexical insertions). Those are v2 cases.
- Don't tag mechanically — tags drive per-category metrics, so accurate tagging matters for SM-2 evidence.
