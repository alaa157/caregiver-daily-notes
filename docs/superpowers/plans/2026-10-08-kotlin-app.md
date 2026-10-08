# Native Kotlin Android App Implementation Plan (issue #27)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the native Kotlin Android app implementing #27, visually faithful to the Arabic design boards, with a complete English version of every screen plus the missing pages designed in the same style. Design boards are committed at `android/android_rtl.html` (15 boards, 390×844 RTL, added `afdbe45` — decoded 2026-10-08, see Design source below); the old `/workspaces/android_rtl.html` local path is superseded.

**Architecture:** Single native Android application in Kotlin using Jetpack Compose + Material3, a manual composition root (no DI framework), Retrofit + OkHttp + kotlinx.serialization, DataStore for token/server-URL/language, and ViewModel + StateFlow for screen state. Keep the web app, Java backend, API contracts, database, and deployment behavior unchanged. Client-side derived presentation is allowed only when it can be computed from bounded API data; do not add per-row request fan-out or infer missing clinical facts. Record the final Android project directory in the repository architecture docs as part of the migration.

**Tech Stack:** Kotlin 2.x, Android Gradle Plugin, Gradle wrapper, minSdk, compileSdk, and JDK versions selected and pinned together in Task 1; coroutines/StateFlow, Retrofit/OkHttp, DataStore Preferences, JUnit + Turbine + Compose UI Test. Do not claim Android 5.0 support unless all selected AndroidX/Compose dependencies support the chosen minSdk; confirm the minimum supported API from the resolved dependency set before freezing it. Arabic is the visual reference language; English is a complete translation. Keep dependency versions stable and mutually compatible rather than using an unpinned “latest” version.

**Spec:** GitHub issue `alaa157/caregiver-daily-notes#27` (product requirements) + the 15 design boards committed at `android/android_rtl.html` (390×844, RTL; board content decoded from the bundle 2026-10-08 — see Design source). Backend contract = preview endpoints on `preview/full-stack` (auth, recipients, notes, addenda, history, signals, summaries, plans); verify actual paths/DTOs against the checked-out API/OpenAPI contract before implementation and record any unresolved gaps for #28. Do not change backend behavior or contracts in this mobile plan.

## Global Constraints

