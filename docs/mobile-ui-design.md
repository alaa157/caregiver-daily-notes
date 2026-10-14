# Mobile UI/UX Design — Caregiver Daily Notes

**Branch:** `feat/mobile-ui-design`  
**Status:** Design specification (implementation follows this document)  
**Canva reference:** https://canva.link/enjs7gleeugybcn

---

## Design Read

Mobile product UI for caregivers (often family members) in Egypt / Arabic-speaking contexts.  
Bilingual Arabic + English with proper RTL support.  
Aesthetic: calm-but-alive, warm, human, dignified — the feeling of a well-designed notebook that happens to be digital.  
Audience is under emotional load; the interface must feel caring rather than clinical or sterile.

**Rejected defaults:** generic teal healthcare cards, AI-purple gradients, Inter + slate, three equal feature cards, flat Material templates, sterile white medical look.

---

## Design Tokens

### Color

| Token | Hex | Role |
|-------|-----|------|
| `sand-50` | `#F7F3EB` | App background, paper feel |
| `sand-100` | `#EFE9DF` | Subtle surface / card secondary |
| `charcoal-900` | `#1C1917` | Primary text |
| `charcoal-600` | `#57534E` | Secondary text |
| `clay-500` | `#C47A5A` | Primary accent / CTAs |
| `clay-600` | `#A86445` | Pressed / strong accent |
| `sage-500` | `#6B8F71` | Positive / good state |
| `coral-500` | `#E07A5F` | Red-flag / urgent (warm, not alarming) |
| `white` | `#FFFFFF` | Cards, elevated surfaces |

Soft natural shadows only. No harsh elevation.

### Typography

- **Display / Titles:** Warm humanist sans (or soft geometric) with slight character — never pure Inter/Roboto.
- **Body / UI:** Highly readable humanist sans, excellent Arabic support.
- **Scale:** Generous, clear hierarchy. Large tap targets. Arabic text must feel native, never secondary.

### Shape & Depth

- Soft, organic rounded corners (not generic 12px system radius).
- Subtle paper-like or very light grain texture optional on background.
- Cards feel like physical notes: soft shadow + slight lift on press.

### Motion Language

- Soft fade + slight scale on card entry.
- Gentle progress indicator on note save.
- Calm, short transitions between screens.
- No looping decorative motion. Motion only serves clarity or emotional reassurance.

---

## Screens

### 1. Splash
Quiet, warm. Soft illustration of care (hands, light, notebook). Logo + short tagline. No hard sell.

### 2. Login
Clean and trustworthy. Language toggle (Arabic ↔ English) clearly visible. Soft clay primary button. Dignity over decoration.

### 3. Home / Dashboard
- List of care recipients with personality (photo or monogram + recent status).
- Soft coral red-flag banner when deterministic flags exist — urgent but not alarming.
- Primary action “Add today’s note” feels inviting and central.
- Calm empty state when no recipients yet.

### 4. Care Recipient Profile
Human and warm. Recent state summary, quick access to notes and plans. No clinical coldness.

### 5. New Daily Note
Structured fields (Mood, Appetite, Sleep, Mobility, Medication taken, Pain, Falls) designed as beautiful, easy-to-tap chips/cards rather than dense form fields.  
Generous free-text area that gracefully supports mixed Arabic/English.  
Save feels satisfying and calm.

### 6. Notes History
Calm timeline or card list, grouped by date. Light filters (date, recipient). Easy scanning under stress.

### 7. Note Detail
Readable. Addendum support shown gently. Clear distinction between original and correction.

### 8. AI Summary
Clear period indicator (7 / 14 / 30 days). Key observations grounded and visual. Avoid chatbot aesthetic. Feels like a thoughtful summary, not a generic AI dump.

### 9. AI Suggested Care Plan
Plan items as thoughtful cards with short evidence quotes.  
Clear, confident actions: **Accept** / **Edit then accept** / **Dismiss**.  
Respect the caregiver’s judgment.

### 10. Plan History
Versioned archive (v1 → v2 → …). Calm, scannable, no visual noise.

### 11. Settings
Language toggle, profile, simple and respectful. No clutter.

---

## RTL & Internationalization

- Full RTL layout for Arabic.
- Bidirectional text handling in free-text notes.
- Language switch is first-class, not buried.
- Sample content in designs must include realistic mixed Arabic + English.

---

## Accessibility & Care Principles

- Large readable type and generous tap targets.
- High contrast on sand background.
- Red-flag treatment is warm coral, never pure medical red alarm.
- Empty states and error states are human and helpful.
- The interface should reduce cognitive load for a tired caregiver.

---

## Implementation Notes

- This document is the source of truth for visual and interaction direction of the mobile client.
- Canva presentation is the visual reference: https://canva.link/enjs7gleeugybcn
- Implementation (Kotlin rewrite or React Native) must follow these tokens and screen intentions.
- Do not fall back to generic Material or system defaults without explicit decision.

---

*Designed with intentional rejection of clinical/template aesthetics. Warmth, dignity, and ease under stress are the product.*
