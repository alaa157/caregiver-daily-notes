# Dogfood QA Report

**Target:** https://follows-replacing-geographical-week.trycloudflare.com
**Date:** 2026-11-15
**Scope:** Full exploratory QA of the public landing page, auth (sign up / sign in / sign out), the authenticated workspace (overview, recipients, record note, notes history, summaries, plans, settings), plus direct API probing with a valid session token. Focus: bugs and logic errors before the capstone presentation.
**Tester:** Hermes Agent (automated exploratory QA, browser-driven)

---

## Executive Summary

| Severity | Count |
|----------|-------|
| 🔴 Critical | 0 |
| 🟠 High | 2 |
| 🟡 Medium | 4 |
| 🔵 Low | 8 |
| **Total** | **14** |

**Overall Assessment:** The app is in good shape for a demo — auth, guards, recipient/note CRUD, corrections, archive/restore, Arabic RTL, dark mode and mobile layout all work, and I found no auth bypass, no IDOR, and no XSS. The two High issues are both reachable through normal clicking: (1) creating a care-plan draft permanently breaks the Plans page, and (2) the structured-correction form lets you save nonsense values ("Banana") into enum fields, which then flow into summaries and safety rules.

---

## Issues

### Issue #1: Creating a care-plan draft breaks the Plans page (it can no longer load)

| Field | Value |
|-------|-------|
| **Severity** | 🟠 High |
| **Category** | Functional |
| **URL** | `/app/notes/plans?recipientId=<id>` |

**Description:**
After a caregiver saves a structured plan draft ("Create a structured draft" → "Save as suggested draft"), the draft *is* saved, but the Plans page can never load again for that recipient. It shows "Care plans could not be loaded. Check your connection and try again." Every API call actually succeeds (verified with a network trace and with curl), so this is a client-side validation bug, not a backend outage.

**Steps to Reproduce:**
1. Sign in, open Plans, select a recipient.
2. "Create a structured draft" → fill a reason, tick an evidence note → "Save as suggested draft".
3. The page shows "The result could not be confirmed…".
4. Click "Reload saved plans" (or reopen the Plans page).

**Expected Behavior:**
The saved suggestion appears under "Suggested plans", ready to accept / edit / dismiss.

**Actual Behavior:**
"Care plans could not be loaded." forever after. `GET /api/recipients/{id}/plans/history` still returns the saved draft, so the data is there — the UI just refuses to render it.

**Root cause (from the client bundle):** the plan normaliser rejects the app's own draft. A saved draft is stored with `sourceType: "CAREGIVER_SUBMITTED"` and `editable: true`, but the client only allows `editable: true` when `sourceType === "AI_GENERATED"`:

```
if (r.editable && (!r.structuredContent || r.sourceType !== "AI_GENERATED" || n.status !== "SUGGESTED"))
  throw new Error("Invalid protected edit capability.");
```

**Screenshot:** `/data/workspace/dogfood-output/screenshots/02-plans-load-error.png`

---

### Issue #2: Enum fields accept arbitrary text ("Banana") — reachable from the UI

| Field | Value |
|-------|-------|
| **Severity** | 🟠 High |
| **Category** | Functional / Data integrity |
| **URL** | `/app/notes/<id>` → "Add structured correction" |

**Description:**
The structured-correction editor exposes Mood, Appetite, Sleep and Mobility as **free-text inputs** (maxlength 40) instead of the constrained chip pickers used everywhere else. Whatever you type is saved and becomes the note's "current effective value". The backend performs no enum validation for these four fields, so bad data is stored permanently and will be fed into AI summaries and the fixed safety rules.

**Steps to Reproduce:**
1. Open any saved note → "Add structured correction".
2. Set Mood → "Set a value" → type `Banana` → "Save corrections".
3. The timeline records "New effective value: Set to Banana" and the effective-value panel shows Mood = Banana.

**Expected Behaviour:**
Only the defined values (Calm/Happy/Anxious/Agitated/Sad/Confused/Unknown, etc.) should be accepted — ideally with the same chip picker the addendum editor uses.

**Actual Behaviour:**
`Banana` is accepted and persisted. Also confirmed at the API level: `POST /api/notes` with `{"mood":"Banana"}` returns 200/201; `sleep`, `appetite` and `mobility` behave the same. (For contrast, `medicationStatus`, `fallReported` and `medicationVerified` *are* validated server-side and return 400.)

**Screenshot:** `/data/workspace/dogfood-output/screenshots/03-note-invalid-mood.png`

---

### Issue #3: Sign-up has no password policy at all

| Field | Value |
|-------|-------|
| **Severity** | 🟡 Medium |
| **Category** | Functional / Security |
| **URL** | `/signup` and `POST /api/auth/register` |

**Description:**
Accounts can be created with trivially weak passwords. Both the client and the server accept them.

**Steps to Reproduce:**
1. Go to `/signup`.
2. Enter any email and password `abc` (or `a`) → "Create account".
3. Account is created and you are signed in.

