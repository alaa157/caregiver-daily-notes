# Care recipients — reversible lifecycle

Authenticated routes:

| Route | Behavior |
| --- | --- |
| `/app/recipients` | List owned recipients, filter by lifecycle status on the client, search by name/relationship, and show today's note status on active cards. |
| `/app/recipients/new` | Create an ACTIVE recipient, then show a next-steps panel (care details, medications, first note). |
| `/app/recipients/:id` | Read owned recipient details, care details, and medications; record today's note or manage the lifecycle for eligible statuses. |
| `/app/recipients/:id/edit` | Edit ACTIVE recipients only; archived and legacy-review records are read-only. |

## Lifecycle and API contract

The list page fetches `GET /api/recipients` once and filters by lifecycle
status on the client; it does not issue one request per status. The lifecycle
filter group appears only when at least one non-ACTIVE status has recipients,
shows a count per populated status, hides entirely when every recipient is
ACTIVE, and falls back to ACTIVE if the selected status becomes empty. The
search box appears only when the loaded list has more than five recipients
(`SEARCH_THRESHOLD`); while hidden, any typed query is ignored and reset.
Active recipient cards show today's note status (recorded, missing, or
unavailable) with a link to open or record the note.

Search filters the already-loaded caregiver-owned list by recipient name,
translated relationship label, and stored relationship value. Detail pages
show the latest `archivedAt` and `restoredAt` timestamps — not a full
lifecycle history. Active cards and the post-create next-steps panel link to
note creation with a prefilled recipient and date.

The Web application implements the reversible recipient archive lifecycle:
`ACTIVE`, `ARCHIVED`, and `LEGACY_INACTIVE_REVIEW`. Archived records remain
readable and can be restored. Legacy inactive records remain for review until
the caregiver resolves them as archived or active.

## Backend contract

The canonical Web lifecycle states are `ACTIVE`, `ARCHIVED`, and
`LEGACY_INACTIVE_REVIEW`. The last state is for pre-existing `active=false`
records whose historical meaning cannot be proven. It is not an archive or
deletion claim. Permanent deletion is not part of this feature.

| Operation | Request |
| --- | --- |
| All owned recipients | `GET /api/recipients` |
| Filtered list | `GET /api/recipients?status=ACTIVE`, `ARCHIVED`, or `LEGACY_INACTIVE_REVIEW` (backend filter; the list page fetches ALL and filters client-side) |
| Details | `GET /api/recipients/{id}` |
| Create | `POST /api/recipients` with `{name, relationship}` → ACTIVE; sends an `Idempotency-Key` header (a random key reused across retries of the same input) |
| Edit | `PUT /api/recipients/{id}` — ACTIVE only |
| Care details | `PATCH /api/recipients/{id}/care-details` with `{dateOfBirth, allergies, conditions, emergencyContactName, emergencyContactPhone}` (blank values clear to null) |
| Archive | `PATCH /api/recipients/{id}/archive` |
| Restore | `PATCH /api/recipients/{id}/restore` |
| Resolve review | `PATCH /api/recipients/{id}/resolve-review` with `{resolution: "ARCHIVED" \| "ACTIVE"}` — legacy-review records only |
| Compatibility alias | `PATCH /api/recipients/{id}/deactivate` — reversible archive |

API responses expose `lifecycleStatus`, compatibility `active`,
`archivedAt`/`archivedBy`, and `restoredAt`/`restoredBy`. Lifecycle
mutations are authenticated, owner-scoped, row-locked, audited operations.
Repeating the current transition is idempotent.

Archiving changes only lifecycle metadata. It never deletes or rewrites notes,
addenda, plans, identifiers, ownership, or historical dates. Historical reads
remain available. New notes, addenda, recipient edits, plan writes, and plan
generation are rejected by the backend while archived. Restoring re-enables
normal operations without changing historical records. Resolving a
legacy-review record to ARCHIVED or ACTIVE applies the same transitions with
the same preservation guarantees.

## Form fields

Only name and relationship are required on create/edit. Relationship is a
chip group with the codes `MOTHER`, `FATHER`, `SPOUSE`, `GRANDMOTHER`,
`GRANDFATHER`, `CHILD`, `SIBLING`, `RELATIVE`, `FRIEND`, `CLIENT`, and
`OTHER`. Selecting `OTHER` reveals a free-text field (up to 80 characters)
whose trimmed value is stored as the relationship; leaving it empty stores
null. Legacy free-text values that match no code are displayed as stored.

Care details — date of birth, allergies, conditions, and emergency contact
name/phone — are editable on the ACTIVE recipient edit page, visible only to
the owning caregiver through owner-scoped endpoints, and are not used by AI
features.

The list page shows today's note status on active cards. History queries all
statuses so archived recipients remain discoverable. Confirmation dialogs
explain reversibility and preservation, show progress/errors, and require
explicit confirmation. English and Arabic labels and RTL layout are included.
Ownership is derived by the backend and never sent by the client.

## Verification

Run from `web/`: `npm test`, `npm run build`,
`npm run test:e2e`, and
`npx playwright test --config src/tests/recipients/playwright.config.ts`.

Run backend tests from repository root with `mvn -f backend/pom.xml test`.
No production migration should be executed during verification.
