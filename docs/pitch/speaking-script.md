# Rafeek — Four-Speaker Presentation Script

**DEPI Software Testing Track · Capstone 2026 · 15 slides**
**Full run: ~13:30–14:00 · Short run (cut marked lines): ~12:00 · Q&A after**

Companion to `rafeek-pitch-deck.pptx` / `.html` / `.pdf`. Slide cues in
brackets; stage directions in italics. Speak the lines naturally — they are
written for the ear, not the page. Do not read the slides; the slides carry
the evidence, you carry the story.

## Run of show

| Block | Speaker | Slides | Time |
|---|---|---|---|
| Opening · problem · solution | **Alaa Shehata** | 1–3 | 2:35 |
| Product: journey · web · Android · bilingual | **Mahmoud Hewidy** | 4–7 | 3:40 |
| Engineering: architecture · backend · safety | **Ebrahim Ahmed** | 8–10 | 3:10 |
| Quality: strategy · evidence · defects & AI | **Mohamed Hemdan** | 11–13 | 3:00 |
| Business value | **Alaa Shehata** | 14 | 0:50 |
| Shared close (Hemdan leads) | **All four** | 15 | 0:45 |

Pace target: ~135 words/minute. Lines marked **[CUT IF SHORT]** are the
first to drop when you need the 10–12 minute version — nothing essential
is lost.

---

## ACT I — ALAA SHEHATA (slides 1–3)

### [SLIDE 1 — cover · 0:45]

Good morning, everyone.

Care doesn't happen in appointments. It happens in hundreds of small
moments, every single day — a meal eaten, a night slept badly, a dose
missed, a fall that nobody wrote down.

I'm Alaa Shehata, project lead of Rafeek — رفيق — Arabic for "companion."
Rafeek is a bilingual platform that keeps those moments together, so the
people providing care never lose the thread.

In the next fourteen minutes we'll show you the product, the engineering
behind it, and the testing evidence that proves it works. Because this is
a software-testing capstone — and quality is our headline feature.

### [SLIDE 2 — the problem · 0:50]

Let me start with the problem.

*Gesture to the scattered note cards.*

This is one day of care, in five places. A daughter writes in a notebook.
The professional caregiver sends voice notes. The agency keeps a binder.
When information lives in five places, the history of a person's care
effectively doesn't exist — the morning shift's knowledge dies at the
handover.

**[CUT IF SHORT]** And notice what we did not put on this slide: no
invented statistics, no fabricated survey numbers. The problem is
observable in how care actually works.

The answer had to respect that: structure — without stripping away the
caregiver's own words.

### [SLIDE 3 — the solution · 1:00]

Our brand mark is called The Gather: four arc-fragments around a shared
centre. That's the product thesis. Separate observations — a mood, a
meal, a night's sleep, a medication — only become a picture when they're
held together.

Rafeek does four things at once. It structures the day. It preserves
history immutably — nothing is silently overwritten. It works natively in
Arabic and English. And it adds AI assistance that stays on a leash:
summaries grounded in the actual notes, and plans a human must accept,
edit, or dismiss.

One positioning point matters for this panel: Rafeek is care-support
documentation. It does not diagnose, it does not monitor medically, and
it never gives medical advice.

Mahmoud Hewidy builds our web frontend — he'll walk you through the
actual product. Over to you, Mahmoud.

---

## ACT II — MAHMOUD HEWIDY (slides 4–7)

### [SLIDE 4 — product journey · 1:00]

Thanks, Alaa.

This is the loop a caregiver actually lives in. You add a person — say,
your mother. Each day you record one structured note: mood, appetite,
sleep, mobility, whether medication was taken, a pain score from zero to
ten, whether a fall happened — and your own words, up to twenty thousand
characters, in either language.

The moment you save, the original is locked. If something was wrong, you
don't edit history — you append a correction, and the app replays the
effective state from the full chain. Then, when you want the bigger
picture, you request a summary over seven, fourteen, or thirty days, and
review a suggested plan.

Notice the design decision underneath: uncertainty is a value. "Not
recorded" and "unknown" are different — and neither is guessed away.

### [SLIDE 5 — web experience · 0:50]

The web client is React 18 with TypeScript — one workspace, not a
collection of pages. The navigation you see here is the app's real
navigation: Overview, Recipients, Notes, Summaries, Plans. The context
bar carries the selected date and recipient everywhere, so you never
wonder whose record you're looking at.