**Expected Behaviour:** A minimum length/complexity policy (and a hint on the form).
**Actual Behaviour:** `a`, `abc`, `12345678` and `password` all registered successfully (HTTP 201). There is no client-side hint about password requirements either.

---

### Issue #4: "Current effective value" ignores addenda

| Field | Value |
|-------|-------|
| **Severity** | 🟡 Medium |
| **Category** | Functional / Logic |
| **URL** | `/app/notes/<id>` |

**Description:**
The "Current effective value" panel reflects structured corrections but not addenda, so it can show a value that is no longer the latest one.

**Steps to Reproduce:**
1. Note with Mood = Calm.
2. Structured correction: set Mood = Sad → effective Mood shows SAD.
3. Addendum ("Add a correction"): correct Mood → Happy → save.
4. Effective-value panel still shows Mood = SAD; the addendum is only listed separately below.

**Expected Behaviour:** The effective value should reflect the most recent correction of either kind (or addenda should be explicitly excluded and labelled as such).
**Actual Behaviour:** The panel is stale and contradicts the timeline.

---

### Issue #5: Care-details save shows a misleading error for a pre-1900 date of birth

| Field | Value |
|-------|-------|
| **Severity** | 🟡 Medium |
| **Category** | UX / Error handling |
| **URL** | `/app/recipients/<id>/edit` → "Care details" |

**Description:**
Entering a date of birth before 1900 passes client validation (it only checks "valid date" and "not in the future"), then the server rejects it — but the form shows a generic, wrong message.

**Steps to Reproduce:**
1. Recipient → Edit → Care details.
2. Set Date of birth = `1800-01-01` (other fields optional) → "Save care details".
3. Error shown: **"Check the name and relationship, then try again."** Nothing is saved.
4. The actual server response is: `dateOfBirth must be between 1900-01-01 and today.`

**Expected Behaviour:** Either block pre-1900 dates on the client (matching the `max` attribute behaviour) or surface the server's field message next to the DOB field.
**Actual Behaviour:** A message about name/relationship appears; the real reason is never shown.

---

### Issue #6: `/signup` page title is "Page not found"

| Field | Value |
|-------|-------|
| **Severity** | 🟡 Medium |
| **Category** | Content / UX |
| **URL** | `/signup` |

**Description:**
The sign-up page renders correctly, but the browser tab title is `Page not found · Caregiver Daily Notes`. `/login` correctly shows "Caregiver sign in", and unknown routes show "Page not found", so this looks like a missing per-route title for the signup route.

**Steps to Reproduce:**
1. Sign out (or open in a fresh tab).
2. Open `/signup` and look at the tab title.

**Expected Behaviour:** A "Create your account"-style title.
**Actual Behaviour:** "Page not found · Caregiver Daily Notes".

---

### Issue #7: Landing page "Today's notes" shows a stale, wrong date

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Content |
| **URL** | `/` |

**Description:**
The landing-page demo widget reads "Today's notes / **Tuesday, 12 November**". The actual date at test time was **Sunday, 15 November 2026** — and 12 November 2026 is a Thursday, so both the weekday and the date are wrong. This is the first thing a reviewer sees.

**Expected Behaviour:** Today's real date (or clearly-labelled sample content).
**Actual Behaviour:** Hard-coded/stale demo content.

**Screenshot:** `/data/workspace/dogfood-output/screenshots/01-landing-stale-date.png`

---

### Issue #8: Placeholder canonical URL left in production

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Content / SEO |
| **URL** | any (in `<head>`) |

**Description:** `<link rel="canonical" href="https://replace-with-production-domain/">` — an unfilled placeholder.

---

### Issue #9: Note-form hint contradicts the actual free-text limit

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Content |
| **URL** | `/app/notes/new` |

**Description:** The Observations legend says "Text fields allow up to 40 characters", but the free-text field allows **20,000** characters (`maxlength=20000`, hint "Up to 20,000 characters"). The 40-character limit only applies to the correction value fields. The hint is in the wrong place / wrong number.

---

### Issue #10: Inconsistent enum labelling across views

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Visual / Content |
| **URL** | note detail, overview, recipient detail |

**Description:** Some views render raw enum keys while others render labels:
- Note detail "Current effective value": `SAD`, `POOR`, `SLEPT_WELL`, `INDEPENDENT`, `MOTHER` (uppercase keys).
- Same note's original panel: "Calm", "Good", "Slept well", "Independent".
- Overview table: `MOTHER`; recipient detail page: "Mother".

This is confusing in a demo and looks unfinished.

---

### Issue #11: Notes filter shows an error but keeps the stale result list

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | UX |
| **URL** | `/app/notes` |

**Description:** Setting From date after To date and clicking "Apply filters" shows "The end date must be on or after the start date." but leaves the previous results on screen ("Showing 1–5 of 5 notes"). The list should be cleared or the error should replace it.

---

### Issue #12: Future observation dates are accepted

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Functional / Logic |
| **URL** | `/app/notes/new` |

**Description:** A note can be saved for a future date (verified: 2026-12-20, and via API 2027-01-01). For a "daily notes" app this is usually a mistake — either block future dates or label them as planned/expected. Note the care-details DOB field *does* have `max=today`, so the two date fields behave inconsistently.

