# TranslatorRep

Personal-use bidirectional Indonesian↔English live-captioned voice + video call app for two Paired Users — Bania (Android) and his girlfriend (iOS).

WebRTC media via LiveKit OSS on Oracle Always-Free; on-device ASR (Android `SpeechRecognizer` + iOS Whisper.cpp); on-device translation (NLLB-200 / MADLAD / Gemma 2B, Week-1 bake-off locks model) wrapped by a `ParticleProcessor` decorator that preserves Indonesian discourse particles, pronoun register, Sundanese lexical insertions, partner honorifics, religious expressions, indirect refusals, and Gen-Z slang; E2EE media + Data Channel via WebRTC Insertable Streams + per-Call X25519 ECDH key exchange.

**Operating cost target:** $0/month (plus ~$10/year fixed for `xaeryx.com` domain).

## Repository Layout

| Path | Contents |
|---|---|
| [`_bmad-output/planning-artifacts/`](_bmad-output/planning-artifacts/) | PRD (rev 3), UX spec, Architecture, Epics, Brief, Domain Research, Technical Research, Implementation Readiness report |
| [`shared/`](shared/) | Cross-platform contracts — canonical names, error codes, Data Channel JSON Schema v1, auth-proxy API spec, RoomState derivation rules, ParticleProcessor fixtures, regression-corpus scaffold |
| [`docs/runbooks/`](docs/runbooks/) | Solo-dev scope-cut runbook, iOS-on-Mac setup runbook (more added per stories) |
| [`android/`](android/) | Android native app (Kotlin, Compose, minSDK 33, target SDK 35) — **Story 1.1 scaffold** |
| `ios/` | iOS native app (Swift, SwiftUI, minOS 17, target iOS 26) — **Story 1.2, on Mac** |
| `infra/` | Oracle VM Docker Compose stack (LiveKit + Redis + Caddy + Node.js auth-proxy) — Story 1.3 |
| `firebase/` | Firebase Auth + Firestore rules + App Check config — Story 1.4 |

## Status

| Story | State | Notes |
|---|---|---|
| 1.1 Android scaffold | 🟡 Files generated; needs Android Studio open + first build | See [android/README.md](android/README.md) |
| 1.2 iOS scaffold | ⏸ Sequenced on Mac | See [docs/runbooks/ios-setup-on-mac.md](docs/runbooks/ios-setup-on-mac.md) |
| 1.3 Oracle VM + Docker | ⏸ Blocked on Oracle/Cloudflare account + ~$10/yr domain | Code/config will be written autonomously once accounts exist |
| 1.4 Firebase + App Check | ⏸ Blocked on Google account / Firebase project creation | Code/config will be written autonomously once project exists |
| 1.7 Shared specs | ✅ Done — see [shared/](shared/) |
| 1.14a PRD reconciliation | ✅ Done — PRD rev 3 |
| 1.14b UX spec reconciliation | ✅ Done — Theme B removed, 10 new component specs added |
| 1.14c Solo-dev scope-cuts runbook | ✅ Done — see [docs/runbooks/solo-dev-scope-cuts.md](docs/runbooks/solo-dev-scope-cuts.md) |

## Where to Start

1. Read the [Implementation Readiness Report](_bmad-output/planning-artifacts/implementation-readiness-report-2026-05-22.md) for the planning-to-implementation handoff.
2. Read the [Solo-Dev Scope-Cuts Runbook](docs/runbooks/solo-dev-scope-cuts.md) NOW (not at trigger time, by definition).
3. See [android/README.md](android/README.md) to open the Android scaffold in Android Studio.
4. See [docs/runbooks/ios-setup-on-mac.md](docs/runbooks/ios-setup-on-mac.md) for the iOS counterpart when you switch to the Mac.

## License

Personal-use project. No license declared. Not for redistribution.
