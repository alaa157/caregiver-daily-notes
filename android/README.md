# Native Android app (Kotlin/Compose)

Native Kotlin application in `android/` (`com.caregiver.mobile`). Consumes the
same Spring Boot REST API as the web app. No cross-platform framework.

- Backend builds with **Maven** (`mvn -f backend/pom.xml …`).
- Android builds with **Gradle** (`./gradlew` in `android/`). The two are independent.

## Requirements

- JDK 17 (Gradle/AGP 8.7.x). Newer JDKs (e.g. 25) fail the build.
- Android SDK: `platforms;android-35`, `build-tools;35.0.0` (see
  `.github/workflows/android.yml`). minSdk 23, target/compile 35.
- A running backend URL for on-device runs (tunnel or direct HTTPS).

## Build variants and runtime mode

- `debug` / `minified`: `DEMO_BACKEND=true` (illustrative demo data, no network) and `LOCAL_MODE=true`.
- `release`: `DEMO_BACKEND=false`, `LOCAL_MODE=true`. The official release is intentionally local-first/standalone: no backend, no login, Room is the source of truth. The demo banner is driven by `DEMO_BACKEND`, not by `LOCAL_MODE`, so production summaries are never mislabeled as demo content.

## Standalone install (no backend, no login)

The app is standalone: install the APK from GitHub Releases and use it without
any backend. AI features (summaries and plan suggestions) need
your own API key.

1. Download the latest `app-release.apk` from the GitHub Releases page.
2. Install it (allow installing from unknown sources).
3. Open Settings > AI, pick a provider (OpenAI, Anthropic, Gemini, xAI, or
   OpenRouter), paste your API key and model id, then tap **Check setup**.
4. On first use you will see a consent dialog: note text used for a summary
   can be sent to the chosen provider. Relationship and care-detail fields are
   not included; the app is not a medical device and is not HIPAA-compliant.

### Getting an API key

- OpenAI: https://platform.openai.com/api-keys
- Anthropic: https://console.anthropic.com/settings/keys
- Gemini: https://aistudio.google.com/app/apikey
- xAI: https://console.x.ai
- OpenRouter: https://openrouter.ai/keys

Each provider's key is stored encrypted in the Android Keystore and never leaves the device. Keys are kept in separate slots, so switching providers never reuses the previous provider's key.

## Commands (run in `android/`)

```bash
# Local JVM unit tests (no device needed)
./gradlew :app:testDebugUnitTest

# Single test class
./gradlew :app:testDebugUnitTest --tests "*StringsParityTest*"

# Instrumentation / Compose UI tests (needs a booted emulator or device;
# NEVER treat testDebugUnitTest as running these)
./gradlew :app:connectedDebugAndroidTest

# Debug APK (universal, minify off) — offline demo
./gradlew :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk

# Minified APK (R8 + resource shrink, debug-signed) — offline demo, smaller
./gradlew :app:assembleMinified
# → app/build/outputs/apk/minified/app-minified.apk
# NOTE: R8 shrinking is slow by nature (full-app analysis). The build cache
# makes subsequent runs reuse it until inputs change. Do not pass --no-daemon:
# gradle.properties keeps the daemon, build cache, and configuration cache on.
```

## Offline demo (debug and minified builds)

`debug` and `minified` set `DEMO_BACKEND=true` and serve the in-memory
`DemoBackendApis` (`data/demo/`) instead of Retrofit: no backend, no
network, no registration needed.

- On the login screen, tap **“Explore the demo (offline)”** — or sign in
  with any credentials; the demo backend accepts everything.
- Recipients, notes, addenda, summaries, and plan accept/dismiss/edit all
  work against canned bilingual data dated relative to today. Mutations
  live only in memory and are never uploaded.
- `release` uses the real backend (`DEMO_BACKEND=false`); the demo entry
  control is absent there.

Lint:

```bash
./gradlew :app:lintDebug
```

## Architecture decisions

- Per-account databases: [`docs/adr/001-per-account-databases.md`](docs/adr/001-per-account-databases.md)

## Structure

```
android/
├── app/src/main/java/com/caregiver/mobile/
│   ├── core/theme/        # palette (#0E6B66 …), type, theme
│   ├── core/navigation/   # 4 tabs + typed routes (plans live under detail/summary, no tab)
│   ├── core/i18n/         # Bidi isolation for mixed-direction text
│   ├── core/network/      # bearer interceptor (configured origin only)
│   ├── core/time/         # locale-aware dates (ar/en)
│   ├── data/              # DataStore settings/token, Retrofit APIs, AuthRepository
│   └── presentation/      # auth, home, recipients, notes, history, summary
├── app/src/main/res/values/strings.xml       # en (complete)
├── app/src/main/res/values-ar/strings.xml     # ar (key parity enforced by test)
└── app/src/test/ + androidTest/ + sharedTest/ # fakes shared by both suites
```

Manifest notes: `supportsRtl=true`, `INTERNET` permission, narrow cleartext
config (HTTPS default; HTTP only for loopback/private hosts, enforced in code
too). Start/end layouts only — no left/right paddings.

## State catalog (board 15)

- Loading: `LoadingRow` everywhere.
- Error + retry: `LoadFailed` + `common_retry`; offline keeps the draft
  (`common_offline`), summary transport failures show the notes-safe
  `summary_ai_down` copy with flags intact.
- Empty: `history_empty`, `people_empty`, `notes_empty`, `plans_empty_title` +
  `plans_empty_action`.
- Safety: non-dismissible banner whenever flags exist — including
  loading/error/AI-unavailable states. No close control by construction.
