# Release Keystore Setup (Story 1.6d + Story 1.4c)

> **Authority:** Walkthrough for generating the upload key that signs release builds for Play Store distribution. Required before starting Story 1.4c (Play Store Internal Testing CI wiring). Story 1.6d landed the signing-config infrastructure that consumes the keystore created here.
>
> **Estimated time:** ~10-15 min one-time. Storing the backup safely takes another ~5 min.
>
> **Prereq:** JDK 17 installed (you have it — Android Studio bundles it; verified via `./gradlew :app:assembleDebug` works).

---

## ⚠️ READ THIS FIRST — keystore loss is unrecoverable

The upload keystore you generate is the **only** key Google Play accepts for updating your app. If you lose it:

- You **cannot publish updates** to this app on Play Store. Ever.
- Recovery options: contact Google Play support to reset the upload key (works only if you enrolled in Play App Signing, which you will), wait days for support response, hope it works.

**Back up the keystore in at least 3 places before continuing past §3.** I'm not joking about this. Multiple solo devs have lost months of work to keystore loss.

Suggested backup pattern:
1. **1Password / Bitwarden / encrypted password manager:** attach the `.jks` file as a secure-document attachment AND store the passwords as a separate item.
2. **Offline encrypted USB drive:** copy the `.jks` to a USB stick, store in a desk drawer or safety-deposit box.
3. **Cloud storage of encrypted blob:** `gpg --symmetric translatorrep-release.jks` → upload `.gpg` to Drive/Dropbox/iCloud. (The `.jks` is already password-protected, so this is belt-and-suspenders.)

---

## §1. Generate the keystore

In a PowerShell terminal at the project root:

```powershell
cd "C:\Users\Brady J Bania\Desktop\ADEV\TranslatorRep\android\app"
keytool -genkey -v `
  -keystore translatorrep-release.jks `
  -keyalg RSA `
  -keysize 2048 `
  -validity 10000 `
  -alias translatorrep `
  -storetype JKS
```

You'll be prompted for:

1. **Keystore password:** make a strong password (24+ chars; use a password manager generator). Type it twice.
2. **What is your first and last name?** → `Brady Bania`
3. **What is the name of your organizational unit?** → `Xaeryx` (or leave blank)
4. **What is the name of your organization?** → `Xaeryx`
5. **What is the name of your City or Locality?** → your city
6. **What is the name of your State or Province?** → your state
7. **What is the two-letter country code for this unit?** → `US`
8. **Is CN=Brady Bania, OU=Xaeryx, O=Xaeryx, ... correct?** → `yes`
9. **Enter key password for <translatorrep>** → press Enter to reuse the keystore password (simpler; less to remember). If you set a separate one, write it down — losing it has the same blast radius as losing the keystore password.

Done. `translatorrep-release.jks` now exists at `android/app/translatorrep-release.jks`. **This file is gitignored** (per existing `*.jks` rule in `android/.gitignore`).

---

## §2. Create `keystore.properties`

The `signingConfigs.release` block in `android/app/build.gradle.kts` reads from `android/app/keystore.properties`. Create it:

`android/app/keystore.properties`:
```properties
storeFile=app/translatorrep-release.jks
storePassword=<your keystore password from §1>
keyAlias=translatorrep
keyPassword=<your key password from §1 — same as keystore password if you pressed Enter>
```

**This file is gitignored** (per existing `keystore.properties` rule in `android/.gitignore`). Verify with `git status` — `keystore.properties` should NOT appear in untracked files.

---

## §3. BACK UP THE KEYSTORE NOW

Don't continue to §4 until you've done all 3 backups from the warning at the top.

Quick checklist:
- [ ] 1Password (or your password manager) has the `.jks` file as a secure attachment + passwords as a separate item.
- [ ] Offline backup somewhere (USB drive in a drawer, etc.).
- [ ] Encrypted cloud backup (`gpg --symmetric translatorrep-release.jks` → upload `.gpg`).

---

## §4. Verify the signing config works

From the `android/` directory:

```powershell
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" PATH="$JAVA_HOME/bin:$PATH" ./gradlew :app:assembleRelease --no-daemon
```

Expect: `BUILD SUCCESSFUL` and **no** Gradle warning about `keystore.properties not found` (which appears if you skipped §2). The release APK lands at `android/app/build/outputs/apk/release/app-release.apk`.

Verify the APK is signed with your upload key (not the debug key). Use `apksigner` (NOT `keytool` — modern APKs use V2/V3 signing which `keytool -printcert -jarfile` can't read):

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat" verify --print-certs app\build\outputs\apk\release\app-release.apk
```

The output should show `Signer #1 certificate DN: CN=Brady Bania, OU=Xaeryx, O=Xaeryx, ...` — NOT `CN=Android Debug, O=Android, C=US` (which is the debug-keystore signature). If you see the debug signature, the signing config fell back to debug because `keystore.properties` wasn't found — re-check §2 path/file.

---

## §5. Add the keystore to GH Actions secrets (Story 1.4c — defer until then)

When you start Story 1.4c, the CI workflow needs to materialize the keystore from a GH secret (same pattern as `GOOGLE_SERVICES_JSON`). Defer this until 1.4c — don't do it now.

Preview of the Story 1.4c CI secret setup:

```powershell
# Base64-encode the keystore for GH secret storage
[Convert]::ToBase64String([IO.File]::ReadAllBytes("translatorrep-release.jks")) | Set-Clipboard
# Paste from clipboard:
gh secret set RELEASE_KEYSTORE_BASE64

# Store the passwords separately
"<your keystore password>" | gh secret set RELEASE_KEYSTORE_PASSWORD
"<your key password>" | gh secret set RELEASE_KEY_PASSWORD
"translatorrep" | gh secret set RELEASE_KEY_ALIAS
```

Story 1.4c will then add a CI workflow step that decodes the base64 keystore + writes `app/keystore.properties` from the secrets before `assembleRelease`.

---

## §6. Common pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `assembleRelease` warning "keystore.properties not found" but expected it to find one | Wrong path; file is at wrong location | File MUST be at `android/app/keystore.properties` (next to `build.gradle.kts`), NOT at repo root |
| `assembleRelease` fails with "keystore was tampered with, or password was incorrect" | `keystore.properties` password doesn't match what you set in §1 | Re-check; passwords are case-sensitive |
| Released APK signed with `Android Debug` cert | `keystore.properties` not loaded (returned to debug fallback) | Run `assembleRelease` again; check Gradle output for the warning |
| `keytool: command not found` | JDK not on PATH | Use the full path: `& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe"` |
| `keytool -printcert` says "Not a signed jar file" | Modern APKs use V2/V3 signing; `keytool` only reads V1 | Use `apksigner` from `$LOCALAPPDATA\Android\Sdk\build-tools\35.0.0\apksigner.bat` instead (per §4) |
| Lost the keystore | 😱 | Contact Google Play support; explain you need an upload key reset; provide proof of app ownership. Plan for days of waiting. |

---

## §7. References

- [Android sign your app — official docs](https://developer.android.com/studio/publish/app-signing)
- [Play App Signing FAQ](https://support.google.com/googleplay/android-developer/answer/9842756) — explains the upload key vs. app signing key separation
- [keytool reference (JDK 17)](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html)
- [Story 1.6d](../../_bmad-output/implementation-artifacts/1-6d-android-ci-flesh-out.md) — the signing-config infrastructure that consumes this keystore
- Story 1.4c (when created) — the CI release-build + Play Console upload story that uses this keystore via GH secrets
