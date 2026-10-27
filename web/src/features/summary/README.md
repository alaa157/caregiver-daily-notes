# Task 6 — AI summary and authoritative safety web UI

The protected screen is `/app/notes/summary`. It requests a recipient's inclusive
7/14/30-day period explicitly and renders the backend summary, evidence,
uncertainty, deterministic safety and trends. It uses the existing tab-scoped
session and runtime API base URL.

## Contract and behavior

The companion backend integration supplies `POST /api/summaries` with the
existing `SummaryRequest` / `SummaryResult` wire contracts. The adapter selects
only the single compatible concrete JSON POST in the public `/v3/api-docs`
document. Documentation requests receive no bearer token. Generation receives
the existing authenticated transport and only these body fields:

```json
{"recipientId":"<owned-recipient-uuid>","periodEndInclusive":"2026-10-26","periodDays":7}
```

The proxy must expose `/api` and `/v3/api-docs`, or runtime config can point to
the backend origin with its owner-provisioned CORS. No secrets belong in web
config. External references, server overrides and templated/ambiguous paths
cannot redirect authentication. The backend loads owned notes and computes
signals; the browser sends no observation/safety data and performs no LLM calls.

- Arabic/English, mixed text, RTL/LTR and inactive recipient history are supported.
- Evidence/quotes are exact escaped text. Only UUID evidence IDs become internal
  authenticated note links; no citations are fabricated.
- Non-dismissible safety is rendered separately before model content. Invalid
  model output cannot remove valid flags. Failed/incomplete regeneration retains
  previously received warnings for the same captured request. Unknown severity
  stays visible with incomplete-safety feedback.
- The disclaimer remains visible. Unclear/conflicting information stays marked
  as uncertainty; no diagnosis, medical inference or clinical trend calculation
  is performed in the browser.
- Generation, cancellation, timeout, unavailable, provider fallback, malformed
  output and regeneration states are explicit. In-flight duplicate requests and
  automatic retries are prevented; abandoned responses are ignored.
- Completion/error/cancel focus and responsive navigation are keyboard safe.
  Result recipient/period context is captured independently from current controls.
- No note/summary/draft data is stored in browser storage or logged.

The backend gives each provider operation a bounded 40-second budget and retains
authoritative safety on provider failure. This is inside the UI's 120-second
request timeout. The integration is ready for owner review with its companion
backend change; deploying/merging that prerequisite is part of owner integration.

## Verification

From `web/`:

```sh
npm ci
npm test
npm run build
npx playwright test --config src/tests/summary/playwright.config.ts
```

The summary unit/component/API tests cover validation, transport/discovery,
quotes, uncertainty, security, safety retention, cancellation and timeout. The
32 contract browser cases cover English/Arabic at 1280/768/390/320px, axe WCAG
2/2.1 A/AA, keyboard focus and overflow.

For real-backend checks, use Java 21, the companion backend and a disposable H2
or PostgreSQL database. Existing disposable startup instructions are in
`web/src/tests/integration/README.md`. For deterministic provider transport:

```sh
python web/src/tests/summary-live/synthetic_provider.py
```

Configure the local backend with `llm.base-url=http://127.0.0.1:8099`, a synthetic
key, no backup key and one attempt. Then, from `web/`:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/summary-live/playwright.config.ts
```

These four cases use real login/JWTs, owned stored notes, real summary HTTP calls,
Java provider parsing/validation, source evidence and safety. They verify all
three periods and a genuine provider HTTP failure with safety retained. The
provider output is an explicit synthetic fixture; no paid/external AI request
is made. Traces/screenshots/video are disabled. Existing recipient/note smoke
assertions were updated to compare current detail DTOs and linked addenda while
still checking every original field exactly.

A browser executable can be supplied with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Local H2/transport tests do not verify the
owner's shared PostgreSQL deployment, CORS/proxy routing or provider availability.
