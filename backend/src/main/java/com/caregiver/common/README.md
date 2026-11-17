# Error contract (#843)

All MVC failures pass through the single `GlobalExceptionHandler` and return the
same body: `{"code","message"}` plus optional `fields` (validation) and
`traceId`. No domain controller or advice defines its own handler for a shared
exception type. Domain-specific **codes** are preserved inside that shape.

## Status matrix

| Condition | Status | Example codes |
|---|---|---|
| Bean validation failure | `422` | `VALIDATION_ERROR` (+ `fields`), `DUPLICATE_EMAIL`, `PROTECTED_PLAN_FIELDS` |
| Malformed or unreadable body | `400` | `VALIDATION_ERROR` |
| Other invalid request values / parameters | `400` | `INVALID_REQUEST`, `INVALID_PARAMETER`, `INVALID_ID`, `MISSING_PARAMETER`, `INVALID_INTAKE_TIME` |
| Conflict, idempotency clash, inactive resource | `409` | `INACTIVE_RECIPIENT`, `NOTE_CONFLICT`, `IDEMPOTENCY_CONFLICT`, `INVALID_PLAN_TRANSITION`, `MEDICATION_CONFLICT` |
| Missing or foreign resource | `404` | `NOT_FOUND`, `RECIPIENT_NOT_FOUND`, `MEDICATION_NOT_FOUND` |
| Bad credentials | `401` | `INVALID_CREDENTIALS`, `UNAUTHORIZED` |
| Access denied | `403` | `FORBIDDEN` |
| Rate limited | `429` | `RATE_LIMITED` (written by the filter with `Retry-After`) |
| Unexpected | `500` | `SERVER_ERROR` |

Successful response shapes are unchanged, and no exception detail, stack
trace, credential, token or request body is serialized.
