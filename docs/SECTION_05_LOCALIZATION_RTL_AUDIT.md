# Section 05 — Localization & RTL Audit

**Scope:** Read-only audit of the Android client in `/tmp/opencode/caregiver-daily-notes`. No project files were changed. I inspected localization resources and setup, screen and data flows, formatting utilities, CSV and backup handling, AI text processing, PDF/share output, and existing localization-related tests. I did not run tests, builds, lint, or device checks; the audit is based on source and test inspection.

## 1. Executive Summary

The app has a **centralized English/Arabic setup**: Arabic is the default, the user can switch languages in the app, and the activity applies the chosen locale and corresponding RTL/LTR direction. English and Arabic resource files have matching key coverage (618 string keys); I found no missing keys or placeholder-type mismatches in the comparison. Dates are generally stored in locale-independent ISO formats, and the app deliberately uses Western digits in both languages.

The main confirmed defect is that **some AI-settings messages are read from the application context**, which retains the locale used at process startup. After switching languages and recreating the activity, those messages can remain in the old language. Other findings are narrower: PDF observation dates remain ISO-formatted; Arabic number input in CSV files is rejected; some Arabic labels read unnaturally; and the bidi/Arabic-normalization code leaves risks that are not covered by the current UI tests.

## 2. Audit Scope and Methodology

I traced language selection and startup through `CaregiverApp`, `MainActivity`, `SettingsStore`, and `LocaleHelper`; reviewed resource coverage and representative Arabic copy; and followed locale-sensitive rendering through history, notes, summaries, CSV import/export, backups, PDF export, and share text. I also reviewed the existing locale, date, bidi, Arabic-normalization, and RTL tests.

**Limitations:** No emulator/device or native Arabic-language review was available. No tests were run. The repository provides the Android client; server-side timestamp semantics and external-provider behavior could not be verified here. I distinguish source-confirmed behavior from risks whose visible effect depends on content or runtime behavior.

## 3. Localization Architecture

- **Supported languages:** English (`en`) and Arabic (`ar`), declared in `res/xml/locales_config.xml`; the manifest enables RTL.
- **Default and selection:** Arabic is the default (`SettingsStore.kt:88`). Settings, onboarding, and the app drawer provide language choices. The selection is persisted in DataStore and mirrored for cold start (`SettingsStore.kt:55–64`); changing it recreates the activity.
- **Application of locale:** `CaregiverApp.attachBaseContext` reads the persisted choice and wraps the application context at startup. `MainActivity.attachBaseContext` separately wraps the activity context using the cached selection (`MainActivity.kt:34–36`, `CaregiverApp.kt:39–68`).
- **Resources and fallback:** English defaults live in `res/values/strings.xml`; Arabic resources are in `res/values-ar/strings.xml`. Android’s normal resource fallback applies if a translation is missing. The two supported languages have matching string keys.
- **Formatting:** `appLocale()` reads the activity’s wrapped configuration, rather than relying on the device’s process locale. `DateFormats` uses explicit English or Arabic patterns. Arabic uses `ar-u-nu-latn`, preserving RTL while choosing Western digits.
- **Strategy assessment:** Mostly centralized and reliable for activity-rendered resources. There is, however, an application-context exception described in LOC-001. The app does not follow the device language by default; it starts in Arabic unless the user selects otherwise.

## 4. Translation Coverage

| Screen / Component | Arabic Support | English Support | Missing or Inconsistent Text | Assessment |
|---|---|---|---|---|
| Authentication and onboarding | Resource-backed | Resource-backed | No key gaps found; language names are intentionally shown in their own languages | Good coverage |
| Home, people, notes, history | Resource-backed; RTL configuration applied | Resource-backed; LTR configuration applied | Some Arabic status/error wording is awkward or inconsistent; see LOC-004 | Broad coverage |
| Plans and AI summaries | Resource-backed UI; AI content is generated dynamically | Resource-backed UI; AI content is generated dynamically | Dynamic content is not consistently protected from bidi interactions; see LOC-003 | UI coverage good; generated text needs edge-case review |
| Settings and AI settings | Resource-backed UI | Resource-backed UI | Some status messages use a context that can retain the startup language; see LOC-001 | Confirmed runtime inconsistency |
| CSV and backup | Resource-backed errors and dialogs | Resource-backed errors and dialogs | CSV imports accept canonical Western digits, not Arabic-Indic/Persian digits; see LOC-005 | UI translated; localized numeral input limited |
| PDF and share output | Localized labels and report headings | Localized labels and report headings | PDF observation dates remain ISO; dynamic text may include mixed-direction content | Partial inconsistency; see LOC-002 and LOC-003 |

