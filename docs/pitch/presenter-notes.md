# Rafeek — Pitch Deck Presenter Notes

**DEPI Software Testing Track · Capstone 2026 · 15 slides · ~14:00 + Q&A**

Companion to `rafeek-pitch-deck.html` (press **N** in the deck for the live notes panel) and `rafeek-pitch-deck.pdf`.

## Run of show

| # | Slide | Speaker | Time |
|---|---|---|---|
| 01 | The Opening | Alaa Shehata | 0:45 |
| 02 | The Problem | Alaa Shehata | 0:50 |
| 03 | The Solution | Alaa Shehata | 1:00 |
| 04 | The Product Journey | Mahmoud Hewidy | 1:00 |
| 05 | The Web Experience | Mahmoud Hewidy | 0:50 |
| 06 | The Android Experience | Mahmoud Hewidy | 1:00 |
| 07 | Bilingual by Design | Mahmoud Hewidy | 0:50 |
| 08 | System Architecture | Ebrahim Ahmed | 1:10 |
| 09 | Backend Engineering & Data | Ebrahim Ahmed | 0:50 |
| 10 | Data Integrity & Safety | Ebrahim Ahmed | 1:10 |
| 11 | The Software Testing Strategy | Mohamed Hemdan | 1:00 |
| 12 | Quality Evidence | Mohamed Hemdan | 1:00 |
| 13 | Defects & AI Evaluation | Mohamed Hemdan | 1:00 |
| 14 | Business Value & Adoption | Alaa Shehata | 0:50 |
| 15 | The Team & the Close | Shared close · led by Mohamed Hemdan | 0:45 |

Total scripted runtime: **14:00** (target 12–15 min). Q&A follows slide 15.

Speaker blocks: **Alaa Shehata** 1–3 & 14 · **Mahmoud Hewidy** 4–7 · **Ebrahim Ahmed** 8–10 · **Mohamed Hemdan** 11–13 + shared close (15). Hand-offs are scripted at the end of slides 3, 7, 10, 13, 14.

---

## Slide 01 — The Opening

**Speaker:** Alaa Shehata · **Time:** 0:45

OPENING — 0:45. "Good morning. Care doesn't happen in appointments — it happens in hundreds of small moments every day: a meal eaten, a night slept badly, a dose missed, a fall nobody wrote down. I'm Alaa Shehata, project lead of Rafeek — رفيق, Arabic for 'companion'. Rafeek is a bilingual platform that keeps those moments together, so the people providing care never lose the thread. Over the next fourteen minutes we'll show you the product, the engineering behind it, and the testing evidence that proves it works — because this is a software-testing capstone, and quality is our headline feature."

**Takeaway:** Rafeek turns scattered daily care moments into a record you can trust.

**Transition:** "Let me start with the problem we kept seeing." → slide 2 (Alaa continues).

---

## Slide 02 — The Problem

**Speaker:** Alaa Shehata · **Time:** 0:50

PROBLEM — 0:50. "None of these are hypothetical failures — they're the default state of informal care documentation. A daughter caring for her mother writes in a notebook; the professional caregiver sends voice notes; the agency keeps a binder. When information lives in five places, the history of a person's care effectively doesn't exist. And note what we deliberately did NOT do here: no invented prevalence statistics, no fabricated survey numbers. The problem is observable in how care actually works, and our answer had to respect that — structure without stripping away the caregiver's own words."

**Term:** 'care continuity' = the same understanding of a person's situation following them across days, shifts, and caregivers.

**Takeaway:** When documentation fragments, the person's history disappears.

**Transition:** "So we built one place where the pieces gather — literally our brand idea. Here's the solution." → slide 3 (Alaa continues).

---

## Slide 03 — The Solution

**Speaker:** Alaa Shehata · **Time:** 1:00

SOLUTION — 1:00. "Our brand mark is called The Gather: four arc-fragments around a shared centre. That's the product thesis. Separate observations — a mood, a meal, a night's sleep, a medication — only become a picture when they're held together. Rafeek does four things at once: it structures the day, it preserves the history immutably, it works natively in Arabic and English, and it adds AI assistance that stays on a leash — summaries grounded in the actual notes, plans that a human must accept, edit, or dismiss. One positioning point matters for this panel: Rafeek is care-support documentation. It does not diagnose, it does not monitor medically, and it never gives medical advice."

**Takeaway:** The Gather is the architecture — fragments in, a trustworthy picture out.

**Transition:** "That's the idea. Mahmoud will now walk you through the actual product, step by step." → slide 4 (hand to Mahmoud Hewidy).

