# Web integration handoff — Member 4, Task 9 (#21)

The web application integrates caregiver sign-in, owned recipients, immutable
daily notes and addenda, filtered history, summaries with server-owned safety,
and versioned care plans. Backend contracts were checked against main commit
`73163f0e0feb0f0ef7c154d17e1b2eb1f9247885`. All Task 9 changes stay in `web/`.

## Run the complete checks

Use Node.js with npm, install the pinned dependencies and Chromium, then run:

```sh
cd web
npm ci
npx playwright install chromium
npm test -- --maxWorkers=2 --minWorkers=1
npm run test:e2e
```

The last command type-checks the entire source/test tree, builds production
assets, and runs the 224 contract browser cases, including axe WCAG 2/2.1 A/AA,
keyboard, RTL/bidi and 1280/768/390/320px checks in both languages. See the
[coverage map](e2e/README.md). `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` can select
an existing Chromium installation without changing project dependencies.

## Real-backend integration

`npm run test:e2e:live` builds the app and runs all six live suites together:
authentication/recipients, notes/addenda, history, summaries/safety, care plans,
and the complete caregiver journey. There are 24 cases in English and Arabic
at desktop and 320px widths. The backend target is restricted to loopback.

These tests create synthetic accounts and records. Use an isolated disposable
database, never the owner's shared data. H2 is an existing backend test
dependency; no backend source changes are needed. Use Java 21 and Maven.

From the repository root, start the deterministic provider in one terminal:

```sh
python web/src/tests/summary-live/synthetic_provider.py
```

Start the real Spring Boot app in a second terminal:

```sh
JWT_SECRET=disposable-local-smoke-secret-at-least-32-bytes \
SPRING_DATASOURCE_URL='jdbc:h2:mem:webhandoff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATASOURCE_USERNAME=sa \
SPRING_DATASOURCE_PASSWORD='' \
SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT=false \
LLM_BASE_URL=http://127.0.0.1:8099 \
LLM_API_KEY= \
LLM_BACKUP_API_KEY= \
LLM_MAX_ATTEMPTS=1 \
GEMINI_API_KEY= \
OPENROUTER_API_KEY= \
mvn -f backend/pom.xml test-compile spring-boot:run \
  -Dspring-boot.run.useTestClasspath=true
```