This is a coverage assessment, not a native-speaker proofread of all 618 strings. Examples of copy issues found are documented in LOC-004.

## 5. RTL Assessment

The manifest declares RTL support, and the locale wrapper derives layout direction from the selected language. Main navigation/back arrows use auto-mirrored icons; forward chevrons explicitly select direction; and the inspected layouts predominantly use logical `start`/`end` positioning rather than physical left/right coordinates.

Existing `RtlUiTest` cases check the layout direction and the presence of Arabic/English labels across major screens. They do **not** establish that every control, chart, cursor, or mixed-content edge case is visually correct. I found no confirmed broad RTL layout failure in the active screens inspected. Device-level checks remain necessary for visual geometry, text selection, and long mixed-direction content.

## 6. Bidi and Unicode Assessment

`Bidi.isolate()` uses FSI/PDI and is applied to several recipient names and inserted values. It is not applied consistently: summary text and stored note/addendum text are rendered directly, as are some medication names (`SummaryScreen.kt:389–393`; `NoteScreens.kt:461, 490`; `MedicationViewModel.kt:301, 431`). PDF output also inserts dynamic content directly (`CareSummaryPdfExporter.kt:104–105, 127–170`).

This is a **confirmed inconsistency in handling**, not proof that every such string visibly reorders. Compose and Android apply bidi algorithms, but mixed Arabic/English text with punctuation, URLs, numbers, or quotes can still need appropriate isolation. Existing UI tests verify visibility of simple mixed strings, not punctuation order, URL placement, or cursor behavior.

`ArabicNormalizer` is comparison-only: it applies NFKC, removes marks and controls, folds Arabic letter variants, and converts Arabic-script digits and punctuation (`ai/ArabicNormalizer.kt:20–44`). The notes themselves are not rewritten. Because the folding is broad, distinct Arabic spellings can normalize identically; this creates a **potential false-match risk** in AI quote/claim comparisons (`ai/ClaimGrounding.kt:109–110`).

## 7. Dates, Times, and Calendars

Care-note dates use ISO `LocalDate` strings and are parsed and compared independently of display locale. History filters convert picker values through UTC to avoid device-time-zone day shifts (`presentation/history/HistoryDates.kt`); existing tests cover several device zones. Display formatting uses Arabic day–month–year and English month–day–year patterns with Western digits.

**Confirmed inconsistency:** The PDF’s recorded-observation lines pass `n.date` directly into the localized string instead of formatting it (`data/export/CareSummaryPdfExporter.kt:168–170`). In an Arabic report, for example, an observation date can appear as `2026-10-07` while the report’s generated date is rendered as `7 أكتوبر 2026`.

**Potential risk:** `DateFormats.parseIsoDate()` discards everything after `T` and displays only the encoded date for addendum timestamps, including values with `Z` or offsets (`core/time/DateFormats.kt:22–26, 70–74`). If those timestamps represent instants and the intended display is the user’s local calendar date, a timestamp near midnight can show a different day. The client’s date-only display intent is clear; the server’s timezone contract is not verifiable here.

The inspected formats and tests use the Gregorian/ISO calendar. Hijri calendar display is not implemented; I treat that as a product limitation, not a defect.

## 8. Numbers and Numeric Formatting

The app intentionally pins Arabic locale formatting to Latin digits. Tests cover this choice for dates and the locale extension; UI tests also assert Western digits in Arabic progress text. There is no user-selectable numeral system. I found no currency-specific flows to audit.

CSV is a narrower case: the documented export shape uses ISO dates and integer pain values. The importer parses pain with `toIntOrNull()` and dates with `LocalDate.parse()` (`data/csv/CsvImportPlanner.kt:162–171, 324–326`). Arabic-Indic or Persian digits in those fields are rejected as invalid. This does not corrupt stored data, and canonical exported CSV uses Western digits, but the importer does not accept localized-digit variants.

## 9. Input and Search Behavior

The app’s pain entry is a 0–10 set of buttons rather than a free-form numeric field. Text fields are used for notes, corrections, names, email, medication details, URLs, and prompts. Stored note and addendum text is passed through without Arabic normalization; the normalizer is used for comparisons, not persistence.

