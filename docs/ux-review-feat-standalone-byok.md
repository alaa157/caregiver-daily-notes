# UI/UX Implementation Review — `feat/standalone-byok-ai`

*Reviewer scope: `android/` only · method: full read of the branch (cloned locally at `9c8f503`), no builds/tests executed locally. Review date: 2026-10-22.*

---

## 1. Verdict (max 8 lines)

**Ready with fixes.** The core journeys work, the safety architecture is real (banner preserved by construction, med lock, consent gate, grounding), and light-mode contrast passes everywhere I computed it.

Top 3 risks:
1. **Dark mode breaks AA on the selected controls of the note editor** (`#0E6B66` on `#1A3A38` = **1.94:1**) and all 19 "Metrolist" accent palettes make the FAB / selected tab / *medication-taken* checkmark nearly invisible (as low as **1.39:1**) — a user-reachable setting that corrupts medication-state legibility.
2. **Arabic-first copy is not actually first-class**: hardcoded English ("3 people"), mistranslated safety wording (“ألم عال”), untranslated theme strings — `StringsParityTest` can't catch any of these.
3. **Standalone-integrity leakage**: a login/server stack remains behind `LOCAL_MODE` and a dev-only "Load sample data" row ships in the release build with no way to ever remove the recipients it injects.

Top 3 strengths: the failure ladder (bounded generation + cancel + retained flags + recovery actions) is the best part of the app; the state/parity/RTL/rotation test net is genuinely protective; the standalone Room path makes offline note save literally thread through no network code.

---

## 2. Scope and method

**Cloned** at `feat/standalone-byok-ai` head `9c8f503` (depth 1). **Read in full:**
docs (`README.md`, `ARCHITECTURE.md`, `android/README.md`, `docs/api/android-gaps.md`) · `core/theme/*` (Color, Theme, Type, Spacing, ThemeMode) · `core/i18n/Bidi.kt` · `core/navigation/RootScreen.kt`, `Routes.kt` · `core/time/DateFormats.kt` · `core/util/LocaleHelper.kt`, `LanguagePrefs.kt` · `MainActivity`, `MainScaffold`, `CaregiverRoot`, `CaregiverApp` (AppGraph) · `presentation/common/` (DesignSystem, NoteRow, AccountPopupMenu, SyncStatusIndicator, AiRecovery, TopBarHide, SquigglySlider/SmartToast/AppDrawer/SwipeActionRow — confirmed unused) · `ui/component/AppNavigation.kt` + dead-file sweep · screens: `onboarding/OnboardingScreen`, `home/HomeScreen`, `home/MedicationViewModel`(+MedicationsCard), `notes/*` (screens + editor/detail VMs, NoteOptions), `recipients/RecipientScreens`, `history/HistoryScreen`, `summary/SummaryScreen`, `summary/SummaryViewModel`, `plans/PlanScreens` + `PlanEditViewModel`, `settings/AiSettingsScreen`, `SettingsScreens`, `ThemeColorsScreen` (first 120 lines), `trends/CareTrendsScreen` · `ai/AiConsentDialog.kt`, `data/local/LocalBackendApis.kt` (summaries fallback), `data/SampleDataLoader.kt`, `app/build.gradle.kts` (build-type flags), `AndroidManifest.xml`, `res/values*` (colors, themes, `strings.xml` and `values-ar/strings.xml` read in full).

**Tests skimmed:** `StringsParityTest`, `BannerRetentionTest`, `RotationUiTest`, `RtlUiTest`, plus the full test file inventory (130+ files listed in §8).

**Not read / not verified:** `backend/`, `web/`, `AboutScreen` (read only key strings), `CsvTransferUi`/`BackupRestoreUi` published VMs in detail, `AiSettingsValidation.kt` logic, full `ThemeColorsScreen` body beyond line 120, auth screens beyond grep. **No Gradle, no emulator, no TalkBack session.** CI run #39835994704 (`Android`) completed successfully on Oct 22 as observed on GitHub Actions — that is external evidence, not something I reproduced.

---

## 3. Journey walkthroughs

**J1 — First launch → first note.** Cold start: `rootScreenFor` waits (`PENDING`) until DataStore answers, so no onboarding flash for completed users (read error → onboarding, safe). Onboarding: pager, language chips top-right writing DataStore + `recreate()`. Skip → Home (empty) → FAB → picker sheet shows "No people yet / Add your first person" → Add person → editor. Save → Room via `LocalBackendApis`, zero AI, zero network. Footer literally states "Saving does not use AI". Under 60s for a focused user; 6–7 taps with the empty-state CTA absorbing one. *Friction:* avatar in the top bar shows "?" forever in standalone (no email exists) — see J8; the empty picker→add-person round trip is one screen more than needed.