---

## Slide 04 — The Product Journey

**Speaker:** Mahmoud Hewidy · **Time:** 1:00

JOURNEY — 1:00. "Thanks, Alaa. I'm Mahmoud Hewidy, I build the web frontend, and this is the loop a caregiver actually lives in. You add a person — say, your mother. Each day you record one structured note: mood, appetite, sleep, mobility, whether medication was taken, a pain score from zero to ten, whether a fall happened, and your own words — up to twenty thousand characters, in either language. The moment you save, the original is locked. If something was wrong, you don't edit history — you append a correction, and the app replays the effective state from the full chain. Then, when you want the bigger picture, you request a summary over seven, fourteen, or thirty days, and review a suggested plan. Notice the design decision underneath: uncertainty is a value. 'Not recorded' and 'unknown' are different, and neither is guessed away."

**Takeaway:** One note a day becomes a history you can replay.

**Transition:** "Let me show you where this lives on the web." → slide 5 (Mahmoud continues).

---

## Slide 05 — The Web Experience

**Speaker:** Mahmoud Hewidy · **Time:** 0:50

WEB — 0:50. "The web client is React 18 with TypeScript — a single workspace, not a collection of pages. On the left, the navigation you see here is the app's real navigation: Overview, Recipients, Notes, Summaries, Plans. The context bar carries the selected date and recipient everywhere, so you never wonder whose record you're looking at. Two engineering points I care about as the frontend owner: first, accessibility is checked, not claimed — axe-core runs in every Playwright viewport project. Second, sessions are tab-scoped: the JWT sits in sessionStorage, not localStorage, so closing the tab ends the session. And everything you'll see in the evidence slides — the 1,086 web unit tests — tests this workspace's actual contracts."

**Takeaway:** One workspace, real structure, checked accessibility.

**Transition:** "Care doesn't only happen where there's a desk. Here's the Android side." → slide 6 (Mahmoud continues).

---

## Slide 06 — The Android Experience

**Speaker:** Mahmoud Hewidy · **Time:** 1:00

ANDROID — 1:00. "The Android app is native — Kotlin 2.0 and Jetpack Compose — and it makes a deliberately different architectural choice from the web. The official release is standalone and local-first: accounts live on the device, each account gets its own Room database, and notes, plans and summaries are stored on-device. No backend is required. That matters in real caregiving: the documentation works in a home with no reliable connection. AI is still available, but through the caregiver's own provider key, stored encrypted in the Android Keystore — never in plain storage, never shared between accounts. For this evaluation there's an offline demo with synthetic data: long-press the logo on the login screen. One distinction to keep clear: web equals connected workspace; Android equals independent device record. Same rules, different execution path."

**Takeaway:** The record survives without a network — and keys never leave the Keystore.

**Transition:** "Both apps share one more non-negotiable: language." → slide 7 (Mahmoud continues).

---

## Slide 07 — Bilingual by Design

**Speaker:** Mahmoud Hewidy · **Time:** 0:50

BILINGUAL — 0:50. "This mirror shows the app's real strings, side by side — not a mockup. 'Save original note' and 'حفظ الملاحظة الأصلية' are the same button in the shipped product. Bilingual support here is an architectural decision: layouts are built with logical properties so RTL is a first-class mode, mixed-direction text is isolated so an English medication name inside an Arabic sentence never corrupts the line, and Arabic parity is enforced by tests — if a string exists in English and not Arabic, the suite fails. For the Egyptian and MENA context this is the difference between a translated app and an app that actually belongs to its users. Accessibility checks run in both languages, in every viewport."

**Takeaway:** Arabic isn't a toggle — it's a tested, structural guarantee.

**Transition:** "That's the product. Ebrahim will now take you under the hood — the architecture and the engineering that makes these promises real." → slide 8 (hand to Ebrahim Ahmed).

---

## Slide 08 — System Architecture

**Speaker:** Ebrahim Ahmed · **Time:** 1:10

ARCHITECTURE — 1:10. "Thank you, Mahmoud. I'm Ebrahim Ahmed, backend engineering. This diagram is the whole system in one view, and the key word is boundaries. Left: the connected path. The React client talks REST to a Java 21, Spring Boot 3.3 backend — and the backend is the authority. Business rules, ownership checks, deterministic safety signals, trend counts, plan versioning and audit all live server-side; clients render, they do not decide. PostgreSQL with Flyway migrations V1 through V17 holds append-only records. Right: the standalone path. The Android release runs independently — Room on-device, its own local accounts, and a direct line to AI providers using the caregiver's own key. Both paths call providers bring-your-own-key style, and both enforce the same output contract: schema-valid, grounded in source notes, medication-locked. The model's output is a suggestion that passed validation — never an instruction."