---

### Issue #13: Settings page is mostly "Not available yet"

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Content / UX |
| **URL** | `/app/settings` |

**Description:** Privacy, Storage and data, Backup and restore, Supported links, Updater, Change log, About and Version check are all "Not available yet". If these are intentional stubs for the demo, consider hiding them or labelling the page as a subset, so reviewers don't count them as broken features.

---

### Issue #14: "Medication report verified" can't be corrected in the structured editor

| Field | Value |
|-------|-------|
| **Severity** | 🔵 Low |
| **Category** | Functional / Consistency |
| **URL** | `/app/notes/<id>` |

**Description:** The structured-correction editor offers 8 fields (Mood, Appetite, Sleep, Mobility, Medication taken, Pain, Fall, Free text) but **not** "Medication report verified", even though that field is recorded on the note and *is* correctable via the addendum editor. The two correction paths cover different field sets.

---

## Issues Summary Table

| # | Title | Severity | Category | URL |
|---|-------|----------|----------|-----|
| 1 | Plan draft breaks the Plans page | 🟠 High | Functional | /app/notes/plans |
| 2 | Enum fields accept arbitrary text | 🟠 High | Functional / Data | /app/notes/<id> |
| 3 | No password policy | 🟡 Medium | Security | /signup |
| 4 | Effective value ignores addenda | 🟡 Medium | Logic | /app/notes/<id> |
| 5 | Misleading care-details error (pre-1900 DOB) | 🟡 Medium | UX | /app/recipients/<id>/edit |
| 6 | /signup title says "Page not found" | 🟡 Medium | Content | /signup |
| 7 | Landing shows stale "Tuesday, 12 November" | 🔵 Low | Content | / |
| 8 | Placeholder canonical URL | 🔵 Low | SEO | any |
| 9 | "40 characters" hint vs 20,000 limit | 🔵 Low | Content | /app/notes/new |
| 10 | Inconsistent enum labelling | 🔵 Low | Visual | note / overview / recipient |
| 11 | Filter error keeps stale results | 🔵 Low | UX | /app/notes |
| 12 | Future observation dates accepted | 🔵 Low | Logic | /app/notes/new |
| 13 | Settings mostly "Not available yet" | 🔵 Low | Content | /app/settings |
| 14 | Verification not correctable in structured editor | 🔵 Low | Consistency | /app/notes/<id> |

---

## What was tested and worked well

- **Auth:** sign up, sign in (empty-field and wrong-password validation with clear messages), sign out, session persisted in `sessionStorage`, auth guard redirects `/app` → `/login` when signed out, `/signup` redirects to `/app` when signed in.
- **Security (no issues found):** unauthenticated API → 401; bad token → 401; cross-user access to another user's recipient/note → 404 (no IDOR); creating a note on another user's recipient → 404; no CORS reflection of an arbitrary `Origin`; free-text XSS payloads are escaped (no execution).
- **Notes:** create, duplicate-same-date blocked with a clear conflict message, "Open note for this date", structured corrections (append-only timeline with previous/new values), addenda, "Show more/less", notes history with recipient/date filters and page size.
- **Recipients:** create, detail tabs, edit name/relationship, care details, medications + intake events, archive **and** restore (both with clear confirmation dialogs and status messaging).
- **Interface:** Arabic (RTL, `dir=rtl`, `lang=ar`, localised dates) and English, light/dark/system theme, no horizontal overflow at 390 px and 320 px on any tested page.
- **Forms:** ARIA wiring is generally good (`role=alert`, `aria-invalid`, `aria-describedby`, labelled inputs, focus management in dialogs). Server-side limits enforced for free text (20,000), recipient name (120), allergies/conditions (500), phone format, DOB range.
- **Console:** no uncaught JS errors or unhandled rejections on any page tested.

## Not tested / limitations

- **AI summary and AI plan generation with a real provider key** — no API key available. With a fake key the app degrades gracefully ("The provider is temporarily unavailable", "These credentials were not saved or activated"; `aiUnavailable: true` from the API). The safety-alerts path could not be exercised end-to-end.
- **Vision-based visual review** was unavailable in this environment, so layout was assessed programmatically (computed widths, overflow, DOM structure) rather than by eye. Screenshots for issues #1–#3 are in `screenshots/` for you to review.
- **Mobile** was checked at 390 px and 320 px only for horizontal overflow, not full touch-target/contrast auditing.
- The demo backend occasionally dropped the browser tab to `about:blank` during testing; this did not affect any finding (each was re-verified after re-login).

## Test data created (for cleanup if you want)

- Accounts: `qa.dogfood.2026@example.com` (password `abc`), `qa.userb.2026@example.com`, plus several `qa.pw.*@example.com` throwaways.
- Recipient "Amina Test" with care details, one medication (Vitamin D), one original note (2026-11-15) with corrections/addenda, notes on 2026-11-10/11/12/14, a future-dated note (2026-12-20), and one suggested plan draft.
