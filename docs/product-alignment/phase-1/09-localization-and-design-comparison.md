# Localization and design comparison

**References:** Android `feat/standalone-byok-ai` at `21876285ad808a6518593ea3974c51b6b1f6bb31`; web/backend `main` at `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7`.

## Language and direction

Both implementations include English and Arabic. Android uses localized resources, locale helpers, bidi isolation and native Compose layout; it includes tests for string parity, locale handling and RTL. Web has English/Arabic message dictionaries and changes document `lang` and `dir` globally; user selection is persisted in localStorage. See Android [`StringsParityTest.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/test/java/com/caregiver/mobile/core/i18n/StringsParityTest.kt#L1-L75); web [`I18nProvider.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/i18n/I18nProvider.tsx#L1-L49), [`AppShell.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/components/AppShell.tsx#L1-L78).

## Design and interaction

- Android uses Material 3, bottom tabs/drawer, native dialogs, device back navigation, theme modes and color palette selection.
- Web uses responsive CSS, a persistent header/nav and footer, route-based pages, focus management, semantic forms, skip link, `aria-*` states and `bdi`/`dir=auto` for user-provided text.
- The language toggle is visible in the web shell; mobile exposes language and theme settings. No web theme/palette preference page was found.
- These are appropriate platform-specific layouts. The product should align labels, meaning, data entry and status behavior—not force identical screen geometry.

## Terminology and behavior gaps

1. Field concepts differ at the contract level: Android `medicationTaken`, `pain`, `fall`, `text`; web/backend `medicationStatus`, `medicationVerified`, `painScore`, `fallReported`, `freeText`. Web supports “unverified” medication status explicitly; Android maps a simpler medication value.
2. Android addenda contain text; web addenda are selected structured corrections. “Addendum” therefore does not promise the same action.
3. Android summary presents text/evidence/flags in its DTO; web displays structured observations, source quotes, safety severity and trends.
4. Recipient lifecycle labels/actions differ: Android archives then offers permanent delete; web deactivates and preserves server history.
5. Mobile has palette/theme and BYOK/consent terminology not present in the web settings surface.

The translations are bilingual, but source inspection was not a human linguistic review. Product labels and safety wording need bilingual review before claiming terminology parity or clinical appropriateness.

## Accessibility and verification

Web source shows focus restoration, labeled controls, `role=status/alert`, keyboard-operable controls and directional icons. Playwright and axe dependencies exist; dependency presence alone does not prove a successful accessibility audit. Android has Compose UI tests for RTL, settings, screen flows and semantics but this investigation did not run them. Neither finding should be interpreted as a full WCAG/accessibility certification.

**Evidence:** Android `android/app/src/main/res/values/strings.xml`, `values-ar/strings.xml`, `core/i18n/`, `core/theme/`, and UI tests under `android/app/src/androidTest`; web `web/src/i18n/translations.ts`, `I18nProvider.tsx`, `components/AppShell.tsx`, and `web/src/tests/`.
