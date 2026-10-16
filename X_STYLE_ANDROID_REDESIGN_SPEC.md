# X-Style Redesign Spec: Caregiver Daily Notes (Android)

Target repo: `alaa157/caregiver-daily-notes`
Target branch: `feat/premium-android-redesign` (latest, commit `99bac54`)
Stack: native Kotlin, Jetpack Compose, Material3, Navigation Compose. Package `com.caregiver.mobile`.

Goal: make the app feel like the X (Twitter) mobile app. That means a feed that scrolls like a timeline, a top header that hides on scroll, an icon-only bottom tab bar, a drawer opened from the avatar, and a round compose button. Keep the caregiving content and the existing brand palette.

---

## 0. Rules for every agent (read first)

1. Do not add new colors. Use `CaregiverColors` in `core/theme/Color.kt`. Dark mode (Task 9) may add dark variants of the same tokens only.
2. Do not hardcode sizes. Add new ones to `AppDimens` in `presentation/common/DesignSystem.kt`.
3. Every new user-facing string goes in BOTH `res/values/strings.xml` and `res/values-ar/strings.xml`. `StringsParityTest` fails otherwise.
4. RTL must keep working. Use start/end, never left/right. Directional icons must flip (see `ForwardChevron`).
5. Keep existing test tags: `tab_${route}` on each tab. Add new tags for new elements, never remove old ones.
6. The safety banner (`SafetyBanner` / `SafetyAlertCard`) is non-dismissible and must never be hidden by scroll behavior, collapsed, or given a close button.
7. Do not invent API endpoints. Check `docs/api/android-gaps.md` before any task that needs new data. If the API lacks it, do it client-side and leave a `TODO(api-gap)` comment.
8. Do not edit `backend/`. Android and backend build separately.
9. One task = one small PR. Branch name `feat/x-style-task-N-short-name`, based on `feat/premium-android-redesign`.
10. Before opening a PR, run in `android/`:
    - `./gradlew :app:testDebugUnitTest`
    - `./gradlew :app:lintDebug`
    - `./gradlew :app:assembleDebug`
    If a command cannot run in your environment, say so in the PR. Never claim it passed.
11. Use JDK 17. Newer JDKs break the build.

## 1. Current state (what exists today)

- `MainScaffold.kt` owns the shell: `Scaffold` with a bottom `NavigationBar` (4 tabs: Home, People, Notes, History, fixed 64dp), an `ExtendedFloatingActionButton` ("Add note"), and a `NavHost` with 180ms fade transitions.
- There is NO shared top bar. Each screen draws its own header (`AppTopBar` in the design system, or an inline `Row` as in `HomeScreen`).
- Tabs navigate with `popUpTo(Home)` + `launchSingleTop`, with no state saving, so tab scroll position is lost.
- Home is a `LazyColumn` of boxed `AppCard`s per care recipient, not a timeline of notes.
- The FAB sends the user to the People tab, because the note editor needs a recipient id.
- "Offline" today only means the demo backend: `DEMO_BACKEND=true` serves in-memory `DemoBackendApis` (`data/demo/`). Nothing is stored on disk and nothing syncs.
- Plans and Settings have no tab. Settings opens from an icon on Home.

## 2. Target design (X pattern, caregiver content)

| X element | Our version |
|---|---|
| Top bar: avatar at start, logo in center, action at end, hides on scroll down, returns on scroll up | Same. Avatar opens drawer. Center shows the app name. End action is a sync/offline status icon. |
| Feed tabs under the header ("For you / Following") | Home sub-tabs: Today, Needs attention, All |
| Timeline of full-width posts separated by hairlines | Timeline of note rows, no boxed cards |
| Icon-only bottom bar, filled icon when active | Same: no labels, outlined to filled icon swap, no pill indicator |
| Tap active tab = scroll to top and refresh | Same on every tab |
| Round compose FAB at bottom end | Round 56dp FAB with a note-add icon, hides on scroll down |
| Pull to refresh | Same on Home, Notes, History |
| Side drawer from avatar | Drawer: profile, People, Plans, Summaries, Settings, language, sync status |
| Post action row (reply, repost, like) | Note action row: add addendum, summary, flag |

