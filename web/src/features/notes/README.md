# Daily notes and append-only corrections — Mahmoud Hewidy (#16)

Signed-in caregivers can open **Daily notes** in the navigation or **Record a
daily note** on the dashboard.

| Route | Behavior |
| --- | --- |
| `/app/notes/new` | Choose an owned recipient/date, record observations, or open the existing note for that date. |
| `/app/notes/:id` | Read the immutable original and its chronological addenda. |
| `/app/notes/:id/addenda/new` | Select specific fields and append replacement observations. |

## Existing backend contract

Source of truth is the checked-in `NoteController`, `NoteRequest`,
`AddendumRequest`, `DailyNoteService`, response DTOs and
`docs/api/daily-notes.md` at main commit `a177ea4`.

- `POST /api/notes`: recipientId/date required; mood, appetite, sleep, mobility,
  medicationStatus, medicationVerified, painScore, fallReported, freeText optional.
- `GET /api/notes/{id}` and
  `GET /api/notes/by-recipient/{recipientId}?date=YYYY-MM-DD` read originals.
- `POST /api/notes/{id}/addenda` appends selected replacement fields;
  `GET /api/notes/{id}/addenda` reads chronological addenda.
- Note and addendum writes send `Idempotency-Key`. Identical replays return the
  original record; different payload/key conflicts and duplicate recipient/date
  notes return 409. No PUT/PATCH/DELETE endpoint is used for notes.
- `GET /api/recipients` provides owned choices, including inactive recipients.
  Inactive recipients can have existing notes opened; new notes require active
  recipients. The backend permits addenda to existing inactive-recipient notes.
- Only documented writable fields are serialized. Caregiver identity comes from
  the existing bearer session and backend authorization; forms accept no owner ID.
  Response fields are validated/projected, and raw error diagnostics are hidden.

## Observations and corrections

The four descriptive observation fields accept reported text up to 40 UTF-16
code units, matching Java validation; they are not invented medical enums.
Medication uses the backend's exact `TAKEN`, `MISSED`, `UNKNOWN` values, plus
null for not recorded. Medication verification and fall reports are independently
nullable booleans. Pain is an optional integer 0–10; zero and false are preserved.
No field is inferred from another. No trends, combined clinical interpretation,
AI calls or medical recommendations are calculated by this UI.

Free text is limited to 20,000 UTF-16 code units. Arabic, English, diacritics,
mixed text, surrounding spaces and newlines pass through without trimming or
normalization. React renders text rather than HTML; bidirectional isolation and
pre-wrapped display preserve readable content. Browser spelling/autocorrection
is disabled on observation text inputs.

An addendum starts with no fields selected. Only selected fields are sent;
unselected fields are omitted. A selected field requires an explicit replacement
value. The UI does not offer clearing fields to null, because null means "no
correction" in the backend. The original and each addendum remain separate,
read-only records in the details screen. Uncertainty is never resolved by the UI.

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
