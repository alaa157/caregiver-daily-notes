# DEF-011 — Session DataStore has no singleton registry: every second graph in a process crashes

- **Severity:** Major (instrumentation estate unusable: 136 of 180 emulator tests failed at setup; any production code path creating a second SessionStore would crash the same way)
- **Component:** `android/.../data/accounts/SessionStore.kt`
- **Environment:** API-34 emulator, first full `connectedDebugAndroidTest` run (CI run 44569896289)
- **Discovered:** 2026-11-19

## Symptom

```
java.lang.IllegalStateException: There are multiple DataStores active for the same file:
/data/user/0/com.caregiver.mobile/files/datastore/session.preferences_pb
```

136 failures across 23 instrumentation classes — every test after the first that constructs an `AppGraph` (instrumentation runs all tests in one process).

## Root cause

`SettingsStore` already solves this with `SettingsStoreRegistry` (one active instance per backing file, documented invariant). `SessionStore.create` never got the same treatment: each call built a fresh `PreferenceDataStoreFactory.create` over the same `session` file. DataStore forbids two active instances per file.

## Fix

`SessionStoreRegistry`, mirroring the settings registry but keyed by the resolved file's **absolute path**: one shared instance per file on a device (all graphs share one filesDir), while Robolectric's per-test filesDir still yields isolated stores per JVM test. `SessionStore.create` delegates to it.

## Verification

- New JVM regression `AppGraphTest.secondGraphInTheSameProcessReusesTheSessionDataStore` (two graphs, one process) — green; `AppGraphTest` 13/13.
- Full JVM suite re-run green; emulator re-run dispatched (see execution report).

**Status: fixed.**
