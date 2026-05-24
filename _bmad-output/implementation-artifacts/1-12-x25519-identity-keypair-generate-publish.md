# Story 1.12: X25519 Identity Keypair — Generate + Publish (Android)

Status: review

<!-- Created 2026-05-24 (autonomous session, post-1.11 merge). Android-only; iOS mirror later.
     Fifth pairing-arc story; the E2EE foundation (ADR-A2). Adds the first crypto dependency
     (Google Tink). Brady approved continuing into 1.12 + the crypto-lib choice was flagged. -->

## Story

As Bania (or his girlfriend) on first launch,
I want my device to generate and publish a long-term X25519 identity public key,
so that future E2EE per-Call key exchanges (Epic 5) can sign ephemeral public keys against my identity, preventing MITM.

## Acceptance Criteria

(From epics.md Story 1.12 / FR-29 / ADR-A2.)

**Given** I am signing in anonymously for the first time on a device,
**When** the first-launch flow runs,
**Then:**

1. **AC-1 (generate):** a long-term X25519 keypair is generated client-side using a vetted crypto library (**Google Tink** `subtle.X25519`).
2. **AC-2 (private key at rest, never networked):** the private key is stored in **EncryptedSharedPreferences** (`security-crypto`) — never plaintext, never logged, never sent over the network (ADR-A2).
3. **AC-3 (publish public key):** the public key is published to `/users/{uid}/identityPub` as Firestore `bytes`/`Blob` (the caller's OWN doc — owner-writable per the rules).
4. **AC-4 (survives restarts):** the keypair survives app restarts — the private key is recoverable from secure storage (a returning launch reuses it, no regeneration).
5. **AC-5 (new key on reinstall):** a re-installed app generates a NEW keypair (no cross-reinstall recovery — accepted v1 limitation; app-storage wipe clears the EncryptedSharedPreferences).
6. **AC-6 (unit tests):** (a) generation succeeds; (b) the private key is never serialized to logs; (c) public-key bytes are 32-byte Curve25519.

**Out of scope (deferred):** per-Call **ephemeral** X25519 + ECDH + HKDF + Insertable-Streams wiring is **Epic 5** (Stories 5.1–5.5). This story is the long-term identity key only. iOS mirror → future story.

**Done criteria:** flips to `review` when AC-1..AC-6 ✅ and local validate (detekt + unit tests + assembleDebug) green → CR pass → `done`. On-device confirmation (key persists across kill; reinstall regenerates; `/users/{uid}.identityPub` populated) folds into the post-1.4c QR-install device pass.

## Tasks / Subtasks

### Phase 1 — Crypto primitive + dependency

- [x] **1.1** Add **Google Tink** (`com.google.crypto.tink:tink-android` 1.15.0) to `libs.versions.toml` + `app/build.gradle.kts`.
- [x] **1.2** `e2ee/X25519Identity.kt` — pure wrapper over Tink `subtle.X25519`: `generatePrivateKey()` / `publicKey(priv)` (raw 32-byte keys; `KEY_SIZE_BYTES = 32`). Compile-checked early to de-risk Tink resolution/API.

### Phase 2 — Secure storage + publish seams

- [x] **2.1** `secure/SecureStorage.kt` — EncryptedSharedPreferences wrapper (Base64 byte get/put; AES256_SIV keys / AES256_GCM values; lazy master-key init).
- [x] **2.2** `e2ee/IdentityKeyStore.kt` (seam) + `SecureIdentityKeyStore` (over SecureStorage).
- [x] **2.3** `e2ee/IdentityPublisher.kt` (seam) + `IdentityFirestoreRepository` (`/users/{uid}.identityPub` = `Blob`, merge-write).

### Phase 3 — Orchestrator + wiring

- [x] **3.1** `e2ee/IdentityRepository.kt` — `ensureIdentity(uid)` (load-or-generate → store → publish; idempotent) + `start(uid)` (once-per-process, fire-and-forget on app scope, `runCatching` so offline publish can't crash).
- [x] **3.2** `TranslatorRepApplication`: expose `identityRepository` (SecureStorage + Firestore + appScope). `MainActivity`: `identityRepository.start(uid)` alongside `pairingStatusRepository.start(uid)` on `SignedIn`.

### Phase 4 — Tests + docs

- [x] **4.1** `e2ee/X25519IdentityTest` (4): 32-byte private, 32-byte deterministic public, public≠private, distinct keys. `e2ee/IdentityRepositoryTest` (2): first-launch generate+store+publish-derived-public; returning-launch reuse (no regenerate).
- [x] **4.2** Story file + `sprint-status.yaml` (1-12 → review) + `docs/project-context.md`.

## Dev Notes

### Why Google Tink (the "vetted crypto library")

The architecture (ADR-A2) names X25519 + EncryptedSharedPreferences but not a specific lib, and none was in the deps. Tink (`subtle.X25519`) is the right choice:

- **Raw 32-byte keys** — `generatePrivateKey()`/`publicFromPrivate()` return raw `ByteArray`, matching the Firestore `bytes` form and **iOS CryptoKit `Curve25519.KeyAgreement`** raw representation (cross-platform byte parity, an NFR).
- **Vetted + maintained** by Google; pure-Java RFC 7748 impl → **runs in plain JVM unit tests** (no Android/Robolectric), so AC-6(c) is tested directly.
- **`computeSharedSecret`** is in the same API — the Epic-5 per-call ECDH reuses it.

Wrapped behind `e2ee/X25519Identity.kt`; callers never touch Tink (the same "wrap the SDK" convention as `UlidGenerator`).

### Private key never logged / networked (AC-2, AC-6b)

The private key is a raw `ByteArray` that lives only in `SecureStorage` (EncryptedSharedPreferences). **Nothing in the e2ee path logs key material** — there is no `SafeLog` call touching a key, and no `AllowedLogKey` for identity (adding one would need a simultaneous iOS PR anyway). "Never serialized to logs" is satisfied by construction (no logging at all in the identity path), which is the strongest form of the guarantee. Only the derived public key is published (AC-3).

### Same testable-seam pattern (Stories 1.8–1.11)

`ensureIdentity` is pure suspend over the `IdentityKeyStore` + `IdentityPublisher` fakes, with the **real** pure-JVM `X25519Identity` (so the tests exercise actual Curve25519). `start()` (app-scope launch) + `SecureStorage` (EncryptedSharedPreferences) + `IdentityFirestoreRepository` are the thin untested Android/SDK wrappers.

### Reinstall → new key (AC-5)

Accepted v1 limitation per ADR-A2: a reinstall wipes app storage (EncryptedSharedPreferences gone), so `loadPrivateKey()` returns null and a fresh keypair is generated + republished. No key escrow/recovery in v1.

### Library references

- [Tink `subtle.X25519`](https://github.com/tink-crypto/tink-java) — `generatePrivateKey`/`publicFromPrivate`/`computeSharedSecret`.
- [`EncryptedSharedPreferences`](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferences) — AES256_SIV/AES256_GCM.
- [Firestore `Blob`](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Blob) — `bytes` field type.
- [RFC 7748](https://www.rfc-editor.org/rfc/rfc7748) — Curve25519 (32-byte keys).

### Source-tree placement

```
android/app/src/main/java/com/xaeryx/translatorrep/
├── MainActivity.kt                        # MODIFIED: start identityRepository on SignedIn
├── TranslatorRepApplication.kt            # MODIFIED: expose identityRepository
├── secure/
│   └── SecureStorage.kt                   # NEW (EncryptedSharedPreferences wrapper)
└── e2ee/                                  # NEW package (ADR-A2)
    ├── X25519Identity.kt                  # NEW (Tink wrapper, pure)
    ├── IdentityKeyStore.kt                # NEW (seam + SecureIdentityKeyStore)
    ├── IdentityPublisher.kt               # NEW (seam + IdentityFirestoreRepository)
    └── IdentityRepository.kt              # NEW (ensureIdentity + start)
android/app/src/test/java/com/xaeryx/translatorrep/e2ee/
├── X25519IdentityTest.kt                  # NEW (4 tests)
└── IdentityRepositoryTest.kt              # NEW (2 tests)
android/gradle/libs.versions.toml          # MODIFIED: + tink
android/app/build.gradle.kts               # MODIFIED: + tink-android
```

### Testing standards

- Pure-JVM JUnit4 (`runBlocking`); SecureStorage/Firestore/`start` are framework-coupled (untested). Local validate (JDK 17): `:app:detekt` 0 smells; `X25519IdentityTest` 4/4 + `IdentityRepositoryTest` 2/2 (32 pairing+e2ee tests total green); `:app:assembleDebug` green (Tink dexed cleanly).

### References

- [epics.md Story 1.12](../planning-artifacts/epics.md) — AC source.
- [architecture.md ADR-A2](../planning-artifacts/architecture.md) — long-term identity key + EncryptedSharedPreferences + identityPub.
- [Story 1.8](./1-8-anonymous-sign-in.md) — the auth layer that supplies the uid this publishes under.

## Dev Agent Record

### Agent Model Used

claude-opus-4-7[1m] (autonomous session 2026-05-24, post-1.11 merge)

### Debug Log References

- Tink resolution + `X25519` API verified early (`:app:compileDebugKotlin` BUILD SUCCESSFUL after adding the dep + wrapper).
- Full validate (JDK 17): `:app:detekt` 0 code smells; `:app:testDebugUnitTest` BUILD SUCCESSFUL (`X25519IdentityTest` 4/4, `IdentityRepositoryTest` 2/2); `:app:assembleDebug` BUILD SUCCESSFUL (Tink dexed via mergeLibDexDebug).

### Completion Notes List

- First crypto dependency added: Google Tink (`tink-android` 1.15.0), wrapped behind `e2ee/X25519Identity.kt`.
- Private key never logged/networked — by construction (no logging in the e2ee path); only the public key is published.
- Identity is ensured/published on the app scope when signed in (alongside pairing-status start), fire-and-forget with offline-safe `runCatching`.
- Reinstall regenerates (accepted v1 limit). EncryptedSharedPreferences (AES256_SIV/GCM) holds the private key at rest.

### File List

**Created:**
- `_bmad-output/implementation-artifacts/1-12-x25519-identity-keypair-generate-publish.md` — this file
- `android/app/src/main/java/com/xaeryx/translatorrep/e2ee/X25519Identity.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/e2ee/IdentityKeyStore.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/e2ee/IdentityPublisher.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/e2ee/IdentityRepository.kt`
- `android/app/src/main/java/com/xaeryx/translatorrep/secure/SecureStorage.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/e2ee/X25519IdentityTest.kt`
- `android/app/src/test/java/com/xaeryx/translatorrep/e2ee/IdentityRepositoryTest.kt`

**Modified:**
- `android/gradle/libs.versions.toml` — + tink / tink-android
- `android/app/build.gradle.kts` — + `implementation(libs.tink.android)`
- `android/app/src/main/java/com/xaeryx/translatorrep/TranslatorRepApplication.kt` — expose identityRepository
- `android/app/src/main/java/com/xaeryx/translatorrep/MainActivity.kt` — start identityRepository on SignedIn
- `_bmad-output/implementation-artifacts/sprint-status.yaml` — 1-12 backlog → review; last_updated bump
- `docs/project-context.md` — pairing-arc status note

### Change Log

- 2026-05-24 — Story 1.12 implemented (Android). X25519 long-term identity keypair via Google Tink (vetted, raw 32-byte, cross-platform-parity, Epic-5-ready); private key in EncryptedSharedPreferences (never logged/networked, ADR-A2); public key published to `/users/{uid}.identityPub` as a Blob. Generate-once + reuse-across-restart; reinstall regenerates. 6 unit tests; detekt clean; assembleDebug green. Status → `review`.
