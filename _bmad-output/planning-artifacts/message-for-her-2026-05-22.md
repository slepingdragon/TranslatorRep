---
title: Message to Bania's girlfriend — UX spec reaction request
date: 2026-05-22
purpose: Mary's recommendation from Step 4 party round — get her direct reaction to the spec before running CA
register: intimate-casual (aku/kamu), Gen-Z code-switched Indonesian; AI-translation disclosure included
---

# Message to send (Indonesian)

> Copy the block below and send via WhatsApp.

```
Hey sayang,

Aku mau nanya satu hal, dan aku butuh reaksi kamu — bukan persetujuan ya.

Beberapa minggu terakhir aku lagi ngedesain app translator-call buat kita. Caption-nya, gimana app-nya "ngomong" balik, gimana dia handle kata-kata Sunda kamu pas dia ga bisa nangkep. Tapi yang ngeganggu aku: keputusan-keputusan ini, kebanyakan aku ambil berdasarkan apa yang aku *pikir* kamu pengen — bukan dari apa yang beneran kamu bilang ke aku. Gap itu yang aku pengen tutup dulu sebelum aku mulai build.

Bisa kasih aku 10 menit — pas next call atau kapan kamu sempet — buat react aja sama yang aku desain? Bukan buat approve, bukan biar sopan. Bilang aja apa yang kerasa aneh. Apa yang kerasa kayak ngawasin kamu. Apa yang bakal bikin kamu mulai ngomong beda gara-gara app-nya lagi denger. Apa yang ga sesuai sama cara kita ngobrol beneran.

Khususnya:
- Cara app-nya handle suara kamu, kerasa kayak dia nilai-nilai kamu ga sih?
- Pas kamu campur Sunda ke obrolan bahasa Indonesia — cara app-nya handle itu bikin kamu kerasa dibedain atau aneh ga?
- Caption-nya kelihatan oke ga? Enak dibaca?
- Ada yang kerasa kayak "app AI" daripada panggilan telepon biasa?

Aku tunjukin mockup-nya nanti pas ketemu, atau bisa juga aku kirim screenshot.

FYI — pesan ini diterjemahin dari bahasa Inggris ke Indonesia sama AI translator ya. Grammar-nya harusnya bener, tapi kalo ada yang bunyinya off atau ga sesuai sama cara aku biasa ngomong, itu AI-nya — bilang aja biar bisa aku perbaiki buat app-nya nanti.

Sayang kamu 🤍
```

# English original (your reference)

> What you actually wrote. Don't send this part — it's for your records and for fixing the Indonesian if she flags anything as off-register.

```
Hey sayang,

I want to ask you something, and I need you to react — not approve.

For the past few weeks I've been designing the translator-call app for us. The captions, how the app talks back, how it handles your Sundanese phrases when it can't catch them. But here's what's been bothering me: most of these decisions, I made based on what I *think* you'd want — not what you actually told me. That's a gap I want to close before I start building.

Can you give me 10 minutes — next time we talk or whenever you have time — to just react to what I designed? Not to approve, not to be polite. Tell me what feels weird. What feels like it's watching you. What would make you start speaking differently because the app is listening. What doesn't sound like how we actually talk.

Especially:
- Does the way the app handles your speech feel like it's grading you?
- When you mix Sundanese into Indonesian — does the way the app handles that make you feel singled out or weird?
- Do the captions look right? Are they easy to read?
- Does anything feel like an "AI app" vs a regular phone call?

I'll show you the mockup next time we're together, or I can send screenshots.

FYI — this message was translated from English to Indonesian by an AI translator. The grammar should be fine, but if anything sounds off or doesn't match how I'd actually say it, that's the AI — let me know so I can fix it in the real app.

Love you 🤍
```

# Translation notes

The Indonesian version uses the same register and rules the v1 Gemini system prompt enforces — so her reaction to the *translation quality* doubles as a sanity check on the system prompt itself.

- **Register:** aku/kamu intimate-casual (never saya/Anda).
- **Code-switching:** English loanwords kept where Indonesian Gen-Z naturally uses them (`app`, `caption`, `build`, `gap`, `react`, `approve`, `screenshot`, `mockup`, `next call`, `AI translator`, `FYI`, `off`, `grammar`). This is how she actually talks per DR §3 (Gen-Z slang).
- **Particles preserved:** `ya` (tag-question intimacy), `sih` (gentle doubt), `aja` (minimizer), `dong`-free intentionally (would've sounded pushy here).
- **Anti-othering:** "othering" rendered as `bikin kamu kerasa dibedain atau aneh ga?` — describes the feeling rather than naming the concept. Same move the `SundanesePlaceholderRow` does in the app (`[Sundanese]` quiet placeholder, no alarm).
- **"Sundanese" → "Sunda":** Indonesian uses the country/language stem, not the English -nese suffix.
- **AI disclosure paragraph** uses the same plain-kind voice as the spec's §"Forbidden Strings" — direct, no apologetic-corporate hedging ("our team has been notified"), no AI sparkle.

# How to send

1. Copy just the **first block** (Indonesian) into WhatsApp. Don't include the English version — that's yours.
2. If she asks what the AI is or pushes back on register, paste the English version for context and ask her to mark what she'd change.
3. **Take screenshots of the In-Call screen in Theme A** from [ux-design-directions.html](./ux-design-directions.html) — open the file in your browser, take a phone-shaped screenshot of one of the In-Call mockups, send via WhatsApp. The Theme A version is the canonical reference and the most "her" use case (9pm Bandung kitchen floor, dark mode). You can also send the Theme C image-bg one so she sees that personalization option exists.
4. **Don't lead with the spec** in the reaction conversation. Lead with the questions in the message — let her answer in her own words first. The spec is reference material, not a checklist.
5. **Write down what she says verbatim**, even the small "I dunno, it's fine" bits. Inferences disguised as observations are exactly what Mary flagged in the Step 4 party round. Her actual words convert hypothesis → evidence.

When you've heard her out, the next BMAD step (CA — Create Architecture) can land cleanly on top of validated UX rather than inferred UX.
