# Mobile App (Android)

React Native CLI Android client for Caregiver Daily Notes. Separate app, same
backend REST API. No Expo, no iOS, no offline mode, no secrets in the app.

Locked toolchain: React Native 0.87.1, React 19.2.3, Node ≥ 22.13
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

The app boots to the login screen. Part A is fully backend-less:
`mobile/src/app/partAClient.ts` builds the injected `ApiClient` on a
scripted `FakeTransport` (synthetic fixtures only, zero network), so login,
registration, and every feature flow work with no backend running. Sign in
with any valid email + ≥ 8-character password. Part B rebinds the app to
`FetchTransport` (the class stays in `mobile/src/lib/` for that phase).
Arabic and English dictionaries plus RTL layout sync are in place, but there
is no in-app language switcher yet — the saved preference (default English)
applies at startup.

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
rotate per session — re-paste, never rebuild. Note: the Server URL is read
by `FetchTransport`, which Part B binds in; Part A runs on the scripted
`FakeTransport` and ignores it.

## Demo procedure (local backend + tunnel)

Part A needs no backend: every step below runs against the scripted
`FakeTransport` responses in `mobile/src/app/partAClient.ts` (synthetic
fixtures only). Steps 1–2 prepare the backend for the Part B binding; they
are not needed for Part A. (The backend has no runnable entrypoint yet —
Member 3 #12.)

1. (Part B only) Start the backend once its entrypoint exists: `mvn -f backend/pom.xml spring-boot:run`
2. (Part B only) Open the tunnel: `cloudflared tunnel --url http://localhost:8080` → copy the `https://…trycloudflare.com` URL
3. Fresh install → launch → login screen renders (English default). There
   are no tabs yet: the More tab and its Settings live behind sign-in.
4. Validation (no backend needed): submit blank/short credentials →
   localized errors
5. Server URL (persisted for Part B; Part A fakes ignore it): login screen
   → Server URL link → paste a URL → Save → confirmation shows; back to
   login
6. Sign in with any valid email + ≥ 8-character password → Notes tab shows
   the recipients list
7. Recipients: Add recipient → submit a name → new row; tap a row → detail
   (Back returns to the list)
8. Notes: detail → Write note → fill mood + pain + text → Save → note
   detail; addendum form → submit → appended below the original (original
   stays read-only)
9. Summary: detail (or note detail) → View summary → summary card with the
   non-dismissible safety banner, evidence quotes, uncertainties
10. History tab → seeded notes listed; recipient/date filters narrow them
11. Plans tab → list → tap a plan → detail with the four localized actions
    → Accept goes through the client and refreshes
12. More tab → Settings shows the saved URL; Reset restores the default
    with confirmation
13. More tab → Logout → back to login, token cleared

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
