# Task 5 — history and filtering (#17)

`/app/notes` now opens the protected history screen; `/app/notes/history` is
also supported. The existing navigation and a link on note screens lead here.
Recording a note remains at `/app/notes/new`.

## Behavior

- The initial range includes today and the preceding 29 local calendar days.
- Filter by all recipients, an individual recipient (including inactive
  recipients), a single date, or an inclusive date range. Apply explicitly
  commits the draft filters and returns to page one. Invalid dates/ranges
  produce associated field errors and focus the first invalid input.
- Applied recipient/date filters, page and size live in the URL. Reloading and
  browser Back restore the result view. Draft edits do not issue requests.
- Reset restores all recipients, the default dates, page one and 20 notes per
  page, including when the draft was edited without being applied.
- The backend determines order, totals and pagination. Sizes are 20, 50 and
  100; changing filters or size resets the page. An emptied/out-of-range page
  offers a return to the first page.
- Each card labels the immutable original, exposes every structured field
  and verbatim free text in an accessible disclosure, and links to its full
  note. Corrections load on demand beneath their own original and show only
  the changed fields, in the backend's chronological order. They never replace
  the original observations.
- Loading, no matches, errors/retry and unavailable corrections have explicit
  states. Recipient metadata failure does not prevent viewing notes: owned
  note recipient IDs remain visible until names can be retried.
- Superseded/unmounted reads are aborted and late responses are ignored.
  Existing auth handling clears a rejected session and protects deep links.
- All new copy is English/Arabic. Names use `bdi`, observation text uses
  `dir="auto"` and preserved whitespace, and CSS uses logical spacing/borders.
  Numeric values remain distinct from missing, uncertain and false values.

## API contract

The implementation uses the existing authenticated endpoints:

```text
GET /api/recipients
GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&recipientId=<optional>&page=0&size=20
GET /api/notes/{id}/addenda
```

History responses are Spring `Page<NoteResponse>` objects. The client validates
page metadata and note fields, discards unexpected response properties and
rejects duplicate or out-of-filter rows. History includes addendum counts;
their bodies are requested only when opened. No owner IDs are submitted.

`ObservationValues` is shared with the existing note details screen so original
and correction fields retain identical display semantics. The frontend does
not merge observations, infer clinical signals, calculate trends or invoke AI.
Backend, mobile, infrastructure and dependency files are unchanged.

## Verification

Verified on 22 October 2026:

| Check | Result |
| --- | --- |
| Unit/component/API suite (`npm test`) | 175 passing, including 44 new history tests |
| History browser suite | 32 passing |
| Existing foundation/auth browser suite | 48 passing |
| Existing daily-note browser suite | 32 passing |
| History against the real Spring Boot backend | 4 passing |
| Production build / TypeScript | Passing |
| Diff whitespace check | Passing |

History browser checks run English/Arabic at 1280, 768, 390 and 320 pixels.
They cover recipient/date/combined filtering, reset, browser Back, server
pagination, 41-result lists, invalid filters, empty/error/retry states,
original/correction relationships, keyboard operation, mixed text, no
horizontal overflow, and axe WCAG 2 A/AA and 2.1 A/AA scans. Screenshots are
written to ignored test output directories. Chromium 134 was selected through
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` for this environment.

From `web/`:

```sh
npm test
npm run build
npx playwright test --config src/tests/history/playwright.config.ts
npx playwright test
npx playwright test --config src/tests/notes/playwright.config.ts
```

### Real-backend integration

Start the unchanged backend with Java 21, Maven and a disposable H2 database
as described in `../../tests/integration/README.md`. Then run:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/history-live/playwright.config.ts
```

The suite accepts loopback backends only, creates synthetic caregivers and
records, authenticates through the actual login UI and forwards requests to
Spring Boot without fabricated responses. English/Arabic run at desktop and
320 pixels. Assertions verify the actual page envelope, 23-note pagination,
combined/single-date filters, inactive recipients, preserved originals,
linked addenda, empty results and cross-caregiver isolation. Traces, video
and screenshots are disabled for these authenticated integration cases.
Discard the disposable database afterward.

H2 verifies the real application, security chain and migrations; this does
not verify a shared PostgreSQL deployment or its reverse proxy. No LLM
requests are made.