- API gaps the client must not invent around: `docs/api/android-gaps.md`.

## AI settings and summaries

- **Models and data use:** choose a provider, then select a catalog model or
  **Other** to enter a model id. Paid models are listed first; free models are
  explicit choices, not defaults or recommendations. A custom id must match
  `^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$`.
  - Gemini data use depends on the API key's billing tier, not the model: a
    free-tier key may allow Google to use sent content to improve its products;
    a key from a project with billing enabled is not used this way.
  - An OpenRouter model ending in `:free` follows its host's data policy, and
    some hosts train on what they receive.
  - The app requires provider-specific consent before sending care notes.
    Acknowledgement of the data-use notice is required for Gemini and free-host
    cases; the notice appears with the selected model.
  - A custom address warning explains that the API key and care notes go to
    that address instead of the provider's default. Use only an address you
    trust.
- **Check setup** runs three checks in order: provider connection, summary
  format, and accuracy/grounding. The checks use a ping or fictional examples;
  they do not send your care notes. The format and accuracy checks wait for
  provider consent before sending their examples.
- **Advanced** contains the custom provider address and optional system,
  summary, and plan instructions. Most people do not need to change these.
- **Summary window:** choose 7, 14, or 30 days and optionally set an end date.
  The date picker does not allow a future date.
- **Observations and trends:** supported observation cards show a claim,
  source quote, and a link to open the source note. The trends compare falls,
  high-pain days (pain 7 or higher), days with missed medication, and days
  with poor appetite across the requested and immediately previous windows.
  Previous-window data is used on the device for this comparison and is not
  sent to the provider.
- If refreshing a summary fails, the app shows recovery guidance and retains
  the last result for PDF export; the result cards are not shown in the failure
  view.
- **Saved summaries** are stored on the device, with at most 20 per person.
  They are included in backups and removed when the person is deleted.
  Reopening a saved result does not call the provider. If you explicitly ask
  for an AI plan suggestion while viewing it, the displayed summary text is
  sent to the selected provider.
- Recipient and saved-summary data use Room v10; current backups use format
  v6. Saved summaries were added in Room v10 and backup format v6.

## Device walkthrough (do in both العربية and English)

1. Login footer → server URL → save → back (once Settings UI lands; today the
   stored URL is used silently).
2. Register → Home greeting (`صباح الخير، <name>` / `Good morning, <name>`).
3. People → Add person (Arabic + English names) → person detail shows today’s
   note status, relationship and care details; Add note / history / care plan /
   summary all navigate.
4. Editor:
   - Every question is optional, and nothing is preselected.
   - Tapping a selected chip clears it.
   - Saving requires at least one answer or some text.
   - **Add details** opens the text box.
   - A second note on the same day offers **View today’s note** and **Add correction**.
5. History → person/date filters narrow; rotation keeps the banner.
6. Summary → choose 7/14/30 and an end date → result shows observations with
   source links, trends, uncertainties, and a disclaimer; airplane mode →
   recovery guidance, with the last result still available for PDF export.

## Note fields

- Pain and fall answers are tracked separately with the `painRecorded` and
  `fallRecorded` flags, so an unanswered question is not confused with pain 0
  or “no fall.”
- CSV exports leave the cell empty for unanswered fields, including pain and
  fall when their recorded flag is false.
- Backup format v4 introduced these flags. Notes and backups created before
  the flags existed are treated as recorded; current backups use format v6.
- The editor’s current chip codes and codes retained for notes written with
  the legacy vocabulary are:

| Field | Current codes | Legacy codes |
| --- | --- | --- |
| Mood | `calm`, `happy`, `anxious`, `agitated`, `sad`, `confused` | `good`, `fair`, `bad` |
| Appetite | `good`, `fair`, `poor`, `refused` | `reduced` |
| Sleep | `restful`, `restless`, `poor`, `sleepless` | `good`, `broken` |
| Mobility | `independent`, `assisted`, `bedridden` | `walks`, `bed` |
| Medication | `taken`, `missed`, `unsure` | `none` |

## People and recipient data

- Names are trimmed, inner whitespace is collapsed, and names are limited to
  120 characters. Avatars show the first Unicode code point of the normalized
  name.
- When a name matches an existing person, the form shows duplicate advice with
  **Open existing**, **Create anyway**, and **Cancel** actions.
- Names and relationships can be edited. Archived people are read-only.
- Relationship choices use these stored codes and localized labels:

| Code | English | Arabic |
| --- | --- | --- |
| `MOTHER` | Mother | الأم |
| `FATHER` | Father | الأب |
| `SPOUSE` | Spouse | الزوج/الزوجة |
| `GRANDMOTHER` | Grandmother | الجدة |
| `GRANDFATHER` | Grandfather | الجد |
| `CHILD` | Child | الابن/البنت |
| `SIBLING` | Sibling | الأخ/الأخت |
| `RELATIVE` | Relative | قريب |
| `FRIEND` | Friend | صديق |
| `CLIENT` | Client | عميل |
| `OTHER` | Other | أخرى |

- Text entered for **Other** is stored as free text (with surrounding
  whitespace trimmed), not mapped to another code; relationship values are
  limited to 80 characters.
- Optional care details are stored on the device and included in backups; they
  are never sent to an AI provider:
  - Date of birth: `YYYY-MM-DD`, from 1900-01-01 through today.
  - Allergies and conditions: up to 500 characters each.
  - Emergency contact name: up to 120 characters.
  - Emergency contact phone: up to 30 characters.
- The person screen shows whether a note was recorded today. Recipient fields
  were added in Room v9 and backup format v5; the current versions are Room
  v10 and backup format v6.
