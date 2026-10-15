# Premium Native Android Offline Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a polished, interactive Kotlin/Compose Android preview that launches and works offline with clearly fictional local data, while preserving the production Retrofit and authentication paths.

**Architecture:** Keep the existing manual `AppGraph`, `BackendApis`, repositories, ViewModels, Compose routes, and API DTOs. Select the in-memory `DemoBackendApis` only for the debug preview build; keep release/minified builds on `RetrofitBackendApis`. Refine the existing theme and shared Compose components, then redesign the existing destinations in place and verify behavior with unit, Compose UI, localization, and offline-device tests.

**Tech Stack:** Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Jetpack Compose / Material 3 (Compose BOM 2024.10.00), Navigation Compose 2.7.7, JDK 17, JUnit 4, Compose UI tests.

**Spec:** User-provided task in the 2026-10-14 conversation. No separate spec file is required or planned.

## Global Constraints

- Use the existing native Android app in `android/`; do not replace it with a mockup or cross-platform client.
- The existing app uses Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.10.00, compile/target SDK 35, and Java/JVM 17.
- Preserve the established primary color `#0E6B66` unless a documented contrast issue justifies a change.
- Debug preview must run without a backend, database, Docker, remote API, or runtime internet connection.
- Keep Retrofit, authentication, API contracts, and production behavior intact; demo-only behavior must remain isolated to the debug build.
- Use fictional demo content only; label illustrative AI content and never present it as diagnosis or medical advice.
- Preserve English and Arabic resources, correct RTL behavior, existing destinations, and existing application data semantics.
- Add no dependency unless an existing dependency cannot meet a demonstrated need.
- Preserve existing work in the tree; do not reset, discard, or overwrite uncommitted changes.
- The feature branch must not target `main`: the native app exists on `preview/full-stack`, while `main` does not contain `android/`. Base the review PR on `preview/full-stack` unless a later repository change makes that branch inappropriate.

## Review Focus

- Cold debug launch with no token and airplane mode must reach useful demo content without attempting a remote request; cover in Tasks 1 and 10.
- Debug demo selection must not leak into release/minified builds, which must still use Retrofit; cover in Task 1.
- Rapid repeated note-save taps must not create duplicate in-memory notes, and the created note must appear in history/detail immediately; cover in Tasks 1 and 5.
- Long Arabic and mixed-direction text, large font scale, and narrow width must remain readable, correctly directed, and unclipped; cover in Tasks 8 and 10.
- Refresh failures must retain already-visible safety flags, while empty/loading/error/success states remain accessible and actionable; cover in Tasks 3, 4, 6, 7, and 10.

---

## Baseline and File Map

The current branch is `feat/premium-android-redesign`, based directly on `preview/full-stack`; it is two commits ahead of that branch. `main` and `preview/full-stack` have diverged, and the current `main` tree does not contain the native Android application. The safe implementation and PR base is therefore the preview branch, not `main`.

The offline-demo wiring has since been committed as `f128881 feat(android): offline demo backend for debug builds`:

- `android/app/build.gradle.kts` — defines `DEMO_BACKEND` per build type.
- `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt` — selects `DemoBackendApis` for the debug build and Retrofit otherwise.
- `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt` — centralized fictional sample data and in-memory API behavior.

The current worktree was clean when inspected; the plan file itself is under a path ignored by `.gitignore`. The committed demo implementation is in scope and must be reviewed, preserved, and completed rather than overwritten. The existing app is Kotlin + Jetpack Compose/Material 3 with a manual `AppGraph`, `BackendApis` seam, feature ViewModels, typed route builders, DataStore settings, Retrofit production APIs, Arabic/English resources, bundled Plex Arabic fonts, and existing JVM plus instrumentation tests.

Existing destinations include login/register/server URL; home; recipients list/add/detail; notes-today/history; note editor/saved/detail/addendum; summary period/result; plans/proposal/edit/versions; and settings. Screens are already connected to the API/ViewModel architecture. The offline demo provider has been started but has no dedicated tests yet.

