# Native Android app (Kotlin/Compose)

Native Kotlin application in `android/` (`com.caregiver.mobile`). Consumes the
same Spring Boot REST API as the web app. No cross-platform framework.

- Backend builds with **Maven** (`mvn -f backend/pom.xml …`).
- Android builds with **Gradle** (`./gradlew` in `android/`). The two are independent.

## Requirements

- JDK 17 (Gradle/AGP 8.7.x). Newer JDKs (e.g. 25) fail the build.
- Android SDK: `platforms;android-35`, `build-tools;35.0.0` (see
  `.github/workflows/android.yml`). minSdk 21, target/compile 35.
- A running backend URL for on-device runs (tunnel or direct HTTPS).

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

## Device walkthrough (do in both العربية and English)

1. Login footer → server URL → save → back (once Settings UI lands; today the
   stored URL is used silently).
2. Register → Home greeting (`صباح الخير، <name>` / `Good morning, <name>`).
3. People → Add person (Arabic + English names) → detail → Add note / history /
   care plan / summary all navigate.
4. Editor → mood required, pain 0–10, free text (Arabic roundtrips byte-identical),
   save → Saved → View note / Create summary.
5. History → person/date filters narrow; rotation keeps the banner.
6. Summary → 7/14/30 → result shows body + evidence (`#<8-char uuid>`) +
   uncertainties + disclaimer; airplane mode → offline error, draft kept, retry works.
