# Android Visual Polish Plan (follow-up to the Kotlin app plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the native Kotlin app's visuals in line with the 15 committed design boards (`android/android_rtl.html`, 390×844 RTL, decoded reference in the Kotlin app plan's Design source section) without changing behavior, strings contracts, or backend calls. Every screen verified by on-device screenshots in `ar` + `en` — CI has no emulator/screenshots, so screenshots recorded in the PR are the acceptance proof.

**Non-goals:** No new screens, no behavior or API-contract changes, no string-key renames (copy fixes keep existing keys; any new key lands in both locales + `StringsParityTest`). Any functional gap found becomes a new issue instead of scope creep here.

## Global Constraints

- Work on `preview/full-stack`. Never bring preview-only backend changes into `main` as part of this work.
- Behavior freeze: ViewModels, repositories, navigation routes, and API calls stay untouched unless a visual fix strictly requires it (then split it into its own commit).
- Typography/colors come from the boards only: primary teal `#0E6B66` (white text on solid buttons), ink `#1F2A2E`, background `#F7F6F3`, plus the plan's full palette. Do not improvise new brand colors.
- `release` buildType stays minify-off; re-verify the `minified` R8 build on device after any resource/theme change.
- TDD where testable (theme tokens, parity, UI tests); visual deltas verified by screenshots.
- Push only when asked.

---

### Task 1: Font + app identity (launcher icon, tab icons, Plex)

**Files:**
- Add: launcher icon set (`mipmap-*`) + 4 tab icons + FAB/add-note artwork in the design language; font bundle under `res/font/` **only if** the IBM Plex Sans Arabic license (bundled woff2 ships inside `android/android_rtl.html`) permits app embedding — otherwise use the downloadable-fonts path (`res/font` + `font-provider` XML, no binary).
- Modify: `core/theme/Type.kt` (Plex family, board scale), `MainScaffold.kt` (wire the 4 tab icons; the slot is currently an empty placeholder), `AndroidManifest.xml` (icon reference).
- Test: `ThemeTest` typography assertions; `ManifestTest` icon reference; screenshots of tab bar selected/unselected in `ar` + `en`.

**Interfaces:**
- Consumes: boards 1–4 tab bar + FAB artwork from `android/android_rtl.html`.
- Produces: branded app icon, icon tab bar, Plex typography app-wide.

- [ ] Steps 1–5: same TDD cycle where testable. Confirm the Plex license decision in the commit message.

### Task 2: Button / chip / card language pass

**Files:**
- Modify: shared button/chip/card styling — teal solid primary (white text), outlined secondary (ink text), teal selected chips; safety banner keeps `#F7E3E1`/`#8B2B25` with no dismiss control; blue `#E8F0F9` info cards on plans/summary.
- Test: extend `ThemeTest` (button/chip color roles); existing UI tests stay green; screenshots of boards 6, 11, 12, 15 states.

**Interfaces:**
- Consumes: component styling used by every screen (no behavior change).
- Produces: one consistent component language; later tasks only compose it.

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 3: Auth screens (board 1) + pre-auth language toggle

**Files:**
- Modify: `presentation/auth/` — bordered inputs, teal primary button, muted helper copy per board 1; add the board's on-screen **English toggle** (pre-auth), which has no app equivalent today (language lives only in post-auth Settings).
- Test: `AuthFormUiTest` extended (toggle flips locale labels ar↔en on the auth screen itself); screenshots board 1 in both locales.

**Interfaces:**
- Consumes: `SettingsStore.language` + `LocaleHelper` (already used post-auth).
- Produces: board-1-faithful login/register; pre-auth locale switch.

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 4: Home + recipients (boards 2–4)

**Files:**
- Modify: `presentation/home/`, `presentation/recipients/` — greeting header, today-progress block, recipient cards (last-note snippet + today flag), detail action rows (Add note / View history / Care plan / Smart summary), gear entry to Settings.
- Test: existing `HomeViewModelTest`/`RecipientsFlowUiTest` stay green; screenshots boards 2–4 in both locales with Arabic + English recipient names (bidi check).

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 5: Note editor + saved + detail + addendum (boards 5–8)

**Files:**
- Modify: `presentation/notes/` — option chips per group with teal selection, fall toggle, **0–10 numbered pain stepper** (boards) with the `0 = no pain · 10 = worst` caption, free-text field + `الحفظ لا يعتمد على الذكاء الاصطناعي` footnote; saved confirmation with View-note + Create-summary actions.
- Copy decision (owner-confirmed before renaming): board 8 titles the screen `إضافة ملحق`; the app's copy says `تصحيح`/correction everywhere. Either align to the board or keep — but stop shipping both. New keys (if any) land in both locales.
- Test: `NoteEditorViewModelTest`/`OptionLabelsTest` stay green; screenshots boards 5 (tall scroll), 6, 7, 8.

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 6: History + summary (boards 9–11)

**Files:**
- Modify: `presentation/history/`, `presentation/summary/` — per-day entries, recipient/date filters, period chips with board copy (`آخر 7 أيام` / `آخر 14 يومًا` / `آخر 30 يومًا` — keep existing string keys, change values in both locales), evidence quotes + uncertainties, disclaimer line, non-dismissible safety banner incl. loading/error/AI-unavailable states.
- Test: `HistoryViewModelTest`/`SummaryViewModelTest`/`BannerRetentionTest` stay green; screenshots boards 9–11 with flags present.

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 7: Plans + settings composition (boards 12–14 + missing pages)

**Files:**
- Modify: `presentation/plans/` (proposal per board 12: `قبول الخطة` teal + `تعديل ثم قبول` + `رفض` over blue info cards; edit per board 13 with `حفظ واعتماد كإصدار جديد`; versions per board 14 newest-first with server reasons), `presentation/settings/` (server-URL editor, language switch + restart notice, logout).
- Test: `PlansViewModelTest`/`PlanDetailViewModelTest`/`PlanEditViewModelTest` stay green; screenshots boards 12–14 + settings pages.

- [ ] Steps 1–5: same TDD cycle where testable.

### Task 8: States catalog + release screenshots (board 15)

**Files:**
- Modify: loading/error/empty/offline/AI-unavailable presentations to board-15 composition (reuse the `common_offline`, `people_empty`, `notes_empty`, `plans_empty`, `safety_banner_cd` catalog).
- Test: full unit suite green; `android-debug-apk` + `android-minified-apk` both build from CI; full screenshot set (boards 1–15 × ar/en) attached to the PR as the acceptance proof.

- [ ] Steps 1–5: same TDD cycle where testable.
