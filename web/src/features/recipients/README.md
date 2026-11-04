# Care recipients — reversible lifecycle

Authenticated routes:

| Route | Behavior |
| --- | --- |
| `/app/recipients` | List, search by name/relationship, with separate Active, Archived, and Needs review lists. |
| `/app/recipients/new` | Create an ACTIVE recipient. |
| `/app/recipients/:id` | Read owned recipient details and lifecycle history; navigate to recipient-filtered history or create a note for an active recipient. |
| `/app/recipients/:id/edit` | Edit ACTIVE recipients only; archived and legacy-review records are read-only. |

## Lifecycle and API contract

Search filters the already-loaded caregiver-owned list by recipient name and
nullable relationship. Detail pages link to history using `?recipientId=<UUID>`
and to note creation using a prefilled recipient query. When an active recipient
and valid date are supplied in the create-note URL, the form preselects them.

The Web application implements the reversible recipient archive lifecycle:
`ACTIVE`, `ARCHIVED`, and `LEGACY_INACTIVE_REVIEW`. Archived records remain
readable and can be restored. Legacy inactive records remain for review.

## Backend contract

The canonical Web lifecycle states are `ACTIVE`, `ARCHIVED`, and
`LEGACY_INACTIVE_REVIEW`. The last state is for pre-existing `active=false`
records whose historical meaning cannot be proven. It is not an archive or
deletion claim. Permanent deletion is not part of this feature.

| Operation | Request |
| --- | --- |
| All owned recipients | `GET /api/recipients` |
| Filtered list | `GET /api/recipients?status=ACTIVE`, `ARCHIVED`, or `LEGACY_INACTIVE_REVIEW` |
| Details / historical view | `GET /api/recipients/{id}` |
| Create | `POST /api/recipients` with `{name, relationship}` → ACTIVE |
| Edit | `PUT /api/recipients/{id}` — ACTIVE only |
| Archive | `PATCH /api/recipients/{id}/archive` |
| Restore | `PATCH /api/recipients/{id}/restore` |
| Compatibility alias | `PATCH /api/recipients/{id}/deactivate` — reversible archive |

API responses expose `lifecycleStatus`, compatibility `active`,
`archivedAt`/`archivedBy`, and `restoredAt`/`restoredBy`. Lifecycle
mutations are authenticated, owner-scoped, row-locked, audited operations.
Repeating the current transition is idempotent. Legacy-review records require
review and cannot be archived/restored automatically.

Archiving changes only lifecycle metadata. It never deletes or rewrites notes,
addenda, plans, identifiers, ownership, or historical dates. Historical reads
remain available. New notes, addenda, recipient edits, plan writes, and plan
generation are rejected by the backend while archived. Restoring re-enables
normal operations without changing historical records.

The active operational UI queries ACTIVE recipients only. History queries all
statuses so archived recipients remain discoverable. Confirmation dialogs
explain reversibility and preservation, show progress/errors, and require
explicit confirmation. English and Arabic labels and RTL layout are included.
Only name and nullable relationship are editable; ownership is derived by the
backend.

## Verification

Run from `web/`: `npm test`, `npm run build`,
`npm run test:e2e`, and
`npx playwright test --config src/tests/recipients/playwright.config.ts`.

Run backend tests from repository root with `mvn -f backend/pom.xml test`.
No production migration should be executed during verification.
