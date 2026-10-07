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

The app boots to the login screen (FakeTransport-free shell; real auth
arrives in Part B). Switch language ar/en in-app later (Tasks A3+); layout
is RTL-synced via `I18nManager`.

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

1. Start the backend once its entrypoint exists: `mvn -f backend/pom.xml spring-boot:run`
2. Open the tunnel: `cloudflared tunnel --url http://localhost:8080` → copy the `https://…trycloudflare.com` URL
3. Paste it into the app's Server URL setting
4. Run through: login → recipients → note → addendum → history → summary → plans

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
