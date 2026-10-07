# Mobile App (Android)

React Native CLI Android client for Caregiver Daily Notes. Separate app, same
backend REST API. No Expo, no iOS, no offline mode, no secrets in the app.

Locked toolchain: React Native 0.87.1, React 19.2.3, Node ≥ 22.11
(CI uses 22), JDK 17 for Android builds, TypeScript strict, Jest +
`@testing-library/react-native` v14 (async `render`/`fireEvent`/`unmount` —
always `await` all three).

## Setup (fresh checkout to login screen)

```bash
cd mobile
npm ci
npx tsc --noEmit
npm test -- --ci
npx react-native start          # terminal 1: Metro
npx react-native run-android    # terminal 2: emulator or USB device
```

The app boots to the login screen. Authentication screens run against
scripted fakes for now; live backend binding arrives in Part B. Arabic and
English dictionaries plus RTL layout sync are in place, but there is no
in-app language switcher yet — the saved preference (default English) applies
at startup.

## Scripts

| Command | What |
|---|---|
| `npm test -- --ci` | Jest suite (no live network; scripted fakes only) |
| `npx tsc --noEmit` | Strict typecheck — must be clean per task |
| `npx react-native run-android` | Install + launch on emulator/device |
| `npm run android` | Same as above |

## Pointing the app at a backend (no rebuild, ever)

Settings (More tab) → Server URL → paste URL → Save. The value persists
on-device (`AsyncStorage`) and every request reads it fresh. Tunnel URLs
rotate per session — re-paste, never rebuild.

## Demo procedure (local backend + tunnel)

Backend dependency: steps 1–2 and 6–8 need a running backend. The backend
has no runnable entrypoint yet (Member 3 #12), so until it lands only steps
3–5 (offline validation + Server URL editor) are exercisable.

Production vs tests: `mobile/App.tsx` wires `ApiClient(new
FetchTransport())` — every request reads the runtime server URL and the
stored token. Tests (and only tests) inject `FakeTransport` with scripted
responses; no live network in tests.

1. Start the backend once its entrypoint exists: `mvn -f backend/pom.xml spring-boot:run`
2. Open the tunnel: `cloudflared tunnel --url http://localhost:8080` → copy the `https://…trycloudflare.com` URL
3. Fresh install → launch → login screen renders (English default). There
   are no tabs yet: the More tab and its Settings live behind sign-in.
4. Validation (no backend needed): submit blank/short credentials →
   localized errors
5. Server URL first: login screen → Server URL link → paste the tunnel URL
   → Save → confirmation shows; back to login. (The production client
   needs this URL to reach the backend — set it before registering.)
6. Register via the register link → signed in → Notes/History/Plans/More
   tabs appear. The tabs are title-only shells for now; the feature
   screens in `mobile/src/features/*` run in tests with fixtures and get
   mounted into navigation in Part B — do not demo them as working flows.
7. More tab → Settings shows the saved URL; Reset restores the default
   with confirmation
8. More tab → Logout → back to login, token cleared

## Emulator networking notes

- Emulator reaching the host machine: `http://10.0.2.2:8080` (not `localhost`).
- Prefer the tunnel `https://` URL everywhere: Android 9+ blocks cleartext
  `http://` by default, and one URL works for emulator, phone, and browser.
- Physical device: same Wi-Fi + host LAN IP, or the tunnel URL (simpler).

## Installing a CI debug APK

1. GitHub → Actions → Mobile workflow run → `mobile-debug-apk` artifact → download
2. `adb install -r app-debug.apk` (emulator running or device attached)
3. Launch "CaregiverMobile" → login screen → set Server URL → go

Debug builds use the template debug keystore (public `android` credentials,
safe to commit). Release signing is Part B with owner-held secrets.

## Android build requirements

Must match `mobile/android/build.gradle`: compile SDK 37
(`platforms;android-37`), build-tools 37.0.0, JDK 17, Gradle wrapper
(gradle-9.4.1). CI installs exactly these (`platform-tools` plus the two
above; never the deprecated `tools` package). No Android SDK is installed
on the dev box, so APK builds and emulator checks run in CI only.