The supplied `/workspaces/android_rtl.html` visual reference is available and labels 15 existing screen/state boards; it is outside the repository and should be used as a visual reference, not copied into the app. Visual issues evidenced in the current UI include repeated raw spacing/shape/type values despite existing tokens; hard-coded white/color values in reusable UI; emoji or text glyphs used as icons; duplicated home progress/attention metrics; some generic or repeated cards; hard-coded display fragments and direct route strings; and screens whose appearance/state behavior still needs narrow-screen, large-text, and RTL verification.

Primary files to change are `android/README.md`, `CaregiverApp.kt`, `CaregiverRoot.kt`, `MainScaffold.kt`, `core/theme/{Color,Spacing,Theme,Type}.kt`, `presentation/common/DesignSystem.kt`, existing screen files under `presentation/{auth,home,history,notes,plans,recipients,settings,summary}/`, `data/demo/DemoBackend.kt`, both `values/strings.xml` and `values-ar/strings.xml`, their related `test/` and `androidTest/` files, and `.github/workflows/android.yml` so checks run for the correct preview PR base. Do not change backend code.

### Phase 0 screen audit (verified against the current implementation)

Every destination below is wired to its ViewModel/repository path and covered by JVM and/or instrumentation tests; no stub or "coming soon" screens remain in `presentation/`. "Backend-backed" means Retrofit production APIs with `DemoBackendApis` serving the debug build; "local-only" means DataStore on-device state.

- Login / register (`presentation/auth/`): backend-backed, complete. `AuthViewModel` validates and submits through `AuthRepository`; debug builds add the demo entry (`AuthFormUiTest`, `RtlUiTest`).
- Server URL (`presentation/settings/ServerUrlScreen`): local-only, complete. Reads/writes DataStore; language switching and sign-out covered by settings/RTL tests.
- Home (`presentation/home/`): backend-backed, complete. Recipients + notes-today window, fall-only safety banner with refresh-failure retention (`HomeViewModelTest`, `HomeUiTest`, `ScaffoldUiTest`).
- Recipients list / add / detail (`presentation/recipients/`): backend-backed, complete. Existing DTO fields only; list → add → detail → editor/summary/plans navigation (`RecipientsFlowUiTest`, recipient ViewModel tests).
- Notes-today / history (`presentation/history/`): backend-backed, complete. Stable-key list, recipient/date filters, empty/error/retry, note-detail navigation (`HistoryFlowUiTest`, `HistoryViewModelTest`).
- Note editor / saved / detail / addendum (`presentation/notes/`): backend-backed, complete. Validation, disabled/progress save, double-tap guard, immutable original with chronological addenda (`NoteEditorFlowUiTest`, editor/detail ViewModel tests).
- Summary period / result (`presentation/summary/`): backend-backed, complete. 7/14/30-day windows, evidence/uncertainty sections, demo content labeled illustrative with a non-diagnosis disclaimer (`SummaryFlowUiTest`, summary ViewModel tests, `BannerRetentionTest`).
- Plans / proposal / edit / versions (`presentation/plans/`): backend-backed, complete. Existing accept/edit/dismiss/version transitions with medication protections unchanged (`PlanFlowUiTest`, plan ViewModel tests).
- Settings (`presentation/settings/SettingsScreens.kt`): local-only, complete. Language chips, server-URL entry, danger-outline sign-out; bilingual RTL verified (`RtlUiTest`).
- Demo provider (`data/demo/DemoBackend.kt`): demo/local-only, complete. Debug-only in-memory fixtures and mutations; release/minified builds select Retrofit (`DemoBackendApisTest`, `AppGraphProviderTest`, `OfflineDemoFlowUiTest`).
- Placeholders: none. Every `AppDestinations` route resolves to an implemented screen.

## Implementation Tasks

### Task 1: Complete and prove the isolated offline demo path

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverRoot.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- Modify: `android/README.md`
- Create: `android/app/src/test/java/com/caregiver/mobile/data/demo/DemoBackendApisTest.kt`
- Create: `android/app/src/test/java/com/caregiver/mobile/AppGraphProviderTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/OfflineDemoFlowUiTest.kt`

