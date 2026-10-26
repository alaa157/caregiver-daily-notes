# Task 7 — Care-plan generation, decisions, protected edits and history

The protected route is `/app/notes/plans?recipientId=<owned-uuid>`. This branch
combines Tasks 6 and 7 in #58 so their navigation/translations work together.
Merge/review order: companion backend integration, then the combined frontend.
It supersedes the separate Task 7 draft #59. Changes stay within the union of
the two issues' allowed frontend directories.

## Caregiver workflow

Select an owned recipient and review the dedicated active accepted-plan view,
pending suggestions and newest-first version history. Inactive recipients and
unknown/legacy formats remain reviewable. Saved content is exact escaped text
or the backend's versioned catalog/medication document; no clinical meaning is
calculated in the browser.

An explicit request can generate a suggestion from a selected 7/14/30-day period
and the last accepted plan. Returned safety stays outside plan content, with
its captured period shown, including provider-fallback responses. A generated
suggestion is never accepted automatically. Failed/uncertain writes require an
explicit read refresh before another decision and are never replayed silently.

Accept/dismiss require confirmation and use the server transitions. The active
plan and history are re-read after a confirmed write; the UI does not invent
versions, status changes or archived records. Mismatched active/history reads
require refresh rather than client-side lifecycle reconciliation.

A server-editable suggestion offers a parameter editor followed by a before/
after review and explicit confirmation. The payload contains only:

```json
{"expectedVersion":2,"changes":[{"index":0,"params":{"withinDays":"10"}}]}
```

Medication names/doses/schedules, action types, reasons, evidence, source metadata
and version numbers are not writable fields. The server retains them, validates
catalog parameters, rechecks the accepted baseline under lock, applies the
medication hard lock and creates a new accepted version. The original AI
suggestion remains a separate archived record. Opaque legacy records cannot
use this protected editor; their medication structure cannot safely be inferred
from prose. The UI follows the server's `editable` capability.

Server-provided `sourceType` distinguishes AI originals, caregiver edits and
caregiver-submitted content. Historical `UNSPECIFIED` authorship is labeled
unknown. A parent link opens/focuses its preserved version. Summary hashes and
opaque references are displayed as references, not invented URLs. Only UUID
note IDs become authenticated internal links.

Arabic/English, RTL/LTR, bidi isolation, keyboard focus/confirmation/cancellation,
responsive wrapping, loading/empty/error states, duplicate-submit guards,
read/generation timeouts and abandoned-response handling are supported. No plan
or editor data is saved to browser storage, logged or sent to an LLM by the
browser. Generation uses the existing authenticated backend transport.

## Backend contracts

| Operation | Endpoint |
| --- | --- |
| Active accepted plan | `GET /api/recipients/{id}/plans/active` |
| Saved versions, newest first | `GET /api/recipients/{id}/plans/history` |
| Generate from owned notes | `POST /api/plans/generate` |
| Accept/dismiss | Bodyless `POST /api/recipients/{id}/plans/{planId}/accept` or `/dismiss` |
| Protected edit | `POST /api/recipients/{id}/plans/{planId}/edit-and-accept` |

The companion backend adds `sourceType`, `structuredContent` and `editable` to
`PlanResponse`. Structured content is schema version 1: existing catalog
`changes` plus immutable `medications`. Generation accepts only recipient ID,
inclusive end date and a supported period; it returns a saved suggested plan
or explicit unavailable state plus authoritative safety. A source summary
reference is a SHA-256 provenance fingerprint, not a retrievable summary record.

Reads validate scoped identities, dates, unique versions, protected formats and
capabilities. Only the exact active-plan `404 {code:"NOT_FOUND"}` envelope means
no accepted plan. Missing routes/recipients and malformed responses remain
errors. No plan idempotency-key API is assumed.

## Verification

From `web/` after installing dependencies and Chromium:

```sh
npm test
npm run build
npx playwright test --config src/tests/plans/playwright.config.ts
```

The plan tests cover protected payload serialization, malformed data, source
metadata, exact text, medication read-only behavior, editor validation/review,
new-version/original distinction, accept/dismiss, duplicate clicks, conflicts,
unknown states, lost responses, stale reads, timeout, session rejection and
focus. Forty contract browser cases run in English/Arabic at 1280/768/390/320px
with axe WCAG 2/2.1 A/AA and overflow checks.

For the real backend, use a disposable database and the explicit loopback
provider fixture documented in Task 6's README. Then run:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/plans-live/playwright.config.ts
```

These four cases exercise real login/JWTs, provider HTTP transport, owned-note
summary/plan generation, protected editing, original preservation, accept/
archive, dismiss, externally changed suggestions, medication-field injection
rejection, cross-caregiver rejection and inactive read-only behavior. They use
synthetic records/provider responses and disable credential traces/screenshots/
video. The backend tests separately verify nonempty medication lists and
add/remove/dose/schedule tampering. No real external AI availability is claimed.

Existing foundation/auth, recipient, note and history suites remain regression
checks. `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` can select an existing browser.
Local H2 tests do not verify the owner's deployed PostgreSQL/proxy/CORS setup.
Owner review and merge are required; do not self-merge or close #19 manually.
