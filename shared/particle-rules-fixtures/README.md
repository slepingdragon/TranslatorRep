# ParticleProcessor Golden-File Fixtures

> **Purpose:** Cross-platform fixture set for the `ParticleProcessor` module (Architecture Pattern §11, Story 3.2). Both Android and iOS test suites load fixtures from THIS directory and assert identical outputs. CI blocks merge on any fixture-test failure.
>
> **Authority:** Architecture §B (Translation Stack) + DR §1 (particles), §3 (Gen-Z slang), §4 (Sundanese insertions), §6 (cultural-pragmatic).
> **Updated:** 2026-05-22 (Story 1.7 scaffolding)

---

## Directory Layout

```
particle-rules-fixtures/
├── README.md                        ← this file
├── particles/                       ← TQ-1 Indonesian discourse particles
│   ├── loh/
│   │   ├── case_001/
│   │   │   ├── source.txt
│   │   │   ├── expected_processed.txt
│   │   │   ├── expected_target.txt
│   │   │   └── metadata.json
│   │   └── …
│   ├── kan/  ...  sih/  ...  dong/  ...  (14 particles total)
├── slang/                           ← TQ-8 Gen-Z 2026 slang dictionary
├── sundanese/                       ← TQ-4 Sundanese lexical insertions
├── honorifics/                      ← TQ-5 partner honorifics
├── religious/                       ← TQ-6 religious expressions
└── refusals/                        ← TQ-7 indirect refusals
```

Each rule category has subdirectories per individual rule (e.g., `particles/loh/`); each rule has ≥3 fixture cases (`case_001/`, `case_002/`, ...) per Story 3.2 acceptance criteria.

---

## Fixture File Contract

Each `case_NNN/` directory contains exactly four files:

| File | Content |
|---|---|
| `source.txt` | The raw input text the speaker said. UTF-8, no trailing newline. |
| `expected_processed.txt` | The text after `ParticleProcessor.preProcess()` — what gets handed to the NMT model. May be identical to `source.txt` if the rule operates only post-processing. |
| `expected_target.txt` | The final text after `ParticleProcessor.postProcess(rawTarget, ...)` returns. This is what the partner sees on screen. |
| `metadata.json` | Structured metadata; see schema below. |

### `metadata.json` schema

```json
{
  "source_lang": "id-ID",
  "target_lang": "en-US",
  "expected_particles": ["loh"],
  "expected_render_mode": "default",
  "expected_translation_status": "ok",
  "tags": ["TQ-1", "emotional-weight", "intimacy-register"],
  "dr_section_ref": "DR §1",
  "notes": "Final-particle 'loh' adds gentle insistence; English equivalent 'you know' must preserve the intimate register."
}
```

| Field | Required | Notes |
|---|---|---|
| `source_lang` | yes | BCP 47 (`id-ID`, `en-US`, `su-ID`). |
| `target_lang` | yes | BCP 47. |
| `expected_particles` | yes | List of particle tokens that `TranslationResult.particlesPreserved` MUST contain after post-processing. Order-insensitive. |
| `expected_render_mode` | yes | `"default"` or `"sundanesePlaceholder"`. |
| `expected_translation_status` | yes | `"ok"`, `"failed"`, or `"low-confidence"`. |
| `tags` | recommended | TQ-category tags (`TQ-1` through `TQ-8`) + free-form descriptive tags. Used for regression-corpus filtering. |
| `dr_section_ref` | recommended | Link back to the Domain Research section that justifies this fixture. |
| `notes` | optional | Human-readable rationale, especially for ambiguous cases. |

---

## Cross-Platform Test Contract

Both Android (`androidTest/` or `unitTest/`) and iOS (`UITests/` or `Tests/`) test suites:

1. Load every fixture in this directory.
2. Run the input through their respective `ParticleProcessor` implementation:
   - `preProcess(source, source_lang, target_lang)` → assert output matches `expected_processed.txt`.
   - Skip the model call in fixture tests (use a `FakeTranslationProvider` that returns a known string — verifies the post-processor's behavior in isolation from the model).
   - `postProcess(rawTarget, originalSource, source_lang, target_lang)` → assert output matches `expected_target.txt` AND `result.particlesPreserved` contains every `expected_particles` token.
3. Cross-platform parity test: identical fixture set → identical outputs. CI blocks merge on any divergence.

---

## Starter Fixture: `particles/loh/case_001/`

A single starter fixture is provided to validate the test harness end-to-end before Story 3.2 populates the full set. See files in `./particles/loh/case_001/`.

---

## Adding a New Fixture

1. Identify which TQ-category and rule the fixture exercises.
2. Create `<category>/<rule>/case_NNN/` with the four required files.
3. Run the cross-platform fixture test locally on both Android and iOS before opening a PR.
4. CR agent verifies: `metadata.json` is valid against the implied schema above; `expected_target.txt` is plausible per DR reference; fixture passes on both platforms.

## Anti-Patterns

- Don't put fixtures that depend on the model's actual output (the harness uses a fake provider to isolate the post-processor).
- Don't add Sundanese full-clause fixtures here — those go under `sundanese/` with `expected_render_mode: "sundanesePlaceholder"`, not as particle-preservation tests.
- Don't reference fixture cases by file path from production code. Fixtures are test-only.