**J2 — AI setup (BYOK).** Settings → AI: provider row (dialog, per-provider key/model/URL load with discard guard — the per-provider slots are understood because the key dialog says "This key is only used for %1$s"), setup guide with working links + no-browser fallback, key dialog (masked), model catalog with free-tier copy, base URL validation, Test connection (20s timeout, cancellable, persists on success), format validation as a second surface, revoke-consent, clear-key confirm. *Friction:* no reveal toggle for the pasted key; all status/results are bare `Text` with no color/semantics — TalkBack users never hear the test finish; a wrong key surfaces as "Failed: Your AI key was rejected…" via `AiRecovery` (good), no network as "unavailable" recovery copy (good). Quota → `rate_limit` copy. Provider switch mid-edit → discard dialog naming the old provider.

**J3 — Daily note entry.** Four single-select chip groups (required mood, inline error), med + fall, pain as eleven 48dp buttons (0–10, captioned), free text required. Footer Save 56dp with persistent caption. State lives in the VM → rotation safe; **process death loses the draft** (no `SavedStateHandle`). The pain slider (`SquigglySlider.kt`) was never wired in; the shipped tap-grid is *better* for precision and TalkBack than a slider, but it should be a deliberate decision, not an orphan component. IME handling: editor has no `imePadding()`; `adjustResize` is not set in the manifest — the save bar may sit under the keyboard (needs device).

**J4 — History and trends.** Filter chips (person dropdown with "All", from/to date pickers, UTC-stable), client-side 20-row paging tied to `snapshotFlow`, pull-to-refresh, `NoteRow` with pending-clock glyph, 4-line cap → "Show more". `NoteRow.showMore` is a bare clickable `Text` (<48dp target). Arabic rel-times are replaced by forced-Latin-numeral dates (`DateFormats` documents this) — consistent everywhere. "Trends" as a product surface does not exist beyond summary text; `CareTrendsScreen` is dead code with hardcoded English.

**J5 — AI summary and plan.** Period picker → result screen. Consent gate before any send (`NeedsConsent` → dialog → `setConsentGiven()`; dismiss = pop). Result: not-a-prescription disclaimer, demo label only when `isDemo`, "AI-generated" marker, red-flags banner (with a *Fall alert* title — see F3), overview, evidence cards with verbatim quotes + "Source note" pill, `UnclearBox` per uncertainty with localized topics (raw keys never leak). View proposals → plan proposal (state-machine-gated buttons, LockBar per item, based-on items, reason in unclear box) → accept/edit-accept/dismiss → versions. *Friction:* citations show only "Source note" — no note date/id, so "verbatim quote" trust relies on scanning the source note yourself; `Plan edit → Save changes` silently **accepts**.

**J6 — Safety signals.** Home: banner pinned above sub-tabs (correct: cannot be scrolled, has no close icon; `RotationUiTest` asserts it survives recreation and that `Close` count == 0 — nice). Detail: `HomeErrorState` re-shows retained flags. Summary: `FlagsBanner` in Content/AiUnavailable/Rejected. People: fall/pain chips via cards. *Gaps:* Home banner reports only the **first** flagged fall silently dropping the rest; other deterministic signals (pain/missed med) surface only as tiny pills, not the banner; the title "Fall alert"/"تنبيه سقوط" is wrong for non-fall flags.

**J7 — Data ownership.** CSV import/export + JSON backup/restore through SAF with no permissions; restore has a preview dialog that counts what is being replaced and states "Restoring replaces all… — cannot be undone"; duplicate-skip policy documented; per-row validation errors are human-phrased. Backup disabled with explanation outside standalone mode (by `localDatabase == null`). Restore in-flight data changed guard exists (`backup_error_conflict`). *Gap:* the sample-data row leaks here too (F6).

**J8 — Settings, theme, About, language switch.** Language switch recreates the activity (`SettingsStore` + SharedPreferences mirror for cold start — a thoughtful workaround); unsaved-changes guard on AI settings publishes to the back-stack entry — tab taps confirm before discarding. Theme: mode + pure-black + palette live preview. Dark mode/pure-black legibility of pills is good (6.7–8.0:1) **except** the PrimarySoft selected-state pair. "Theme & Colors", "On/Off", and palette names stay English in Arabic (F4). About: credits old backend-era roles ("Daily notes API", "Auth, people & plans") — history the standalone release shouldn't need to explain.

---

## 4. Findings (ranked)