Two points I care about as the frontend owner. Accessibility is checked,
not claimed — axe-core runs in every browser viewport. **[CUT IF SHORT]**
And sessions are tab-scoped: the sign-in token lives in sessionStorage,
so closing the tab ends the session. Privacy by default.

### [SLIDE 6 — Android experience · 1:00]

Care doesn't only happen where there's a desk.

The Android app is native — Kotlin and Jetpack Compose — and it makes a
deliberately different architectural choice. The official release is
standalone and local-first: accounts live on the device, each account
gets its own Room database, and notes, plans and summaries are stored
on-device. No backend required. That matters in real caregiving:
documentation works in a home with no reliable connection.

AI is still available — through the caregiver's own provider key, stored
encrypted in the Android Keystore. Never in plain storage, never shared
between accounts. **[CUT IF SHORT]** For this evaluation there's an
offline demo with synthetic data — long-press the logo on the login
screen.

Keep one distinction clear: web is the connected workspace; Android is
the independent device record. Same rules, different execution path.

### [SLIDE 7 — bilingual by design · 0:50]

One more non-negotiable: language.

This mirror shows the app's real strings, side by side — not a mockup.
"Save original note" and "حفظ الملاحظة الأصلية" are the same button in
the shipped product.

Bilingual support here is architectural: layouts mirror fully
right-to-left, mixed-direction text is isolated so an English medication
name inside an Arabic sentence never corrupts the line — and Arabic
parity is enforced by tests. If a string exists in English and not
Arabic, the suite fails.

For Egypt and the wider region, that's the difference between a
translated app and an app that belongs to its users.

Ebrahim will take you under the hood.

---

## ACT III — EBRAHIM AHMED (slides 8–10)

### [SLIDE 8 — architecture · 1:10]

Thank you, Mahmoud. I'm Ebrahim Ahmed, backend engineering.

This diagram is the whole system in one view — and the key word is
boundaries.

On the left, the connected path: the React client talks REST to a Java
21, Spring Boot backend — and the backend is the authority. Business
rules, ownership checks, deterministic safety signals, trend counts, plan
versioning, audit — all server-side. Clients render; they do not decide.
PostgreSQL, with versioned migrations, holds append-only records.

On the right, the standalone path: Android runs independently, with its
own local store and a direct line to AI providers.

Both paths use bring-your-own-key — meaning the caregiver's own provider
account, their cost, their data policy. And both enforce the same output
contract: schema-valid, grounded in source notes, medication-locked. The
model's output is a suggestion that passed validation — never an
instruction.

### [SLIDE 9 — backend contracts · 0:50]

Eight domains share one API, and every request passes the same gauntlet:
validation, authentication, then owner-scoped queries.

A detail we're proud of, because it's a security decision: request
another caregiver's resource, and you get 404 — not 403. A 403 would
confirm the record exists; a 404 reveals nothing.

Writes carry an idempotency key — retrying a save replays the original
result instead of duplicating it. That's what makes the "safe retry" in
the UI honest: the backend guarantees it. **[CUT IF SHORT]** Errors
follow one unified contract, and every meaningful action lands in an
append-only audit log.

### [SLIDE 10 — integrity & safety · 1:10]

This is the slide I'd ask you to remember.

On the left: how a record lives. The original note is immutable.
Corrections arrive as addenda or typed events — set a value, clear it,
mark it unknown, mark it not recorded. The state you see is a
deterministic replay of the original plus every event. Anyone can
reconstruct what was known, when, and by whom.

On the right: the model's leash. Red flags — a fall, high pain, repeated
missed medication — are computed by ordinary code, independently of any
language model. The model may raise urgency; it can never lower or
remove a flag, never touch medication names, doses or schedules, and plan
actions come from a fixed catalog. Every claim must quote a real source
note. And the final decision — accept, edit, dismiss — belongs to the
caregiver.

We say it in one line: the model never holds the pen on safety.

Mohamed will show you how we prove all of this.

---

## ACT IV — MOHAMED HEMDAN (slides 11–13)

### [SLIDE 11 — testing strategy · 1:00]

Thank you, Ebrahim. I'm Mohamed Hemdan — backend and security — and I'll
close out with the quality evidence.

This is a Software Testing track capstone, so testing isn't a chapter at
the end; it's how the project was run. We started from risk: what would
actually harm a care recipient, or leak their data? A model suppressing a
safety flag. A silent edit to history. Cross-caregiver access.
Model-authored medication advice.

