# Task 6: AI summary and safety web interface

Protected route: `/app/notes/summary`, linked from authenticated navigation.
The screen supports on-demand 7/14/30-day requests with an inclusive end date,
inactive recipients, backend evidence and note links, explicit uncertainty,
backend trend counts, English/Arabic, and isolated mixed-direction text.

## Backend prerequisite: not yet exposed on main

Rechecked against `main` at `20d3e73` (2026-10-25):
`backend/.../ai/SummaryService.java` implements the service and
`SummaryRequest`/`SummaryResult` define its Java contracts. There is
**no summary HTTP controller** or corresponding OpenAPI operation. Issue #24's
completion comment also describes a service rather than an HTTP endpoint.

Consequently this frontend must not be called end-to-end complete yet. The
current backend yields the understandable unavailable state and no guessed POST
request. Success/fallback tests use explicit synthetic contract fixtures; they
do not establish that a deployed AI service works.

Before this task can be accepted, the backend owner needs to expose and document
an authenticated, caregiver-scoped summary operation that loads owned notes and
signals, invokes `SummaryService` on the server, and returns authoritative safety
even when model generation fails. Verify unauthorized/cross-caregiver requests,
inclusive periods, evidence grounding, fallback, and a complete browser request
against that real operation. Provider credentials stay on the server.

## Transport and discovery

The request body matches the existing Java record exactly:

```json
{"recipientId":"<owned recipient UUID>","periodEndInclusive":"2026-10-24","periodDays":7}
```

The response matches `SummaryResult`: `summary` (observations, uncertainties,
aiUnavailable), `safety` (signals, needsDoctorBanner), and `trends`.
`SummaryObservation` supplies text, noteId, and quote; `SummaryUncertainty`
supplies topic and detail. The browser does not fabricate or revalidate medical
evidence, calculate safety rules, infer medication states, or call an LLM.

Since no HTTP path has been agreed, `api/summary.ts` uses the backend's public
`/v3/api-docs` (enabled by its existing Springdoc/SecurityConfig). It accepts
exactly one concrete `/api/...` POST with local schema references to
`SummaryRequest` and `SummaryResult` and the expected fields. Foreign server
URLs, external references, parameterized paths, and ambiguous operations are
ignored/rejected. Only those three request fields are serialized.

Generation uses the existing authenticated API transport and its central 401
handling. Public documentation does not receive a bearer token. An `/api`
suffix in the runtime base is removed only for the public documentation read.
The owner's proxy must expose `/v3/api-docs` as well as `/api`; existing proxy,
backend, public config, and infrastructure files are not changed here. If the
owner chooses a different wire contract, this adapter must be updated together
with its tests before enabling the feature.

## Safety and lifecycle behavior

- The disclaimer is present before loading and through every outcome.
- Backend safety is parsed separately and rendered before model text, without a
  dismiss/collapse control. Model text cannot inject markup, styles or links.
- All rule IDs remain visible verbatim. Unknown or malformed severities show a
  prominent incomplete safety notice. Missing/malformed safety is never an all-clear.
- Same-request warnings survive failed/cancelled/timed-out regeneration and
  malformed replacement safety. A complete new backend safety evaluation may
  replace an older one; the browser does not overrule the backend.
- Form edits do not relabel an older result. Results retain a captured recipient
  and exact period. Different recipients do not inherit each other's flags.
- Backend `aiUnavailable` suppresses generated observations, retaining available
  safety, uncertainty and trend information. Missing or malformed content is
  identified explicitly, without fabricated fallback prose.
- Only UUID-shaped evidence IDs become internal note links. Raw quotes/text are
  escaped React text, not HTML/Markdown, and preserve whitespace and diacritics.
- Requests start only on submit; there are no automatic retries or generations.
  In-flight duplicates are blocked. Cancel, navigation and the 120-second UI
  timeout abort reads and ignore late responses, even if a transport ignores
  abort. Cancelling does not promise cancellation of an already-started server
  generation. A subsequent submit starts a new generation request.
- Keyboard focus moves to the rendered result or status after completion,
  failure, timeout or cancellation; editing controls does not move focus.
- Summary text, quotes and drafts are not stored in browser storage or logs.
- Workspace navigation stops being sticky below 1100px so it cannot cover
  safety information and keyboard targets when links wrap.

## Verification

Verified on 2026-10-25 after updating the branch onto `main` at `20d3e73`:

| Suite | Passing cases |
| --- | ---: |
| Unit/component/API (including 59 summary cases) | 234 |
| Summary browser, both languages and four widths | 32 |
| Existing foundation/authentication browser | 48 |
| Existing recipients browser | 32 |
| Existing daily-note browser | 32 |
| Existing history browser | 32 |
| Total distinct cases | 410 |

TypeScript, production build, and `git diff --check` also pass. Browser cases
use intercepted contract fixtures, including safety and failure responses; none
prove a successful real-backend summary or shared deployment integration.

From `web/`:

```sh
npm ci
npm test
npm run build
npx playwright test --config src/tests/summary/playwright.config.ts
npx playwright test
npx playwright test --config src/tests/recipients/playwright.config.ts
npx playwright test --config src/tests/notes/playwright.config.ts
npx playwright test --config src/tests/history/playwright.config.ts
```

The summary browser suite exercises English/Arabic at 1280, 768, 390 and 320px,
keyboard submission, all periods, unsafe text/evidence, malformed output,
unknown urgency, unavailable/fallback/error states, cancellation, no duplicate
requests, overflow, and axe WCAG 2/2.1 A/AA checks. Select an available browser
with `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` when needed. This environment uses
Chromium 133. No network requests to an LLM are made by any tests.

No backend, mobile, dependency, workflow, or deployment files change. Keep issue
#18 open and the PR draft until the backend prerequisite and real integration
verification are complete.