History offers person/date filtering, not free-text search. I found no Arabic-aware note-search behavior to assess, and no search feature should be inferred from the presence of normalization in the AI path. The current RTL tests cover a mixed Arabic email’s visibility, but not cursor movement or pasted strings containing URLs and punctuation.

## 10. Data Integrity Assessment

Locale switching changes display resources and layout direction; it does not appear to rewrite note text, canonical status values, or ISO dates. CSV and backup use structured values rather than translated labels as internal identifiers. Arabic normalization is explicitly kept out of stored text.

The principal integrity-adjacent concern is the **comparison-only** normalizer’s broad equivalences: a false match could affect whether AI-provided text is treated as grounded, even though no stored content is modified. This is a risk, not evidence of existing data corruption.

## 11. Detailed Findings

### LOC-001 — AI-settings messages can retain the startup language

- **Severity:** MEDIUM
- **Category:** Language switching / Translation
- **Affected files:** `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`; `MainActivity.kt`; `presentation/settings/AiSettingsScreen.kt`; `data/SettingsStore.kt`
- **Location:** `CaregiverApp.kt:39, 41–68`; `MainActivity.kt:34–36`; `SettingsStore.kt:55–64`; `AiSettingsScreen.kt:324–326, 357–381, 402`
- **Description and evidence:** `CaregiverApp` creates the shared `AppGraph` with the application context. That context is wrapped with the selected locale at process startup. On language change, the setting and activity cache are updated and the activity is recreated, but the application context is not recreated. AI-settings status and error text is obtained via `graph.context.getString(...)`.
- **Reproduction scenario:** Start the app in Arabic, switch to English without terminating the process, then cancel or run a connection test that produces a status message. Conversely, start in English, switch to Arabic, and trigger the same path.
- **Expected behavior:** AI-settings messages use the currently selected app language.
- **Actual behavior:** These application-context strings can resolve in the language selected at process startup, while the recreated activity and its resource-backed labels use the new language.
- **Impact:** A visible language mismatch in connection, cancellation, and failure feedback.
- **Suggested fix:** Resolve display strings from the current activity/composition context, or otherwise ensure the resource context used for those messages follows runtime language changes.
- **Confidence:** High.

### LOC-002 — PDF observation dates are not localized

- **Severity:** LOW
- **Category:** Dates
- **Affected files:** `android/app/src/main/java/com/caregiver/mobile/data/export/CareSummaryPdfExporter.kt`
- **Location:** `CareSummaryPdfExporter.kt:168–170`
- **Description and evidence:** Recorded-observation lines pass `n.date` directly into `pdf_observation_line`, although other report dates use `DateFormats.historyDay(...)`.
- **Reproduction scenario:** Export a report containing notes while the app language is Arabic.
- **Expected behavior:** Observation dates follow the report’s chosen localized date format.
- **Actual behavior:** The observation date remains ISO `YYYY-MM-DD`; surrounding report dates and labels are localized.
- **Impact:** Inconsistent date presentation in exported reports.
- **Suggested fix:** Format each observation date through the shared date formatter before inserting it into the PDF string.
- **Confidence:** High.

### LOC-003 — Mixed-direction dynamic content is not consistently isolated

- **Severity:** MEDIUM
- **Category:** Bidi
- **Affected files:** `presentation/summary/SummaryScreen.kt`; `presentation/notes/NoteScreens.kt`; `presentation/home/MedicationViewModel.kt`; `data/export/CareSummaryPdfExporter.kt`
- **Location:** `SummaryScreen.kt:389–393`; `NoteScreens.kt:461, 490`; `MedicationViewModel.kt:301, 431`; `CareSummaryPdfExporter.kt:104–105, 127–170`
- **Description and evidence:** These paths render AI summaries, user-authored text, medication names, or PDF content without explicit isolation, while other dynamic values use `Bidi.isolate()`.
- **Reproduction scenario:** View or export Arabic content containing an English name, URL, phone number, model identifier, or punctuation adjacent to Arabic text.
- **Expected behavior:** Mixed-script content remains readable, with punctuation and inserted identifiers in their intended positions.
- **Actual behavior:** No explicit isolation is applied consistently; actual display depends on the text and platform bidi resolution.
- **Impact:** Potential punctuation/order confusion in care notes, summaries, and exported/shared content.
- **Suggested fix:** Identify and isolate inserted tokens at the relevant boundaries, and add tests with mixed scripts, punctuation, URLs, and numbers. Avoid indiscriminately wrapping entire paragraphs.
- **Confidence:** High for the missing isolation; Medium for visible impact.

