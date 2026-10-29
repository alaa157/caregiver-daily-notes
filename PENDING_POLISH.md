# Localization polish

Completed on `feat/standalone-byok-ai`.

- [x] Hide the dangling `NoteRow` separator on the last row of the home and
      history timelines, and drop blank ` · ` labels from the home and
      recipient latest-note summaries.
- [x] Declare the per-app locale config (`en`, `ar`) in the manifest.
- [x] Pin Western digits for Arabic through the `ar-u-nu-latn` locale while
      keeping Arabic text and RTL layout.
- [x] Externalize the summary share message and the AI format-validation
      probe text into the `en`/`ar` string resources.

## Final verification

All passed with the Android SDK 35 toolchain:

- `:app:assembleDebug`
- `:app:testDebugUnitTest`
- `:app:lintDebug`