**Interfaces:**
- Consumes: `BackendApis`, existing API interfaces/DTOs, `AuthRepository`, and the existing `BuildConfig.DEMO_BACKEND` work in the current dirty tree.
- Produces: Debug-only demo entry and local API behavior using the existing API interfaces; non-debug builds continue to instantiate `RetrofitBackendApis`.

- [ ] **Step 1: Add failing demo API tests** named `defaultFixturesAreConsistent`, `createdNoteAppearsInHistoryAndDetail`, `addendumAppendsWithoutChangingOriginal`, `planActionsUpdateLocalVersions`, and `unknownIdsReturnNotFound`. Assert stable recipient/note links, chronological addenda, in-memory mutations, and expected failure behavior.
- [ ] **Step 2: Run the focused tests and verify they fail for missing/incorrect demo behavior.** Run `cd android && ./gradlew :app:testDebugUnitTest --tests "*DemoBackendApisTest*" --no-daemon`. Expected: failures until the demo fixture/API behavior is covered.
- [ ] **Step 3: Review and complete `DemoBackendApis`** with a small internally consistent fictional fixture set using existing DTOs. Keep data and mutations local to the provider, use date-relative fixtures, label summary/plan examples as illustrative, and ensure plan copy does not read as medical advice.
- [ ] **Step 4: Add a debug-only “Explore demo” entry** using the existing auth/repository path and local demo API; keep that control absent from release/minified builds. Avoid weakening production authentication or bypassing the production API.
- [ ] **Step 5: Add `AppGraphProviderTest`** asserting the current build's `BuildConfig.DEMO_BACKEND` matches the selected API implementation, without initiating HTTP calls. Run the same test against debug, release, and minified unit-test variants so the non-debug provider selection is verified.
- [ ] **Step 6: Verify focused unit tests pass.** Re-run the command from Step 2.
- [ ] **Step 7: Add `OfflineDemoFlowUiTest`** to start at the real root, enter the demo, navigate to recipients/history, save a valid note, and open its detail; assert no network server is configured or contacted and the saved note is immediately visible.
- [ ] **Step 8: Update `android/README.md`** to distinguish offline debug preview from backend-connected release/minified builds, document the debug APK path and the demo entry, and state that demo mutations are in-memory and are not uploaded.
- [ ] **Step 9: Run provider tests for debug, release, and minified variants** with `cd android && ./gradlew :app:testDebugUnitTest :app:testReleaseUnitTest :app:testMinifiedUnitTest --no-daemon`; verify all variants select the expected provider.
- [ ] **Step 10: Verify the committed offline-demo work remains intact** and inspect `git diff` plus `git status`; do not clean or reset user changes.
- [ ] **Step 11: Commit** as `feat(android): verify offline demo entry and provider isolation`.

### Task 2: Consolidate the visual system into named theme tokens

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/theme/Color.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/theme/Spacing.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/theme/Theme.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/theme/Type.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/common/DesignSystem.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/core/theme/ThemeTest.kt`

**Interfaces:**
- Consumes: Current primary teal `#0E6B66`, current neutral/semantic palette, bundled Plex Arabic fonts, and Material 3.
- Produces: Named palette roles, spacing/type/shape tokens, and reusable controls/states consumed by all screen tasks.

- [ ] **Step 1: Extend `ThemeTest`** with assertions that the primary color remains `0xFF0E6B66`, semantic colors are distinct from their containers, and app typography uses the bundled Plex Arabic family.
- [ ] **Step 2: Run `cd android && ./gradlew :app:testDebugUnitTest --tests "*ThemeTest*" --no-daemon` and verify the new role assertions fail before implementation.**
- [ ] **Step 3: Refine token objects** for canvas/surface/ink/muted/border/primary/success/warning/info/danger roles, 8-dp rhythm and screen/control/card dimensions, and consistent shapes; preserve existing roles and values unless a specific contrast or layout problem is demonstrated.
- [ ] **Step 4: Refine shared components** in `DesignSystem.kt` to consume tokens, replace text/emoji icon parameters with existing Material icons or typed icon inputs, and preserve accessible content descriptions, state text, touch targets, and existing loading/empty/error/retry behavior.
- [ ] **Step 5: Replace duplicated raw values in shared components** with tokens and verify `ThemeTest` passes with the Step 1 assertions.
- [ ] **Step 6: Commit** as `feat(android): unify premium theme tokens and components`.

