# Recipients API

All endpoints require a valid caregiver bearer token. The caregiver identity is derived from the verified token; request bodies never select an owner. Recipient IDs belonging to another caregiver are indistinguishable from unknown IDs and return `404 RECIPIENT_NOT_FOUND`, not `403`.

The five care-detail fields (`dateOfBirth`, `allergies`, `conditions`, `emergencyContactName`, and `emergencyContactPhone`) are health data. They are returned only to the owning caregiver and are not used by AI features.

## Recipient response

Successful single-recipient operations return a `RecipientResponse` with these fields:

| Field | Type |
|---|---|
| `id` | UUID |
| `name` | string |
| `relationship` | string or `null` |
| `active` | boolean compatibility field |
| `lifecycleStatus` | `ACTIVE`, `ARCHIVED`, or `LEGACY_INACTIVE_REVIEW` |
| `archivedAt`, `restoredAt` | ISO-8601 timestamp or `null` |
| `archivedBy`, `restoredBy` | caregiver UUID or `null` |
| `dateOfBirth` | `YYYY-MM-DD` date or `null` |
| `allergies`, `conditions` | string or `null` |
| `emergencyContactName`, `emergencyContactPhone` | string or `null` |
| `createdAt`, `updatedAt` | ISO-8601 timestamp |

Error responses have the shape `{"code":"...","message":"..."}`.

## List recipients

**GET `/api/recipients`**

- **Request:** optional query `status`: `ACTIVE`, `ARCHIVED`, or `LEGACY_INACTIVE_REVIEW`. Omit it to list every owned recipient.
- **Response:** array of `RecipientResponse` objects.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_LIFECYCLE_STATUS` for an unsupported status.

## Get a recipient

**GET `/api/recipients/{id}`**

- **Request:** `id` is a recipient UUID.
- **Response:** one `RecipientResponse`, including archived historical records and care details.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller.

## Create a recipient

**POST `/api/recipients`**

- **Request body:** `name` is a required, non-blank string up to 120 characters; `relationship` is an optional nullable string up to 80 characters. Values are trimmed; a blank relationship becomes `null`. The recipient starts `ACTIVE`.
- **Optional header:** `Idempotency-Key`, up to 100 characters. A blank key is treated as absent. Repeating the same key with the same normalized body replays the original recipient with status `201`; reusing the key with a different body returns `409 IDEMPOTENCY_CONFLICT`.
- **Response:** `201 Created` with a `RecipientResponse`.
- **Errors:** `401 UNAUTHORIZED`; `400 VALIDATION_ERROR` if the idempotency key exceeds 100 characters; `422 VALIDATION_ERROR` for an invalid or unreadable request body; `409 IDEMPOTENCY_CONFLICT` for key reuse with a different body; `409 RECIPIENT_CONFLICT` for a recipient data conflict.

## Update a recipient

**PUT `/api/recipients/{id}`**

- **Request:** `id` is a recipient UUID. The body uses the create fields: `name` (required, non-blank, max 120 characters) and `relationship` (nullable, max 80 characters). Values are trimmed; a blank relationship becomes `null`. Only `ACTIVE` recipients can be updated.
- **Response:** `RecipientResponse`.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` for an archived or legacy-review recipient; `409 RECIPIENT_CONFLICT` for a data-integrity conflict; `422 VALIDATION_ERROR` for an invalid or unreadable request body.

## Replace care details

**PATCH `/api/recipients/{id}/care-details`**

- **Request:** `id` is a recipient UUID. The body contains all five optional fields; together they replace the existing care details. `dateOfBirth` is a nullable `YYYY-MM-DD` date between `1900-01-01` and today. `allergies` and `conditions` are nullable strings up to 500 characters each. `emergencyContactName` is a nullable string up to 120 characters. `emergencyContactPhone` is a nullable string up to 30 characters matching `^[0-9+()\- ]{3,30}$`. Text is trimmed; `null` or a blank text value clears that field. Only `ACTIVE` recipients can be updated.
- **Response:** `RecipientResponse`, including the updated care details.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `400 VALIDATION_ERROR` for an out-of-range date, invalid phone, or over-length field; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` for an archived or legacy-review recipient; `409 RECIPIENT_CONFLICT` for a data-integrity conflict; `422 VALIDATION_ERROR` for an invalid or unreadable request body.

## Archive a recipient

**PATCH `/api/recipients/{id}/archive`**

- **Request:** `id` is a recipient UUID; no body. Archiving is reversible and preserves history. Repeating the operation on an already archived recipient is idempotent.
- **Response:** `RecipientResponse`.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` if the recipient is in `LEGACY_INACTIVE_REVIEW` and needs explicit resolution; `409 RECIPIENT_CONFLICT` for a data-integrity conflict.

## Restore a recipient

**PATCH `/api/recipients/{id}/restore`**

- **Request:** `id` is a recipient UUID; no body. Restores an `ARCHIVED` recipient to `ACTIVE`. Repeating the operation on an already active recipient is idempotent.
- **Response:** `RecipientResponse`.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` if the recipient is in `LEGACY_INACTIVE_REVIEW` and needs explicit resolution; `409 RECIPIENT_CONFLICT` for a data-integrity conflict.

## Deactivate alias

**PATCH `/api/recipients/{id}/deactivate`**

- **Request:** `id` is a recipient UUID; no body. This backward-compatible alias performs the same reversible archive operation as `PATCH /api/recipients/{id}/archive`.
- **Response:** `RecipientResponse`.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` if the recipient is in `LEGACY_INACTIVE_REVIEW` and needs explicit resolution; `409 RECIPIENT_CONFLICT` for a data-integrity conflict.

## Resolve a legacy-review recipient

**PATCH `/api/recipients/{id}/resolve-review`**

- **Request:** `id` is a recipient UUID. The body is `{"resolution":"ARCHIVED"}` or `{"resolution":"ACTIVE"}`. The choice is required; this operation applies only to `LEGACY_INACTIVE_REVIEW`.
- **Response:** `RecipientResponse` with the selected lifecycle state.
- **Errors:** `401 UNAUTHORIZED`; `400 INVALID_ID` for a malformed UUID; `400 VALIDATION_ERROR` if the body is missing or the resolution is not `ARCHIVED` or `ACTIVE`; `404 RECIPIENT_NOT_FOUND` if the recipient does not exist or is not owned by the caller; `409 INACTIVE_RECIPIENT` if the recipient is not awaiting legacy-review resolution; `409 RECIPIENT_CONFLICT` for a data-integrity conflict; `422 VALIDATION_ERROR` for an unreadable request body.