### LOC-004 — Several Arabic labels are awkward or inconsistent in register

- **Severity:** LOW
- **Category:** Translation
- **Affected files:** `android/app/src/main/res/values-ar/strings.xml`
- **Location:** `strings.xml:39, 65, 75, 255, 616`
- **Description and evidence:** Examples include `ألم عال` for “High pain,” `لا سقوط` for “No fall,” and colloquial `اختار الحالة المزاجية` amid otherwise mostly Modern Standard Arabic copy. The same care recipient is also labeled `المستفيد` in the PDF and `مستلمًا` in the backup preview, while UI copy uses “person in care” terminology.
- **Reproduction scenario:** View those labels in Arabic home, note, PDF, and backup flows.
- **Expected behavior:** Natural, consistent Arabic terms for care statuses and the recipient/care-recipient concept.
- **Actual behavior:** The examples vary in naturalness and terminology.
- **Impact:** Reduced clarity and polish in a care context; the “fall” and pain labels are especially worth review for clarity.
- **Suggested fix:** Have an Arabic reviewer standardize terminology and register across these related resources.
- **Confidence:** Medium. These are linguistic assessments and should be validated by a native reviewer.

### LOC-005 — Arabic-Indic/Persian digits in CSV date and pain fields are rejected

- **Severity:** INFO
- **Category:** Numbers / Input
- **Affected files:** `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`; `data/csv/CareNoteCsv.kt`
- **Location:** `CsvImportPlanner.kt:162–171, 324–326`; `CareNoteCsv.kt:57–69`
- **Description and evidence:** The importer expects an ISO date parsed by `LocalDate.parse()` and an integer parsed by `toIntOrNull()`. Neither path converts Arabic-Indic or Persian digits.
- **Reproduction scenario:** Import a CSV with Arabic-Indic digits in a date or pain value.
- **Expected behavior:** Depends on the intended CSV contract. The documented canonical export format uses ISO dates and integer values, but does not state whether localized digits are accepted.
- **Actual behavior:** Such rows are rejected rather than normalized.
- **Impact:** Localized-digit CSV files may require manual conversion; no silent data transformation was observed.
- **Suggested fix:** Explicitly document ASCII-digit CSV requirements, or add narrowly scoped digit conversion if localized CSV input is intended.
- **Confidence:** High.

### LOC-006 — Offset addendum timestamps are reduced to their encoded date

- **Severity:** LOW
- **Category:** Dates / Time zones
- **Affected files:** `android/app/src/main/java/com/caregiver/mobile/core/time/DateFormats.kt`; `presentation/notes/NoteScreens.kt`
- **Location:** `DateFormats.kt:22–26, 66–74`; `NoteScreens.kt:482`
- **Description and evidence:** `parseIsoDate()` drops the time and offset by taking the text before `T`; the addendum UI then displays that date only. Tests verify acceptance of offset timestamps but do not test conversion across a local-day boundary.
- **Reproduction scenario:** Supply an addendum timestamp whose UTC/source date differs from the user’s local date because of its offset.
- **Expected behavior:** Depends on the timestamp contract: either preserve the source-zone date intentionally, or show the user-local date for an instant.
- **Actual behavior:** Always displays the date portion encoded in the timestamp; no timezone conversion occurs.
- **Impact:** A possible one-day discrepancy if these values represent instants intended for local-time display.
- **Suggested fix:** Confirm the API contract and document the intended date semantics; if local-time rendering is intended, parse the timestamp as an instant and convert using the chosen zone.
- **Confidence:** Medium; server timestamp semantics were not verifiable in this repository.

### LOC-007 — Arabic comparison normalization can collapse distinct spellings

- **Severity:** MEDIUM (potential risk)
- **Category:** Unicode / AI evidence matching
- **Affected files:** `android/app/src/main/java/com/caregiver/mobile/ai/ArabicNormalizer.kt`; `ai/ClaimGrounding.kt`; `ai/SummaryAiService.kt`
- **Location:** `ArabicNormalizer.kt:34–44`; `ClaimGrounding.kt:109–110`
- **Description and evidence:** In addition to removing diacritics and formatting marks, normalization maps `ى` to `ي` and `ة` to `ه`, among other folds. The normalized values are used in evidence/claim matching.
- **Reproduction scenario:** Compare a note and model quote that differ only by a folded letter where the spelling difference changes lexical interpretation—for example, `على` and `علي` become identical under the `ى` → `ي` fold.
- **Expected behavior:** Tolerate harmless orthographic variants without treating materially different text as identical evidence.
- **Actual behavior:** The comparison layer treats the folded forms as equal; no stored text is changed.
- **Impact:** Potential false quote/evidence matches in Arabic AI output. This is not a storage-corruption finding.
- **Suggested fix:** Review which folds are needed for quote matching, and add negative tests for distinct words/names as well as positive variant tests.
- **Confidence:** Medium; the equivalence is confirmed, but an incorrect user-visible match was not reproduced.