| ID | Sev | Area | Finding | Evidence | Why it matters | Recommended fix | Effort |
|----|-----|------|---------|----------|----------------|-----------------|--------|
| **F1** | **S1** | Accessibility / dark mode | Selected chip label `Primary` on `PrimarySoft` = **1.94:1** in dark mode (fails even the 3:1 large-text floor); used for the mood/appetite/pain chips and the summary period chips during note entry and summary generation | `core/theme/Color.kt:99–107` (dark `Primary=0xFF0E6B66`, `PrimarySoft=0xFF1A3A38`); `NoteScreens.kt:157–166, 256–265`; `SummaryScreen.kt:119–127` | A tired caregiver in a dark bedroom cannot reliably see which mood / med status is selected — selection mistakes on a nightly task | Add a dark-mode `PrimarySelectedText` token (e.g. `#9BD8CF`, computed ≥4.5:1 on `#1A3A38`) and use it for `selectedLabelColor` in dark schemes; keep `Primary` in light | S |
| **F2** | **S1** | Trust / medication + theming | Any of the 19 Metrolist-style accent palettes writes `MaterialTheme.primary` with white content: white-on-yellow **1.39:1**, lime **1.79:1**, amber **1.79:1**, orange **2.37:1**, cyan **2.74:1**. Consumers include the FAB, selected bottom-tab icon (dark ~2.0–2.9 on most) and the **medication "taken" checkmark** (`MaterialTheme.colorScheme.primary` tint) | `Theme.kt:141–160` (`palettePrimary`), `Theme.kt:196–204` (`base.copy(primary=primary, primaryContainer=primary)`), `Theme.kt:78–90` (`onPrimary=White`); `MainScaffold.kt:334–360` (FAB colors); `MedicationViewModel.kt:289–293` (check tint); `AppNavigationBar` selected icon uses `Theme primary` | The user picks a cosmetic color in Settings and silently degrades the legibility of *whether a dose was marked taken* — a medication-error surface, not a taste issue. Also violates the repo rule "no new colors outside `CaregiverColors`" | Constrain `palettePrimary` output: compute a `contentColor` from luminance (dark text `#1F2A2E` for light accents) or exclude the failing accents from `PaletteColors` in `ThemeColorsScreen`; route the med checkmark through `CaregiverColors.Primary` (teal) instead of M3 primary. Computed AA-passing pairs: keep indigo/rose/purple/deep purple/blue-grey/rose2/brown; drop/fix dynamic, crimson, blue, sky_blue, cyan, teal2, green, light_green, lime, yellow, amber, orange, deep_orange | M |
| **F3** | **S1** | Safety copy | Every summary safety banner is titled **"Fall alert"** (`home_safety_title`) regardless of flag: HIGH_PAIN, REPEATED_MISSED_MED, POOR_APPETITE_DAYS, MODEL_URGENT all render under "تنبيه سقوط"/"Fall alert" | `SummaryScreen.kt:333–344` (`FlagsBanner` → `SafetyAlertCard(title = stringResource(R.string.home_safety_title))`, `flagText` 631–642); strings `home_safety_title:33` | A caregiver seeing "Fall alert" on a missed-medication streak is misinformed about the actual danger; on the most serious screen in the app | Per-flag title, e.g.: EN `alarms_title` → "Safety alert"; AR `<string name="home_safety_title">تنبيه أمان</string>` (or add `alarms_title` and use it in `FlagsBanner`; keep "Fall alert" only for the Home fall banner) | S |
| **F4** | **S1** | Localization integrity | Hardcoded English in core surfaces: People header `"$count person"/"$count people"`; `dark_theme_on/off`, `dark_theme_follow_system` and 17 palette-name strings untranslated in `values-ar`; `StringsParityTest` iterates keys/placeholders only → all pass | `RecipientScreens.kt:131–138`; `values-ar/strings.xml:449–451, 425–444`; `StringsParityTest.kt:60–105` | The release brand promise is "Arabic is a first-class layout"; an Egyptian caregiver sees mixed-language UI in the People tab header and the theme screens | Replace the count with a bilingual placeholder (e.g. AR `"<string name="people_count">عدد الأشخاص: %1$d</string>"` — one key both locales); translate the theme strings: `"الوضع الداكن مشغّل"` / `"مغلق"` / `"حسب النظام"` and Arabicize palette names | S |
| **F5** | **S1** | Data loss / draft | Typed note text survives rotation (VM) but **not process death**: `EditorState` has no `SavedStateHandle`/`rememberSaveable` bridge, no periodic Room draft row; the addendum form does the same | `NoteEditorViewModel.kt:66–87` (plain `MutableStateFlow`), `NoteScreens.kt:175–198, 564–587`; counterpart guard exists only for AI-Settings dirty state (`AiSettingsScreen.kt:129–144`) | Rule 7 says *"Never … a lost note or draft."* An OS kill with a half-typed note at 2am destroys the caregiver's work exactly when they have least patience to retype | In `NoteEditorViewModel` take `SavedStateHandle`; mirror `pain/fall/mood/text` (`state.set(key, value)` on every setter, restore in `init`). ~15 lines | S |
| **F6** | **S1** | Standalone integrity / data ownership | Settings > **"Load sample data"** ships in the **release** standalone build (`LOCAL_MODE=true` in release) and inserts 2 sample recipients into the user's real Room DB; guard is only `if (dao.all().isNotEmpty()) return`; **there is no delete/deactivate-person control anywhere**, so the inserts are permanent | `SettingsScreens.kt:241–256`; `SampleDataLoader.kt:16–20`; `app/build.gradle.kts:63–66`; no delete anywhere: grep over `RecipientDao/RecipientsViewModel/RecipientScreens` | A real caregiver's first-run Home gets two fake people ("الشخص التجريبي أ…") they cannot remove — reads as data corruption, undermines trust in an app whose pitch is "your data" | Wrap the row so it only appears for `DEMO_BACKEND` graphs (demo-only), i.e. **remove it from all standalone variants**; or gate + add a confirm dialog + red styling | S |
| **F7** | **S2** | Visual / bidi | People-tab avatars render **empty**: `Avatar(Bidi.isolate(recipient.name).take(1))` — `isolate` prepends FSI (U+2066), so `take(1)` returns the invisible isolate char, not the initial letter | `Bidi.kt:12–16,21` (`isolate` returns `"$FSI$text$PDI"`); `RecipientScreens.kt:177`; compare correct call `HomeScreen.kt:389` (`card.name.take(1)`) | Every person card in People reads as a blank gray circle — looks like a loading bug, drags down trust | `Avatar(letter = recipient.name.take(1))` (raw name; the letter is a single visible char so no isolate needed) | S |
| **F8** | **S2** | BYOK friction / a11y feedback | Pasted key is permanently masked (no reveal toggle); all status outcomes on the AI screen are plain `Text` with **no `liveRegion`/announcement and no color coding** — success (`settings_ai_connection_ok_saved`) and failure look identical to TalkBack and at a glance | `AiSettingsScreen.kt:654–667` (PasswordVisualTransformation, no trailingIcon), `:394, 523, 549–562` (bare `Text` outputs) | Pasting a 50-char key twice because you cannot verify it; blind users learn results only if they know to rescan | Add `visualTransformation = if (visible) None else Password…` with a trailing eye IconButton + `contentDescription`; wrap status in `Modifier.semantics { liveRegion = LiveRegionMode.Polite }` and tint success=Success/error=Danger tokens | S |
| **F9** | **S2** | Failure ladder drift | The promised middle rung "template summary" does not exist in standalone: the no-key path returns `text=""` + flags + uncertainty `ai_not_configured`; AI-key-failure path goes flag-card + recovery copy; nothing generates a deterministic template text from the structured notes | `LocalBackendApis.kt:161–190` (fallback returns empty text), `SummaryViewModel.kt:178–241` (`AiUnavailable` only), repo rule 7 (README "template summary, then AI unavailable") | Not data loss — flags survive and recovery actions exist (good) — but the ladder's advertised rung is absent, so a provider outage yields *no* usable patient-facing narrative during a doctor visit | Either build a tiny on-device template renderer from the same `SignalDto` inputs (falls/pain/med lines — ≤1 screen of code, no AI) or reword the README promise to "flags + recovery" | M |
| **F10** | **S2** | Plans / trust-copy | Plan *edit* screen: single button "Save changes"/"حفظ التعديل" appends an **edited-and-accepted** version immediately — the save *is* the accept. Same family: `Dismiss` applies instantly with no confirmation on an action that is permanent-ish (creates a version) | `PlanScreens.kt:285–364` (only Save in BottomActionBar), `PlanEditViewModel.kt:91–102` (`EDITED_STATUS` append), `PlanScreens.kt:253–261` (dismiss direct) | Product rules 5/7 spirit: nothing that changes a care decision should look like a neutral file operation | Rename button to "Save & accept" (EN) / "حفظ وقبول" (AR) or add a confirm dialog stating the previous version goes to archived; simplest = string swap: `plan_edit_save` → EN "Save & accept", AR "حفظ وقبول" + `plan_versions_hint` already explains versioning | S |
| **F11** | **S2** | Status semantics | `StatusChip`: any unrecognized/`Archived` status falls to `else -> GreenPill(...)` — archived versions shown in success-green with a success-coded label in Plans/Version history | `PlanScreens.kt:130–139` | Color signaling says "done/good" for a closed/ignored plan; a caregiver scanning fast misreads it as active | Add `GrayPill` arm for `"Archived"` and unknown statuses (localized `PlanStatusText` already exists for archived) | S |
| **F12** | **S2** | Dead code / drift | Whole Metrolist port layer and X-spec remnants are compiled but unreachable: `SquigglySlider`, `AppDrawer`, `SwipeActionRow`, `SmartToast`, `EmptyPlaceholder`×2, `CreatePlaylistDialog`, `SongDropdownSelect`, `SettingsSleepTimerDialog`, `DraggableScrollbarOverlay`, `ShowMediaInfo`, `SortHeader`, `ExpandableText`, shimmer placeholders, dead `CareTrendsScreen` (hardcoded English strings), plus 30+ sleep-timer/lyrics/playlist strings in both locales; Auth/Login/Register/ServerUrl stack reachable only in non-`LOCAL_MODE` (currently unreachable in every shipped variant) | grep evidence: each name referenced only by itself, e.g. `presentation/common/SquigglySlider.kt`, `AppDrawer.kt`; `CareTrendsScreen.kt:23–25` literal "Care Trends"; `CaregiverRoot.kt:58–71` login branch; strings 468–493 | Repository health; risk that "backend-era" strings/components resurface; resource-shrunk release still carries all string resources kept live by referenced code paths. Notably: `SquigglySlider` (promised accessible pain slider) is unused | Prune (separate PR). For `SquigglySlider`: either delete it or wire it as an *optional* fine control over the pain grid. Login stack: delete since no shipped build flags it on | M (mechanical) |
| **F13** | **S2** | Consent quality | Consent says *"Care notes are sent to the chosen AI provider"* — one line, nowhere names the actual provider, and never says "over the internet" or that notes may contain names/health info. For a non-technical Egyptian caregiver this is thin for the most consequential tap in the product | `AiConsentDialog.kt:9–22`, strings `ai_consent_title/body` (EN+AR) | Consent quality was a headline product rule (#3 "state exactly what leaves the phone and to whom"); the dialog is honest on the medical-device point but vague on *to whom* | Use the dialog's context (it can accept the provider name like the unsaved-changes dialog does): EN `"ai_consent_body"` → `"Your care notes from the chosen period — including names and health details you wrote — are sent over the internet to %1$s and are processed to write the summary. Not a medical device; no storage guarantees."` AR `"تُرسل ملاحظات الرعاية للفترة المختارة — بما فيها الأسماء وتفاصيل الصحة — عبر الإنترنت إلى %1$s لإنشاء الملخص. ليس جهازاً طبياً ولا يوجد ضمان حُفظ."` (pass `provider.displayName`) | S |
| **F14** | **S2** | Font-scale resilience | Fixed heights clip overflowing content at 200%: editor textarea `Modifier.height(130.dp)` (with `minLines=5`), addendum `height(150.dp)`, `PlanEdit` line field `height(64.dp)`, `TrendRow` label `Modifier.width(80.dp)` | `NoteScreens.kt:197, 586`; `PlanScreens.kt:336`; `DesignSystem.kt:710–729` | Large-font users (older caregivers — the product's real demographic) get half-hidden input boxes; buttons elsewhere already do it right (`heightIn(min=)`, DesignSystem 286, 321) | Replace `height` with `heightIn(min=…)` in all four sites; widen `TrendRow` label to `IntrinsicSize`/weight or drop the fixed width | S |
| **F15** | **S3** | PDF export UX | Export button labels itself `"..."` literally while working (not localized, ambiguous) and `exportError` is rendered **twice** in the same column | `SummaryScreen.kt:474, 476, 538`; `export_failed` string exists and is used | Small, but this is a doctor-facing artifact; duplicated error lines look broken | Localize waiting state (`exporting…` string) and delete the second `exportError?.let` | S |
| **F16** | **S3** | Banner completeness | Home fall banner reports only the **first** flagged person (`flags.firstOrNull()`); others silently dropped from the banner (chips still carry them) | `HomeScreen.kt:442–452` | Two falls the same day: one is invisible at banner level | Compose all flagged names: `home_safety_fall">%1$s and %2$d more` style (2 strings, EN+AR) | S |
| **F17** | **S3** | Platform polish | No predictive-back opt-in (`android:enableOnBackInvokedCallback` absent); tablet `AppNavigationRail` exists but nothing uses it (portrait-only assumption); About credits retired backend roles ("Daily notes API"); editor form-error copy for a local failure reuses `auth_error_unreachable` ("Could not reach the server") — server-era phrasing | `AndroidManifest.xml:16–24`; `AppNavigationRail` unused; About strings; editor catch on `NoteScreens.kt:203` | Ship hygiene on a stated "public standalone release" | Add manifest attribute; delete the About role strings; new `editor_save_fail` copy with no server wording | S |

All S1s covered above; no S0 found against the hard product rules (banner non-dismissible ✓, med lock visible everywhere it matters ✓, consent before first send ✓, local-first save ✓, no invention of note timestamps ✓).

---

## 5. Accessibility audit

Computed from the hex values in `Color.kt` (`caregiverScheme`, lines ~88–152):

| Check | Result | Evidence (computed ratios) |
|---|---|---|
| Ink on Surface | ✅ Pass | 13.95:1 light / 13.40:1 dark |
| Ink on Background | ✅ Pass | 13.60:1 / 14.66:1 |
| Muted on Surface / Background | ✅ Pass | 5.27 / 5.14 light; 8.75 / 9.56 dark (≥4.5) |
| White on Primary button | ✅ Pass | 6.34:1 (teal `#0E6B66`) |
| Primary on PrimarySoft | ⚠️ Pass light, ❌ **Fail dark** | light **4.90:1** ✓ · dark **1.94:1** ✗ (fails 4.5 and 3.0) → F1 |
| Danger on DangerContainer | ✅ Pass | 6.89 / 6.03 |
| Success on SuccessContainer | ✅ Pass | 6.81 / 6.22 |
| Info on InfoContainer | ✅ Pass | 7.24 / 7.75 |
| Warning on WarningContainer | ✅ Pass | 6.55 / 8.01 |
| GrayPill content/container | ✅ Pass | 7.89 / 6.78 |
| LightBluePill content/container | ✅ Pass | 7.51 / 7.75 |
| AvatarContent on AvatarContainer | ✅ Pass | 6.74 / 7.75 |
| Ink on LockBar | ✅ Pass | 12.96 / 13.40 |
| Accent palettes with white content (F2) | ❌ Fail for 13 of 21 | yellow **1.39**, lime **1.79**, amber **1.79**, orange **2.37**, light_green **2.50**, cyan **2.74**, sky_blue **3.08**, dynamic **3.45**, crimson **3.49**, deep_orange **3.48**, green **3.30**; pass list: indigo 6.87, rose 5.87, metrolist 6.44, purple 7.04, deep_purple 8.02, indigo2 7.73, brown 7.61, rose2 4.95, blue_grey 5.40 |
| Touch targets | ⚠️ Mostly pass, 1 known fail | Pain buttons 48dp ✓ (`NoteScreens.kt:165`); back 48 ✓ (`DesignSystem.kt:121–137`); med dose IconButtons 48 ✓; sync icon 48 ✓ (`SyncStatusIndicator.kt`); chips rely on M3 `LocalMinimumInteractiveComponentEnforcement` (*likely* 48 touch / 32 visual — needs device verify); **`NoteRow` "Show more" is a bare clickable `Text` — no minimum-target guarantee** (`NoteRow.kt:138–149`) → Fail (S2) |
| TalkBack semantics | ⚠️ Good foundations, gaps in feedback | Med-card dose state descriptions are exemplary (`MedicationViewModel.kt:259–296`); history filter chips carry `stateDescription` (`HistoryScreen.kt:424–429, 469–473`); avatar menu has content descriptions; **missing**: live-region announcements for AI-settings test results, summary `suggestStatus`; the `✓` inside selected chips is read as a literal check char (minor) |
| Font scale 200% | ⚠️ Unverified / likely partial | By reading code: `heightIn(min=)` on buttons handles wrap ✓ (`DesignSystem.kt:283–321`); fixed-height textareas clip (F14); `TrendRow` 80dp label clips Arabic labels; onboarding pager card scrolls ✓ (`OnboardingScreen.kt:101–106`) |
| Color-independence | ✅ Pass | Status never uses color alone: sync icon glyphs differ (CloudDone/Sync/CloudOff/ErrorOutline, `SyncStatusIndicator.kt:31–43`); pills always carry text; fall vs pain distinguished by label text |
| Focus order | ⚠️ Unverified | No explicit `semantics { traversalIndex }`; default order follows composition order (sensible here); pop-ups (`AccountPopupMenu`, sheet) not focus-trapped — verify on device |

---

## 6. RTL and Arabic audit

| Area | Status | Evidence |
|---|---|---|
| Layout mirroring | ✅ Pass | Consistent `start/end` usage; no `left/right` grep hits in `presentation/` |
| Icon mirroring | ✅ Pass | `AutoMirrored.ArrowBack` (`DesignSystem.kt:129`); directional chevrons pick left/right by layout direction (`DesignSystem.kt:355–368`); history call-to-action arrow is a text glyph `"←"/"→"` (`HistoryScreen.kt:324`) — works but inconsistent weight (minor) |
| Bidi isolation | ✅ Excellent | `Bidi.isolate` used at high density — Home greeting/notes, NoteRow, detail titles, plan items/reasons, history dropdown entries, picker rows; unit-tested (`BidiTest`); **except F7** where isolation is misused for the initial |
| Numerals | ✅ Consistent | `DateFormats` forces `ar-u-nu-latn` ("7 أكتوبر"), documented; pain digits, period chips, stats — one convention (deliberately Latin digits) |
| Arabic typography | ✅ Pass | Bundled IBM Plex Sans Arabic for every scale step (`Type.kt`) — no mismatched Latin fallback; line-heights set on long body text (`NoteScreens.kt:461–464`) |
| Arabic copy quality | ⚠️ Several real defects | See table below — including one on a safety chip |
| RTL rendering tests | ⚠️ Thin | `RtlUiTest` asserts direction + mixed-name isolation on login & home (login is not even in the standalone flow!) — history/summary/plans uncovered |

Arabic strings needing fixes:

| Key | Current AR | Problem | Suggested AR | Why |
|---|---|---|---|---|
| `chip_high_pain`, `flag_pain` | `ألم عال` | Misspelled/ambiguous (`عال`); inconsistent with `home_empty_attention_body` which says "ألم شديد" | `ألم شديد` | Same concept must read identically everywhere; `عال` is not Standard Arabic and will confuse older readers — on a safety chip |
| `editor_error_mood` | `اختار الحالة المزاجية` | Wrong verb inflection (reads "he chose…") | `اختر الحالة المزاجية` | Imperative form; a spelling error on the primary validation message of the core flow |
| `plans_versions_count` | `%1$d إصدارات` | Arabic plural breaks for 1/2 ("2 إصدارات" is wrong) | `عدد الإصدارات: %1$d` | One key reads correctly for any count; avoids adding plural forms |
| `dark_theme_on/off/follow_system` | `On` / `Off` / `Follow system` | Untranslated in `values-ar` | `تشغيل` / `إيقاف` / `حسب النظام` | Visible on the theme screen for every Arabic user |
| `settings_ai_unsaved_message` | `لم يتم حفظ تغييراتك إلى %1$s` | Awkward preposition | `لم يتم حفظ تغييراتك على %1$s` | Idiomatic preposition; fires often during AI setup |

---

## 7. Safety, privacy and trust review

- **Consent dialog — core loop correct, copy thin.** Gate is structurally sound: `NeedsConsent` is reached before a request is ever made (`SummaryViewModel.load:133–147`), `setConsentGiven()` is the only path past it, revoke re-arms it with a confirm. The plan-suggest flow checks the same store flag. Copy itself is vague about destination (F13).
- **Key handling UX — strong backend, weak front stage.** `AiKeyStore` per provider; the key is saved *before* the provider write ("a refused keystore write can never leave the app pointing at a provider whose key was never saved", `AiSettingsScreen.kt:164–185` — genuinely good reasoning); invalidated-key state is a distinct UX message both in settings and on the summary screen with a forward action. Masking-forever and no TalkBack feedback are the gaps (F8).
- **Safety banner persistence — genuinely non-dismissible.** Verified by construction + tests: no close affordance in either tree, banner lives outside scroll/PullToRefresh zones, error states carry `lastContent.flags`, `RotationUiTest` recreates the activity and asserts banner + `Close` count == 0, `BannerRetentionTest` covers degraded states at the VM level. This is the highest-assurance area of the app. Open questions: wrong banner title for non-fall flags (F3), first-flag-only truncation (F16), high-pain is banner-less on Home.
- **Med hard lock — visible in all four contexts**: `plan_meds_locked` LockBar on every proposal item card, `summary_meds_locked` with a lock glyph at the end of the summary body, `plan_not_prescription_body` ("AI never changes medications"), and `plan_suggest_medications_rejected` when the model tries. The UI never offers a medication edit surface inside a proposal. The manual med tracker and AI proposals are intentionally separate data paths — acceptable.
- **Grounding citations — honest but minimal.** Verbatim quotes with Bidi isolation, "Source note" pill, not clickable, carries no date/linkback (`SummaryDto.EvidenceDto` has only `noteId+quote` — server gap per `docs/api/android-gaps.md`). Until the API adds note metadata, add the derived date client-side by resolving `noteId` against the already-loaded history window.
- **Disclaimers — density is right.** "Not a prescription"/"based on recorded notes" appears on summary and plan screens as non-dismissible info cards, without wall-of-lawyering. Demo labeling (`isDemo`) can never fire in production. Good.

---

## 8. Test coverage gaps

**Present and protective:** strings parity + placeholders; `Bidi` unit tests; route/root logic tests; `TopBarScrollRegressionTest`; `UnsavedChangesNavTest`; planner/state machine tests; `AiRecovery` copy tests; demo-labelling tests; `RtlUiTest`; `RotationUiTest` (incl. banner + Close==0); `SummaryDemoLabelUiTest`; `NoteEditorFlowUiTest` / `HistoryFlowUiTest` / `SettingsFlowUiTest` / `PlanFlowUiTest` / `SummaryFlowUiTest` / `AuthFormUiTest` / `HomeUiTest` / `RecipientPickerUiTest` / `DrawerSyncUiTest`. Genuinely above typical standards for an indie release.

**UX-critical behaviors with no automated test:**

| Behavior | Coverage today |
|---|---|
| Safety banner *non-dismissible* on Summary/History surfaces (Home covered) | none — rotation only tests Home banner |
| Selected-state **contrast** in dark mode | none — nothing checks color math anywhere |
| Consent gate *blocks the send* (not just the UI) | VM tests cover the flag, not that `summarize` is never invoked without consent |
| Note-editor draft across process death (`SavedStateHandle`) | none |
| Offline/local-first save (Room path independent of network stack) | `NoteDaoTest`/`LocalSummaryFlagsTest` cover data; no UI-level "save works with no network" test |
| Arabic *copy quality* (non-English values vs banned-latin-words list) | parity test only checks keys — "On"/"Off"/"3 people" all sail through |

**Five highest-value tests to add:**

1. `BannerNonDismissableEverywhereUiTest` (Compose UI): for Home, Summary-Content, Summary-AiUnavailable — assert SafetyAlertCard is displayed **and** `onAllNodesWithText("Close").assertCountEquals(0)` and that scroll does not remove the banner from the tree.
2. `ConsentGateBlocksSendTest` (JVM, fake `LlmClient`): consent not accepted → `client.complete`/`summaries()` is **never called**; after `setConsentGiven()` → called exactly once.
3. `EditorDraftProcessDeathTest` (Robolectric + `SavedStateHandle` helpers): recreate the ViewModel from the same handle, assert mood/pain/text/fall restored — the regression harness for F5.
4. `ArabicStringHygieneTest` (JVM, pure string scan): parse `values-ar` and fail on untranslated values (`>On<`, `>Off<`, `>$count person<`) and known defect strings (`>ألم عال<`) — the cheap auto-catch for F4/§6 copy rot.
5. `DarkModeChipContrastTest` (JVM, pure): instantiate `caregiverScheme(dark=true)` and assert `contrastRatio(Primary, PrimarySoft) >= 4.5` — the F1 regression tripwire.

---

## 9. Quick wins vs. bigger bets

**Under ~1 hour each**
1. F7 avatar initial (1-line fix, `RecipientScreens.kt:177`).
2. F3 per-flag banner title (add `alarms_title`, swap call site, 2 string files).
3. F11 StatusChip archived→GrayPill (`PlanScreens.kt:131–139`).
4. F6 remove/hide "Load sample data" row in standalone variants.
5. F14 `height`→`heightIn(min=)` at 4 sites; remove duplicate `exportError` line and localize "..." in PDF export (F15).
6. F8 key-reveal eye toggle on the key dialog.
7. F10 rename `plan_edit_save` EN+AR to "Save & accept"/"حفظ وقبول".
8. AR fix batch: `ألم شديد`, `اختر الحالة`, `تغييراتك على`, translate `dark_theme_*` (§6 table).
9. F16 compose all flagged people in the Home banner copy (EN+AR).
10. `android/README.md` header correction: drop the backend/login/tunnel lines (known drift).

**Bigger investments**
1. **F1+F2 theme hardening** — a dark `PrimarySelectedText` token + luminance-aware palette content colors; expected impact: dark mode and color personalization stop degrading the two most consequential UI (selection state, taken-medicine mark).
2. **F9 standalone template summary** — deterministic text from `SignalDto` inputs (falls/pain/med misses/appetite), labeled "deterministic, not AI"; expected impact: a doctor-facing output even with dead AI — the single biggest product-promise gap.
3. **TalkBack feedback protocol** — `liveRegion` on all AI test/summary/save status outcomes; expected impact: screen-reader users stop being blind to async results.
4. **Dead-code cleanup** (F12) — one mechanical PR trimming the Metrolist port and dead screens; smaller APK and an honest About page.
5. **Citation upgrade once the API closes the gap** — evidence cards render note date + recipient when the summary response carries it (flagged in `docs/api/android-gaps.md`); expected impact: caregiver can *verify* any claim in one tap — the trust axis of the product.

---

## 10. Unverified / needs on-device check

1. **Chip touch targets (M3 minimum-target enforcement).** With TalkBack on the note editor, focus each chip and confirm the green focus rectangle spans ≥48dp.
2. **Keyboard/IME behavior in the editor** (no `imePadding`, no `adjustResize` in the manifest): type in the free-text field with the keyboard open; confirm the Save bar and textarea stay above the IME; repeat rotated with keyboard open.
3. **Font scale 200%:** Settings → Display → Font size 200%; reopen the note editor, summary body, plan edit; look for textarea clipping (F14) and `TrendRow` label truncation (`DesignSystem.kt:725`).
4. **TalkBack announcements for async outcomes** (AI test, summary, save): Settings → AI → Test connection with TalkBack on; confirm the result is spoken unprompted.
5. **Predictive back** behavior on a 14+ device/gesture navigation: back from a detail screen.
6. **Language switch mid-flow:** change language in Settings and confirm scroll position/state survives `recreate()` where it should.
7. **Pure-black theme legibility:** enable pure black + dark, screenshot summary success/error cards and the safety banner.

---

*No code was changed during this review beyond adding this report; the review was performed on a read-only local clone at branch head `9c8f503`.*