### Task 3: Redesign the navigation shell and home dashboard

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeScreen.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/ScaffoldUiTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/home/HomeUiTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/home/HomeViewModelTest.kt`

**Interfaces:**
- Consumes: `HomeContent`, `HomeState`, `MainTab`, `AppRoutes`, and shared design components from Tasks 1–2.
- Produces: A refined four-tab shell and dashboard that render existing data/flags without adding metrics or changing refresh semantics.

- [ ] **Step 1: Add UI assertions** for selected/inactive tab accessibility labels, one primary note action, recipient cards, empty content, and the safety banner in content and refresh-error states.
- [ ] **Step 2: Run the focused instrumentation tests** with `cd android && ./gradlew :app:connectedDebugAndroidTest --tests "*ScaffoldUiTest*" --tests "*HomeUiTest*" --no-daemon`; expected: missing assertions fail before the UI update.
- [ ] **Step 3: Update `MainScaffold`** to consume shared spacing/color/shape tokens and existing tab routes, retain all current destinations/back-stack behavior, and keep the primary note action clearly differentiated.
- [ ] **Step 4: Update `HomeScreen`** to remove repeated summary metrics, create a clear greeting/action/recipient hierarchy from `HomeContent`, use semantic icons instead of glyphs, and preserve safety flags during refresh and failure.
- [ ] **Step 5: Verify** `HomeViewModelTest`, `HomeUiTest`, and `ScaffoldUiTest`; confirm the ViewModel still makes bounded existing requests and no home count/flag is invented.
- [ ] **Step 6: Commit** as `feat(android): refine home dashboard and navigation`.

### Task 4: Redesign recipient list, add form, and detail

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Existing `RecipientDto`, recipient ViewModels, `AppRoutes`, and shared tokens/components.
- Produces: Consistent recipient list/add/detail screens using only fields already present in recipient and note models.

- [ ] **Step 1: Extend `RecipientsFlowUiTest`** with `emptyListShowsLocalizedEmptyState`, `failedLoadCanRetry`, `validAddReturnsToUpdatedList`, `recipientOpensDetail`, and `longMixedNameRemainsVisible`; assert loading and error copy are announced.
- [ ] **Step 2: Implement the recipient screen hierarchy** with tokenized layout, proper RTL-aware directional icons, useful existing secondary data, and explicit primary versus secondary actions; do not invent age or health profile fields.
- [ ] **Step 3: Run recipient tests** with `cd android && ./gradlew :app:testDebugUnitTest --tests "*RecipientsViewModelTest*" --tests "*RecipientDetailViewModelTest*" :app:connectedDebugAndroidTest --tests "*RecipientsFlowUiTest*" --no-daemon`; verify all pass and create/refresh/back-stack behavior remains wired to the existing ViewModel.
- [ ] **Step 4: Commit** as `feat(android): redesign recipient screens`.

### Task 5: Redesign note creation, saved confirmation, detail, and addenda

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteScreens.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteEditorViewModel.kt` only if an observed UI interaction exposes a submission/state defect
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteDetailViewModel.kt` only if needed to support existing behavior
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/notes/NoteEditorFlowUiTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/notes/NoteEditorViewModelTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/notes/NoteDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Existing note/addendum DTOs and ViewModels; `CreateNoteRequest` and `CreateAddendumRequest` remain unchanged.
- Produces: Tokenized note forms and details that retain original-note immutability, validation, submit state, and chronological addendum display.

- [ ] **Step 1: Add UI tests** named `noteFormValidatesRequiredMoodAndText`, `validNoteAppearsAfterSave`, `doubleTapSaveCreatesOneDemoNote`, `noteDetailSeparatesOriginalFromAddenda`, and `addendumAppendsChronologically`; assert that a rejected/failed submission leaves entered text visible.
- [ ] **Step 2: Run those tests and verify the new behavior assertions fail** using the focused instrumentation and JVM test selectors.
- [ ] **Step 3: Redesign the form** with clear observation groups, responsive pain selection, readable free-text entry, visible validation/server feedback, keyboard-aware scrolling/focus, and disabled/progress save states. Keep every existing structured field and request mapping.
- [ ] **Step 4: Redesign saved/detail/addendum screens** to distinguish the immutable original from append-only correction history, show dates and existing status values, and use shared components/icons instead of glyphs.
- [ ] **Step 5: Verify** UI and ViewModel tests, including Arabic free-text preservation and the local demo save-to-history round trip.
- [ ] **Step 6: Commit** as `feat(android): polish note entry and addendum flows`.

### Task 6: Redesign history and notes-today list

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/history/HistoryScreen.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/history/HistoryViewModel.kt` only for defects demonstrated by tests
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/history/HistoryFlowUiTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/history/HistoryViewModelTest.kt`

**Interfaces:**
- Consumes: Existing history entries, recipient/date filters, and note detail route.
- Produces: Readable stable-key lazy history list with functional filters, empty/error/retry states, and navigation to note details.

- [ ] **Step 1: Add `HistoryFlowUiTest` cases** `recipientAndDateFiltersUpdateResults`, `emptyRangeShowsEmptyState`, `failedHistoryCanRetry`, `entryOpensNoteDetail`, and `longArabicPreviewWraps`; assert existing date boundary semantics remain unchanged.
- [ ] **Step 2: Redesign `HistoryScreen`** with clear recipient/date context, balanced timeline/card treatment, tokenized styles, and localized directional affordances; preserve today's-note filtering and history navigation.
- [ ] **Step 3: Run history tests** with `cd android && ./gradlew :app:testDebugUnitTest --tests "*HistoryViewModelTest*" :app:connectedDebugAndroidTest --tests "*HistoryFlowUiTest*" --no-daemon`; verify lazy items continue using stable note IDs and no new API requests are introduced from recomposition.
- [ ] **Step 4: Commit** as `feat(android): redesign note history`.

### Task 7: Redesign summary and care-plan destinations

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/summary/SummaryScreen.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlanScreens.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/summary/SummaryFlowUiTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/plans/PlanFlowUiTest.kt`
- Test: existing summary/plan ViewModel tests under `android/app/src/test/java/com/caregiver/mobile/presentation/{summary,plans}/`