**Term:** BYOK = bring your own key; the user's provider account, the user's cost and data policy.

**Takeaway:** Two execution paths, one set of non-negotiable rules.

**Transition:** "Let's zoom into the backend itself — the contracts that make records reliable." → slide 9 (Ebrahim continues).

---

## Slide 09 — Backend Engineering & Data

**Speaker:** Ebrahim Ahmed · **Time:** 0:50

BACKEND — 0:50. "Eight domains share one API surface, and every request passes the same gauntlet: validation, JWT authentication, then owner-scoped queries. A detail we're proud of because it's a security decision: if you request another caregiver's resource, you get 404 — not 403. A 403 would confirm the record exists; a 404 reveals nothing. Writes carry an Idempotency-Key: retrying a save replays the original result instead of duplicating it, and a genuine conflict returns 409. That's what makes the 'safe retry' in the UI honest — the backend guarantees it. Errors follow one unified contract, migrations are versioned V1 to V17, and every meaningful action lands in an append-only audit log with secret redaction. These aren't aspirations; each one has dedicated tests you'll see in the evidence."

**Term:** idempotency = performing the same request twice has the same effect as once.

**Takeaway:** The contract is the product — retries are safe, ownership is invisible, history is provable.

**Transition:** "Now the principles that matter most in a care context: integrity and safety." → slide 10 (Ebrahim continues).

---

## Slide 10 — Data Integrity & Safety

**Speaker:** Ebrahim Ahmed · **Time:** 1:10

INTEGRITY & SAFETY — 1:10. "This is the slide I'd ask you to remember from the engineering section. Left: how a record lives. The original note is immutable. Corrections arrive as addenda or typed events — set a value, clear it, mark it unknown, mark it not recorded — and the effective state you see is a deterministic replay of the original plus every event. Anyone can reconstruct what was known, when, and by whom. Right: the model's leash. Red flags — a fall, high pain, repeated missed medication — are computed by ordinary code, before and independently of any LLM. The model may raise urgency; it can never lower or remove a flag, never touch medication names, doses or schedules, and plan actions come from a fixed catalog: check-ins, meal reminders, mobility goals, education, contact a doctor. Every claim must quote a real source note. And the final decision — accept, edit, dismiss — belongs to the caregiver. We say it in one line: the model never holds the pen on safety."

**Takeaway:** Immutability plus a leashed model equals a record a clinician could trust.

**Transition:** "Promises like these are only worth what you can prove. Mohamed will show you exactly how we prove them." → slide 11 (hand to Mohamed Hemdan).

---

## Slide 11 — The Software Testing Strategy

**Speaker:** Mohamed Hemdan · **Time:** 1:00

TESTING STRATEGY — 1:00. "Thank you, Ebrahim. I'm Mohamed Hemdan — backend and security, and I'll close out with our quality evidence. This is a Software Testing track capstone, so testing isn't a chapter at the end; it's how the project was run. We started from risk: what would actually harm a care recipient or leak their data? A model suppressing a safety flag. A silent edit to history. Cross-caregiver access. Model-authored medication advice. Each risk maps to a guard you can see in the pyramid — deterministic red-flag tests, immutability and replay tests, ownership tests, medication hard-lock tests, and a deterministic AI evaluation harness. The lifecycle is the full DEPI discipline: plan and analyze risk, design structured cases, execute, file defects with reproduction, fix, and verify regression. Every tool on this slide is in the repository — JUnit 5, Spring Boot Test, JaCoCo, Vitest, Testing Library, Playwright, axe-core. And there's a formal IEEE 829-style test-case specification — a 62-page PDF — prepared for DEPI review."

**Takeaway:** Risk chose the tests; the tests guard the promises.

**Transition:** "Strategy is intent. Here are the measured numbers." → slide 12 (Mohamed continues).

---

## Slide 12 — Quality Evidence

**Speaker:** Mohamed Hemdan · **Time:** 1:00

QUALITY EVIDENCE — 1:00. "These numbers are measured, not estimated, and each one is reproducible from the repository. Backend: seven hundred tests executed, seven hundred passed, zero failures, zero errors — across ninety-three test classes that boot real Spring contexts with Flyway migrations. JaCoCo coverage: ninety-three point three percent of lines, seventy-six point seven percent of branches. Web: one thousand eighty-six cases across forty-nine files, all passing. Now the distinction that matters for honesty: the Android and browser figures are inventory — one hundred ninety-two JVM classes with sixteen hundred fifty-four methods, thirty-six Compose classes with one hundred eighty methods, eighty Playwright definitions across four viewports, three hundred twenty defined runs. They're wired into CI and the first full runs are in flight; we do not claim they all executed and passed in this verification pass. The repository states exactly this, and so do we. Five hundred forty-two issues closed, and a sixty-two-page formal test specification behind all of it."

