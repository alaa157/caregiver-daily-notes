# DEF-002 — Web unit suite drift after UI contract changes

- **Severity:** Major (109 of 1,080 unit cases red across 19 files)
- **Component:** `web/src` test suites
- **Environment:** Vitest 2.1 + Testing Library, jsdom, Node 18+
- **Discovered:** 2026-11-19, full `vitest run` during the test-estate audit

## Expected vs actual

Full suite: **971 passed / 109 failed (1,080)**, 19 failed files — largest: `App.test.tsx` (28), `Summary.test.tsx` (17), `Overview.test.tsx` (17), `Recipients.test.tsx` (17). A 32-failure subset in `App`/`Settings`/`LandingRoute` was previously confirmed in an isolated run, ruling out load-induced flakiness as the primary cause.

Dominant patterns (all intentional app changes the tests never absorbed):

1. **Language toggle rename.** `AppShell` buttons now render `t('switchLanguage')` — accessible name `العربية` / `English`. Tests still query the old `t('switchToArabic')` names (`التغيير إلى العربية` / `Switch to Arabic`).
2. **BYOK dialog redesign.** Model selection moved from a free-text input to a catalog radio group plus an "other" custom-model input; a data-use acknowledgement checkbox gates the connection test; submit is guarded by `modelReady && !ackBlocked`. Tests type into a `Model` input that no longer exists, so the connection test never fires and `Connection test succeeded` never renders.
3. **Error-status mocks** aligned to the pre-#843 contract (400 for validation).

## Fix

Test-only updates to the current contracts (no app behavior changes), across 19 test files:

- Language-toggle queries updated to the `switchLanguage` accessible names (`العربية` / `English`).
- BYOK dialog tests rewritten for the catalog radio picker, custom-model input, data-use acknowledgement gate, and connection-test flow.
- Error-status mocks aligned with the unified contract (422 validation / 400 malformed).
- Remaining three: MTD pagination assertion given a load-tolerant timeout (passes isolated in 236 ms; flaked under full-suite parallel load); the medication-empty disclaimer scoped to `#recipient-medications` (the panel mounts twice in the detail view); plans nav assertion accepts both responsive nav variants (`Care plans` / `Plans`) and asserts exactly one carries `aria-current="page"`.
- Two temporary debug test files removed.

## Verification

Full suite (2026-11-19): **49 files, 1,086/1,086 passing, 0 failures** — up from 971/1,080 at discovery.

**Status: fixed.**
