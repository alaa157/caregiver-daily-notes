# Care recipients web UI — Mahmoud Hewidy (#15)

Authenticated routes:

| Route | Behavior |
| --- | --- |
| `/app/recipients` | List the authenticated caregiver's active and inactive recipients. |
| `/app/recipients/new` | Create a recipient. |
| `/app/recipients/:id` | Read recipient details and confirm deactivation. |
| `/app/recipients/:id/edit` | Edit an active recipient; inactive records are read-only. |

## Backend contract

The UI follows the existing `RecipientController`, `RecipientRequest`,
`RecipientResponse`, and `RecipientExceptionHandler` on `main`:

| Operation | Request |
| --- | --- |
| List | `GET /api/recipients` → array of recipient responses |
| Details | `GET /api/recipients/{id}` → recipient response |
| Create | `POST /api/recipients` with `{name, relationship}` → 201 recipient response |
| Edit | `PUT /api/recipients/{id}` with `{name, relationship}` → recipient response |
| Deactivate | `PATCH /api/recipients/{id}/deactivate` without a body → inactive recipient response |

Names are required, trimmed, and limited to 120 UTF-16 code units, matching
the backend's Java string validation. Relationships are optional, trimmed,
and limited to 80 code units; clearing a relationship sends `null`.

Only `name` and `relationship` are serialized for writes. No ownership,
caregiver ID, or editable active flag is accepted in forms or forwarded by
the API adapter. Ownership and authorization remain backend responsibilities.
Responses are validated and projected onto the documented safe fields.
Unavailable/forbidden recipients share a generic message; raw backend error
messages are never rendered.

The current backend's POST response can contain `null` timestamps because it
constructs the response before persistence flush. The create adapter validates
and returns the identity/name/relationship/active fields needed for navigation.
The details route then loads the committed record via GET, where timestamps
remain mandatory and validated. The UI never fabricates dates or retries a
successful POST to obtain them.

The backend's 409 inactive-recipient conflict preserves entered values,
blocks further saves, and offers an explicit reload. Deactivation uses the
dedicated endpoint and keeps the returned record visible as inactive. The UI
does not delete records, offer reactivation, or allow editing inactive records.
Confirmation explains that historical notes and plans are preserved.

The existing authentication provider attaches bearer tokens and handles
session rejection. Recipient records are not written to browser storage.
Abandoned requests are aborted, late responses are ignored, and duplicate
submissions are blocked. Cancelling an in-flight form request stops the UI
from awaiting it; an operation already accepted by the backend may still finish.

## UI and accessibility

- Arabic/English text, localized dates, and logical CSS for RTL/LTR.
- Bidirectional isolation for recipient-provided names and relationships.
- Loading, empty, retryable API/network error, validation, unavailable, conflict,
  saving, success, and inactive states.
- Native modal confirmation, initial focus on Cancel, keyboard focus wrapping,
  Escape/cancel behavior, and focus restoration or success announcement.
- Labeled fields, linked validation hints, live status/error messages,
  protected deep links, and responsive layouts down to 320px.

Feature code and tests live in the issue's designated frontend directories.
The existing `src/app/App.tsx` also has the minimal integration needed to mount
the protected recipient routes and link to them from the dashboard.

## Verification

Run from `web/` after installing dependencies and Playwright Chromium:

```sh
npm test
npm run build
npm run test:e2e
npx playwright test --config src/tests/recipients/playwright.config.ts
```

The separate Task 3 Playwright configuration keeps its browser tests inside
the issue's allowed `src/tests/` directory and reuses the existing four
viewport projects and preview server. The standard `test:e2e` command runs
the existing foundation/authentication suite; run both browser commands.

Tests use synthetic recipient records and contract-shaped intercepted API
responses. They verify request methods/bodies, bearer headers, safe ownership
handling, validation, all CRUD/deactivation interactions, errors, conflict
recovery, cancelled/late requests, keyboard interaction, Arabic/RTL,
WCAG 2.1 A/AA axe checks, and horizontal-overflow checks.

The opt-in [real-backend suite](../../tests/integration/README.md) exercises
list/create/details/edit/deactivate, cross-caregiver rejection and existing-note
preservation against a disposable local backend. Verification of the owner's
shared deployment still requires its current backend/proxy configuration and
a synthetic caregiver account.
Browser contract tests do not establish backend authorization or historical
database preservation. No backend, mobile, infrastructure, or workflow changes
are included.
