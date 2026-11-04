Decision record: [Design System Parity Decisions](DECISIONS.md).

# Android → Web Design Parity Gaps

Source: Android branch `feat/standalone-byok-ai`; target web branch `main`. Read-only inspection; web comparison limited to package metadata and easily identifiable theme/base files.

| Mobile design decision | Where it's defined | What the web needs to do |
|---|---|---|
| Two primary calculations coexist: Material 3 follows selected accent; CaregiverColors falls back to teal for most palette names. | `core/theme/Theme.kt:103-125,204-212`; `core/theme/Color.kt:86-92` | Resolve before unifying primary. Do not assume custom components always use selected accent. |
| Standard accent schemes use white onPrimary/onPrimaryContainer, even for light accents. | `core/theme/Theme.kt:22-62` | Provide accessible foreground color per palette. Several combinations fail WCAG AA. |
| Light neutrals are warm off-white, ink, muted blue-gray and soft borders. | `core/theme/Color.kt:122-150` | Use these semantic roles for mobile parity; inspected web base currently uses another green/neutral palette. |
| Dark mode uses charcoal surfaces, light ink and muted blue-gray text. | `core/theme/Color.kt:93-121` | Implement a complete dark token set; inspected web base has no equivalent dark block. |
| System, Light and Dark modes are supported. | `core/theme/ThemeMode.kt:9-21`; `ThemeColorsScreen.kt:86-153` | Support three-mode resolution and persist explicit user preference. |
| Pure-black changes Material 3 background/surface only; custom surfaces remain charcoal. | `core/theme/Theme.kt:208-218`; `core/theme/Color.kt:93-121` | Decide whether to reproduce partial behavior or make it consistent. |
| Twenty accent swatches exist; Dynamic is fixed coral, not wallpaper-derived. | `core/theme/Theme.kt:79-126,167-188`; `ThemeColorsScreen.kt:155-177` | Use same names/values if full parity is required; do not imply OS wallpaper color. |
| Metrolist is a separate Material 3 scheme, not a visible swatch. | `core/theme/Theme.kt:128-160,190-213` | Decide whether it belongs in web selector or remains special. |
| IBM Plex Sans Arabic supplies Latin and Arabic glyphs on Android. | `core/theme/Type.kt:9-42`; `res/font/plex_arabic_*.ttf` | Consider same family for closest parity; web currently uses DM Sans and Noto Sans Arabic. |
| Material 3 typography is inherited with local overrides such as 22sp/28sp headings and 16sp/29sp summary text. | `core/theme/Type.kt:20-42`; `DesignSystem.kt:140-177,239-260`; `SummaryScreen.kt:393-402` | Define semantic web type roles and preserve screen-specific overrides. |
| Spacing uses 4/8/16/24/32/48dp rhythm plus 12dp and 14dp component gaps. | `core/theme/Spacing.kt:11-18`; `DesignSystem.kt:80-113` | Reuse spacing tokens across screen gutters, forms, cards and lists. |
| Cards use 14dp radius, soft border, subtle 1dp shadow and 14dp padding. | `DesignSystem.kt:71-78,332-352` | Use shared card treatment; avoid heavy shadows or unrelated radius scales. |
| Buttons use 12dp radius and 48/56dp heights; inputs use 12dp and 52dp minimum height. | `DesignSystem.kt:71-78,263-330`; `RecipientScreens.kt:496-524` | Match button/input geometry plus focused/disabled/error states. |
| Status pills use 999dp radius, 13sp bold text and gray/green/red/yellow/light-blue variants. | `DesignSystem.kt:388-423`; `Color.kt:13-40` | Use semantic pills with visible text; do not encode state by color alone. |
| Safety alert uses danger surface/border/text plus warning icon and written copy. | `DesignSystem.kt:425-460`; `HomeScreen.kt:456-480`; `SummaryScreen.kt:307-319` | Keep warning icon and explanation. Red is not a validated clinical severity score. |
| Info alert uses blue-tinted surface, border, info icon and explanatory text. | `DesignSystem.kt:462-499` | Recreate as distinct semantic component. |
| Reusable error/warning components use text and recovery actions; no dedicated icon is in their component bodies. | `DesignSystem.kt:577-628` | Keep readable copy and retry/recovery; adding icons would be a design change. |
| CSV/backup notices use success/danger pairs and dismiss action. | `CsvTransferUi.kt:55-97`; `BackupRestoreUi.kt:48-88` | Keep semantic result pairs and visible result text. |
| Sync has synced/syncing/offline/failed icons and accessible labels; failed state retries. | `SyncStatusIndicator.kt:25-61` | Preserve four states and accessible labels; do not use tint alone. |
| Note timeline uses full-width rows, not card-per-item; avatar, author/time, body, pills and divider. | `NoteRow.kt:41-55,66-171`; `HistoryScreen.kt:83-93` | Build a shared timeline row and avoid elevating each entry. |
| Note body collapses after four lines with Show more. | `NoteRow.kt:63-64,138-163` | Preserve truncation and expansion behavior. |
| Main navigation has Home, People, Notes and History tabs; plans are nested destinations. | `core/navigation/Routes.kt:6-15,27-67` | Preserve four-tab structure and separate plan flow. |
| Bottom navigation is a 64dp strip with 22dp icons, 11sp labels and transparent selection indicator. | `MainScaffold.kt:281-324`; `AppNavigation.kt:197-298` | Match compact geometry and selected/unselected icon treatment when appropriate. |
| Tab top bar has centered title, avatar and sync status; detail screens have back affordance. | `DesignSystem.kt:140-177,179-237`; `MainScaffold.kt:247-279` | Reuse distinct tab-root and detail-header patterns. |
| Tab routes show a 56dp FAB whose action varies by active tab. | `MainScaffold.kt:326-363`; `DesignSystem.kt:98-101` | Keep tab-level add separate from detail/form bottom actions. |
| Note editor uses grouped fields, wrapping chips, 48dp pain controls and persistent bottom save. | `NoteScreens.kt:94-98,152-228,244-271` | Preserve section rhythm, chip wrapping and persistent action. |
| Summary is reading-first with disclaimers, evidence, safety flags and follow-up actions. | `SummaryScreen.kt:92-132,272-319,361-415,557-583` | Preserve hierarchy and separate evidence/disclaimer/safety groups. |
| Settings uses grouped low-elevation surfaces; theme screen has preview, mode, pure-black and swatches. | `CaregiverSettings.kt` (`CaregiverSettingsGroup`); `SettingsScreens.kt:110-232`; `ThemeColorsScreen.kt:86-179` | Preserve grouped settings sections and theme controls. |
| RTL includes auto-mirrored icons, logical positions and bidi isolation. | `Bidi.kt:3-23`; `DesignSystem.kt:120-138,354-368`; `NoteRow.kt:81-88,108-125` | Use logical CSS properties, isolate mixed-script values and mirror only directional icons. |
| Loading skeletons follow list row geometry and share a shimmer theme. | `NoteRow.kt:174-200`; `MainActivity.kt:64-67` | Match skeletons to final layout; do not always use full-page spinners. |
| Top-bar fades are 300/200ms; route transitions use 200ms slide/fade. | `MainScaffold.kt:254-257,380-417` | Match only equivalent transitions and honor web reduced-motion preference. |
| Trends screen currently has no chart implementation or chart tokens. | `CareTrendsScreen.kt:22-29` | To be checked when mobile trends is implemented; do not infer chart colors. |
| XML colors only include launcher background; no separate Shape.kt or Elevation.kt was found in inspected theme layer. | `res/values/colors.xml:1-5`; `Theme.kt:64-74`; `DesignSystem.kt:80-113` | Use Compose source for UI tokens; XML mirroring is not required. |
| Web package does not declare Tailwind. | `web/package.json:17-38`; `web/src/styles/base.css:1-17` | Tailwind is optional; CSS variables fit inspected stack. |
| Inspected web base has DM Sans/green/light-neutral styling and no root dark-theme block. | `web/src/styles/base.css:1-17,131-145` | Compare deeper feature CSS before implementation; this was not a full web audit. |

