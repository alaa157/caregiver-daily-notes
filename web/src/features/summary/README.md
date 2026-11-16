# Task 6 — AI summary and authoritative safety web UI

The protected screen is `/app/notes/summary`. It requests a recipient's inclusive
7/14/30-day period explicitly and renders the backend summary, evidence,
uncertainty, deterministic safety and trends. It uses the existing tab-scoped
session and runtime API base URL.

## Contract and behavior

The backend exposes `POST /api/summaries/byok`. Generation is impossible without
caregiver-configured BYOK credentials (AI settings dialog); the page shows
`summaryByokMissingCredentials` instead of calling the API. The request body is
exactly:

```json
{"summary":{"recipientId":"<owned-recipient-uuid>","periodEndInclusive":"2026-10-26","periodDays":7},"credentials":{"providerId":"GEMINI","model":"<model-id>","apiKey":"<key>"}}
```

The runtime config can point to the backend origin with its owner-provisioned
CORS. No secrets belong in web config. The backend loads owned notes and
computes signals; the browser sends no observation/safety data and performs no
LLM calls itself — the backend makes the provider call with the supplied
credentials.

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

Live BYOK suites (`src/tests/summary-live/`) need `CAREGIVER_TEST_API_URL` plus
`CAREGIVER_E2E_BYOK_PROVIDER`/`_MODEL`/`_KEY`; see `web/HANDOFF.md`.

The summary unit/component/API tests cover validation, transport, quotes,
uncertainty, security, safety retention, cancellation and timeout. The
32 contract browser cases cover English/Arabic at 1280/768/390/320px, axe WCAG
2/2.1 A/AA, keyboard focus and overflow.

For real-backend checks, use Java 21, the companion backend and a disposable H2
or PostgreSQL database. The backend needs no provider configuration: summary
calls carry per-request BYOK credentials. Live cases exercise a real provider,
so supply a disposable account's key via environment (never committed):

```sh
export CAREGIVER_E2E_BYOK_PROVIDER=OPENAI   # OPENAI | GEMINI | XAI | OPEN_ROUTER
export CAREGIVER_E2E_BYOK_MODEL=<model id>
export CAREGIVER_E2E_BYOK_KEY=<api key>
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/summary-live/playwright.config.ts
```

These cases use real login/JWTs, owned stored notes, real summary HTTP calls,
Java provider parsing/validation, source evidence and safety across all three
periods. Without the `CAREGIVER_E2E_BYOK_*` variables the live suite skips.
Traces/screenshots/video are disabled.

A browser executable can be supplied with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Local H2/transport tests do not verify the
owner's shared PostgreSQL deployment, CORS/proxy routing or provider availability.
