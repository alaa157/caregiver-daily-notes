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
./gradlew :app:testDebugUnitTest --no-daemon

# Single test class
./gradlew :app:testDebugUnitTest --tests "*StringsParityTest*" --no-daemon

# Instrumentation / Compose UI tests (needs a booted emulator or device;
# NEVER treat testDebugUnitTest as running these)
./gradlew :app:connectedDebugAndroidTest --no-daemon

# Debug APK (universal: arm64-v8a, armeabi-v7a, x86_64; minify off)
./gradlew :app:assembleDebug --no-daemon
# → app/build/outputs/apk/debug/app-debug.apk
```

Lint:

```bash
./gradlew :app:lintDebug --no-daemon
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
