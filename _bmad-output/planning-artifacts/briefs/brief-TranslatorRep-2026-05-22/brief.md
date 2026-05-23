---
title: TranslatorRep — Product Brief
status: draft
created: 2026-05-22
updated: 2026-05-22
author: Bania
---

# Product Brief: TranslatorRep

## Executive Summary

TranslatorRep is a custom audio-only video-calling app that Bania and his girlfriend install on their phones (Android and iPhone) to have **real-time captioned voice conversations across the English ↔ Indonesian language barrier**. Each side speaks in their own language; both see the other's speech as live captions on screen, translated in under three seconds.

The product exists because the obvious alternatives don't work. WhatsApp, FaceTime, and every other consumer call platform run inside OS sandboxes that block third-party apps from capturing call audio or drawing translation overlays on top of an active call. Six steps of technical research confirmed this against primary Android and Apple developer docs — the floating-HUD-over-WhatsApp pattern Bania initially imagined is technically impossible. The buildable answer is to *own the call*: a small, focused app whose only job is "translated voice calls between two paired people."

For Bania and his girlfriend, this is not a market product — it's a tool to remove the friction inside the deep conversations they already have. WhatsApp stays for everything else.

## The Problem

Bania speaks English; his girlfriend speaks Indonesian and Sundanese, and **switches between them mid-conversation**. She has some conversational English but is not fluent. He speaks no Indonesian or Sundanese.

They already have long, deep conversations — this isn't a relationship where they can't communicate. It's a relationship where **misunderstandings show up inside those conversations**, in the moments that matter most: nuance, emotional weight, slang, jokes that need context. A perfectly fine conversation can land badly because a single phrase didn't translate cleanly in her head before she said it in English, or because he heard her words but missed her meaning.

The dominant friction is **asymmetric effort**: she carries the entire translation load mentally while talking to him. He doesn't share that load because he can't — he has no Indonesian. Over a 30-minute call she's done thousands of micro-translations and dozens of "wait, the right English word is..." moments. He's just had a conversation. This invisible labor isn't sustainable indefinitely, and it warps the rhythm of how they talk.

Today's workarounds — Google Translate copy-paste over text, halting in-call translations, accepting that depth comes at her expense — work but cost something each time.

## The Solution

A two-person audio-calling app, polished but minimal in scope:

- **Pair once.** Both install the app, anonymously sign in, exchange a 6-digit pairing code. Paired forever.
- **Call like any other app.** Tap to call; native iOS CallKit / Android ConnectionService make incoming calls ring like a normal phone call, lock screen and all.
- **Inline captions.** Both sides see a live scrolling caption area showing what was just said (source language) and the translation (target language). Captions appear within ~2 seconds of speech.
- **No video v1.** Voice and captions only. Faces stay on WhatsApp.
- **Free forever** at this scale. Architecture runs on free tiers of LiveKit Cloud, Firebase, Google Gemini AI Studio, and Cloud Run.

Architecture detail is in the [TR document](../../research/technical-cross-platform-indonesian-sundanese-live-translation-whatsapp-research-2026-05-22.md); this brief stays at the experience level.

**Known v1 gap to be honest about:** because v1 is Indonesian-only, the moments she switches into Sundanese mid-conversation will not translate. Those phrases will appear as garbled Indonesian or be dropped. v2 closes this gap — and given that mid-conversation switching is her actual pattern, v2 is not optional eventually. It's not in v1 for build-speed reasons, not because it doesn't matter.

## Who This Serves

**Bania (Android, English) and his girlfriend (iPhone, Indonesian + Sundanese).** Two users. Two specific people in a relationship.

What success looks like for him: he stops missing nuance. The conversations they're already having stop having quiet misunderstanding undercurrents.

What success looks like for her: she stops carrying the translation load alone. She speaks Indonesian or Sundanese and trusts that he gets what she meant. There's also a second-order signal — *he built this for them* — which matters independently of how well the captions work.

This is not a product for a market segment. It's designed for two specific people, and the design accountability is to them.

## Success Criteria

Three concentric circles, weakest to strongest:

1. **Functional baseline:** 5 successful translated calls without bugs. Translation latency median <2.5s, p95 <4s. iPhone battery drain <30% per 30-min call. Cost: $0/month sustained.
2. **Quality bar:** Subjective translation accuracy ≥80% on conversational speech across 10 sample calls.
3. **The metric that matters:** **They use TranslatorRep ≥3x per week for the first month**, instead of falling back to WhatsApp-with-no-translation. If they don't pick it up over the easier-to-reach existing app, the technical work was wasted.

And one qualitative win that justifies the build: at some point in the first month, they have a long, deep conversation where **no miscommunication happens** — where every nuance lands the way the speaker meant it. That's the real success signal.

## Scope and Timeline

**v1 in scope:**

- Bidirectional Indonesian ↔ English live captioned voice calls between two paired users.
- Native Android (Kotlin/Compose) and iOS (Swift/SwiftUI) apps.
- Inline captions in the call screen (no overlay, no external display).
- Pairing via 6-digit code; anonymous Firebase Auth.
- Incoming-call push with native CallKit / ConnectionService UI.
- Polished, native UI from day one.
- Settings: provider toggles, transcript history opt-in (per-device, never synced).
- $0/month operating cost.
- Personal sideload distribution — no Play Store, no App Store.

**v2 (sequence likely in this order):**

- **Sundanese support.** Closes the code-switching gap. Adds `chirp_2 chunked Recognize` + Google NMT for SU.
- **Video.** Captions overlaid on remote video stream.

**Out entirely:**

- WhatsApp / FaceTime / Meet integration (technically impossible per TR).
- Group calls.
- Conversation content analytics or telemetry.

**Timeline:** ~1 week ramp + 4–6 weeks of focused solo work for v1. Bania wants this fast — the architecture and stack choices have already been optimized for build speed (LiveKit Cloud over self-hosted WebRTC, native over cross-platform framework, free tiers over enterprise providers). No further compression possible without sacrificing the polish bar.

## Vision

If v1 lands and gets used, the natural arc:

- **Weeks 1–4 after ship:** v1 in daily use; bugs found and fixed.
- **Months 2–3:** v2 Sundanese closes the code-switching gap. This is the version where the app earns its place permanently.
- **Months 3–6:** video, polish, transcript review features if there's appetite.
- **Beyond:** publishing it on App/Play Stores for other couples in similar situations is a *possibility, not a plan*. Bania wants this for them first, fast. Anything beyond that has to be earned by sustained use.

The honest core: this app exists to help two specific people who love each other communicate without translation labor between them. Whether it ever becomes more than that is a question the experience will answer.