**Interfaces:**
- Consumes: Existing summary/plan API DTOs, ViewModels, and existing disclaimer/safety/error semantics.
- Produces: Clearly labeled illustrative demo content, accessible AI/unavailable/uncertainty/safety sections, and working existing plan transitions.

- [ ] **Step 1: Add `SummaryFlowUiTest` and `PlanFlowUiTest` cases** for 7/14/30-day selection, illustrative demo label, disclaimer and source evidence, safety flags on summary failure, empty plan list, and accept/edit/dismiss/version navigation.
- [ ] **Step 2: Redesign summary and plans screens** with clear distinction between source notes, illustrative AI text, user-reviewed plan states, uncertainty, and safety warnings. Do not call a real AI service in debug demo mode or invent a diagnosis/claim.
- [ ] **Step 3: Run summary and plan tests** with `cd android && ./gradlew :app:testDebugUnitTest --tests "*SummaryViewModelTest*" --tests "*PlansViewModelTest*" --tests "*PlanDetailViewModelTest*" --tests "*PlanEditViewModelTest*" :app:connectedDebugAndroidTest --tests "*SummaryFlowUiTest*" --tests "*PlanFlowUiTest*" --no-daemon`; assert existing acceptance/edit/dismiss flows and medication protections are unchanged.
- [ ] **Step 4: Commit** as `feat(android): redesign summaries and care plans`.

