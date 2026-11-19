# DEF-004 — Typed corrections return 500 for notes with never-recorded fields

- **Severity:** Major (core workflow broken for a common note shape)
- **Component:** `backend/.../notes/ObservationColumns.java`, `NoteCorrectionService`
- **Environment:** Spring Boot integration tests (H2), reproducible against any database
- **Discovered:** 2026-11-19, `NoteControllerTest.structuredCorrectionsAreTypedAppendOnlyAndIdempotent`

## Repro

1. Create a daily note reporting only some observations (e.g. mood/appetite/sleep/mobility).
2. `POST /api/notes/{id}/corrections` with a typed batch (SET/UNKNOWN/CLEAR/NOT_RECORDED).
3. Actual: **500 `SERVER_ERROR`**. Expected: 201 with the appended events and replayed effective observations.

## Evidence

Temporary stack logging in the catch-all handler (reverted after diagnosis) showed two successive `NullPointerException`s from `Objects.requireNonNull` inside `NoteObservations`:

```
NullPointerException: state
  at Observation.<init> → ObservationColumns.observation → observations()
  → EffectiveObservationCalculator.replayWithHistory → NoteCorrectionService.response
NullPointerException: medicationVerification
  at NoteObservations.<init> → ObservationColumns.observations (same path)
```

Root cause: columns for never-recorded fields are null in the row. The read path (`display()`) tolerates null, but `observations()` — used only by the correction replay — passed null straight into records that require non-null. The JPA mapping marks the verification column `nullable=false` while the migration allows null, so `ddl-auto: validate` never caught the mismatch.

## Fix

Commit `8b3df8fa`: null observation state maps to `NOT_RECORDED`; null medication verification maps to `NOT_ASSERTED` — the same canonical defaults as `NoteObservations.notRecorded()`.

## Verification

`NoteControllerTest` 18/18 green, including the typed-batch idempotent-replay test; full suite 700/700.

**Status: fixed.**
