# Firebase App Check — iOS Provider Setup

> **STATUS: DEFERRED to Story 1-4b-ios-firebase-init** (future Mac/iOS Claude session).
>
> Per Bania's direction 2026-05-23 ("we will do android all first, then i want to have a separate claude session in the future to build out the ios app"), iOS Firebase setup is sequenced after Android Story 1.4 lands AND after Story 1.2 (Xcode project on Mac) lands.

---

## When this file gets populated

The future Story 1-4b will:

1. Document the manual `GoogleService-Info.plist` download + placement at `ios/TranslatorRep/Resources/GoogleService-Info.plist` (gitignored).
2. Register the DeviceCheck App Check provider per [Firebase App Check iOS docs](https://firebase.google.com/docs/app-check/ios/devicecheck-provider) (or the newer **App Attest** provider for iOS 14+ devices, which is recommended over DeviceCheck for new apps).
3. Mirror this file's structure: setup steps + per-device debug token + enforcement gate rollout.

---

## Why iOS is not done together with Android

- Story 1.2 (iOS Xcode project scaffold) is gated on Mac access.
- Bania's solo-dev strategy prioritizes shipping Android end-to-end first, then doing iOS in a single focused session.
- Doing iOS App Check now without an Xcode project would mean writing Swift code that can't compile against any target — wasted work that would rot before the Mac session picks it up.

When the Mac/iOS session runs Story 1-4b, this file becomes the canonical record for iOS App Check setup, parallel to [`android-providers.md`](./android-providers.md).