### Task 8: Redesign authentication/settings and complete localization/accessibility

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/auth/AuthForm.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/SettingsScreens.kt`
- Modify: affected screens from Tasks 3–7 as needed for localized copy and accessibility semantics
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ar/strings.xml`
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/i18n/Bidi.kt` only if tests prove a gap
- Test: `android/app/src/test/java/com/caregiver/mobile/core/i18n/StringsParityTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/auth/AuthFormUiTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/rtl/RtlUiTest.kt`

**Interfaces:**
- Consumes: Existing `AuthViewModel`, settings/localization flow, `Bidi`, locale-aware dates, and existing English/Arabic resources.
- Produces: Polished auth/settings experiences with debug-only demo entry, complete string parity, semantic controls, and correct locale-driven LTR/RTL.

- [ ] **Step 1: Add auth/settings UI tests** for demo entry visibility only in debug, password visibility behavior if added, busy/error states, language switching, URL settings preservation, and sign-out; verify release does not expose the demo entry.
- [ ] **Step 2: Move changed visible copy into both resource files**; add no untranslated composable text except standard language names and user data. Keep format placeholders equivalent.
- [ ] **Step 3: Update auth/settings layout** for keyboard use, validation, server errors, focus, tap targets, and consistent design tokens; do not change auth or URL contracts.
- [ ] **Step 4: Extend RTL tests** to cover recipient/note/plan/summary screens, mixed Arabic-English IDs/email, directional icons, and date/number content. Add a test with large font scale and long Arabic labels.
- [ ] **Step 5: Run** `cd android && ./gradlew :app:testDebugUnitTest --tests "*StringsParityTest*" --tests "*BidiTest*" --no-daemon` and the focused auth/RTL instrumentation tests; verify both locales and all string placeholders match.
- [ ] **Step 6: Commit** as `feat(android): polish bilingual auth and accessibility`.

### Task 9: End-to-end offline verification, screenshots, and Android CI

**Files:**
- Modify: `.github/workflows/android.yml`
- Test: relevant existing and newly added JVM/instrumentation tests
- Output: `android/app/build/outputs/apk/debug/app-debug.apk`

**Interfaces:**
- Consumes: All screens, debug offline graph, and current Gradle build/test configuration.
- Produces: Verified offline debug APK, meaningful Android CI on the correct PR base, and documented verification results in the PR description (not a new product spec).

- [ ] **Step 1: Add an Android CI test/lint job or steps** for `:app:testDebugUnitTest` and `:app:lintDebug`; include `preview/full-stack` in the `pull_request.branches` trigger because that is the correct native-app PR base. Retain existing debug/minified APK artifact behavior.
- [ ] **Step 2: Run the required checks with JDK 17** from `android/`:

```bash
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:lintDebug --no-daemon
```

Expected: all commands succeed and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 3: If a compatible emulator/device is available, run** `./gradlew :app:connectedDebugAndroidTest --no-daemon`; report unavailable infrastructure rather than claiming success if none is available.
- [ ] **Step 4: Install the debug APK, enable airplane mode, launch it, enter demo mode, and walk all existing major destinations.** Verify recipient details, note save/detail/addendum, history filters, summary/plan actions, settings/language switching, and no network-dependent startup/navigation.
- [ ] **Step 5: Capture and review screenshots** for home, recipient list/detail, history, note form, and at least one Arabic RTL screen. Fix clipping, bad spacing, weak contrast, incorrect direction, or obscured actions before completion.
- [ ] **Step 6: Re-run string parity and the complete JVM suite** after screenshot-driven fixes; inspect `git diff --check`, the complete diff, and worktree state to ensure no backend/API/dependency changes or unrelated edits were introduced.
- [ ] **Step 7: Build the final debug APK** and report its exact path, commands/results, emulator availability, screenshots, and known limitations. Prepare a review PR against `preview/full-stack`; do not merge it.
- [ ] **Step 8: Commit** as `ci(android): validate offline preview builds on preview PRs`.
