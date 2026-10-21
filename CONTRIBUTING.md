# Contributing

Thanks for working on Caregiver Daily Notes. This is a four-person project and the
conventions below keep the work reviewable by everyone.

## Where to work

Each area of the codebase is owned by one person and tracked by that person's own
issues. Check the ownership table in [ARCHITECTURE.md](./ARCHITECTURE.md) before you
start — if you want to change an area you do not own, open an issue first and agree the
scope with the owner.

| Area | Owner | Issues |
|---|---|---|
| `android/`, `backend/.../ai/`, AI evaluation harness, integration & release | [Alaa](https://github.com/alaa157) | [#22](https://github.com/alaa157/caregiver-daily-notes/issues/22)–[#29](https://github.com/alaa157/caregiver-daily-notes/issues/29) |
| `backend/.../notes/` | [Ebrahim Ahmed](https://github.com/ebrahimahmedss47-commits) | [#30](https://github.com/alaa157/caregiver-daily-notes/issues/30)–[#34](https://github.com/alaa157/caregiver-daily-notes/issues/34) |
| `backend/.../auth/`, `recipient/`, `plans/`, `audit/` | [Mohamed Hemdan](https://github.com/mohamed-hemdan1) | [#8](https://github.com/alaa157/caregiver-daily-notes/issues/8)–[#12](https://github.com/alaa157/caregiver-daily-notes/issues/12) |
| `web/`, frontend E2E & accessibility | [Mahmoud Hewidy](https://github.com/hewidyy) | [#13](https://github.com/alaa157/caregiver-daily-notes/issues/13)–[#21](https://github.com/alaa157/caregiver-daily-notes/issues/21) |

## Working rules

- **One concern per PR.** Unrelated refactors do not belong in a feature PR.
- **Nobody merges their own PR.** Get one review from someone else.
- **Never commit secrets.** No API keys, no real `.env`, no keystores. The app's provider
  keys live encrypted in the user's Android Keystore and never reach our servers.
- **Notes are data, not instructions.** Any component that passes user text to a model
  must keep it delimited and out of the instruction position.
- **The deterministic safety layer comes first.** AI may raise urgency; it may never
  lower a signal, and it may never change a medication dose.
- **Say what you tested.** Note the exact commands in the PR body.

## Before you push

```bash
# Backend
mvn -f backend/pom.xml test

# Android — unit tests run on the JVM, no device needed
cd android && ./gradlew :app:testDebugUnitTest

# Android — Compose UI tests need a booted emulator or device
cd android && ./gradlew :app:connectedDebugAndroidTest
```

CI runs the same checks on every push and pull request: the Android workflow builds the
APK, runs the unit tests and lint in a single Gradle invocation, and the backend workflow
runs the Maven test suite with coverage.

## Style

- Java, Kotlin, TypeScript, Gradle, XML: 4 spaces, LF line endings. `.editorconfig` and
  `.gitattributes` enforce this — do not reformat a file you are not changing.
- Kotlin: follow the existing file conventions in `android/app/src/main/java/com/caregiver/mobile/`.
- Java: the backend uses the same 2-space Google-style formatting that is already in the tree.
- Keep public APIs documented with a short comment that says *why*, not *what*.
