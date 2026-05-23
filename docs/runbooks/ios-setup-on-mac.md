# iOS Setup On Mac — Runbook

> **Purpose:** Step-by-step setup for the iOS scaffold (Story 1.2). Xcode is macOS-only, so this work happens on Bania's Mac. The cross-platform Swift source files I can pre-generate on Windows; the `.xcodeproj` itself and all builds happen on the Mac.
>
> **Status:** ⏸ Not started. Bania switches to the Mac when ready.
> **Updated:** 2026-05-22.

---

## Why Mac

Xcode (and `xcodebuild`, `xcrun`, the iOS Simulator, the Whisper.cpp `XCFramework` build toolchain, TestFlight upload, and the Core ML compiler) only run on macOS. Apple ships no first-party developer toolchain for any other OS, and the few "remote Mac" services that exist (MacStadium, etc.) are expensive and add latency. You have a Mac; use it.

Windows / WSL CAN do:
- Generate Swift source files (`.swift`) — pure text.
- Generate `Info.plist`, `*.entitlements` (XML files).
- Generate `Package.swift` for SPM (pure text).
- Author shared specs that iOS code will consume (already done — `/shared/`).

Windows / WSL CANNOT do:
- Create or edit the `.xcodeproj` directory (Xcode-only format).
- Compile Swift (the `swiftc` compiler ships only on macOS / Linux, and Apple frameworks like SwiftUI / AVFoundation / Speech / Network are macOS-only).
- Run iOS Simulator.
- Compile / link Whisper.cpp Core ML support.
- Build IPAs / TestFlight uploads.

---

## Prerequisites (verify on Mac)

- [ ] **macOS:** Sonoma (14) or newer. Sequoia (15) preferred for iOS 26 SDK support.
- [ ] **Xcode:** 16 or newer (16.2+ ships iOS 26 SDK). Install from App Store (~12 GB download).
- [ ] **Command Line Tools:** `xcode-select --install` if not already.
- [ ] **Apple Developer Account:** Free tier is fine for sideload + TestFlight Ad Hoc (FR-distribution per PRD §8). Paid is NOT required for v1.
- [ ] **Physical iPhone for testing:** the girlfriend's iPhone model, paired with Mac via cable for the first run. The Whisper.cpp battery measurement (Story 3.5 AC) requires real-hardware testing.
- [ ] **Git, gh CLI** (already on most Macs via Homebrew or Xcode tools).

---

## Step-by-Step

### Step 1 — Clone the repo on Mac

```bash
cd ~/Code  # or wherever you keep projects
git clone <your-repo-url> TranslatorRep
cd TranslatorRep
```

If you've been working on this only on Windows so far, push from Windows first:

```bash
# On Windows
cd c:\Users\bania\Desktop\TranslatorRep
git remote add origin <your-repo-url>
git push -u origin main
```

