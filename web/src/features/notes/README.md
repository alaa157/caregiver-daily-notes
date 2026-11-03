# Caregiver daily-note workflow

Signed-in caregivers can open **Daily notes** in the navigation or create a
note from the workspace overview and recipient detail.

| Route | Behavior |
| --- | --- |
| `/app/notes/new?recipientId=<UUID>&date=YYYY-MM-DD` | Choose an active owned recipient/date, record observations, or open the existing note for that date. Valid query values prefill the form. |
| `/app/notes/:id` | Read the immutable original, typed correction timeline, effective values, and compatible legacy addenda. |
| `/app/notes/:id/addenda/new` | Append a supported correction through the existing note workflow. |

## API and domain contract

The source of truth is the current backend contract in
[`docs/api/daily-notes.md`](../../../../docs/api/daily-notes.md):

- `POST /api/notes` accepts a recipient UUID, calendar date, and canonical
  observation states. `medicationVerification` is independent of observations.
- `GET /api/notes/{id}` and
  `GET /api/notes/by-recipient/{recipientId}?date=YYYY-MM-DD` read saved notes.
- `GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&recipientId=<optional>&page=0&size=20`
  supplies stable, date-filtered history pages (maximum size 100).
- `POST` and `GET /api/notes/{id}/corrections` append/read immutable typed
  events, with explicit `SET`, `CLEAR`, `UNKNOWN`, and `NOT_RECORDED`
  operations and deterministic effective-value replay.
- Existing text addenda remain readable and are not reclassified as structured
  correction events. Original note columns are never overwritten by corrections.
- Note and correction writes retain backend idempotency behavior. Ownership is
  derived from the authenticated caregiver; inaccessible resources are not
  exposed through frontend URL checks alone.
- Inactive recipients can be read historically but cannot receive ordinary new
  notes; the backend enforces this restriction. Issue #69's reversible archive
  contract is still open and is not modeled by this feature.

The Web adapter validates response shapes and sends only supported fields.
Caregiver identity and observation-derived clinical conclusions are never
submitted by the browser.

## Observation and correction display

The editor preserves `RECORDED`, `UNKNOWN`, `NOT_RECORDED`, and
`NOT_APPLICABLE` distinctly. Legacy `LEGACY_AMBIGUOUS` observations remain
historical and visibly uncertain. Typed values are displayed only for
`RECORDED`; false, zero, and empty recorded text are not treated as missing.
Medication verification remains an independent verification value and is never
inferred from the medication observation.

The note detail shows the original record separately from subsequent events,
with event actor/time/sequence where supplied by the API. Corrections are
rendered in backend order; effective values use the backend projection and
retain their source operation. Legacy addenda remain visible as separate
historical records. No correction calculation, clinical threshold, or trend is
invented in React.

## Field handling

Observation fields retain their established text, numeric, boolean, and
medication types. Pain remains an integer from 0 through 10; false and zero
are valid recorded values. Free text is rendered as text with preserved
whitespace and bidirectional isolation, not as HTML. Arabic, English, mixed
content, diacritics, and line breaks are preserved.

No field is inferred from another. The UI does not calculate combined clinical
interpretations, AI output, or medical recommendations. Supported state/value
rules and correction operation semantics are defined by the backend API
contract, not duplicated in React.

## Save state and response loss

The form generates one cryptographically random key per valid submission and
captures an immutable payload snapshot. In-flight duplicate submissions are
blocked synchronously. Explicit validation/access failures keep entered values
editable. On a network/server error or unreadable success response, a write may
already have committed: inputs lock and **Retry the same save** reuses the same
payload and key. Addendum idempotency conflicts retain that attempt too.

Caregivers can instead open the note for its recipient/date or review saved
addenda. No automatic second POST with a fresh key is issued. The cancel link
and page-unload warning explain that a save might already have completed.
Abandoned requests are aborted and late results are ignored; aborting cannot
undo a server-accepted write.

The retry snapshot/key lives only in the mounted form's memory. Navigating away,
reloading or closing the tab loses it; inspect the saved original/addenda before
resubmitting after leaving an uncertain save. Cross-tab/reload deduplication of
originals remains backed by the backend recipient/date constraint. No claim of
cross-page addendum deduplication is made. Notes and drafts are never persisted
to browser storage, URLs or logs. Session handling remains in the auth provider.

## Tests

From `web/`, with dependencies and Playwright Chromium installed:

```sh
npm test
npm run build
npm run test:e2e
npx playwright test --config src/tests/recipients/playwright.config.ts
npx playwright test --config src/tests/notes/playwright.config.ts
```

The new component/API tests cover required metadata, boundaries, exact payloads,
null/unknown/false/zero distinctions, immutable originals, selected corrections,
loading/empty/errors/conflicts, safe response-loss replay, double clicks,
cancellation, protected routes and Arabic/RTL. The new browser suite runs both
languages at desktop/tablet/mobile/320px, checking flows, focus, horizontal
overflow and axe WCAG 2.1 A/AA. Existing foundation/auth/recipient browser suites
remain separate regression checks. Screenshots use synthetic data only.

### Opt-in real backend checks

Start the unchanged backend with Java 21 and Maven and a **disposable local
synthetic database**. One option uses its existing H2 test dependency and real
Flyway migrations, without changing backend files:

```sh
JWT_SECRET=disposable-notes-test-secret-at-least-32-bytes \
SPRING_DATASOURCE_URL='jdbc:h2:mem:notesweb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATASOURCE_USERNAME=sa \
SPRING_DATASOURCE_PASSWORD='' \
SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT=false \
mvn -f backend/pom.xml test-compile spring-boot:run \
  -Dspring-boot.run.useTestClasspath=true
```

This public signing value is for disposable tests only. Shared deployments use
an environment-provided private secret. Then, from `web/`:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/notes-live/playwright.config.ts
```

The configuration accepts loopback origins only. Four Arabic/English cases at
desktop/320px create synthetic caregivers/recipients/notes/addenda. They
deliberately drop the first note and addendum responses **after** the real
backend commits, then verify replay returns the same IDs and leaves exactly one
original and one addendum. They also verify exact mixed text, unchanged original
content, selected fields only, cross-caregiver rejection and date conflicts.
The suite leaves its synthetic records in the disposable database until that
database is discarded. Traces/video/screenshots are disabled for these checks.

All API responses in this suite come from the real Spring Boot server, bridged
through Playwright to model the documented same-origin `/api` proxy. This does
not test the owner's deployed proxy/CORS or PostgreSQL configuration. Shared
environment smoke checks still require its current URL and a synthetic account.

## Integration and scope

The existing App has the minimal protected-route mount, dashboard link and
availability label needed for the feature. AppShell owns navigation/document
titles; its authenticated links wrap on narrow screens. Feature code, API,
translations, components, routes and tests otherwise stay in the designated
frontend directories. No backend, mobile, workflows or infrastructure changes
are included. Task 4 is based on main and does not depend on unmerged PR #53.
