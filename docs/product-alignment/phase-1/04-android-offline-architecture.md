# Android offline-first architecture verification

**Reference:** `feat/standalone-byok-ai` at `21876285ad808a6518593ea3974c51b6b1f6bb31`.

## Startup and build selection

`CaregiverRoot` reads onboarding settings, then checks the build's `LOCAL_MODE`: when true, it goes directly to `MainScaffold` and does not read the auth token or show login/register/server URL. `AppGraph` selects `LocalBackendApis` and creates Room when local mode is true. See [`CaregiverRoot.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/CaregiverRoot.kt#L18-L73) and [`CaregiverApp.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt#L84-L175).

| Build type | `DEMO_BACKEND` | `LOCAL_MODE` | Selected source from code |
|---|---:|---:|---|
| debug | true | true | Room-backed `LocalBackendApis` |
| minified | true | true | Room-backed `LocalBackendApis` |
| release | false | true | Room-backed `LocalBackendApis` |

`LOCAL_MODE` takes precedence in `AppGraph`; therefore no checked-in build variant selects the Retrofit-backed graph or schedules the outbox worker. `forceProduction` exists on `AppGraph` for test use. `DEMO_BACKEND` is not proof that shipped builds use the in-memory demo provider. [`build.gradle.kts`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/build.gradle.kts#L52-L87), [`CaregiverApp.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt#L89-L129).

## Capability matrix (source-level)

| Capability | Offline status | Evidence / qualification |
|---|---|---|
| Startup, navigation, settings, language/theme | Fully local | Settings and application composition use local preference stores. |
| Recipient, notes, addenda, history | Fully local | `LocalBackendApis` uses Room; normal release does not need the caregiver API. |
| Medication tracking and plan persistence | Fully local | Local tables and APIs. Plan generation may still need an AI provider. |
| Summary safety and no-key fallback | Fully local | Local signals and safety are computed from Room; no model narrative is produced without AI. |
| BYOK summary/plan generation | Local with optional network functionality | Direct LLM HTTP calls use a configured key; per-provider consent is checked before summary generation. |
| CSV import/export and full backup/restore | Fully local in checked-in release | Release graph uses Room store. A server/API graph's CSV store can use network; backup is disabled when there is no authoritative local store. |
| PDF export/share | Local generation; optional external app for sharing | PDF creation uses local summary/note data; share sheet is user initiated. |
| Public update check | Requires internet | Reads GitHub releases; no caregiver data is sent. |
| Login/register/server URL | Not part of release local path | These screens exist only when `LOCAL_MODE=false`; no checked-in variant currently does this. |
| Room cache, note outbox and server sync | Unknown/not verified for a shipped build | Code and tests exist, but no current build flag selects the graph. The upload DTO is also incompatible with current main backend (see document 7). |

This is source evidence, not a claim that every device/platform state has been runtime-tested offline. Android has tests for local UI, repository cache fallback, outbox behavior, Room migrations, and local/demo flows. The test sources were inspected but not run here.

## Transfer safety and data ownership

The checked-in release's Room database is its source of truth. Backup restore replaces local restorable data only after review and a stale-state check, in one transaction. It does not touch network, tokens, AI keys, settings or outbox; local deletion is an explicit confirmation flow. These are useful guardrails, but they do not implement user-selected mobile-to-server transfer. Any future transfer must keep Room authoritative and must not silently overwrite/delete local records.

## Internet destinations

1. User-selected LLM provider after consent (note/summary data leaves device).
2. GitHub Releases update check.
3. Optional server/Retrofit path only if a future supported build selects non-local mode.

No backend request is needed for normal release startup, local note entry, local history, local backup/restore, or local medication tracking, based on the selected graph in source.
