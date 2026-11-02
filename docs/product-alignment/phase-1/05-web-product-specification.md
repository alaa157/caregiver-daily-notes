# Web product specification

**Reference:** `main` at `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7`. Web behavior below is the implementation in `web/`, not the Android functional reference.

## Routes and pages

`App.tsx` declares public home and login pages, a protected dashboard, recipient routes, note routes, and a catch-all. The route components yield 14 named page components when shared page wrappers are counted as one page each; this excludes dialogs and reusable components. [`App.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/app/App.tsx#L69-L83), [`RecipientsRoutes.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/routes/RecipientsRoutes.tsx#L1-L12), [`NotesRoutes.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/routes/NotesRoutes.tsx#L1-L18).

| Page/workflow | Actual behavior and rules |
|---|---|
| Public landing | Product introduction, disclaimer, feature cards, and sign-in link. |
| Login/session | Email/password form; client validates email and password presence/byte limit, then calls `/api/auth/login`. JWT remains in `sessionStorage` (or memory if storage is blocked); expiration and 401 clear the session. No registration page/route is present, although backend registration exists. |
| Dashboard | Two links (recipients and create note); it is not a data-rich daily dashboard. |
| Recipients | List and create with name/optional relationship; view relationship and timestamps; edit active record; deactivate through a confirmation dialog. Deactivation preserves backend history. |
| Create note | Choose active recipient and date (date defaults to today but is editable); enter structured mood/appetite/sleep/mobility, medication status/verification, pain 0–10, fall, free text. Values can be omitted; lengths are bounded. The UI can open an existing note for a recipient/date rather than creating a duplicate. |
| Note detail/addendum | Original record is shown as immutable; addendum UI selects which structured fields to correct and sends only selected values. Original and addenda remain separately visible with timestamps. |
| History | Date range, optional recipient, page size and server pagination; query is reflected in URL. Uses retry/error/empty states and shows inactive recipient context. |
| Summary | Select recipient, 7/14/30-day window, and editable inclusive end date. Generates through backend, with abort/cancel, 120-second timeout, safety retained independently from malformed AI content, evidence quotes linked to source note, uncertainties and trends. |
| Care plans | Select recipient; generate from chosen summary period; show active plan, suggestions and version history; accept/dismiss with confirmation; edit only permitted structured AI-generated suggestions. UI refuses inconsistent snapshots and blocks ambiguous writes rather than inventing client state. |
| Not found and shared shell | Global language toggle, site navigation, sign-out, skip link, focus-on-route-change, document title, and page-specific not-found states. |

The typed API modules validate server responses. `ApiClient` uses no browser cookies, `cache: no-store`, `redirect: error`, and root-relative routes. It does not provide offline reads/writes. There is no web service worker, IndexedDB cache or offline outbox found in `web/src`.

## Backend interaction and data flow

`AuthProvider` wraps the API client with a bearer session. Recipient, note, summary and plan features use dedicated TypeScript APIs; data is read/written on the backend, not cached locally. Notes use idempotency keys and uncertain-write UI states; summary discovers the compatible OpenAPI operation and checks its request/response schema. See [`notes.ts`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/api/notes.ts#L1-L148), [`summary.ts`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/api/summary.ts#L1-L142), [`plans.ts`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/api/plans.ts#L1-L148).

## Current limitations and implementation status

- Account registration, password reset/recovery, profile management and browser-local backup are not exposed by the inspected web routes.
- No local medication catalogue/intake screen, CSV import/export, full backup/restore, PDF sharing, theme chooser or AI provider/key settings were found.
- Web summary/plan AI uses backend-managed providers; the browser does not call an LLM directly.
- The UI is backend-dependent: network/API failures show errors/retry states rather than a local write path.
- Web API contracts align with current main backend DTOs; that does not make the Android branch wire-compatible.

## Evidence and verification boundary

Relevant source: [`RecipientsPages.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/features/recipients/RecipientsPages.tsx#L36-L129), [`NotesPages.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/features/notes/NotesPages.tsx#L20-L95), [`HistoryPage.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/features/history/HistoryPage.tsx#L15-L76), [`SummaryPage.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/features/summary/SummaryPage.tsx#L18-L130), [`PlansPage.tsx`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/web/src/features/plans/PlansPage.tsx#L1-L242). Existing web tests and Playwright projects were inspected but not run during this phase.