Visual rules:
- Rows: 16dp horizontal padding, 12dp vertical, 1dp `BorderSoft` divider between rows, no card shadow.
- Row layout: 40dp avatar at start, content column beside it. Line 1 is bold name, then a muted dot and relative time ("2h"). Line 2 is the note text, max 4 lines then "Show more". Line 3 is pills (mood, pain, fall). Line 4 is the action row.
- Header height 56dp. Bottom bar 56dp plus system inset (currently 64dp, keep one value in `AppDimens.BottomNavH`).
- Motion: tab change cross-fade 180ms stays. Header and FAB hide and show with the scroll direction.

## 3. Tasks (do in order)

### Task 0: Check dependencies (no code change unless needed)
- Read `android/app/build.gradle.kts` and `gradle/libs.versions.toml`.
- Record the Compose BOM and Material3 version in the PR description.
- `PullToRefreshBox` needs Material3 1.3 or newer. `enterAlwaysScrollBehavior` is in Material3 already.
- If Material3 is older, bump only that, and only if tests still pass.
- Done when: the PR states the versions and whether a bump was needed.

### Task 1: Shared top bar with scroll hiding
- Files: `MainScaffold.kt`, `presentation/common/DesignSystem.kt`.
- Add `AppTopBar`-style `CenterAlignedTopAppBar` to the Scaffold `topBar` for the 4 tab routes only. Detail routes keep their own `AppTopBar` with back button.
- Use `TopAppBarDefaults.enterAlwaysScrollBehavior()` and `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` on the Scaffold.
- Start slot: `Avatar` (user initial). End slot: placeholder status icon.
- Remove the duplicated greeting header row from `HomeScreen` and move the greeting into the first feed item.
- Safety banner: render it OUTSIDE the scrolling area, directly under the top bar, so it stays visible (Rule 6).
- Done when: scrolling down hides the bar, scrolling up shows it, banner always visible, UI works in Arabic RTL, tests and lint pass.

### Task 2: X-style bottom bar
- File: `MainScaffold.kt`.
- Set `alwaysShowLabel = false` on items, indicator color transparent, selected icon `Filled.*`, unselected `Outlined.*` (add the Outlined imports).
- Save tab state: `navigate(tab.route) { popUpTo(graph start) { saveState = true }; launchSingleTop = true; restoreState = true }`.
- Re-tapping the active tab emits a "scroll to top" event (use a `SharedFlow` or a `ScrollToTop` object in `AppGraph`) that each tab screen collects.
- Keep `contentDescription` on icons (the label is gone, accessibility needs it). Use the existing tab title strings.
- Keep the `tab_${route}` test tags.
- Done when: switching tabs preserves scroll position, re-tap scrolls to top, TalkBack reads each tab name.

### Task 3: Round FAB with recipient picker
- File: `MainScaffold.kt`, new `presentation/notes/RecipientPickerSheet.kt`.
- Replace `ExtendedFloatingActionButton` with a round `FloatingActionButton` (56dp, `Icons.Filled.NoteAdd`, `CaregiverColors.Primary`).
- Tap opens a `ModalBottomSheet` listing active recipients. Picking one navigates to `AppRoutes.noteEditor(recipientId)`. This fixes the current detour through the People tab.
- If there is exactly one recipient, skip the sheet and go straight to the editor.
- Hide the FAB while scrolling down (animate with `AnimatedVisibility`), show on scroll up.
- Reuse the recipients data source already used by `RecipientsScreen`. Do not add a new endpoint.
- Done when: FAB to editor takes at most 2 taps, works offline against the demo backend, test tag `fab_add_note` exists.

### Task 4: Note timeline row component
- New file `presentation/common/NoteRow.kt`.
- Composable `NoteRow(avatarLetter, name, time, body, pills, onClick, actions)` following section 2 layout. Use `Bidi.isolate()` for names and mixed-direction text exactly as `HomeScreen` does.
- Use existing pills: `GreenPill`, `RedPill`, `GrayPill`, `YellowPill`, `LightBluePill`.
- Add a `NoteRowSkeleton` using `SkeletonBar`.
- Add a Compose preview and one UI test with an Arabic name and an English name.
- Done when: row renders correctly in LTR and RTL, no shadow, hairline divider, tests pass.

