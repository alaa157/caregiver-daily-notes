# Pending localization polish

Remaining items for the `feat/standalone-byok-ai` localization/RTL pass.
Do not build until all items below are pushed.

## 1. Conditional NoteRow separator (dangling separator)
- File: `android/app/src/main/java/com/caregiver/mobile/presentation/common/NoteRow.kt`
- The trailing `HorizontalDivider` renders after every row, including the last.
- Make the divider conditional (e.g. `showDivider: Boolean = true`) and pass
  `false` for the last item in list callers, or move the divider into the list.
- Also guard dangling ` · ` separators in `HomeScreen.RecipientRow` and
  `RecipientScreens` latest-note summary when a label is blank.

## 2. Android per-app locale config
- File: `android/app/src/main/AndroidManifest.xml`
- Add `android:localeConfig="@xml/locales_config"` to `<application>`.
- Create `android/app/src/main/res/xml/locales_config.xml` listing `en` and `ar`.

## 3. Latin-digit policy for Arabic
- File: `android/app/src/main/java/com/caregiver/mobile/core/util/LocaleHelper.kt`
- In `configurationFor`, use `Locale.forLanguageTag("ar-u-nu-latn")` for Arabic
  so Arabic UI keeps Arabic text/RTL but renders Western digits.
- Add a test in `LocaleHelperTest` pinning the resulting language tag.

## 4. Optional: externalize remaining hardcoded copy
- Share text in `CareSummaryShare.shareText` (bilingual strings).
- AI format-validation probe text in `AiSettingsScreen`.
- Keep pure-function tests passing if these move to resources.

## 5. Final verification (after all pushes)
- Run `:app:assembleDebug`, `:app:testDebugUnitTest`, and `:app:lintDebug`.
- Fix any failures with follow-up commits.
