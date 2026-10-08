# Android 0.1.0 — debug draft (Task 8)

> Draft only. Debug APK from CI (`android-debug-apk` artifact in the
> `Android` workflow). No Play release. No secrets bundled — token lives in
> DataStore, base URL is a runtime setting.

## What's in this build

- Native Kotlin/Compose app (`com.caregiver.mobile`, minSdk 21, target/compile 35).
- Auth (login/register), home dashboard, recipients, note editor/detail/addendum,
  history + single-day notes, AI summary with non-dismissible safety banner.
- Arabic/English with `supportsRtl=true`; mixed-direction names/quotes isolated.
- Board-15 states: loading / error + retry / empty / offline-kept-draft /
  AI-unavailable (notes-safe).

## Known gaps (not in this build)

- Plans UI (list/proposal/edit/versions + transitions) — API seam only.
- Settings UI (server-URL editor, language switch, logout) — store only.
- See `docs/api/android-gaps.md` for backend-owned gaps (history counts,
  pagination, summary trend sections, home safety aggregate).

## Verify on device (ar + en)

1. Install `app-debug.apk` on an arm64 device or x86_64 emulator.
2. Login → Home greeting shows the email local-part, RTL mirrors.
3. People → add person (Arabic name + English name) → detail actions navigate.
4. Add note → chips single-select, pain 0–10, save → Saved screen.
5. History → filters narrow; Summary → 7/14/30 → banner stays on flags,
   survives rotation and retry-after-failure, never has a close control.
6. Airplane-mode submit → offline error with retry, draft kept.
