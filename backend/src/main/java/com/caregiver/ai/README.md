# Web summary and protected-plan integration

This companion backend change completes the API prerequisites for Member 4
Tasks 6 and 7. Frontend changes remain in their own pull requests.

## HTTP contracts

| Operation | Request | Response |
| --- | --- | --- |
| Summarize owned notes | `POST /api/summaries/byok` | `SummaryResult`: summary, authoritative safety, trends |
| Generate a validated suggestion | `POST /api/plans/generate/byok` | plan or null, aiUnavailable, authoritative safety |
| Test provider credentials | `POST /api/ai/byok/connection-test` | connection success flag |
| Edit and accept | Existing `POST /api/recipients/{recipientId}/plans/{planId}/edit-and-accept` | New immutable accepted version |

Summary and generation requests wrap those fields in a `summary` object and
attach request-scoped `credentials` (`providerId`, `model`, `apiKey`). There is
no server-configured provider: calls without credentials fail closed. Both
operations are advertised as JSON in OpenAPI. Authentication uses the existing bearer chain.
The server loads owned recipients, notes, original observations and chronological
addenda. No client-supplied notes, signals or ownership claims are used. The
existing signal projection preserves contradictory/unverified medication states.
Trend inputs include the prior seven-day comparison window.

Source loading finishes its read transaction before provider work starts.
A bounded pool limits each provider operation to 40 seconds, including queue
wait. Summary-plus-plan generation therefore has at most two provider budgets,
inside the web client's 120-second request budget. Workers only call the
request-credentialed LLM; they never read security context or persist plans. Timeout,
malformed output and provider failure return unavailable model content with
deterministic safety/trends retained. No diagnostics or prompts are logged.

## Provider request and failure behavior

Request-scoped model IDs must match
`^[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}$`.

For requests with credentials whose provider is `OPENAI`, the chat-completions
body uses `max_completion_tokens` instead of `max_tokens` and omits both
`temperature` and `reasoning`. Other requests retain the configured
OpenAI-compatible parameter shape.

The provider transcript does not include caregiver, recipient, addendum,
correction-event or actor IDs. Note IDs remain in the `[NOTE id="..."]` wrappers
so the model can cite source notes; they are checked against the loaded notes
before the summary is returned.

Unavailable summaries include `unavailableReason`, one of:
`INVALID_KEY`, `INVALID_MODEL`, `RATE_LIMIT`, `TIMEOUT`,
`PROVIDER_UNAVAILABLE` or `INVALID_OUTPUT`. HTTP `400`, `401`, `403` and `404`
responses are permanent failures: the client returns fallback immediately
without retrying or sleeping. HTTP `408`, `429`, 5xx responses, timeouts and
other I/O failures retry using the configured attempt count and exponential
backoff (defaults: three attempts, 200 ms base). The reason on the fallback is
the reason from the last failed attempt.

## Protected plan format and edits

The server persists a schema-version-1 document with existing catalog changes
and a separate immutable medication list. Parameter names must match the
catalog; values are bounded, check-in days are 1–365, and meal reminder times
use HH:mm. The HTTP edit body is now:

```json
{
  "expectedVersion": 2,
  "changes": [{ "index": 0, "params": { "withinDays": "10" } }]
}
```

It can change only existing parameter values. Action types, change count/order,
reasons, evidence, parent/summary references and medication names/doses/schedules
come from the original server record. Unknown request fields are rejected.
The server reconstructs the new document, applies the medication hard lock,
locks/rechecks the current accepted baseline and performs the existing
transactional archive/version workflow. Accepting a structured suggestion also
rechecks its baseline and medications. Invalid/repeated/stale transitions return
409. Protected/invalid edits return 422 without changing the original.

**Medication hard lock scope (issue #751):** `MedicationHardLock` is a hard guard
over server-owned medication values on plan documents and suggestions. It is
**reserved for future use** and is **not connected to the recipient's real
medication records**. AI generation no longer instructs the model to echo the
medication list, does not validate any echo it still returns (whitespace or
otherwise), and always saves the baseline medication list. A differing echo
therefore no longer fails generation. The lock remains a hard failure, never a
warning, wherever it is applied to server-owned values.

**Intentional API change:** unrestricted `content` replacement at edit-and-accept
is rejected. Other clients must adopt the protected payload above. Opaque legacy
plans stay readable and can use their existing lifecycle, but cannot be edited
through this operation. A legacy free-text suggestion cannot replace an active
protected plan. Generation from an opaque accepted baseline returns unavailable
instead of guessing or erasing its medication section.

Migration V6 adds server-owned `sourceType`: `UNSPECIFIED` for historical rows,
`CAREGIVER_SUBMITTED` for generic text submissions, `AI_GENERATED` only after the
validated pipeline, and `CAREGIVER_EDITED` for protected edits. A plan response
also includes `structuredContent` and the server's `editable` capability.
An edited version links to the preserved original suggestion. The summary
reference is `summary-sha256:<digest>` of the actual source result: a provenance
fingerprint, not a stored/retrievable summary ID. No historical authorship is
inferred from status or free text.

## Verification

Run with Java 21 and Maven:

```sh
mvn -f backend/pom.xml test
```

The HTTP tests use real authentication, Flyway/H2 and a mocked LLM. They verify
inclusive windows, evidence, provider failure safety, OpenAPI, ownership,
protected edits, medication add/remove/dose/schedule rejection, source metadata,
stale baselines and inactive recipients. Provider deadline tests cover stalled
and throwing delegates. Existing backend tests remain part of the suite.

The web tests in `web/src/tests/summary-live/` and `plans-live/` additionally run
against the real application and an explicit loopback OpenAI-wire fixture in
`web/src/tests/summary-live/synthetic_provider.py`. Set the test backend's LLM
base URL to `http://127.0.0.1:8099`, use a synthetic key, disable the backup,
and run the provider fixture alongside it. Use a disposable database. Production
provider keys remain environment-owned; no test calls Gemini/OpenRouter.

The integration tests verify HTTP transport, ownership, safety and persistence;
they do not establish shared PostgreSQL/proxy configuration or external provider
availability. No mobile, dependency, deployment, infrastructure or workflow
changes are included. Owner review/merge remains required.