(You don't have a GitHub remote yet — set one up first via `gh repo create xaeryxapps/TranslatorRep --private` from either machine.)

### Step 2 — Open `/ios/` in Xcode

`/ios/` doesn't exist yet. Story 1.2 creates it. Either:

**Option A — Have Claude (on Mac, via the same Claude Code CLI) generate the iOS source files autonomously.** When you're on the Mac with Claude Code running, say "generate the iOS scaffold per Story 1.2." Claude will produce:

- `/ios/TranslatorRep/TranslatorRepApp.swift` (`@main App` SwiftUI lifecycle entry point)
- `/ios/TranslatorRep/MainView.swift` (hello-world view hosting `MonochromeGlassPanel`)
- `/ios/TranslatorRep/UI/Components/MonochromeGlassPanel.swift` (the SwiftUI primitive — `Material` thickness variants)
- `/ios/TranslatorRep/UI/Theme/TranslatorRepStyle.swift` (Theme A tokens, view modifier)
- `/ios/TranslatorRep/UI/Theme/Color+TranslatorRep.swift` (color extensions matching the Android `Color.kt` tokens)
- `/ios/TranslatorRep/UI/Theme/Typography+TranslatorRep.swift` (font sizes matching Android `Type.kt`)
- `/ios/TranslatorRep/Info.plist` (NSMicrophoneUsageDescription, NSCameraUsageDescription, NSPhotoLibraryUsageDescription, UIBackgroundModes voip)
- `/ios/TranslatorRep/TranslatorRep.entitlements` (`com.apple.developer.voip` + push environment)
- `/ios/Package.swift` if going SPM-first OR `/ios/Podfile` if going CocoaPods; SPM strongly recommended.
- `/ios/README.md`

After Claude generates them, YOU manually create the Xcode project that consumes them:

- Open Xcode → File → New → Project → iOS App.
- Product name: `TranslatorRep`.
- Team: your free Apple ID team.
- Bundle ID: `com.xaeryx.translatorrep` (must match Android `applicationId` for shared Firebase project conventions).
- Interface: SwiftUI.
- Language: Swift.
- Storage: None (or Core Data — we'll swap to SwiftData later per Story 8.6).
- Include Tests: yes.
- Location: `/ios/` directory in the repo.
- Replace the auto-generated Swift files with Claude's generated ones; add the SPM dependencies (LiveKit Swift SDK, Firebase iOS SDK, Whisper.cpp XCFramework) via `File → Add Package Dependencies`.

**Option B — Have me generate the Xcode project structure as files now (Windows-side), and you "Add Files" them into Xcode.** This works but Xcode often re-formats `.xcodeproj/project.pbxproj` on first save, leading to git churn. Option A is cleaner.

### Step 3 — Add SPM dependencies in Xcode

`File → Add Package Dependencies...` then add:

- **LiveKit Swift SDK:** `https://github.com/livekit/client-sdk-swift` — version 2.14.1 minimum (Insertable Streams support per architecture.md addendum).
- **Firebase iOS SDK:** `https://github.com/firebase/firebase-ios-sdk` — version 11.x. Add products: `FirebaseAuth`, `FirebaseFirestore`, `FirebaseAppCheck`, `FirebaseCrashlytics`.
- **Whisper.cpp XCFramework:** see [Whisper.cpp Core ML setup](https://github.com/ggerganov/whisper.cpp#core-ml-support) — typically built from source per the README and added as a local SPM package. Alternative: use a community Whisper.cpp Swift package wrapper if you find one that ships the small multilingual model.

### Step 4 — Configure signing & entitlements

In Xcode → TranslatorRep target → Signing & Capabilities:

- Team: your Apple ID.
- Bundle ID: `com.xaeryx.translatorrep`.
- Add capabilities:
  - **Background Modes** → Voice over IP (FR-7 / Story 2.4 PushKit incoming-Call).
  - **Push Notifications** (for PushKit VoIP).
  - **Background Modes** → Audio (for active Call mic capture).
  - **Background Modes** → Remote notifications.

The `TranslatorRep.entitlements` file Claude generates should already have `com.apple.developer.voip = true` + push environment.

### Step 5 — First build + run on real iPhone

- Connect girlfriend's iPhone via cable. Trust the Mac on the iPhone when prompted.
- In Xcode: select the iPhone as the run destination.
- ⌘+R to build + run.
- First build takes ~5 min (SPM dependency resolution, Whisper.cpp XCFramework compile).
- The hello-world Compose-equivalent (`MainView` with the `MonochromeGlassPanel`) should appear on the iPhone.

The iOS-side AC parity with Story 1.1 Android:

- ✅ `TranslatorRepStyle.swift` view modifier exists at root applying Theme A tokens (UX-DR2).
- ✅ `.preferredColorScheme(.dark)` set at `WindowGroup`.
- ✅ No accent color set.
- ✅ `MonochromeGlassPanel.swift` exists rendering `.thickMaterial` / `.regularMaterial` / `.ultraThinMaterial` variants (UX-DR5 — true backdrop blur via SwiftUI native `Material` types).
- ✅ Hello-world view launches on physical iPhone.

### Step 6 — TestFlight Ad Hoc distribution setup (Story 1.2 finale)

For sideload-equivalent distribution per PRD §8:

- Free Apple Developer account: 7-day Ad Hoc cert; you re-sign + re-install via Xcode every week. Acceptable for a 2-user personal app.
- Paid Apple Developer ($99/year): 1-year Ad Hoc cert; no re-install treadmill. Worth the $99 if v1 ships and you keep using it.

You can defer the paid-developer decision until you've actually shipped v1 to her iPhone and want to stop re-installing weekly.

---

## What Happens Next (after Story 1.2)

Once both `/android/` and `/ios/` scaffolds build a hello-world `MonochromeGlassPanel` on their respective real devices:

- Story 1.3 (Oracle VM + LiveKit Docker) — backend.
- Story 1.4 (Firebase init + Firestore rules + App Check) — Firebase project.
- Story 1.5 (SafeLog + lint + ULID) — cross-platform primitives.
- Story 1.6 (CI/CD per stack) — three GitHub Actions workflows.
- Story 1.8–1.13 — pairing user flows on both platforms.

By end-of-Epic 1 you're paired between the two real devices through a real backend.

---

## Cross-Platform Parity Checklist (Story 1.1 + 1.2 paired AC)

When both scaffolds are running their hello-world screens on their respective real devices, confirm:

- [ ] Same dark background color (`#0A0A0B`).
- [ ] Same glass panel rounded corner (~24 dp/pt).
- [ ] Same glass panel translucency (you should be able to see the dark background through it).
- [ ] Same border (1 dp/pt, ~8% white opacity).
- [ ] Same title text color (~95% white opacity).
- [ ] Same body text color (~70% white opacity).
- [ ] No accent color anywhere.
- [ ] No Material You dynamic color "leak" on Android (even if Galaxy theme is colorful).
- [ ] No System Light mode "leak" on iOS (even if device is in Light mode).

If any of these diverge: open a parity-bug story before proceeding to Story 1.3.

---

## Open Questions to Resolve at Story 1.2

- ULID library for iOS (`/shared/canonical-names.md` flags this as TBD). Candidates: `ulid-swift` from oklog or a Swift-native impl. Pick one when authoring `IDs/UlidGenerator.swift` in Story 1.5.
- Whisper.cpp model choice: `ggml-small.bin` (~140 MB) vs `ggml-base.bin` (~75 MB). Smaller = faster + less battery drain; larger = better Indonesian recognition. Architecture defers this to scaffolding-time per Gap I.3. Start with `small`; downgrade to `base` if battery measurements (Story 3.5 AC) exceed targets.
- Whisper.cpp Swift integration approach: official XCFramework via local SPM, OR a community wrapper. Pick the one that builds cleanly with the least friction.

---

## When You Come Back to Windows After iOS Work

Push from Mac:

```bash
git add ios/
git commit -m "Story 1.2 — iOS scaffold + hello-world"
git push
```

Pull on Windows:

```bash
cd c:\Users\bania\Desktop\TranslatorRep
git pull
```

The `/android/` and `/shared/` work continues on Windows; you only context-switch to Mac for iOS-specific stories (1.2, 3.5, 6.x video, 8.x post-Call surfaces require platform tests on both sides).
