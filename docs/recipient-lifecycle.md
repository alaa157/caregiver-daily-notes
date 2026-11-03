# Reversible recipient lifecycle (Web)

## Canonical states

The Web recipient lifecycle is explicit:

- `ACTIVE`: available for ordinary care workflows and recipient edits.
- `ARCHIVED`: reversibly archived; read-only in operational workflows. Historical information remains accessible.
- `LEGACY_INACTIVE_REVIEW`: a pre-lifecycle record with `active=false` whose historical meaning is not proven. This state must not be silently treated as archived or deleted.

Permanent deletion is not part of Issue #69. No archive or restore operation deletes a recipient or any child record.

The existing `active` column remains as a compatibility projection: it is true only for `ACTIVE`, and false for `ARCHIVED` and `LEGACY_INACTIVE_REVIEW`. The new `lifecycle_status` column is authoritative.

## Persistence and migration

Migration: `backend/src/main/resources/db/migration/V8__recipient_archive_lifecycle.sql`.

The migration is additive and leaves recipient identifiers, caregiver ownership, child foreign keys, and historical rows intact. Its backfill is evidence-safe:

- Existing `active=true` rows become `ACTIVE`.
- Existing `active=false` rows become `LEGACY_INACTIVE_REVIEW`.
- No legacy archive timestamp, actor, reason, or restore event is fabricated.

The schema adds `archived_at`, `archived_by`, `restored_at`, and `restored_by`, consistency checks between lifecycle state and the compatibility boolean, actor foreign keys to `caregivers`, and an index on caregiver, lifecycle, and creation time.

A newly created recipient starts as `ACTIVE`. A real archive writes its timestamp and authenticated caregiver actor. Restore records its timestamp and actor while retaining the prior archive provenance. Repeating the current transition is idempotent and does not generate new metadata.

## API

All operations derive the caregiver identity from the authenticated principal; request bodies cannot select an owner.

| Method | Path | Contract |
| --- | --- | --- |
| GET | `/api/recipients` | List all owned recipients |
| GET | `/api/recipients?status=ACTIVE` | Filter by lifecycle; also supports `ARCHIVED` and `LEGACY_INACTIVE_REVIEW` |
| GET | `/api/recipients/{id}` | Read owned details, including archived history |
| POST | `/api/recipients` | Create an `ACTIVE` recipient |
| PUT | `/api/recipients/{id}` | Edit active recipients only |
| PATCH | `/api/recipients/{id}/archive` | Archive an active recipient |
| PATCH | `/api/recipients/{id}/restore` | Restore an archived recipient |
| PATCH | `/api/recipients/{id}/deactivate` | Compatibility alias for reversible archive |

Unknown or cross-caregiver IDs remain indistinguishable (404). Invalid lifecycle filters return 400. Archived/legacy mutation conflicts return 409. Lifecycle writes are transactional and lock the owned recipient row.

## Operational workflow rules

Archived and legacy-review recipients are excluded from active note-creation selectors. The backend also locks and validates the recipient before creating daily notes or appending note addenda, so stale clients cannot bypass the restriction.

Plan mutations and plan generation use the owned-recipient row lock and reject non-active recipients. Existing plans and historical notes, addenda, structured correction events, and reports remain readable. Restoring returns the recipient to active operational selectors; no historical content or dates are rewritten.

The Web recipient management page has separate Active, Archived, and Needs review filters. Archived detail pages are read-only, show archive/restore metadata, and offer Restore. Legacy-review records remain read-only and have no automatic archive or restore action. Confirmation dialogs identify the recipient, explain preserved history and reversibility, and display loading, error, and success feedback. English and Arabic strings are supplied through the translation catalogue, with RTL layouts.

## Verification scope

Automated coverage is added for recipient API filters and operations, archive/restore dialog behavior, legacy-review presentation, legacy migration backfill, authenticated ownership, idempotency, historical child-record preservation, and blocking writes while archived.

Run:
- Backend: `mvn -f backend/pom.xml test`
- Web typecheck/build: `cd web && npm run typecheck && npm run build`
- Web unit tests: `cd web && npm test`
- Browser tests: `cd web && npm run test:e2e`
- Live API/browser checks (when the test API is configured): `cd web && npm run test:e2e:live`

No production migration is to be run during implementation or verification.
