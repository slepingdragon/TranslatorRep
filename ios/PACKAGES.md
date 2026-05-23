# iOS Package & Wire-Up Crib Sheet

Story 1.5 wrote Swift source files + `.swiftlint.yml` to the filesystem **before** the
Xcode project itself exists (Story 1.2, deferred to Mac). This file documents what
Story 1.2 needs to do on the Mac to integrate them. Keep it short — every entry
must be actionable in <5 minutes.

---

## SPM Dependencies to Add (Story 1.2 → Xcode → Package Dependencies)

| Package | URL | Version Rule | Purpose | Wired in |
|---|---|---|---|---|
| swift-ulid | `https://github.com/oherrala/swift-ulid` | Up to next minor — pinned to a tagged release, NOT `main` | 26-char Crockford base32 ULID generation | `IDs/UlidGenerator.swift` (Story 1.5) |
| FirebaseAuth | `https://github.com/firebase/firebase-ios-sdk` | Pin per Firebase BOM equivalent | Anonymous Auth | Story 1.8 |
| FirebaseFirestore | same as above | same | Pairing + per-call metadata | Story 1.9+ |
| FirebaseAppCheck | same as above | same | DeviceCheck provider gating backend | Story 1.4 |
| FirebaseCrashlytics | same as above | same | SafeLog Crashlytics route | Story 1.5 (already imported in `Logging/SafeLog.swift`) |
| LiveKit (Swift) | `https://github.com/livekit/client-sdk-swift` | Tag matching Android's `2.25.3` Insertable Streams support | WebRTC + Data Channel + E2EE | Epic 2+ |
| Whisper.cpp XCFramework | (custom build steps — see architecture §B) | n/a | On-device ASR (FR-13 / Epic 3.5) | Story 3.5 |

When wiring the swift-ulid dep in Xcode:

```
File → Add Package Dependencies → paste URL → "Up to Next Minor" from tagged release → Add
```

Then in the target's build settings, ensure the `swift-ulid` library is in
"Frameworks, Libraries, and Embedded Content" with "Do Not Embed."

---

## SwiftLint Wire-Up (Story 1.2)

`.swiftlint.yml` already exists at the workspace root. Story 1.2 needs to:

1. **Install SwiftLint on the Mac:** `brew install swiftlint` (or pin a version in the
   project's `Mintfile` later).
2. **Add a Build Phase Run Script** to the iOS target (Xcode → target → Build Phases →
   "+" → New Run Script Phase) with this script:

   ```bash
   if which swiftlint >/dev/null; then
       swiftlint
   else
       echo "warning: SwiftLint not installed — see ios/PACKAGES.md"
   fi
   ```

3. **Smoke-test the custom rule.** Add a `print("violation")` to any non-`Logging/SafeLog.swift`
   file → build → expect the `forbid_direct_ios_logging` rule to fire with
   `error: Use SafeLog.event(_:_) instead. See architecture §14.` Revert.

CI integration (running SwiftLint outside Xcode) is wired up in Story 1.6.

---

## Cross-Platform ULID Parity Test (Story 1.5 → 1.2 close-out)

Translate `android/app/src/test/java/com/xaeryx/translatorrep/ids/UlidParityTest.kt`
into a Swift XCTest file (suggested path: `TranslatorRepTests/IDs/UlidParityTests.swift`).

The locked test vector:

- `timestamp_ms = 1779458031242` (= `2026-05-22T13:53:51.242Z`)
- `random_80bit_hex = "0102030405060708090A"` (10 bytes)
- `expected_ulid = "01KS7ZDFMA041061050R3GG28A"`

The iOS `UlidGenerator.encodeCanonical(timestampMs:random80BitBigEndian:)` (in
`IDs/UlidGenerator.swift`) MUST produce that exact string. If it doesn't, the
underlying Swift implementation has a Crockford bug — fix the encoder before
shipping, because subsequent stories (Story 1.9 onwards) generate Pair IDs that need
to round-trip identically between platforms.

---

## SafeLog Wire-Up (already done in source, just needs Xcode project import)

Story 1.5 wrote these files to `ios/TranslatorRep/`. Story 1.2 needs to drag them
into the Xcode project's file tree (Xcode 16+ uses PBXFileSystemSynchronizedRootGroup
which picks them up automatically; older Xcode requires "Add Files to TranslatorRep").

- `IDs/UlidGenerator.swift`
- `Logging/SafeLog.swift`
- `Logging/AllowedLogKey.swift`
- `Logging/ErrorCode.swift`
- `Logging/CrashlyticsConfig.swift`

Smoke test on Mac after import:

1. Build the target — expect successful compile.
2. From `TranslatorRepApp.swift`'s `init()`, call `SafeLog.event(.callId, "smoke")`
   in DEBUG → expect `os_log` line in Console.app.
3. Add `print("violation")` to `TranslatorRepApp.swift` → expect SwiftLint error.
