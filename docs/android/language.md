# App language (Arabic / English)

## Mechanism

One source of truth, one application point:

- **Source of truth** — the `language` value in `SettingsStore`
  (DataStore). `SettingsStore.DEFAULT_LANGUAGE = "ar"`.
- **Process cache** — `LocaleHelper` (core/util) mirrors the stored value:
  `CaregiverApp.attachBaseContext` reads the store once per process and
  seeds it; `SettingsStore.setLanguage` writes through to it.
- **Application point** — `MainActivity.attachBaseContext` wraps its base
  context with `LocaleHelper.wrap(base, cachedOr(DEFAULT_LANGUAGE))`.
  Everything the activity composes (text, RTL direction, date formats)
  resolves through that context, from the first frame — no flash of the
  previously selected language.
- **Switching** — the Settings chips call `graph.settings.setLanguage(code)`
  and then `activity.recreate()`. The recreated activity re-runs
  `attachBaseContext`, so the new locale applies immediately; navigation
  state survives via the activity's saved state.

No `AppCompatDelegate`/`LocaleManager` state is involved, so the DataStore
value cannot disagree with an Android-level locale. On API 33+ the app
does not appear in the system per-app-language screen (`localeConfig` is
deliberately absent) — language is chosen inside the app only.

## What "correct" means here

- Arabic selection changes visible text *and* layout direction (RTL);
  English selection restores LTR.
- The choice survives activity recreation, rotation, and process death
  (DataStore).
- There is no recreate loop: the chips only recreate when the stored
  language actually differs.

## Tests

- `app/src/test/.../core/util/LocaleHelperTest.kt` — the helper derives
  Arabic+RTL / English+LTR configurations, the Arabic resources resolve,
  and saving a language feeds the cache and persists. (Robolectric ignores
  `createConfigurationContext` overrides, so the wrapped-context apply is
  asserted on device, not here.)
- `app/src/test/.../presentation/settings/SettingsScreenTest.kt` — every
  Settings section and all four Data & Backup actions render (this is the
  regression test for the name-collision bug that emptied the list), the
  backup actions stay visible with their explanation where there is no
  local store, CSV stays enabled, and the AI settings screen renders its
  rows.
- `app/src/androidTest/.../MainActivityLocaleTest.kt` — drives the real
  activity: save + recreate flips `resources.configuration` language and
  layout direction. Run on a device/emulator with
  `./gradlew :app:connectedDebugAndroidTest`.