**Takeaway:** Executed results and inventory are labeled separately — that's the discipline.

**Transition:** "Tests also found real problems. Here's what happened to them — and how we evaluate the AI itself." → slide 13 (Mohamed continues).

---

## Slide 13 — Defects & AI Evaluation

**Speaker:** Mohamed Hemdan · **Time:** 1:00

DEFECTS & AI EVAL — 1:00. "Eight defects were documented by this testing effort; seven are fixed and verified; one — DEF-008, issue eleven-oh-two, a setup-check key-state problem on the cloud harness — is openly tracked as under investigation. We show the open one on purpose: a defect register you can trust includes what isn't finished. The case study is DEF-001: a single migration statement incompatible with H2 cascaded into three hundred twenty-five errors. We isolated it, fixed it, verified it — and watched coverage jump from a misleading fifty percent floor to the true ninety-three point three. On the right: the AI itself is tested deterministically. Twenty-two synthetic golden cases — English, Modern Standard Arabic, Egyptian dialect, mixed text, contradictory entries, noise, irrelevant content, and prompt-injection attempts treated as data. Four metrics: red-flag recall, schema validity, groundedness, medication safety — all enforced at one-point-zero by a test that fails CI on regression. To be explicit: these are harness metrics on synthetic data. They are not clinical validation, and we claim none."

**Takeaway:** Even the AI has a test suite — and the open defect is on the slide, not hidden.

**Transition:** "Alaa will close with who this is for, and how we'd responsibly find out." → slide 14 (hand back to Alaa Shehata).

---

## Slide 14 — Business Value & Adoption

**Speaker:** Alaa Shehata · **Time:** 0:50

BUSINESS — 0:50. "Thank you, Mohamed. So who is this for, and how would we find out responsibly? Three segments to investigate — family caregivers first, because in Egypt and the wider region care is family-led; then professional caregivers and small care organizations. The value proposition is deliberately concrete: organized documentation, continuity, traceable history, bilingual workflows, transparent AI, human-reviewed actions. Notice what this slide does not contain: no revenue projections, no pricing, no invented market size, no adoption statistics. This is a capstone, and we present business hypotheses with a validation strategy — understand workflows, interview users, validate with synthetic data, and only then consider a controlled pilot, after privacy and security requirements are met. The repository documents the gaps honestly: no approved privacy policy or terms yet, no retention schedule. Those are gates, not footnotes — and owning them is part of engineering credibility."

**Takeaway:** A credible path to adoption starts with honesty about what isn't ready.

**Transition:** "Let me bring the team up to close — because this product is four disciplines working as one." → slide 15 (shared closing, led by Mohamed Hemdan).

---

## Slide 15 — The Team & the Close

**Speaker:** Shared close · led by Mohamed Hemdan · **Time:** 0:45

CLOSING — 0:45 total, four voices. HEMDAN (lead, 0:15): "Four disciplines, one product. I'll speak for the quality side: every promise you heard today has a test behind it — and the ones that don't yet are on the record as open." EBRAHIM (0:10): "For backend: the contracts are the guarantee — immutable history, ownership, and safety that no model can override." MAHMOUD (0:10): "For frontend: two languages, two platforms, one experience that respects the caregiver's words and direction." ALAA (0:10): "And as project lead: Rafeek is a companion — رفيق — for the people who carry care every day. Care, recorded. Continuity, protected. Thank you — we're happy to take your questions."

**Takeaway:** Care, recorded. Continuity, protected.

**Note:** all four members speak; keep each closing to one sentence. Q&A follows.

---

## Delivery reminders

- Do not read slide text verbatim — the slides carry the evidence, the speakers carry the story.
- Keep the executed-vs-inventory distinction on slide 12 explicit; it is the credibility anchor.
- Slide 13: the 1.0 eval metrics are deterministic harness results on synthetic cases — never clinical validation.
- Slide 14: no revenue, pricing, market-size or adoption numbers — hypotheses and a validation path only.
- Slide 15: each member speaks one sentence; Hemdan leads and hands to Q&A.
- Synthetic data only, everywhere. Rafeek is care-support documentation, not a medical device.
