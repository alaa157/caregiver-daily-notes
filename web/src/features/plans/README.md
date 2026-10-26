# Member 4 Task 7 — Care-plan review and history (draft)

Issue: https://github.com/alaa157/caregiver-daily-notes/issues/19

The protected page is `/app/notes/plans`, with an optional
`?recipientId=<uuid>` deep link. This mount uses the existing protected notes
router so every change stays within the issue's allowed paths. The branch is
based on `main`; it does not bundle or depend on the unmerged Task 6 code in
draft PR #58. Issue #18 is still an integration prerequisite for the overall
summary-to-plan experience.

## Implemented

- Explicit owned-recipient selection, including inactive recipients; URL
  restoration and canceled/ignored obsolete reads.
- Separate authoritative active accepted-plan view, pending suggestions and
  newest-first saved version history.
- Exact saved content, status, timestamps and source references. HTML is text,
  mixed Arabic/English and whitespace stay unchanged, and opaque references
  are isolated with `bdi`. Only UUID-shaped evidence IDs become internal note
  links. A source-plan link opens/focuses the preserved original in history.
- Confirmed accept/dismiss operations with bodyless POST requests. The client
  never supplies a new lifecycle status or version, archives a record itself,
  or synthesizes success from local state. All committed reads are refreshed
  after a successful operation.
- No duplicate in-flight decisions. Conflicts, invalid transitions and
  uncertain responses pause decisions until an explicit read refresh succeeds.
  A failed write is never replayed automatically. Unknown statuses stay
  read-only, and inconsistent active/history snapshots require another read.
- Loading, no recipients, empty active/suggestion/history, validation,
  unavailable, read timeout, service error, conflict and uncertain-save states.
- English/Arabic, RTL/LTR, responsive wrapping, keyboard confirmation/cancel
  focus restoration, completion/error focus and screen-reader feedback.
- Existing session rejection/expiry handling, no content/browser persistence,
  no logging, no LLM calls and no client-written suggested plans.

## Why editing remains disabled

**Task 7 is not complete and this PR must stay draft.** Rechecked `main` at
`20d3e73` on 2026-10-26:

1. `PlanResponse.content` is an opaque string. `EditPlanRequest` accepts another
   unrestricted content string. `PlanService.editAndAccept` stores it without
   calling `MedicationHardLock` or a structured/catalog validator. The AI
   suggestion pipeline has a medication comparison, but that is not invoked
   by this HTTP edit operation. An unrestricted web text area would therefore
   allow changing a medication section. The web API deliberately exposes no
   edit-and-accept method, and the control is disabled with a visible reason.
   This preserves the available lock rather than claiming the frontend could
   provide server authorization or reliable medication detection from prose.
2. The response has no source-type/authorship field. `SUGGESTED` identifies a
   lifecycle state, not AI authorship: the generic suggestion POST accepts
   caller text. The UI displays returned summary/note/parent references and
   states that source type is unavailable. It does not invent an AI badge.
3. `PlanSuggestionService` and `SuggestedPlanStore` are internal contracts;
   this branch does not invent a generation endpoint, serialize an undocumented
   structured plan into the free-text field, or imply the AI pipeline is
   integrated with this UI.

The backend owner needs to expose/document protected editable non-medication
fields, retain the original medication data server-side, enforce the lock and
catalog at the HTTP edit boundary, and return authoritative source type and
parent provenance. Then implement the structured edit/review/confirm flow,
wire it to that operation, and add success/invalid/medication-tampering tests
against the real backend. Preserve the original suggestion as its own record.
Do not enable a free-text replacement form as a workaround. Backend, mobile,
infrastructure, dependencies and workflows are outside this branch's scope.

## Current wire contract

All routes below come from the checked-in `PlanController` and authenticated
caregiver transport. Ownership remains enforced by the server.

| Operation | Request |
| --- | --- |
| Active accepted plan | `GET /api/recipients/{recipientId}/plans/active` |
| All saved versions, newest first | `GET /api/recipients/{recipientId}/plans/history` |
| Accept a suggestion | `POST /api/recipients/{recipientId}/plans/{planId}/accept`, no body |
| Dismiss a suggestion | `POST /api/recipients/{recipientId}/plans/{planId}/dismiss`, no body |

The DTO contains `id`, `recipientId`, `version`, `status`, `content`,
`sourceSummaryId`, `sourcePlanId`, comma-separated `evidenceNoteIds`, and
`createdAt`. Reads reject malformed or mismatched recipient data and duplicate
history IDs/versions. The active endpoint's exact `404 { code: "NOT_FOUND" }`
envelope means no accepted plan; missing routes/recipients and other failures
remain errors. No idempotency-key contract is assumed for plan actions.

## Verification commands

From `web/` after `npm ci` and installing Playwright Chromium:

```sh
npm test
npm run build
npx playwright test --config src/tests/plans/playwright.config.ts
npx playwright test
npx playwright test --config src/tests/recipients/playwright.config.ts
npx playwright test --config src/tests/notes/playwright.config.ts
npx playwright test --config src/tests/history/playwright.config.ts
git diff --check
```

The new tests cover contracts and malformed/scoped responses, source links,
exact mixed text, escaped markup, pending/active/history separation, keyboard
confirmation/cancel, accept/dismiss, duplicate clicks, uncertain responses,
invalid transitions, required refresh, inactive recipients, unknown states,
abandoned requests, session rejection and medication edit blocking. Browser
tests include axe WCAG 2/2.1 A/AA and overflow checks in English/Arabic at
1280/768/390/320px. These are explicit synthetic contract fixtures, not proof
of an AI-generated plan or of protected editing.

An optional existing browser can be selected with
`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`.

## Disposable real-backend browser suite

Run the unchanged Java 21/Spring Boot backend with a **disposable synthetic
database**. The existing H2 test dependency and Flyway migrations can be used
as described in `../../tests/integration/README.md`. For example, from the
repository root:

```sh
JWT_SECRET=disposable-local-smoke-secret-at-least-32-bytes \
SPRING_DATASOURCE_URL='jdbc:h2:mem:planswebsmoke;MODE=PostgreSQL;DB_CLOSE_DELAY=-1' \
SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver \
SPRING_DATASOURCE_USERNAME=sa SPRING_DATASOURCE_PASSWORD='' \
SPRING_FLYWAY_PLACEHOLDER_REPLACEMENT=false \
mvn -f backend/pom.xml test-compile spring-boot:run \
  -Dspring-boot.run.useTestClasspath=true
```

The signing value above is public and disposable-test-only. For shared
environments, use their environment-provided private secret. From `web/`:

```sh
CAREGIVER_TEST_API_URL=http://127.0.0.1:8080 \
npx playwright test --config src/tests/plans-live/playwright.config.ts
```

The suite accepts loopback origins only and disables traces, screenshots and
video. It creates two synthetic caregivers, a recipient, a supporting note and
plan versions for each case; discard the database afterward. No live AI call
is made. It verifies real login/JWTs, accept/archive, dismiss, an externally
changed suggestion returning 409, saved edited/original history linkage,
exact content and source references, inactive review and cross-caregiver
read/write rejection in English/Arabic at desktop and 320px. The test seeds an
existing edited version through the current API to verify its display; this
does **not** validate a protected edit workflow or enable the web editor.

Requests are bridged from browser `/api` to the loopback service. This does
not verify deployed CORS/proxy routing, PostgreSQL behavior, AI authorship or
generation. Keep #19 open until the protected editor, authoritative source
contract, dependency integration and owner acceptance are complete.