## Open developer questions

1. Should CaregiverColors.Primary always follow palettePrimary(), or should web mirror the current discrepancy? Resolved by [DS-01](DECISIONS.md#ds-01).
2. Should light accents use a dark onPrimary, or should accent colors be darkened to retain white labels? Resolved by [DS-02](DECISIONS.md#ds-02).
3. Should Metrolist become a visible theme option? Resolved by [DS-03](DECISIONS.md#ds-03).
4. Is Dynamic intentionally fixed coral, or should it eventually use Android dynamic color? Resolved by [DS-04](DECISIONS.md#ds-04).
5. Should pure-black affect every surface consistently? Resolved by [DS-05](DECISIONS.md#ds-05).
6. Should web use IBM Plex Sans Arabic for both languages? Resolved by [DS-06](DECISIONS.md#ds-06).
7. Are current status mappings approved, including unknown plan statuses falling back to green? Open — see [DS-07](DECISIONS.md#ds-07).
8. What chart visualization is intended for Trends? Android currently has only a placeholder. Resolved by [DS-08](DECISIONS.md#ds-08).
9. Should web support all accent choices and pure-black in its first parity release? Resolved by [DS-09](DECISIONS.md#ds-09).
10. Is Tailwind planned? It is not declared in the inspected web package. Resolved by [DS-10](DECISIONS.md#ds-10).

## Inspection limitations

- `res/values/styles.xml` was not found.
- `res/values/colors.xml` only defines the launcher background.
- No dedicated Android Shape.kt or Elevation.kt was found in the inspected theme package; those values live in Theme.kt and shared components.
- Web inspection was intentionally shallow and limited to package metadata and easily identifiable base theme files.
