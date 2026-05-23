# Firebase App Check — Android Provider Setup

> **Status:** Skeleton — populated with actual project details during Story 1.4 Phase 0 + Phase 1. This file is the historical record per Story 1.4 AC-3 wording ("setup notes captured in `/firebase/appcheck/android-providers.md`"). The step-by-step walkthrough lives in [`docs/runbooks/firebase-setup-android.md`](../../docs/runbooks/firebase-setup-android.md) §5.

---

## Architecture

**App Check goal:** Verify that backend Firebase calls (Firestore reads/writes, future auth-proxy `/token` calls) come from the genuine TranslatorRep Android app running on a genuine Android device — not from a script, browser, or modified APK.

**Two providers, BuildConfig-gated:**

| Build variant | Provider | Class |
|---|---|---|
| Debug (`BuildConfig.DEBUG == true`) | DebugAppCheckProviderFactory | `com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory` |
| Release (`BuildConfig.DEBUG == false`) | PlayIntegrityAppCheckProviderFactory | `com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory` |

Selection logic lives in `android/app/src/main/java/com/xaeryx/translatorrep/firebase/FirebaseBootstrap.kt`.

---

## Play Integrity (release) setup

### One-time setup (Bania)

Per [`docs/runbooks/firebase-setup-android.md`](../../docs/runbooks/firebase-setup-android.md) §5:

- [ ] Firebase project: `<project-id-here>` (filled in during Phase 0.4)
- [ ] Firebase console → App Check → Android app → **Register with Play Integrity** ✅
- [ ] Verdict decryption: **Google-managed** (recommended) ✅
- [ ] TTL: **1 hour**
- [ ] Google Cloud Console → APIs & Services → Library → **Play Integrity API → Enabled** ✅
- [ ] Google Play Console → developer account active ($25 fee paid) ✅
- [ ] Google Play Console → Create app for `com.xaeryx.translatorrep` ✅
- [ ] Play Console → App integrity → Play Integrity API → **Linked to Google Cloud project** ✅
- [ ] (Optional but recommended) Linked SHA-1 fingerprint(s) for the debug + release signing keys via Firebase console → Project settings → Your apps → Android → SHA certificate fingerprints

### Per-device verification

Once the app ships to a real device, `PlayIntegrityAppCheckProviderFactory.getInstance()` is automatically invoked by FirebaseBootstrap at app start. It:

1. Calls Google Play Services on the device → gets a Play Integrity verdict
2. Encrypts the verdict (handled by Google-managed decryption — opaque to us)
3. Submits to Firebase App Check service
4. Receives an App Check token (~1h TTL)
5. Token is automatically attached as `X-Firebase-AppCheck` header on every subsequent Firestore call

**You don't see this happening — it's automatic.** Verify via Firebase console → App Check → Apps → your Android app → "Recent requests" graph. After running the app for a few minutes, you should see verified requests showing up.

### Failure modes + symptoms

| Symptom | Likely cause |
|---|---|
| All Firestore calls return `PERMISSION_DENIED` in release builds | App Check enforced + token failing — check console for verdict failures |
| Firebase console shows "0 verified, N unverified" requests | Play Integrity provider not actually wired, OR enforcement not enabled (check App Check → Apps → APIs tab) |
| Verdict failures in console with "Device integrity check failed" | Rooted/emulator/sideloaded build — expected; legitimate users on Play-Services-equipped devices should pass |
| Verdict failures with "App integrity check failed" | APK not built with the upload key that's registered with Play Integrity. Sign release builds with the upload-key from Play Console only. |

### Enforcement gate (delayed enable)

Firebase App Check has a **monitor-only mode** (track verdicts but don't reject calls) and an **enforced mode** (reject calls without valid tokens). The console default is monitor-only.

**Recommended rollout:**

1. Story 1.4 ships with App Check in monitor-only mode. Verify Recent Requests show legit traffic.
2. After at least 24 hours of monitoring + zero unexpected rejections, flip Firestore enforcement ON: Firebase console → App Check → APIs → Cloud Firestore → **Enforce**.
3. Repeat for any future Firebase services (Cloud Storage if added, etc.).

This avoids accidentally locking out real users on day 1 from a misconfiguration.

---

## Debug provider (development) setup

The debug provider exists because real Play Integrity attestation fails on:

- Emulators without Play Services (AOSP system images, most stock AVDs)
- Sideloaded debug builds (the upload-key SHA1 isn't registered)
- Rooted devices

For local development, FirebaseBootstrap installs `DebugAppCheckProviderFactory` when `BuildConfig.DEBUG == true`. On first run, it logs a debug token to Logcat:

```
DebugAppCheckProvider: Enter this debug secret into the allow list in
the Firebase Console for your project: <UUID-style-token>
```

### Per-device debug token registration (Bania)

For each dev device:

1. Run debug build on the device. Filter Logcat for `DebugAppCheckProvider`.
2. Copy the token.
3. Firebase console → App Check → Apps → Android app → **⚙ Manage debug tokens → Add debug token**.
4. Paste + name it (`bania-pixel-debug`, `s24-ultra-debug`, etc.).
5. Save. Subsequent App Check calls from that device use the registered token.

**Alternative: persistent token via `local.properties`** — FirebaseBootstrap reads `firebaseAppCheckDebugToken` from `android/local.properties` (gitignored) and seeds the debug provider with it. Use this to make CI / fresh AVDs reproducible without re-registering each time.

```properties
# android/local.properties (gitignored)
firebaseAppCheckDebugToken=<paste-token-from-firebase-console>
```

---

## References

- [Story 1.4 spec](../../_bmad-output/implementation-artifacts/1-4-firebase-init-firestore-rules-baseline-app-check-providers.md)
- [Setup runbook](../../docs/runbooks/firebase-setup-android.md)
- [Firebase App Check Android docs](https://firebase.google.com/docs/app-check/android/play-integrity-provider)
- [Debug provider docs](https://firebase.google.com/docs/app-check/android/debug-provider)
- [Play Integrity API overview](https://developer.android.com/google/play/integrity/overview)
