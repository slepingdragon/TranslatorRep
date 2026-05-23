# Firebase Setup — Android (Manual Walkthrough)

> **Authority:** This runbook walks Bania through the manual Firebase console + Google Play Console setup required by [Story 1.4](../../_bmad-output/implementation-artifacts/1-4-firebase-init-firestore-rules-baseline-app-check-providers.md). The code-side Android wiring is scaffolded in PR feature/1-4-firebase-android; activation (uncommenting plugins + calling `FirebaseBootstrap.init`) happens in a follow-up dev session AFTER you complete these steps.
>
> **Estimated manual time:** ~30 minutes if you already have a Google account + Google Play Console developer account ($25). ~60 minutes if you need to create the Play Console account too.
>
> **iOS half:** Out of scope for this runbook. Future Story 1-4b will document iOS setup (`GoogleService-Info.plist` + DeviceCheck App Check provider) in a separate Mac/iOS Claude session.

---

## Prerequisites

- ✅ Google account (Bania already has one — same one as for the Xaeryx domain ownership ideally)
- ⚠️ **Google Play Console developer account ($25 one-time fee)** — required for Play Integrity App Check provider. Sign up at https://play.google.com/console/signup. If you don't want to pay $25 right now, you can skip §5 (Play Integrity) and use only the Debug App Check provider — but `FirebaseBootstrap` will then fail App Check in any release build, blocking eventual store publication. Recommended: pay the $25 now to unblock everything.
- ✅ Node.js + npm (to install Firebase CLI). If missing: install via [nvm-windows](https://github.com/coreybutler/nvm-windows) on Windows.

---

## §1. Create the Firebase project

1. Open https://console.firebase.google.com → sign in with your Google account.
2. Click **Add project** → **Project name:** `TranslatorRep` (or `TranslatorRep-Prod` if you want a clean naming scheme for adding a `TranslatorRep-Dev` project later).
3. **Google Analytics for this project?** → **Disable** (recommended for privacy-first apps; you can enable later if needed). Click **Create project** → wait ~30s → **Continue**.
4. You're now in the project dashboard. Note the **Project ID** (auto-generated slug like `translatorrep-abc12`) — you'll need it later. Find it in the URL or under **⚙ Project settings** at the top-left.

---

## §2. Add the Android app

1. From the project dashboard, click the **Android** icon (or **Add app** → Android).
2. **Android package name:** `com.xaeryx.translatorrep` — must match `android/app/build.gradle.kts` line 17 (`namespace = "com.xaeryx.translatorrep"`) exactly. Typos here cost an hour to debug.
3. **App nickname:** `TranslatorRep Android` (display-only).
4. **Debug signing certificate SHA-1** (optional for now, REQUIRED for Play Integrity):
   - Locally: `cd android && ./gradlew :app:signingReport` — look for the `debug` variant's `SHA1` line.
   - OR in Android Studio: **Gradle → :app → Tasks → android → signingReport** → run → copy SHA1 from the output.
   - Paste into the Firebase form. You can add it later via **⚙ Project settings → Your apps → Android → SHA certificate fingerprints**.
5. Click **Register app** → **Download `google-services.json`** → save the file. **Move it to `android/app/google-services.json`** (next to `build.gradle.kts`, NOT at repo root).
6. Verify it's gitignored: `git status` should NOT list `google-services.json`. If it does, double-check `android/.gitignore` has both `google-services.json` and `app/google-services.json` lines (it should, per Story 1.1 scaffold).
7. **Skip the SDK setup steps in the Firebase wizard** — the Firebase BOM + per-SDK dependencies are already declared in `android/app/build.gradle.kts`. Click **Next → Next → Continue to console**.

---

## §3. Enable Anonymous Authentication

1. Firebase console → left sidebar → **Build → Authentication** → **Get started**.
2. **Sign-in method** tab → click **Anonymous** → toggle **Enable** → **Save**.
3. That's it. No further config — the Android client calls `auth.signInAnonymously()` and Firebase mints a UID.

---

## §4. Enable Cloud Firestore

1. Firebase console → left sidebar → **Build → Firestore Database** → **Create database**.
2. **Mode:** choose **Start in production mode** (we'll deploy our own rules from `firebase/firestore.rules` in Phase 2 — don't bother with the wizard's default rules).
3. **Cloud Firestore location:**
   - **First choice:** `asia-southeast2` (Jakarta) — closest to the target user base (Indonesian Bahasa speakers).
   - **Fallback:** `asia-southeast1` (Singapore) — fine, low latency to Indonesia.
   - **Avoid:** us-central or anything in the Americas — adds ~200ms latency to every Firestore op.
   - ⚠️ **Location is permanent** — you cannot move a Firestore database after creation. Pick carefully.
4. Click **Enable** → wait ~30s → empty Firestore is provisioned.
5. **No collections to create manually** — the app code creates `/users/{uid}` etc. on first write. Just need the empty database provisioned and rules deployed.

---

## §5. Enable App Check with Play Integrity

This is the most involved step. App Check verifies that backend calls come from your real app on a real Android device, not from a script or emulator.

### 5a. Enable App Check in Firebase console

1. Firebase console → left sidebar → **Build → App Check** → **Get started** if first time.
2. Find your Android app in the list → click **Register** under **Play Integrity** column.
3. Firebase asks for a **Play Integrity API verdict-decryption configuration**. Two options:
   - **Google-managed decryption** (recommended for solo dev) — Firebase handles decryption automatically. Just click through.
   - **Self-managed decryption** — for paranoid setups; skip.
4. Set **TTL** to **1 hour** (default; balance between freshness and rate-limit). Click **Save**.

### 5b. Enable Play Integrity API in Google Cloud Console

App Check uses the Play Integrity API which lives in Google Cloud, not Firebase directly.

1. Go to https://console.cloud.google.com → top bar **Select a project** → pick the project Firebase auto-created when you created your Firebase project (it'll have the same Project ID).
2. Sidebar → **APIs & Services → Library** → search **Play Integrity API** → click → **Enable**. Takes ~30s.

### 5c. Link to Google Play Console

If you haven't yet set up Google Play Console for `com.xaeryx.translatorrep`:

1. Go to https://play.google.com/console → if first time, complete developer registration ($25 one-time).
2. **Create app** → App name `TranslatorRep` → Default language English (United States) (you can localize later) → App or game **App** → Free → accept declarations → **Create app**.
3. You don't need to fill out the full store listing right now. We just need the package name registered.
4. **App integrity** (left sidebar under **Release**) → **Play Integrity API** tab → **Link project** → select the Google Cloud project from §5b.

### 5d. (Recommended) Generate a debug App Check token

Real Play Integrity attestation requires the app to be signed with the upload key + the device to have Play Services. For dev builds, use the Debug provider:

1. Run the debug build on a device once (after this story's Phase 1 lands) — `FirebaseBootstrap.init()` will install `DebugAppCheckProviderFactory` and log a debug token to Logcat (filter `tag=DebugAppCheckProvider`).
2. Copy that token → in Firebase console → **App Check → Apps → your Android app → ⚙ → Manage debug tokens → Add debug token** → paste + give a name (e.g., `bania-pixel-debug`).
3. The Debug provider now produces App Check tokens accepted by Firebase backends for this specific debug device.
4. Add to `android/local.properties` (gitignored): `firebaseAppCheckDebugToken=<token>` — `FirebaseBootstrap` will read this in debug builds.

---

## §6. Install Firebase CLI + link the local `firebase/` directory

1. **Install:** `npm install -g firebase-tools` (global install). Verify: `firebase --version` → should print e.g., `13.x.x`.
2. **Authenticate:** `firebase login` → opens browser → sign in with the same Google account that owns the Firebase project. Approves CLI access.
3. **Link local dir to project:**
   ```bash
   cd "C:\Users\Brady J Bania\Desktop\ADEV\TranslatorRep\firebase"
   firebase use --add
   ```
   - Prompts you to pick from your Firebase projects → select `TranslatorRep` (or whatever you named it in §1).
   - Prompts for an alias → use `default`.
   - This creates `firebase/.firebaserc` mapping the local dir to the project — typically safe to commit (no secrets).

---

## §7. Deploy Firestore rules

After Phase 1 lands (Android plugins uncommented + FirebaseBootstrap activated) but before doing the smoke test, deploy the rules from `firebase/firestore.rules`:

```bash
cd firebase
firebase deploy --only firestore:rules
```

Expected output ends with `✔ Deploy complete!`. Verify in Firebase console → Firestore → **Rules** tab — the deployed rules should match `firebase/firestore.rules` byte-for-byte.

If you get a permissions error, run `firebase login --reauth` and retry.

---

## §8. Run the smoke test (Phase 3 — AFTER Android wiring lands)

This is documented in [`1-4-firebase-init-...md`](../../_bmad-output/implementation-artifacts/1-4-firebase-init-firestore-rules-baseline-app-check-providers.md) Tasks 3.1–3.3. Walk through it on a real device or an emulator with Play Services installed (any Google Play system image AVD).

Expected SafeLog output in Logcat (filter `tag=TranslatorRep`):

```
firebase_init=success
app_check_init=debug          # release build: =playintegrity
auth_uid=<first-4-chars>      # 4 chars only; never log full UIDs
smoke_users_write=success
smoke_forbidden_write=denied  # PERMISSION_DENIED expected — proves rules enforce
```

If any line is missing or shows `=failed`, capture the Logcat slice + paste into the Story 1.4 Dev Agent Record → Debug Log References for triage.

---

## Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `google-services` plugin error on `assembleDebug`: "File google-services.json is missing." | `android/app/google-services.json` not placed | Re-download from Firebase console → place at exact path |
| `FirebaseInitializationException: Default FirebaseApp is not initialized` at runtime | `FirebaseBootstrap.init(this)` not called in `Application.onCreate()` | Uncomment the call (Phase 1.2) |
| App Check 403 in release builds: "App not registered" | Play Integrity not enabled in Google Cloud Console (§5b) | Enable the API |
| App Check works in debug but not release | Debug token not promoted; Play Integrity not actually working | Verify §5c link + check Firebase console → App Check → Recent requests for verdict failures |
| Firestore writes succeed but reads return empty | Auth not yet completed — Firestore client is signed-out | Ensure `signInAnonymously().await()` BEFORE any Firestore operation |
| Rules deploy fails with `Project NOT_FOUND` | `firebase use` not run; or wrong project alias | Re-run `firebase use --add` from `firebase/` dir |
| Local debug build works on physical device but App Check token rejected | Debug token from §5d not added to Firebase console for THIS device | Each debug device needs its token added separately |

---

## What happens after this runbook

When you've completed §1–§7 (and §8 will run in Phase 3 after the Android wiring lands), tell Claude:

> Firebase Story 1.4 Phase 0 complete. Project ID is `<project-id>`. Ready for Phase 1 wiring.

Claude will then:
1. Uncomment the two Firebase plugins in `android/app/build.gradle.kts` (Phase 1.1)
2. Activate `FirebaseBootstrap.init(this)` in `TranslatorRepApplication.onCreate()` (Phase 1.2)
3. Implement `FirebaseSmokeTest.runOnce()` (Phase 1.4)
4. Update `AllowedLogKey` enum with the new keys (`firebase_init`, `app_check_init`, `auth_uid`, `smoke_users_write`, `smoke_forbidden_write`)
5. Open a PR with the wiring; you do the manual device smoke test (Phase 3); story flips to `review` then `done`.

That follow-up PR is small + mechanical because the heavy lifting (scaffolding, design, runbook authoring) is in this PR.