### Task 5: Home as a timeline with sub-tabs and pull to refresh
- Files: `presentation/home/HomeScreen.kt`, `HomeViewModel.kt`.
- Replace the card list with `LazyColumn` of `NoteRow`.
- Add a `PrimaryTabRow` under the top bar with 3 tabs: Today, Needs attention, All. Filter client-side from data `HomeViewModel` already loads.
- Wrap in `PullToRefreshBox` calling `vm.refresh()`. Remove the thin `LinearProgressIndicator` refresh bar.
- Keep stats (done count, attention count) as a compact row at the very top of the feed.
- Pagination: check `docs/api/android-gaps.md`. If the API has no paging, page client-side (load more when the last 5 rows are visible).
- Keep empty, error, and loading states using `AppEmptyState`, `AppErrorState`, `NoteRowSkeleton`.
- Done when: all three sub-tabs filter correctly, refresh works, states match README "State catalog", existing Home tests still pass or are updated with the same coverage.

### Task 6: Apply the timeline to Notes and History tabs
- Files: `presentation/history/HistoryScreen.kt` (used by both tabs).
- Render with `NoteRow`, add pull to refresh and scroll to top support from Task 2.
- Keep date and person filters on History. Show them as a collapsible chip row under the top bar. Notes tab keeps `showFilters = false`.
- Done when: both tabs share the same row design and filters still narrow results.

### Task 7: Avatar drawer
- Files: `MainScaffold.kt`, new `presentation/common/AppDrawer.kt`.
- Wrap the scaffold in `ModalNavigationDrawer`. Avatar in top bar opens it. Swipe from the start edge also opens it (mirrors in RTL automatically).
- Items: profile header (name), People, Plans (`AppDestinations.Plans`), Settings, language switch (reuse whatever Settings already does for locale), sync status line.
- Move the Settings icon off Home into the drawer.
- Done when: every item navigates, drawer closes after navigation, back button closes an open drawer first.

### Task 8: Real offline mode (largest task, split into sub-PRs)
Current offline is only the in-memory demo. For real offline use:
- 8a: Add Room database for recipients and notes, as the single source for UI. Repositories read from Room, refresh from the network when online.
- 8b: Add an outbox table for notes created or edited offline (status: pending, syncing, failed). Show a clock icon on pending rows (like an unsent post).
- 8c: Add a WorkManager job that drains the outbox when connectivity returns, with retry and backoff. Never upload demo data.
- 8d: Show status in the top bar end slot: synced, syncing, offline, failed (tap to retry).
- Constraint: the existing offline draft behavior (summary screen keeps draft on transport failure) must not regress.
- Constraint: check `docs/api/android-gaps.md` for idempotency or conflict support before 8b. If missing, generate a client UUID per note and note the gap in the PR.
- Done when: in airplane mode a note can be created and appears in the timeline as pending, then syncs automatically after reconnecting.

### Task 9 (optional): Dark mode
- Add dark variants of the existing tokens in `Color.kt` and wire them in `Theme.kt`. Follow system setting. Verify contrast of pills and the safety banner.

## 4. Verification checklist (copy into every PR)

- [ ] Works in English and Arabic (RTL), including rotation
- [ ] Safety banner still visible and non-dismissible
- [ ] No new colors or hardcoded dimensions
- [ ] New strings exist in both `strings.xml` files
- [ ] `./gradlew :app:testDebugUnitTest` result noted
- [ ] `./gradlew :app:lintDebug` result noted
- [ ] `./gradlew :app:assembleDebug` result noted
- [ ] Screenshots attached (LTR and RTL)

## 5. Not verified yet

This spec was written from these files only: `android/README.md`, `MainScaffold.kt`, `DesignSystem.kt`, `Routes.kt`, `HomeScreen.kt`, `Color.kt`. Before Tasks 5, 6 and 8, the agent must read `HistoryScreen.kt`, the view models, `data/` repositories, and `docs/api/android-gaps.md`, and adjust the task if reality differs.