The displayed signing value is public and disposable-test-only. Provider
fixtures return synthetic data over real HTTP; no external AI is contacted.
Once the backend is listening, run from `web/`:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 npm run test:e2e:live
```

The new journey performs UI login, recipient creation/editing, note creation,
separate correction, language switching, recipient/date filtering, summary
generation and evidence navigation, plan generation and explicit acceptance,
reload, recipient deactivation and logout. It checks original text preservation,
saved plan history, one write per note/addendum, no medical content/passwords in
browser storage, persistent disclaimers, authoritative HIGH safety, axe and
layout. Existing live suites additionally cover rejected credentials,
cross-caregiver access, lost-response idempotent replay, pagination, all summary
periods, provider failure, protected parameter edits, version preservation,
accept/archive/dismiss and stale decisions.

The browser transport forwards actual requests/responses to the local backend,
including `/v3/api-docs`; it does not supply fake successful DTOs or JWTs.
Traces, screenshots and video are disabled for live tests. Records disappear
when the disposable in-memory backend stops. They are not deleted through
product APIs.

## API contract map

| Feature / operation | Backend contract consumed by the browser |
| --- | --- |
| Sign in | `POST /api/auth/login` with `email`, `password`; reads `{token}` |
| Recipient list / details | `GET /api/recipients` and `GET /api/recipients/{id}`; owner fields are never sent |
| Recipient create / update | `POST /api/recipients`, `PUT /api/recipients/{id}` with `name`, nullable `relationship` |
| Deactivate recipient | Bodyless `PATCH /api/recipients/{id}/deactivate`; historical data remains readable |
| Original daily note | `POST /api/notes` with `recipientId`, ISO `date` and reported observation fields; `Idempotency-Key` supports retry |
| Note details / date lookup | `GET /api/notes/{id}`, `GET /api/notes/by-recipient/{recipientId}?date=...`; original fields are read-only |
| Addenda | `GET` / `POST /api/notes/{id}/addenda`; writes send only selected corrections and a separate `Idempotency-Key` |
| History | `GET /api/notes?from=...&to=...&page=...&size=...` plus optional `recipientId`; reads Spring `Page` fields |
| Summary discovery | Public `GET /v3/api-docs` without bearer; requires one concrete compatible `SummaryRequest` / `SummaryResult` JSON POST |
| Summary generation | Currently `POST /api/summaries` with `recipientId`, `periodEndInclusive`, `periodDays` (7, 14, 30); reads independent `summary`, `safety`, `trends` |
| Generate saved suggestion | `POST /api/plans/generate` with the same three request fields; reads `plan`, `aiUnavailable`, authoritative `safety` |
| Active plan / history | `GET /api/recipients/{id}/plans/active` and `/history`; only active-plan `404 {code:"NOT_FOUND"}` means no accepted plan |
| Accept / dismiss | Bodyless `POST /api/recipients/{id}/plans/{planId}/accept` or `/dismiss`; committed active/history snapshots are re-read |
| Protected edit / acceptance | `POST /api/recipients/{id}/plans/{planId}/edit-and-accept` with `expectedVersion`, indexed `changes[].params`; no medication or ownership fields |

DTO fields, validation and failure behavior remain documented alongside
[auth](src/features/auth/README.md), [recipients](src/features/recipients/README.md),
[notes](src/features/notes/README.md), [history](src/features/history/README.md),
[summaries](src/features/summary/README.md) and [plans](src/features/plans/README.md).
No registration, refresh, logout, summary-persistence or note-editing endpoint is
invented by the UI. Account provisioning remains outside this frontend scope.

## States, safety and production assets

Each major data screen has explicit loading, empty, unavailable/error and retry
feedback. Form values survive recoverable failures; uncertain note writes keep
the captured payload/key. Uncertain plan decisions require an explicit read
refresh. Existing browser tests exercise these states, cancellation, validation,
timeouts, keyboard focus and expired sessions.

The app footer always carries the translated care-support disclaimer. Summary
and plan safety is rendered separately from generated content and cannot be
dismissed. Provider failure or malformed content cannot clear received warnings
for the captured request. Clinical rules, ownership, JWT verification and
medication hard locks execute on the server, never in the browser.

Production code imports no synthetic test records, mock handlers or fake login
behavior. Test fixtures remain in test-only paths. The obsolete unused
"Coming soon" translations were removed. Build assets and public runtime config
contain no provider credentials or signing secrets. The only public configuration
field is `apiBaseUrl`; fonts are served locally. A user's runtime JWT lives in
memory and tab-scoped sessionStorage, as documented in the auth session model.

## Owner integration points

Validated locally on 2026-11-01 with Chromium 153.0.8010.0 and Java 21:
296 unit/component tests, 224 default browser cases and all 24 real-backend
cases passed. TypeScript, the production build, documentation links and
`git diff --check` passed. The production source/public assets/build scan found
no credential-pattern hits or test-fixture imports.

1. Publish `dist/` with SPA fallback to `index.html` for `/app/*` deep links.
2. Serve `config.json` without permanent caching. Its single `apiBaseUrl` can be
   changed after build with `npm run config:runtime`; sessions are scoped to
   that backend. Never add keys or credentials to public configuration.
3. For the same-origin setup (`apiBaseUrl: ""`), proxy both `/api/*` and
   `/v3/api-docs` to the current backend. Returning the SPA HTML for API/docs
   requests will fail contract validation. An optional `/api` suffix is joined
   once by the client; docs remain a sibling path.
4. For a separate HTTPS backend origin, the owner must provision browser CORS
   for the web origin, JSON, Authorization and Idempotency-Key. The current
   backend does not provision CORS. Rotating the tunnel URL alone is insufficient.
5. Verify that shared PostgreSQL migrations are applied, a synthetic caregiver
   can log in, and provider configuration is available server-side. No deployment,
   shared infrastructure or repository settings are changed by this PR.

Local integration validates the real application, JWT chain, Flyway and H2 with
synthetic provider transport. It does not certify the owner's hosted proxy/CORS,
PostgreSQL concurrency or current external provider availability. Repeat the
caregiver smoke journey against that environment during owner deployment review.

Submit for `alaa157` review and merge. Task 9 closes through owner merge of the
linked PR; this handoff does not self-merge or manually close the assignment.
