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

## Fix in progress

Test-only updates to the current contracts (no app behavior changes). Shared helpers fixed once where they are the root cause. Final totals and changed-file list will be appended here when the suite is green.

**Status: in progress.**
