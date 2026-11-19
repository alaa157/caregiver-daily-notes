# DEF-003 — Backend suites stale after the unified error contract

- **Severity:** Major (24 failing tests masked real signal; docs contradicted the API)
- **Component:** backend test suites + `docs/api/daily-notes.md`
- **Environment:** OpenJDK 21, H2 integration contexts
- **Discovered:** 2026-11-19, first full suite run after DEF-001 unblocked the integration layer

## Expected vs actual

The Nov 17 refactor `7bb1f11c` ("unify the error status contract", #843) moved bean/domain validation to **422** and kept malformed/unreadable requests at **400**; `SummaryService` moved from throwing on ungrounded citations to per-item evidence screening with an `INVALID_OUTPUT` degradation state. Tests and API docs still encoded the old contract:

- 21 status expectations inverted (e.g. invalid plan parameter values expected 400, API returns 422; protected-field injection at the HTTP boundary expected 422, API returns 400).
- Parameterized intake test: unknown enum value and malformed timestamp are deserialization failures (400); a missing required field is bean validation (422) — one shared expectation cannot cover all three.
- Two summary-window tests expected `InvalidModelOutputException`; the service now drops ungrounded observations and reports `droppedObservationCount` + `aiUnavailable=true` / `INVALID_OUTPUT` when nothing survives.
- Token-bucket test expected two tokens at t=60s after consuming one at t=30s; continuous refill (capacity 2 / 60s) accrues exactly one token per 30s — the implementation matches its javadoc, the expectation did not.
- Plan evidence assertion compared against a stale literal `"outer-note"` instead of the created note's id.

## Fix

Commit `8326f6ab`: expectations aligned per cause (422 validation / 400 malformed), screening-contract assertions, corrected refill math, real note id; `docs/api/daily-notes.md` now documents 422 `VALIDATION_ERROR` and 422 `PROTECTED_PLAN_FIELDS`.

## Verification

Full suite 700/700 green (run 7, 2026-11-19). No production code changed by this fix — the API behavior was the intended contract.

**Status: fixed.**