## 12. Missing Test Coverage

Existing tests cover locale configuration and activity recreation (`LocaleHelperTest`, `MainActivityLocaleTest`), RTL direction and representative screens (`RtlUiTest`), date formatting and date-picker UTC conversion (`DateFormatsTest`, `HistoryDatesTest`), and the bidi helper and Arabic normalizer.

Gaps identified by inspection:

- Switching languages and then generating AI-settings statuses from the shared application context.
- Mixed punctuation, URLs, phone numbers, technical identifiers, and long Arabic/English text in summaries, editable notes, PDF, and share output.
- Visual alignment and cursor/selection behavior for mixed-direction fields on an RTL device.
- PDF observation-date localization.
- Offset timestamps crossing a local-day boundary.
- Arabic-Indic/Persian CSV date and numeric input, including explicit expected rejection or acceptance.
- False-positive Arabic quote/claim comparisons after normalization.
- A true process-restart check for language persistence. Existing tests verify persistence/cache paths and activity recreation, but do not kill and relaunch the process during this audit.

No tests were executed in this audit.

## 13. Prioritized Remediation Plan

**Priority 1 — Confirmed runtime inconsistency:** Fix LOC-001 so AI-settings feedback follows the selected language after an in-session switch.

**Priority 2 — User-visible consistency and evidence risks:** Localize PDF observation dates (LOC-002); add targeted mixed-bidi coverage and close gaps where required (LOC-003); review the normalization folds and negative evidence-matching cases (LOC-007).

**Priority 3 — Arabic polish and import expectations:** Review labels and terminology with an Arabic reviewer (LOC-004). State the CSV digit convention or intentionally support localized digits (LOC-005).

**Future clarification:** Confirm the server’s addendum timestamp semantics before deciding whether offset conversion is appropriate (LOC-006). Hijri calendar display is an optional product choice, not a confirmed defect.

## 14. Final Verdict

1. **Is Arabic localization complete and consistent?** Resource coverage is complete by key comparison, but runtime AI-status language and several Arabic labels are inconsistent.
2. **Is English localization complete and consistent?** English resources are present across the inspected UI; no key gaps were found.
3. **Does RTL work throughout the application?** Locale direction is applied centrally and major screens have RTL coverage; exhaustive visual correctness is not established.
4. **Are mixed Arabic-English strings displayed correctly?** Some names and values are isolated, but dynamic content handling is inconsistent; complex punctuation and URL cases remain unverified.
5. **Is Unicode and Arabic text handling reliable?** Stored text is preserved, but comparison normalization is broad enough to create potential false matches.
6. **Are dates and times locale-aware and accurate?** Most date display is localized and storage is ISO-based; PDF observation dates are not localized, and addendum offset semantics are unresolved.
7. **Are numbers and numeral systems handled correctly?** The app consistently chooses Western digits. CSV import does not accept Arabic-Indic/Persian digits.
8. **Does language switching preserve user data?** No locale-driven data rewrite was found; activity resources switch, but some application-context messages may stay in the startup language.
9. **Are search and input fields reliable for Arabic and English?** Text fields preserve input, but bidi editing edge cases are not covered. The app has no note-text search; CSV numeric/date input is strict.
10. **Five issues to address first:** (1) stale AI-settings messages after language switching, (2) unlocalized PDF observation dates, (3) mixed-direction dynamic-content coverage, (4) potential false Arabic evidence matches, and (5) awkward/inconsistent Arabic care terminology.

**Overall:** English/Arabic resource coverage and the core locale/RTL architecture are strong, but the runtime context mismatch and untested mixed-direction/AI-text paths keep the experience from being fully consistent.

**Self-check:** Accuracy 4/5 (source-backed; server timestamp semantics and visual bidi outcomes remain unverified); completeness 4/5 (all requested audit areas addressed, but no native-speaker review or device testing); clarity 5/5; actionability 5/5; conciseness 3/5 (the requested report structure requires substantial detail). Overall: **4.2/5**.