- Work on `preview/full-stack` unless the owner explicitly selects another branch. Keep this migration scoped to the Android client and its mobile documentation/CI; never bring preview-only backend changes into `main` as part of this work.
- Use `com.caregiver.mobile` as the proposed application namespace unless an existing app identity or release requirement in the repository conflicts. Record any package/application-ID decision in Task 1; do not make implementation depend on an unanswered approval step.
- Web stays React + TypeScript. Backend stays Java 21 + Spring Boot + Maven. Android uses Gradle; Maven remains backend-only.
- No secrets in code/APK; token in DataStore, never logged; base URL runtime-editable (issue #27 Task 11), never a build constant.
- TDD per repo standing orders: failing test first, minimal implementation, suite green before commit; push only when asked.
- RTL: `supportsRtl=true`, start/end layouts only (no left/right), Compose `LayoutDirection` mirroring, bidi-safe mixed text; every screen verified in both `ar` and `en` locales.
- Device support: choose and document a minSdk compatible with the selected Compose/AndroidX stack; layout must hold at 360×640 dp; avoid unnecessary cold-start dependencies. Add compatibility guards only where the selected minSdk requires them.
- Every Arabic string has an English twin (test enforces key parity); tone: warm, plain, dialect-free English for Egyptian-dialect source (e.g. `مش متأكد` → `Not sure`, `اتحفظت على الخادم` → `Saved to the server`).
- Safety banner is non-dismissible (no close control of any kind) whenever flags exist, including loading/error states.
- Original notes are read-only everywhere; corrections only via addenda.

## Design source (decoded 2026-10-08 from `android/android_rtl.html`; supersedes the local-path extraction)

Boards carry their own bundled **IBM Plex Sans Arabic** woff2 (Arabic + Latin subsets) — the app still uses the system default font (see Task 10).

Palette (exact): primary teal `#0E6B66`, ink `#1F2A2E`, muted `#5B6B70`, background `#F7F6F3`/`#FAF9F5`, borders `#C5CDD1`/`#E3E7EA`, danger `#8B2B25` on `#F7E3E1`/`#EBC3BF`, success `#1F5C38` on `#E3F1E8`, info `#24507E` on `#E8F0F9`/`#C9DAEC`, warning `#7A4B00` on `#FBF0D9`. Boards additionally use `#E8EDF3`/`#EEF1F4`/`#E4EEF8` surfaces on plans/summary screens.

Boards (Arabic titles): 1 login `تسجيل الدخول`, 2 home `الرئيسية`, 3 recipients `الأشخاص`, 4 recipient detail, 5 add-today-note (tall scroll, 390×1560), 6 saved confirmation `تم الحفظ`, 7 note detail (390×1120), 8 addendum titled `إضافة ملحق` (note: the app's copy says `تصحيح`/correction — align in Task 10), 9 history `السجل`, 10 summary period picker, 11 summary+safety result, 12 plan proposal, 13 plan edit (save button `حفظ واعتماد كإصدار جديد`; the design's reason field `سبب التعديل` has no request sink and is omitted — see gaps doc), 14 plan versions, 15 loading/error/empty catalog.

Component language: teal solid primary buttons with white text; secondary actions outlined with ink text; selected chips/options teal; pain is a **0–10 numbered stepper** with teal selection. Board 1 has an **English toggle on the auth screen itself** (the app only offers language post-auth in Settings — see Task 10). Period chips read `آخر 7 أيام` / `آخر 14 يومًا` / `آخر 30 يومًا` (app strings differ — align in Task 10). Board 12 actions: `قبول الخطة` (teal) + `تعديل ثم قبول` + `رفض` over blue `#E8F0F9` info cards.

Tabs (4): `الرئيسية` Home, `الأشخاص` People, `الملاحظات` Notes, `السجل` History — plus a prominent add-note action. No More tab and no plans tab in the design: plans live under recipient detail (`خطة الرعاية`) and summary follow-ups.

## Contract deltas (design needs vs backend reality)

- Recipient rows may show last-note snippet and today status only when available from a bounded history request; do not issue one notes request per recipient. Home progress must use a bounded date range and documented pagination/limits. If the current API cannot provide the data without unbounded reads or request fan-out, omit the derived count/snippet and record the gap for a separate API issue. **Age is omitted** until a real birthdate/age field exists; do not fake one.
- Home greeting `صباح الخير، أحمد`: no profile endpoint exists — greet from the registered email's local-part, fallback to a nameless greeting. Time-of-day aware (morning/evening), both languages.
- Evidence `المصدر: ملاحظة #104`: backend returns UUID noteIds — display the short 8-char prefix, never invent sequential numbers.
- Dates `7 أكتوبر`: `java.time` with `ar`/`en` locales (`Oct 7` in English).
- Mood enum stays English on the wire (`good/...`); only labels translate.

## Pages missing from the design (specified here, same visual language)

- **Register** (Task 3): mirrors board 1 — title `إنشاء حساب`, email + password + confirm fields, same bordered inputs and teal primary button, `لديك حساب؟ سجّل الدخول` back-link; validation copy mirrors login.
- **Server URL setting** (Task 7): title `عنوان الخادم`, current-URL line, URL field, Save `حفظ` with saved-confirmation line, Restore-default `استعادة الافتراضي` with reset-confirmation; reachable from the login footer link (pre-auth) and Settings (post-auth). Invalid input shows the invalid-URL line; nothing else changes.
- **Settings** (Task 7): rows — server URL (navigates above), language `اللغة` (English/العربية switch + `أعد فتح التطبيق لتطبيق الاتجاه` restart notice), logout `تسجيل الخروج` (clears token → login). Entry: gear icon on the Home top bar.
- **Add recipient** (Task 4): full screen — title `إضافة شخص تحت الرعاية`, name field, required-name error `الاسم مطلوب`, Save; success returns to the list with the new row.
- **Plans list** (Task 7): title `الخطط`, rows showing plan id + latest status chip + version count; empty state reuses the board-15 no-plan copy (`لا توجد خطة رعاية نشطة حالياً` + `عرض المقترحات` action); tap → proposal/detail. Entry points: recipient detail `خطة الرعاية` row and summary follow-up.

## Review Focus

- Mixed-direction text (Arabic UI with English names/numbers, Egyptian-dialect quotes inside evidence) must stay readable — bidi isolation on every interpolated string; test with an English recipient name in `ar` locale and vice versa.
- The safety banner must survive rotation, process death, and degraded states (loading/error/AI-unavailable) with flags intact — test all three.
- Offline airplane-mode submit must surface the offline error with retry and never lose the draft — test draft retention across the error.
- Token expiry mid-session must return to login without leaking the previous screen's data — test with a 401 on an authed call.
- The 72-byte password and pain 0–10 rules must behave identically to the backend — test boundary values against the same constants.

---

### Task 1: Module scaffold + theme + navigation shell

**Files:**
- Create: Android Gradle project/module (Gradle wrapper, version catalog, manifest with `supportsRtl=true`, selected `minSdk`/`compileSdk`, launcher icon placeholder), `MainActivity.kt`, `CaregiverApp.kt` (Application + manual composition root), `core/theme/{Color,Type,Theme}.kt` (palette above), `core/navigation/Routes.kt` (type-safe routes for screens in this plan), `MainScaffold.kt` (4 tabs + add-note action).
- Test: local unit tests for theme tokens and route/state definitions; Compose UI tests for rendered navigation labels and reachable screens. Keep unit-test and device/emulator test commands distinct; do not assert desugaring through a UI navigation test.

**Interfaces:**
- Consumes: nothing (foundation).
- Produces: `CaregiverApp` with an initially minimal `AppGraph` composition root that later tasks extend; `AppTheme` + `CaregiverColors`; `Route` sealed set; scaffold with placeholder content per tab.

- [x] **Step 1: Write the failing tests.** Theme test asserts primary `#0E6B66`/background `#F7F6F3`/danger `#8B2B25`; nav test asserts all routes exist and tab labels resolve in both locales.
- [x] **Step 2: Run.** `cd android && ./gradlew :app:testDebugUnitTest --tests "*ThemeTest*"` → FAIL.
- [x] **Step 3: Implement.** Android project under `android/` with application module `:app`, selected minSdk/compileSdk and required desugaring, theme tokens, nav graph, scaffold; placeholder screens only. Layouts must hold at 360×640 dp without horizontal scroll.
- [x] **Step 4: Run.** Run the relevant local unit tests and lint. Run Compose instrumentation tests with the appropriate connected-device/emulator task when an emulator is available; do not treat `testDebugUnitTest` as running instrumentation tests.
- [x] **Step 5: Commit.** `git commit -m "feat(android): scaffold module, theme, navigation shell"` on `preview/full-stack`. Landed as `e31bc71`.

### Task 2: Settings + token + API client seam

**Files:**
- Create: `data/SettingsStore.kt` (DataStore: documented non-secret base-URL default, language, token get/set/clear), `data/api/{ApiClient,AuthApi,RecipientApi,NoteApi,SummaryApi,PlanApi}.kt` (Retrofit interfaces + DTOs matching the selected backend contract), `data/AuthRepository.kt` (login/register/logout, 401 → logout event), `core/network/AuthInterceptor.kt` (Bearer from the token store per call).
- Test: `SettingsStoreTest` (persist/roundtrip/reset), `ApiContractTest` (DTO serialization fixtures vs backend shapes; 401 maps to logout, never crash).

**Interfaces:**
- Consumes: `AppGraph` (Task 1).
- Produces: `api: BackendApi` (all feature APIs), `auth: AuthRepository { token: StateFlow<String?>, signIn, signUp, signOut }`, `settings: SettingsStore`.

- [x] **Step 1: Write the failing tests.** Roundtrip + reset; login DTO serializes to `{email,password}`; 401 → token cleared.
- [x] **Step 2: Run.** → FAIL.
- [x] **Step 3: Implement.** Ensure every request uses the currently saved base URL. Retrofit normally binds a base URL when its service is created, so implement and document a deliberate dynamic-origin strategy (for example, a tested OkHttp interceptor that safely replaces only the request origin, or rebuilding the Retrofit service when the URL changes). Validate scheme/host, reject user-info/query/fragment components, preserve API paths, reject malformed URLs, and allow cleartext HTTP only if the project explicitly needs a development endpoint. Attach bearer tokens only to requests for the configured origin and prevent cross-origin redirects from forwarding authorization. A changed URL must not silently lose the saved token. No secrets in code.
- [x] **Step 4: Run.** → PASS; full unit suite green.
- [x] **Step 5: Commit.** `git commit -m "feat(android): settings, token store, API client seam"`. Landed as `ac02325`. Follow-up fix `4063017` made the DataStore single-owner (`SettingsStores` shared by `attachBaseContext` and `AppGraph`).

### Task 3: Auth screens (login + register in design style)

**Files:**
- Create: `presentation/auth/{LoginScreen,RegisterScreen,AuthViewModel}.kt`.
- Test: `AuthScreenTest` (blank/invalid-email/short-password inline errors ar+en; valid submit calls repo; 401 shows invalid-credentials copy and stays).

**Interfaces:**
- Consumes: `AuthRepository` (Task 2).
- Produces: authed nav graph (signed-in → tabs; signed-out → auth).

- [x] Steps 1–5: same TDD cycle. Login mirrors board 1 (email, password, submit, register link); register mirrors it + confirm field. Design language: teal primary button, bordered inputs, muted helper copy. Landed as `f17a6d4`. Known gap carried to Task 10: board 1's on-screen English toggle has no app equivalent (language lives only in post-auth Settings).

### Task 4: Home dashboard + recipients

**Files:**
- Create: `presentation/home/HomeScreen.kt` (greeting, safety banner slot, today progress when bounded data supports it, today's recipient cards), `presentation/recipients/{RecipientsScreen,RecipientDetailScreen,AddRecipientScreen,RecipientsViewModel}.kt`.
- Test: progress math, status chips (done/missed/high-pain), add-recipient validation, detail actions navigate.

**Interfaces:**
- Consumes: `RecipientApi`, `NoteApi` (Tasks 2–3 for auth gate).
- Produces: recipient flows per boards 2–4 (cards show last-note snippet + today flag only when bounded data supports them; detail shows Add note / View history / Care plan / Smart summary actions; age row omitted per deltas).

- [x] Steps 1–5: same TDD cycle. Client-side aggregation for progress/snippets/flags; English twins for every string. Landed as `34498b9`.

### Task 5: Note editor, saved confirmation, note detail, addendum

**Files:**
- Create: `presentation/notes/{NoteEditorScreen,NoteSavedScreen,NoteDetailScreen,AddendumScreen,NotesViewModel}.kt`.
- Test: chip single-select per group, pain 0–10 bounds, mood-required + localized errors, Arabic text byte-identical roundtrip, original read-only with addenda appended, addendum submit appends.

**Interfaces:**
- Consumes: `NoteApi` (create/get/append).
- Produces: boards 5–8 (option chips for mood/appetite/sleep/mobility/medication, fall toggle, pain stepper with `0 = no pain · 10 = worst` caption, free-text field, `الحفظ لا يعتمد على الذكاء الاصطناعي` footnote; saved screen with View-note + Create-summary actions).

- [x] Steps 1–5: same TDD cycle. Landed as `537cc72`.

### Task 6: History + summary period + summary result

**Files:**
- Create: `presentation/history/HistoryScreen.kt`, `presentation/summary/{SummaryPeriodScreen,SummaryResultScreen,SummaryViewModel}.kt`.
- Test: recipient/date filters narrow the list; period picker 7/14/30; banner present with flags + no dismiss control in content/loading/error; evidence quotes + uncertainties render; AI-unavailable copy with notes-safe reassurance.

**Interfaces:**
- Consumes: `NoteApi`, `SummaryApi` (Task 2).
- Produces: boards 9–11 (per-day entries — addendum counts omitted, no server count; see `docs/api/android-gaps.md` — medical disclaimer line; trends/appetite/sleep/medication blocks render only the `text`/`evidence`/`uncertainties` the API returns).

- [x] Steps 1–5: same TDD cycle. Landed as `3d25049`.

### Task 7: Plans (list new + proposal + edit + versions) + settings

**Files:**
- Create: `presentation/plans/{PlansScreen,PlanProposalScreen,PlanEditScreen,PlanVersionsScreen,PlansViewModel}.kt`, `presentation/settings/SettingsScreen.kt` (server URL editor with save confirmation + reset, language switch with RTL-restart notice, logout).
- Test: four localized actions in both languages, illegal-transition 422 surfaces as localized error (no crash), version history newest-first, edit appends a version with items only (the reason is server-generated — the plan's earlier "with reason" wording was wrong; see `docs/api/android-gaps.md`), settings URL persist/reset/logout flow incl. token clear.

**Interfaces:**
- Consumes: `PlanApi`, `SummaryApi`, `SettingsStore`, `AuthRepository` (Tasks 2, 6).
- Produces: boards 12–14 + the four missing pages in design language (teal primary actions, blue info cards `#E8F0F9`, status chips green/red/blue). Known gap recorded post-landing: `appendVersion` skips transition validation and the `editAccept` endpoint is uncalled — see `docs/api/android-gaps.md`.

- [x] Steps 1–5: same TDD cycle. Landed as `75646dd`; review follow-ups as `69da3a9` (edit retry reloads, typed People route, saveable server-URL state, refresh-preserving plans list).

### Task 8: States, RTL audit, bilingual parity, demo build

**Files:**
- Modify: cross-cutting (loading/error/empty per board-15 catalog incl. offline, AI-unavailable, field-error, safety-rule notices).
- Test: `StringsParityTest` (every `ar` key exists in `en` and vice versa, no empty values); RTL screenshot-style assertions (layout direction, mirrored rows) for login/home/recipients/editor/summary/plans; rotation + process-death banner retention.

**Interfaces:**
- Consumes: all screens.
- Produces: shippable debug APK from CI using the selected Android project path and Gradle task (`android-debug-apk` artifact from `.github/workflows/android.yml`), plus a release-notes draft (`docs/releases/android-0.1.0-draft.md`). A squeezed R8 `minified` buildType (`android-minified-apk`, debug-signed, 1.7 MB) was added beyond this task in `b9c1edb`; both APKs ship on the `android-0.1.0-preview` pre-release. Define target ABIs/artifact naming in the workflow; do not assume an existing mobile APK workflow or artifact contract.

- [x] **Step 1: Write the failing tests.** Parity + RTL + retention tests. Landed `StringsParityTest` (ar/en key parity, non-empty, placeholder consistency, board-15 catalog), `BidiTest` (FSI/PDI isolation), `BannerRetentionTest` (flags across AiUnavailable/Rejected/offline-error + recreated VMs).
- [x] **Step 2: Run.** → FAIL.
- [x] **Step 3: Implement.** Fill gaps; complete Arabic and English device walkthroughs for every screen, including mixed-direction content and accessibility basics (labels, focus order, scalable text). Landed `core/i18n/Bidi.kt` wired into home greeting/cards/banner and summary evidence; board-15 state strings; `android/README.md` walkthroughs. Follow-up `4063017` added `RtlUiTest` (per-screen labels + real `LayoutDirection` probes incl. Bidi names), `RotationUiTest` (banner across recreation, no dismiss), `ManifestTest`, and the single-owner `SettingsStores` fix.
- [x] **Step 4: Run.** Full unit suite + connected-check subset green; CI APK builds.
- [x] **Step 5: Commit.** `git commit -m "feat(android): states, RTL audit, bilingual parity"`. Landed as `eadef80`.

### Task 9: Remove React Native residue and align project docs

**Files:**
- Inspect and remove only confirmed React Native mobile source/configuration, dependency manifests/lockfiles, generated Metro/autolinking output, and mobile-only Node dependency artifacts from the authoritative project checkout. Preserve web React/TypeScript files, Java backend files, Android identity/resources that are intentionally reused, and shared CI steps.
- Update `README.md`, `ARCHITECTURE.md`, mobile setup/testing/release documentation, and GitHub Actions workflows to describe the selected native Android project path and actual Gradle commands. Keep web React + TypeScript descriptions intact. Remove stale RN/Metro/Babel/Jest mobile instructions and references; distinguish Gradle for Android from Maven for backend.

**Checks:**
- Repository-wide search finds no stale React Native mobile instructions; remaining React/TypeScript references belong to the web app.
- Review `git status` and ignored-file state before deleting local artifacts. If old mobile source is unavailable or ignored in the checkout, report that explicitly and do not claim it was removed from version control.
- Run documentation/link checks and `git diff --check`; do not add or run tests as part of this cleanup task unless separately requested.

- [x] **Step 1.** Inventory tracked, ignored, and untracked mobile files and classify them before deletion. Done: no tracked `mobile/` files remained past `ef346a4`; no ignored/untracked residue.
- [x] **Step 2.** Remove confirmed React Native artifacts and stale mobile instructions without touching web/backend source or contracts. Done — docs only (`README.md`, `ARCHITECTURE.md`, one build comment); the `mobile/` tree itself was already gone.
- [x] **Step 3.** Search the repository and review the diff for scope and wording; record unavailable/untracked residue as a migration gap. Done: remaining RN mentions are this plan file (task definition) plus the standing "No Expo" rule.
- [x] **Step 4. Commit.** `git commit -m "chore(android): remove React Native residue and update docs"`. Landed as `51cbf5a`.

### Task 10: Visual polish vs the decoded boards (follow-up, not in #27 scope)

**Why:** Tasks 1–9 deliver behavior with stock Material3 components in the design palette. The decoded boards (`android/android_rtl.html`, see Design source) specify more: the app has no launcher icon, empty tab icons, system-default font, and several composition/copy deltas. CI has no emulator/screenshots, so verify with on-device screenshots in both locales.

**Files:**
- Add: launcher icon + tab icons in the design language, bundled Arabic font (boards ship IBM Plex Sans Arabic woff2 — check its license before bundling; fallback: downloadable font), board-15 empty-state art if cheap.
- Modify: theme typography (Plex), button/chip shapes to board styling, auth screens (pre-auth English toggle per board 1), period chips copy (`آخر 7 أيام` / `آخر 14 يومًا` / `آخر 30 يومًا`), pain 0–10 numbered stepper with teal selection, plans/proposal/edit composition per boards 12–13, addendum copy (`ملحق` per board 8 vs current `تصحيح` — confirm with owner before renaming strings).
- Test: screenshot/manual walkthrough per board (1–15) in `ar` + `en`; no behavior or contract changes — any functional gap found becomes a new issue instead of scope creep here.

**Constraints:**
- No string-key renames without updating both locales + `StringsParityTest`; no backend changes; no new product features.
- Keep `release` buildType minify-off; verify the `minified` R8 build on device after any resource/theme change.

- [ ] Steps 1–5: same TDD cycle where testable (unit-test what is unit-testable; visual deltas verified by screenshots, recorded in the PR).