Each risk maps to a guard in that pyramid — deterministic red-flag tests,
immutability and replay tests, ownership tests, medication hard-lock
tests, and a deterministic AI evaluation harness.

The lifecycle is the full discipline: plan and analyze risk, design
structured cases, execute, file defects with reproduction, fix, verify
regression. Every tool on this slide is in the repository. **[CUT IF
SHORT]** And there's a sixty-two-page formal test specification behind
it.

### [SLIDE 12 — quality evidence · 1:00]

These numbers are measured, not estimated — each one reproducible from
the repository.

Backend: seven hundred tests executed, seven hundred passed, zero
failures, zero errors — across ninety-three test classes that boot real
Spring contexts with migrations applied. Coverage: ninety-three point
three percent of lines, seventy-six point seven percent of branches.
Web: one thousand eighty-six cases, all passing.

Now the distinction that matters for honesty: the Android and browser
figures are inventory — sixteen hundred fifty-four JVM methods, three
hundred twenty defined browser runs across four viewports. They're wired
into CI, first full runs in flight. We do not claim they all executed and
passed in this verification pass. The repository states exactly this —
and so do we.

### [SLIDE 13 — defects & AI evaluation · 1:00]

Testing found real problems. Eight defects documented; seven fixed and
verified; one — DEF-008 — openly tracked as under investigation. We show
the open one on purpose: a defect register you can trust includes what
isn't finished.

The case study: one migration statement, incompatible with the test
database, cascaded into three hundred twenty-five errors. We isolated it,
fixed it, verified it — and watched coverage jump from a misleading
fifty-percent floor to the true ninety-three point three.

And the AI itself is tested: twenty-two synthetic golden cases — English,
formal Arabic, Egyptian dialect, mixed text, contradictions, noise, even
prompt-injection attempts. Four metrics — red-flag recall, schema
validity, groundedness, medication safety — all enforced at one-point-zero
by a test that fails CI on regression.

To be explicit: these are harness metrics on synthetic data. Not clinical
validation. We claim none.

Alaa will close with who this is for.

---

## ACT V — ALAA SHEHATA (slide 14)

### [SLIDE 14 — business value · 0:50]

Thanks, Mohamed.

Who is this for — and how would we find out responsibly? Three segments
to investigate: family caregivers first, because in Egypt and the wider
region care is family-led; then professional caregivers and small care
organizations.

Notice what this slide does not contain: no revenue projections, no
pricing, no invented market size. This is a capstone — we present business
hypotheses with a validation strategy: understand workflows, interview
users, validate with synthetic data, and only then consider a controlled
pilot — after privacy and security requirements are met.

The repository documents the gaps honestly: no approved privacy policy or
terms yet, no retention schedule. Those are gates, not footnotes. Owning
them is part of engineering credibility.

Let me bring the team up to close — because this product is four
disciplines working as one.

---

## SHARED CLOSE — ALL FOUR (slide 15 · 0:45)

*Step forward together. Hemdan leads; each member speaks one sentence.*

**HEMDAN:** Four disciplines, one product. I'll speak for quality: every
promise you heard today has a test behind it — and the ones that don't
yet are on the record as open.

**EBRAHIM:** For backend: the contracts are the guarantee — immutable
history, ownership, and safety no model can override.

**MAHMOUD:** For frontend: two languages, two platforms, one experience
that respects the caregiver's words and direction.

**ALAA:** And as project lead: Rafeek is a companion — رفيق — for the
people who carry care every day. Care, recorded. Continuity, protected.
Thank you — we're happy to take your questions.

---

## Timing & cut plan

| Version | What to do | Runtime |
|---|---|---|
| Full | Everything as written | ~14:00 |
| Short | Drop every **[CUT IF SHORT]** line | ~12:00 |
| Tight | Short version + halve the slide-13 case study, brisk pace | ~10:45 |

Rehearsal rules:
- Time each act separately; the hand-off lines are part of the speaker's
  time, not extra.
- Numbers are spoken slowly and once — let the slide carry the repetition.
- The executed-vs-inventory distinction (slide 12) and "not clinical
  validation" (slide 13) are never cut, even in the tight version.
- If a demo question comes mid-talk: "synthetic data only, and we'll show
  you in Q&A" — then keep moving.