# Caregiver Daily Notes — Android visual design guide

## Scope and source

Source of truth: Android Compose implementation on `feat/standalone-byok-ai`. This describes mobile design, not a web redesign.

Theme sources:
- `android/app/src/main/java/com/caregiver/mobile/core/theme/Color.kt`
- `android/app/src/main/java/com/caregiver/mobile/core/theme/Theme.kt`
- `android/app/src/main/java/com/caregiver/mobile/core/theme/Type.kt`
- `android/app/src/main/java/com/caregiver/mobile/core/theme/Spacing.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/common/DesignSystem.kt`

Additional sources: `NoteRow.kt`, `CaregiverSettings.kt`, `AccountPopupMenu.kt`, `SyncStatusIndicator.kt`, screen files for home/recipients/notes/history/summary/plans/settings, `MainScaffold.kt`, `AppNavigation.kt` and `Bidi.kt`.

## Decisions that affect this guide

The web parity direction is tracked in [Design System Parity Decisions](DECISIONS.md). The decisions affecting this guide are [DS-01](DECISIONS.md#ds-01) (selected palette is the web primary source), [DS-02](DECISIONS.md#ds-02) (computed accessible foregrounds), [DS-04](DECISIONS.md#ds-04) (fixed-coral Dynamic naming), [DS-06](DECISIONS.md#ds-06) (IBM Plex Sans Arabic for both languages) and [DS-07](DECISIONS.md#ds-07) (status mapping remains open and blocks status-pill/plan-status work).

## 1. Visual personality

The app is warm, restrained, practical and care-oriented. Its default appearance uses warm off-white surfaces, charcoal-green text, muted blue-gray secondary text, teal-led accents, thin borders, modest elevation and rounded controls. It feels more like a quiet utility than a decorative dashboard.

Sources: `Color.kt:86-150`; `Theme.kt:64-74`; `DesignSystem.kt:71-78,332-423`.

## 2. Color

### Caregiver neutral roles

| Role | Light | Dark |
|---|---|---|
| Background | `#F7F6F3` | `#121616` |
| Surface | `#FAF9F5` | `#1A1F1F` |
| Ink | `#1F2A2E` | `#E3E7EA` |
| Muted | `#5B6B70` | `#B0BEC5` |
| Border | `#C5CDD1` | `#3A4548` |
| Soft border | `#E3E7EA` | `#2A3336` |

Source: `Color.kt:93-150`.

### Accent palettes

Twenty visible swatches are declared: Dynamic, Crimson, Rose, Purple, Deep Purple, Indigo, Blue, Sky Blue, Cyan, Teal, Green, Light Green, Lime, Yellow, Amber, Orange, Deep Orange, Brown, Grey and Blue Grey. Dynamic is fixed coral `#ED5564`, not wallpaper-derived. Metrolist is a separate full scheme and is not one of the visible swatches.

Source: `Theme.kt:79-126,128-160,167-188`; `ThemeColorsScreen.kt:155-177`.

### Primary implementation discrepancy

`palettePrimary()` supplies the selected Material 3 accent. `caregiverScheme()` changes `CaregiverColors.Primary` only for indigo, rose and metrolist; other palette names fall back to `#0E6B66`. Rose and indigo component colors also differ from their M3 accents. Example: teal M3 primary is `#00897B`, while component primary is `#0E6B66`.

Sources: `Color.kt:86-92`; `Theme.kt:103-125,190-212`.

Do not silently unify these values in web until the source discrepancy has an explicit resolution.

### Metrolist and pure black

Metrolist has its own Material 3 light/dark schemes. Light primary is `#6750A4`, background/surface `#FFFBFE`; dark primary is `#D0BCFF`, background/surface `#1C1B1F`. Pure-black only changes Material 3 background and surface to `#000000`; custom CaregiverColors retain regular dark values.

Sources: `Theme.kt:128-160,200-218`; `Color.kt:93-121`.

## 3. Status and severity colors

There is no calibrated multi-level clinical severity palette in the inspected UI. Red is reused for fall flags, high pain, errors, dismissed plans and safety banners; do not interpret it as a clinical severity score.

| Usage | Color | Additional cue |
|---|---|---|
| Home done today | Green pill | Text label; no icon |
| Home high pain / fall flag | Red pill | Text label; no icon |
| Home no note / missed today | Gray pill | Text label; no icon |
| Home safety banner | Danger fill, border and text | Warning icon + title/body |
| Archived recipient / no note | Gray pill | Text label |
| Recipient done | Green pill | Text label |
| History fall tag | Red pill | Text label |
| Plan accepted / edited accepted | Green pill | Text label |
| Plan dismissed | Red pill | Text label |
| Plan suggested | Light-blue pill | Text label |
| Unknown plan status fallback | Green pill | Text label; may overstate success |
| Summary red flags | Danger treatment | Warning icon + title/body |
| AI recovery warning | Warning treatment | Message text |
| Load error | Danger treatment | Message text + retry action |
| CSV/backup success or failure | Success/danger treatment | Result text + dismiss action |
| Field validation | Danger text | Written validation message |
| Note saved | Success | Check glyph + title |
| Sync states | Muted/primary/danger | Distinct cloud/sync/error icons + accessible label |
| Pending note sync | Muted | Clock glyph + accessible label |

Sources: `HomeScreen.kt:456-480`; `RecipientScreens.kt:348-349,628-639`; `HistoryScreen.kt:304-307`; `PlanScreens.kt:133-139`; `DesignSystem.kt:425-499,577-628`; `SyncStatusIndicator.kt:25-61`; `NoteRow.kt:119-135`; `NoteScreens.kt:324-345`; `CsvTransferUi.kt:55-97`; `BackupRestoreUi.kt:48-88`.

Pills use text, not color alone. Safety, informational, sync, pending and saved states also have icons/glyphs. CSV/backup notifications rely on written result text.

## 4. Typography

The app bundles IBM Plex Sans Arabic in regular, medium and bold. It supplies both Latin and Arabic glyphs; no separate Android Latin family is used.

Files: `res/font/plex_arabic_regular.ttf`, `plex_arabic_medium.ttf`, `plex_arabic_bold.ttf`. Source: `Type.kt:9-42`.

The app starts from Material 3 `Typography()` and copies all 15 styles with PlexArabic. The default Material 3 scale is retained except where screens apply local overrides.

| Style | Size / line height | Weight |
|---|---:|---:|
| displayLarge | 57 / 64 | 400 |
| displayMedium | 45 / 52 | 400 |
| displaySmall | 36 / 44 | 400 |
| headlineLarge | 32 / 40 | 400 |
| headlineMedium | 28 / 36 | 400 |
| headlineSmall | 24 / 32 | 400 |
| titleLarge | 22 / 28 | 400 |
| titleMedium | 16 / 24 | 500 |
| titleSmall | 14 / 20 | 500 |
| bodyLarge | 16 / 24 | 400 |
| bodyMedium | 14 / 20 | 400 |
| bodySmall | 12 / 16 | 400 |
| labelLarge | 14 / 20 | 500 |
| labelMedium | 12 / 16 | 500 |
| labelSmall | 11 / 16 | 500 |

Common local overrides: main heading 22sp bold/28sp line height; section heading 19sp bold; group label 14sp bold; top-bar title 18sp bold; note-row name 15sp bold/time 13sp/body 15sp; summary body 16sp/29sp; primary button 15sp bold (large 17sp); pill 13sp bold.

Sources: `Type.kt:20-42`; `DesignSystem.kt:140-177,207-260,263-330,388-423`; `NoteRow.kt:108-150`; `SummaryScreen.kt:393-402`.

## 5. Spacing and layout

Canonical 8dp rhythm plus 4dp half-step: 4, 8, 16, 24, 32 and 48dp. Additional common values: 12dp card/list gap; 14dp section gap; 16dp screen horizontal padding; 14dp card padding; 20dp section padding; 24dp page padding; 16dp alert padding.

Sources: `Spacing.kt:5-18`; `DesignSystem.kt:80-113`.

## 6. Shape, border and elevation

Global Material 3 shape overrides: extraSmall 12dp; small 12dp; medium 12dp; large 14dp; extraLarge 28dp.

Component values: card 14dp; button/input 12dp; account popup 16dp; pill 999dp; pain square 8dp; swatch 12dp; avatars circular; settings groups 24dp outside corners and 6dp joins; dialog uses AlertDialogDefaults.shape (app extraLarge 28dp).

Cards use a 1dp soft border and 1dp shadow. Settings groups use zero elevation and subtle surface tint. Bottom navigation/action surfaces rely on borders instead of prominent shadows. Account popup uses 8dp tonal elevation.

Sources: `Theme.kt:64-74`; `DesignSystem.kt:71-78,332-352`; `AccountPopupMenu.kt:42-48`; `CaregiverSettings.kt` (`groupRowShape`); `Dialog.kt:80-95,207-223`.

## 7. Component specifications

### Buttons

Primary: full width by default, 48dp minimum height, 56dp large action, 12dp radius, accent fill, white bold label (15sp/17sp). Disabled container uses reduced opacity. Secondary: surface fill, 1dp outline, 12dp radius, ink label; danger label for destructive variants.

Source: `DesignSystem.kt:263-330`.

### Inputs

Outlined Material 3 fields; standard minimum height 52dp; 12dp radius; primary focused border; neutral unfocused border; surface fill. Validation appears as supporting danger-colored text. Note and addendum fields use taller 130dp/150dp heights.

Sources: `RecipientScreens.kt:496-524`; `NoteScreens.kt:178-200,581-603`.

### Cards and pills

Cards: 14dp radius, 1dp soft border, surface fill, 1dp shadow, normally 14dp inner padding and 8dp content gap. Status pills: 999dp radius, 3dp vertical/10dp horizontal padding, 13sp bold; variants gray/green/red/yellow/light-blue. Selected form chips use primary-soft fill and primary label; selection can include a visible check.

Sources: `DesignSystem.kt:332-423`; `NoteScreens.kt:244-271`.

### Dialogs

Shared dialog wrapper uses 24dp outer margin, 24dp content padding, Material 3 AlertDialog surface/shape, 0dp default tonal elevation and 24dp spacing before actions.

Source: `Dialog.kt:69-142,201-228`.

### Top bar, navigation and lists

Tab shell: centered app title, 56dp bar, 36dp avatar at start and sync status at end. Four tabs: Home, People, Notes, History. Bottom strip is 64dp with 22dp icons and 11sp labels; selected icon uses primary and unselected icon uses onSurfaceVariant. A 56dp FAB appears on tab routes. Detail screens use a visible back affordance; the back target is 48dp.

Sources: `DesignSystem.kt:120-138,179-237`; `MainScaffold.kt:247-365`; `AppNavigation.kt:197-298`; `Routes.kt:6-15`.

Timeline rows are full-width rather than card-per-item: 40dp avatar, author/time, body, optional pills/actions and 1dp divider. Body collapses after four lines with Show more. Pending sync shows a clock glyph and accessible description.

Source: `NoteRow.kt:41-55,57-64,66-171`.

### Empty, loading and error

Empty state: centered icon, bold title, muted support text and optional secondary action. Error: danger treatment, message and retry. Warning and info have dedicated semantic containers. Loading uses skeletons matching final row geometry and a shared shimmer theme. Busy state uses a 20dp spinner, label and 6dp progress track.

Sources: `DesignSystem.kt:425-499,533-670`; `NoteRow.kt:174-200`; `MainActivity.kt:64-67`.

## 8. Main screen patterns

- **Home:** shared tab shell; compact summary stat cards; safety banner when relevant; scrolling recipient/note content; tab-level FAB. `HomeScreen.kt:82-83,175-244,343-384,456-480`.
- **Recipients:** 22sp bold heading, 13sp count subtitle, 16dp gutters, 12dp list gaps, interactive recipient cards, archived/no-note/done pills. `RecipientScreens.kt:103-106,220-240,282-284,334-349,477-524,613-639`.
- **Notes/editor:** timeline rows; grouped form; wrapping chips; 48dp pain controls; taller textarea; persistent bottom save action; centered save confirmation. `NoteScreens.kt:94-98,152-228,244-271,312-345`.
- **History:** heading and person/date filters above full-width timeline; pull-to-refresh/client-side paging; shared empty/error states. `HistoryScreen.kt:83-93,128-173,181-228`.
- **Trends:** placeholder only (title, pain heading, empty string); no chart palette or visualization tokens. `CareTrendsScreen.kt:22-29`.
- **Summary:** reading-first long form; disclaimers/info cards, evidence cards, red-flag alerts and follow-up actions. `SummaryScreen.kt:92-132,272-319,361-415,557-583`.
- **Plans:** plan cards, status pill, information card and grouped bottom accept/edit/dismiss/version actions. `PlanScreens.kt:65-110,133-139,168-269`.
- **Settings:** grouped scrollable sections, language chips, AI/theme entries, CSV import/export, backup/restore; theme preview, System/Light/Dark, pure-black and palette controls. `SettingsScreens.kt:110-232`; `ThemeColorsScreen.kt:86-179`.

## 9. RTL and Arabic

Android applies the locale before the first frame. Observed safeguards: auto-mirrored back/forward icons; layout-start/end positioning; explicit RTL/LTR paragraph direction for free text; Unicode FSI/PDI isolation of mixed-script values; one Plex Arabic family for both languages.

Sources: `MainActivity.kt:24-38,43-69`; `Bidi.kt:3-23`; `NoteRow.kt:81-88,108-125`; `DesignSystem.kt:120-138,354-368`.

For web parity use logical properties such as `padding-inline`, `margin-inline`, `inset-inline-start` and `inset-inline-end`. Mirror only directional icons. Isolate names, IDs, numbers and dates embedded in Arabic text; setting `direction: rtl` alone does not guarantee correct bidi ordering.

## 10. Accessibility and contrast

Contrast ratios were calculated from extracted sRGB hex values using WCAG relative luminance. AA is 4.5:1 for normal text and 3:1 for large text/non-text UI.

| Pair | Ratio | AA normal text |
|---|---:|---|
| Light ink/background | 13.60:1 | Pass |
| Light ink/surface | 13.95:1 | Pass |
| Light muted/background | 5.14:1 | Pass |
| Dark ink/background | 14.66:1 | Pass |
| Dark ink/surface | 13.40:1 | Pass |
| Dark muted/background | 9.56:1 | Pass |
| Light danger/container | 6.89:1 | Pass |
| Light success/container | 6.81:1 | Pass |
| Light info/container | 7.24:1 | Pass |
| Light warning/container | 6.55:1 | Pass |
| Dark danger/container | 6.03:1 | Pass |
| Dark success/container | 6.22:1 | Pass |
| Dark info/container | 7.75:1 | Pass |
| Dark warning/container | 8.01:1 | Pass |

White text on several palette accents fails AA: Dynamic 3.45; Crimson 3.49; Blue 3.68; Sky Blue 3.08; Cyan 2.74; Teal 4.32; Green 3.30; Light Green 2.50; Lime 1.79; Yellow 1.40; Amber 1.79; Orange 2.37; Deep Orange 3.48. Rose, Purple, Deep Purple, Indigo, Brown, Grey and Blue Grey pass. Metrolist light passes 6.44; Metrolist dark onPrimary passes 7.71.

Observed targets include 48dp back/sync controls, buttons minimum 48dp, large actions 56dp and palette swatches 48dp. Sync, pending-note, back and tab controls have accessibility labels. No explicit Android reduced-motion preference implementation was found in the inspected layers.

Sources: `Theme.kt:22-62,103-125`; `DesignSystem.kt:87-113,263-330`; `SyncStatusIndicator.kt:31-61`; `ThemeColorsScreen.kt:224-233`; `NoteRow.kt:119-135`.

## 11. Shallow web observations

The inspected web setup uses DM Sans Variable for Latin and Noto Sans Arabic Variable for Arabic. `web/src/styles/base.css` defines a green/light-neutral style with root colors including `#213B32`, `#FAFAF6`, `#285B45`, `#1D4635` and an 18px general radius. Arabic-specific typography and RTL directional-icon mirroring are present. `web/package.json` does not declare Tailwind.

Sources: `web/package.json:17-38`; `web/src/main.tsx:10-12`; `web/src/styles/base.css:1-17,131-145`.

This was not a feature-by-feature web UI audit.
