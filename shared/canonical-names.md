# Canonical Concept Names & Term Forms

> **Authority:** This file mirrors [architecture.md §1 "Canonical Concept Names & Term Forms Table"](../_bmad-output/planning-artifacts/architecture.md#1-canonical-concept-names--term-forms-table) and §3 "Naming Conventions" + §4 "ID Format". The Architecture document is the source-of-truth. This file exists to give Android (Kotlin) and iOS (Swift) implementations a single shared reference to grep against — without requiring agents to load the full architecture document.
>
> **Updated:** 2026-05-22 (Story 1.7)
> **Versioning policy:** Any name change requires an ADR amendment in `architecture.md`, simultaneous Kotlin + Swift PRs, and an update to this file. The Code-Review agent rejects single-platform renames.

---

## 1. Canonical Glossary

| Prose form | Identifier form (code) | JSON / wire form | Forbidden synonyms |
|---|---|---|---|
| Pair | `Pair` (entity), `pair()` (action verb) | `pairId` | PairedUsers, Couple, Bond |
| Pairing Code | `PairingCode` | `pairingCode` | PairCode, Code, OTP |
| Call | `Call` | `callId`, `callType` | CallSession, Conversation, Session |
| CallSession | `CallSession` | n/a (local layer) | CallController, CallManager, CallVM |
| CallType | `CallType` (enum: `audio`, `video`) | `call_type: "audio" \| "video"` | — |
| Utterance | `Utterance` | `utteranceId`, `utterance_id` (wire) | Segment, Phrase, Speech |
| Caption | `Caption` | — | CaptionLine, Subtitle, Transcript |
| PartialCaption | `PartialCaption` | — | InProgressCaption, StreamingCaption |
| Source Text | `SourceText` | `source_text` (wire) | OriginalText, InputText |
| Target Text | `TargetText` | `target_text` (wire) | TranslatedText, OutputText |
| ASR Provider | `AsrProvider` | — | SpeechRecognizer, TranscriptionProvider |
| Translation Provider | `TranslationProvider` | — | Translator, TranslationEngine |
| ParticleProcessor | `ParticleProcessor` | — | RulesEngine, ParticleHandler, ParticleLayer |
| TranslationStatus | `TranslationStatus` (enum: `ok`, `failed`, `lowConfidence`) | `translation_status` | TranslationResult.kind |
| RenderMode | `RenderMode` (enum: `default`, `sundanesePlaceholder`) | `render_mode` | DisplayMode, CaptionStyle |
| RoomState | `RoomState` (enum: `active`, `waitingForPartner`, `ended`) | n/a (local) | CallState, SessionState |
| CaptionStack | `CaptionStackView` (iOS) / `CaptionStackComposable` (Android) | — | CaptionList, CaptionHistory, ChatList |
| MonochromeGlassPanel | `MonochromeGlassPanel` | — | GlassPanel, BlurPanel |
| E2EE Key Exchange | `E2EEKeyExchange` | — | KeyAgreement, KeyHandshake |
| LanguageCode | `LanguageCode` (BCP 47 string wrapper) | `source_lang`, `target_lang` (BCP 47) | LocaleCode, Lang |

---

## 2. Naming Conventions

| Surface | Convention |
|---|---|
| Kotlin types | PascalCase |
| Kotlin vals / funs / params | camelCase |
| Swift types | PascalCase |
| Swift vars / funcs / params | camelCase |
| File names | Match contained primary type (`CaptionRow.kt` / `CaptionRow.swift`) |
| Data Channel JSON fields | **snake_case** (locked) |
| Firestore document fields | **camelCase** (Firebase convention) |
| Language codes (external) | **BCP 47** (`id-ID`, `en-US`, `su-ID`) |
| Flores-200 codes (internal) | `ind_Latn`, `eng_Latn`, `sun_Latn` — **only between `TranslationProvider.translate()` input and the model call boundary. Never in logs, telemetry, error codes, model-download metadata, file paths, or any other surface.** Convert at boundary, no exceptions. |
| Logging field keys | snake_case (and must be from the `AllowedLogKey` enum — see SafeLog spec) |
| Error codes | `ERR_<DOMAIN>_<CONDITION>` SCREAMING_SNAKE_CASE — see [error-codes.md](./error-codes.md) |

---

## 3. ID Format (Locked Globally)

- **All canonical-entity IDs** (`Pair.id`, `Call.id`, `Utterance.id`, `Caption.id`, `MessageId` in Data Channel payloads): **`String`, ULID format, 26 characters, Crockford base32**.
- ULIDs are time-sortable and collision-resistant at 2-user scale.
- **Library pinning (Gap I.12 — locked at Story 1.5, 2026-05-23):**
  - **Android:** `com.aallam.ulid:ulid-kotlin:1.3.0` (Kotlin Multiplatform; JVM variant selected automatically via Gradle Module Metadata). Wrapped behind `android/app/src/main/java/com/xaeryx/translatorrep/ids/UlidGenerator.kt`. Production calls go through `UlidGenerator.next()`.
  - **iOS:** `https://github.com/yaslab/ULID.swift` (MIT; SPM module name `ULID`; pinned to a tagged release ≥ `1.3.1` per `ios/PACKAGES.md` — concrete tag chosen at Story 1.2 close-out). Wrapped behind `ios/TranslatorRep/IDs/UlidGenerator.swift`.
  - Both `UlidGenerator` types expose a library-independent `encodeCanonical(timestampMs, random80BitBigEndian)` helper that implements Crockford base32 encoding directly (no library dependency). This is what the parity test exercises — the production `next()` paths delegate to their respective libraries, which are assumed spec-compliant.
- **Test vector** (locked at Story 1.5; cross-platform parity verification):
  - Input timestamp: `2026-05-22T13:53:51.242Z` (`1779458031242` ms since Unix epoch)
  - Random component (80 bits / 10 bytes, big-endian hex): `0102030405060708090A`
  - Expected output: **`01KS7ZDFMA041061050R3GG28A`**
  - Both `UlidGenerator.encodeCanonical(1779458031242, [0x01, 0x02, ..., 0x0A])` calls — Android and iOS — must produce that exact string.
- Firestore document IDs follow the same ULID convention where the app generates them (calls, utterances). Where Firebase auto-generates (e.g., `pairs/{pairId}`), Firebase IDs are accepted but treated as opaque strings.

---

## 4. Renaming a Canonical Concept

Process (architecture.md §2 Rename Policy):

1. ADR amendment in `architecture.md` with rationale.
2. Simultaneous PR touching both Kotlin + Swift code paths.
3. Update PRD reconciliation references (`prd.md` rev bump if user-facing terminology changes).
4. Update UX spec if a UX-visible name.
5. Update this file (`canonical-names.md`) and `error-codes.md` if applicable.

Code-review agent will reject:

- Single-platform renames.
- Stories introducing a new synonym for a concept already in this glossary.
- Inline ad-hoc renames in PR diffs without ADR provenance.

---

## 5. See Also

- [error-codes.md](./error-codes.md) — `ERR_*` / `WARN_*` / `INFO_*` registry
- [data-channel-schema-v1.json](./data-channel-schema-v1.json) — wire payload contract (snake_case)
- [auth-proxy-api.md](./auth-proxy-api.md) — backend HTTP contract (camelCase)
- [state-derivation.md](./state-derivation.md) — `RoomState` derivation from `room.remoteParticipants`
- [particle-rules-fixtures/](./particle-rules-fixtures/) — golden-file `ParticleProcessor` test fixtures
- [regression-corpus/](./regression-corpus/) — Translation provider bake-off corpus
