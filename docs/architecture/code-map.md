# Verified repository code map

**Repository:** `alaa157/caregiver-daily-notes`  
**Branch inspected:** `main`  
**Reference-only Android branch named in the task:** `feat/standalone-byok-ai` (not used to infer web/backend implementation)  
**Scope:** the current source tree and the configuration/test files listed below. Paths are repository-relative.

This document records names and behavior verified in the `main` tree. A NOT FOUND entry means the named implementation was not present in the inspected tree; it is not a statement about future work.

## 1. Routing and navigation

**Frontend files and symbols**

- `web/src/main.tsx` — `Bootstrap`; loads runtime config and mounts `I18nProvider`, `ApiProvider`, `AuthProvider`, and `BrowserRouter` before `App`.
- `web/src/app/App.tsx` — exported `App`; root `Routes`, public `HomePage` and `NotFoundPage`, protected workspace branch.
- `web/src/app/ProtectedRoute.tsx` — re-exports `ProtectedRoute` from `web/src/routes/ProtectedRoute.tsx`.
- `web/src/routes/ProtectedRoute.tsx` — route authentication boundary.
- `web/src/routes/RecipientsRoutes.tsx` — exported `RecipientsRoutes`; recipients child routes.
- `web/src/routes/NotesRoutes.tsx` — exported `NotesRoutes`; notes/history/summary/plans child routes.
- `web/src/components/AppShell.tsx` — `AppShell`; header navigation, language switch, route title, focus and scroll behavior.
- `web/src/components/AppShell.css` — shell/navigation CSS.

**Routes verified in code**

- `/` → `HomePage`.
- `/login` → `LoginPage`.
- Under `<Route element={<ProtectedRoute />}>`: `/app` → `WorkspaceOverview`; `/app/recipients/*` → `RecipientsRoutes`; `/app/notes/*` → `NotesRoutes`.
- Recipients children: index list, `new`, `:id`, `:id/edit`, and `*` not-found.
- Notes children: index/history, `history`, `summary`, `plans`, `new`, `:id`, `:id/addenda/new`, and `*` not-found.

**How to add a route**

1. For a recipient child page, add the page import and a `<Route path="..." element={...} />` in `web/src/routes/RecipientsRoutes.tsx`.
2. For a notes child page, add the page import and route in `web/src/routes/NotesRoutes.tsx`.
3. For a new protected root under `/app`, add its route inside the existing `<Route element={<ProtectedRoute />}>` in `web/src/app/App.tsx`.
4. Update the path-to-document-title mapping in `web/src/components/AppShell.tsx` when the new path needs a specific title.
5. Add English and Arabic translation keys in the relevant files described in section 3.

**Pattern file:** `web/src/routes/NotesRoutes.tsx` for a nested authenticated workflow route.

## 2. Home / overview

**Frontend**

- `web/src/features/overview/WorkspaceOverview.tsx` — exported `WorkspaceOverview`; reads selected calendar date from URL search params; calls recipient list and note-history APIs; renders recorded/missing/error note status per recipient.
- `web/src/features/overview/overview.css` — overview styles.
- `web/src/tests/Overview.test.tsx` — Vitest/Testing Library overview tests and fixtures.

**Backend/API**

- No dedicated overview controller or overview DTO was found.
- The page uses recipient list and note-history endpoints: `GET /api/recipients` and `GET /api/notes?recipientId&from&to&page&size` (recipientId is optional).
- The response types used by the web client are `Recipient[]` and `NotesHistoryPage` containing `DailyNote[]`.

**Pattern file:** `web/src/features/overview/WorkspaceOverview.tsx`.

## 3. Recipients

**Frontend**

- `web/src/api/recipients.ts` — `Recipient`, `RecipientInput`, `RecipientLifecycleStatus`, `RecipientListFilter`, `createRecipientsApi`, `validateRecipient`, `recipientErrorMessage`.
- `web/src/features/recipients/RecipientsPages.tsx` — `RecipientsListPage`, `CreateRecipientPage`, `RecipientPage`, and not-found page components.
- `web/src/features/recipients/RecipientForm.tsx` — recipient create/edit form.
- `web/src/features/recipients/RecipientLifecycleDialog.tsx` — archive/restore confirmation UI.
- `web/src/features/recipients/useRecipientResource.ts` — recipient resource hook.
- `web/src/features/recipients/recipients.css` — feature styles.
- Tests: `web/src/tests/Recipients.test.tsx`, `web/src/tests/recipientsApi.test.ts`.

**Backend**

- `backend/src/main/java/com/caregiver/recipient/RecipientController.java` — REST mappings.
- `RecipientService.java`, `CareRecipient.java`, `CareRecipientRepository.java`, `RecipientLifecycleStatus.java`.
- DTOs: `RecipientRequest`, `RecipientResponse`.

**Endpoints**

- `POST /api/recipients` — body `RecipientRequest`; response `RecipientResponse`.
- `GET /api/recipients` — optional `status` query parameter (`RecipientLifecycleStatus`); response `List<RecipientResponse>`.
- `GET /api/recipients/{id}` — response `RecipientResponse`.
- `PUT /api/recipients/{id}` — body `RecipientRequest`; response `RecipientResponse`.
- `PATCH /api/recipients/{id}/archive` — response `RecipientResponse`.
- `PATCH /api/recipients/{id}/restore` — response `RecipientResponse`.
- `PATCH /api/recipients/{id}/deactivate` — backward-compatible archive alias; response `RecipientResponse`.

**Pattern file:** `web/src/api/recipients.ts` for typed endpoint parsing and validation; UI pattern `web/src/features/recipients/RecipientForm.tsx`.

## 4. Notes and corrections

**Frontend**

- `web/src/api/notes.ts` — `DailyNote`, `NoteAddendum`, `CreateNoteInput`, `AddendumInput`, `CorrectionEvent`, `CorrectionHistory`, `NotesHistoryQuery`, `NotesHistoryPage`, and `createNotesApi`.
- `web/src/api/observation.ts` — observation-state and verification types/helpers: `ObservationState`, `VerificationValue`, `Observation<T>`, `NoteObservations`, `ObservationSet`, `emptyObservation`, `hasRecordedValue`.
- `web/src/features/notes/NoteForm.tsx` — note editor.
- `web/src/features/notes/noteForm.ts` — note-form pure helpers.
- `web/src/features/notes/NotesPages.tsx` — note create/detail pages.
- `web/src/features/notes/ObservationStateField.tsx`, `ObservationValues.tsx`, `CorrectionEditor.tsx`, `CorrectionHistory.tsx`, `useNotes.ts`.
- `web/src/features/notes/notes.css`.
- Tests: `web/src/tests/Notes.test.tsx`, `web/src/tests/notesApi.test.ts`.

**Backend**

- `backend/src/main/java/com/caregiver/notes/NoteController.java` — REST mappings.
- `DailyNoteService.java`, `DailyNote.java`, `DailyNoteRepository.java`, `NoteCorrectionService.java`, `NoteCorrectionEvent.java`, `NoteAddendum.java`, `NoteSignalService.java`.
- DTOs and request types: `NoteRequest`, `NoteResponse`, `NoteDetailResponse`, `AddendumRequest`, `NoteAddendumResponse`, `CorrectionRequest`, `CorrectionHistoryResponse`, `NoteCorrectionEventResponse`, `ObservationSetRequest`, `NoteObservations`, `VerificationState`.

**Endpoints**

- `POST /api/notes` — body `NoteRequest`; response `NoteResponse`; optional `Idempotency-Key` header.
- `GET /api/notes/{id}` — response `NoteDetailResponse`.
- `GET /api/notes/by-recipient/{recipientId}?date=YYYY-MM-DD` — response `NoteDetailResponse`.
- `GET /api/notes?recipientId=&from=&to=&page=&size=` — response Spring `Page<NoteResponse>`; `from` and `to` are ISO dates.
- `POST /api/notes/{id}/addenda` — body `AddendumRequest`; response `NoteAddendumResponse`; optional `Idempotency-Key`.
- `GET /api/notes/{id}/addenda` — response `List<NoteAddendumResponse>`.
- `POST /api/notes/{id}/corrections` — body `CorrectionRequest`; response `CorrectionHistoryResponse`; optional `Idempotency-Key`.
- `GET /api/notes/{id}/corrections` — response `CorrectionHistoryResponse`.
- `GET /api/notes/signals?recipientId=&from=&to=` — response `List<NoteSignals>`.

**Observation invariant verified in `web/src/api/observation.ts`:** the supported states are `RECORDED`, `UNKNOWN`, `NOT_RECORDED`, `NOT_APPLICABLE`, and `LEGACY_AMBIGUOUS`. False, zero, and empty text can be valid recorded values. Verification is independent: `VERIFIED`, `UNVERIFIED`, `NOT_ASSERTED`. Do not coerce missing values to zero, false, or normal.

**Pattern file:** `web/src/features/notes/NoteForm.tsx` for form behavior; `backend/src/main/java/com/caregiver/notes/NoteController.java` for API mapping.

## 5. History

**Frontend**

- `web/src/features/history/HistoryPage.tsx` — exported `HistoryPage`; reads/writes URL query state and renders paginated history.
- `HistoryFilterForm.tsx`, `HistoryNoteCard.tsx`, `historyFilters.ts`, `history.css`.
- API is `createNotesApi().history` in `web/src/api/notes.ts`.
- Tests: `web/src/tests/History.test.tsx`, `web/src/tests/historyApi.test.ts`; Playwright files under `web/src/tests/history/` and `web/src/tests/history-live/`.

**Backend**

- There is no separate history controller. History is handled by `NoteController.history` using `GET /api/notes`.
- DTOs are `NoteResponse` and Spring `Page<NoteResponse>`.

**Pattern file:** `web/src/features/history/HistoryPage.tsx`.

## 6. Trends

**Backend**

- `backend/src/main/java/com/caregiver/ai/TrendCalculator.java` — `computeSingle`, `computeTrends`, and counters for falls, high pain, missed medication, and poor appetite.
- `TrendResult.java` — response record with metric, previous count, current count, and delta.
- Trends are included in `SummaryResult.trends`; summary service constructs the result.

**Frontend**

- `web/src/api/summary.ts` defines `SummaryTrend` and parses trend data.
- `web/src/features/summary/SummaryResult.tsx` renders the trends section.
- No separate `web/src/features/trends/` page/component or standalone trend endpoint was found in the inspected tree.
- Backend test: `backend/src/test/java/com/caregiver/ai/TrendCalculatorTest.java`.

**Endpoint:** trends are returned as part of `POST /api/summaries` in `SummaryResult`; no dedicated trend endpoint was found.

**Pattern file:** `backend/src/main/java/com/caregiver/ai/TrendCalculator.java`.

## 7. Summary and evidence

**Frontend**

- `web/src/api/summary.ts` — `SummaryRequest`, `SummaryObservation`, `SummaryUncertainty`, `SummaryContent`, `SummarySafety`, `SummaryTrend`, `SummaryResponse`, `readSummarySafety`, `readSummaryResponse`, `retainSafety`, and `findSummaryEndpoint`.
- `web/src/features/summary/SummaryPage.tsx` — summary request screen.
- `web/src/features/summary/SummaryResult.tsx` — summary, uncertainties, evidence, trends rendering.
- `web/src/features/summary/summary.css`.
- Tests: `web/src/tests/Summary.test.tsx`, `web/src/tests/summaryApi.test.ts`; Playwright files under `web/src/tests/summary/` and `web/src/tests/summary-live/`.

**Backend**

- `backend/src/main/java/com/caregiver/ai/SummaryController.java` — `POST /api/summaries`.
- `SummaryApplicationService.java`, `SummarySourceLoader.java`, `SummaryDataBuilder.java`, `SummaryService.java`, `EvidenceValidator.java`, `SummaryRequest.java`, `SummaryResult.java`, `GroundedSummary.java`, `SummaryObservation.java`, `SummaryUncertainty.java`.
- Request DTO: `SummaryRequest(recipientId, periodEndInclusive, periodDays)`; allowed periods are 7, 14, and 30.
- Response DTO: `SummaryResult(summary, safety, trends)`.
- Tests include `backend/src/test/java/com/caregiver/ai/SummaryServiceTest.java`, `EvidenceValidatorTest.java`, `SummaryRequestTest.java`, `SummaryPlanHttpTest.java`.

**Endpoint:** `POST /api/summaries` — body `SummaryRequest`; response `SummaryResult`.

**Pattern file:** `backend/src/main/java/com/caregiver/ai/SummaryApplicationService.java`.

## 8. BYOK AI

**Found in current code**

- `backend/src/main/java/com/caregiver/ai/LlmClient.java`, `ChatCompletionsLlmClient.java`, `FailoverLlmClient.java`, `LlmRequest.java`, `ProviderDeadline.java`.
- `backend/src/main/java/com/caregiver/config/LlmProperties.java` — `llm.*` properties; comments identify `GEMINI_API_KEY` and `OPENROUTER_API_KEY` environment variables.
- `backend/src/main/java/com/caregiver/config/LlmClientConfig.java` — creates a configured primary provider and optional backup provider; returns `FailoverLlmClient`.
- `backend/src/main/java/com/caregiver/ai/PlanGenerationController.java` — `POST /api/plans/generate`, body `SummaryRequest`; result record `PlanGenerationController.Result(plan, aiUnavailable, safety)`.

**NOT FOUND (searched `backend/src/main/java/com/caregiver/ai/`, `backend/src/main/java/com/caregiver/config/`, `web/src/api/`, and all `web/src/features/` paths):**

- A caregiver-supplied key field in a request DTO.
- A BYOK-specific controller/endpoint or per-request provider credential object.
- A web BYOK settings page or key-management feature.
- A BYOK key store/client module.
- A backend BYOK key-persistence entity/table.
- A key-removal endpoint.
- A documented per-request key-redaction/discard lifecycle.

Current implementation instead has provider keys in backend environment configuration and an optional configured provider failover. Do not describe that as caregiver BYOK.

**Pattern file:** NOT FOUND for per-request BYOK. The nearest provider-call implementation is `backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java`; it is not a key-management pattern.

## 9. Safety signals and acknowledgement

**Found in current code**

- `backend/src/main/java/com/caregiver/ai/SafetySignalEvaluator.java` — deterministic evaluation rules.
- `SafetyEvaluation.java`, `SafetySignal.java`, `SafetyRuleId.java`, `backend/src/main/java/com/caregiver/common/Severity.java`.
- `backend/src/main/java/com/caregiver/notes/NoteSignalService.java` and `NoteController.signals` serve `GET /api/notes/signals?recipientId=&from=&to=`; response `List<NoteSignals>`.
- `web/src/features/safety/SummarySafety.tsx` — `SummarySafety`; displays signals in the summary UI.
- `web/src/api/summary.ts` — `SummarySafety`, `SafetySignal`, and `readSummarySafety`.
- Tests: `backend/src/test/java/com/caregiver/ai/SafetySignalEvaluatorTest.java`, `SafetyRegressionTest.java`, `web/src/tests/Summary.test.tsx`.

**NOT FOUND:** a safety-signal acknowledgement endpoint, acknowledgement DTO/entity, or acknowledgement UI was not found in the inspected backend/controller and web feature trees. Do not invent an existing acknowledgement API.

**Pattern file:** `backend/src/main/java/com/caregiver/ai/SafetySignalEvaluator.java` for deterministic rule evaluation.

## 10. Plans

**Frontend**

- `web/src/api/plans.ts` — `CarePlan`, `PlanAction`, `PlanChange`, `PlanDocument`, `PlanEditRequest`, `PlanGeneration`, `createPlansApi`, `readPlan`.
- `web/src/features/plans/PlansPage.tsx` — `PlansPage`, `PlanWorkspace`, `Confirmation`.
- `PlanCard.tsx`, `PlanEditor.tsx`, `PlanGenerationForm.tsx`, `plans.css`.
- Tests: `web/src/tests/Plans.test.tsx`, `web/src/tests/plansApi.test.ts`; Playwright paths under `web/src/tests/plans/` and `web/src/tests/plans-live/`.

**Backend**

- `backend/src/main/java/com/caregiver/plans/PlanController.java` — plan persistence/lifecycle routes.
- `PlanService.java`, `CarePlan.java`, `CarePlanRepository.java`, `StoredPlanVersion.java`, `PlanDocument.java`, `PlanDocumentCodec.java`, `PlanStatus.java`, `PlanSource.java`, `PlanAction.java`.
- DTOs: `PlanRequest`, `EditPlanRequest`, `PlanResponse`.
- AI generation: `backend/src/main/java/com/caregiver/ai/PlanGenerationController.java`, `PlanGenerationService.java`, `PlanSuggestionRequest.java`, `PlanSuggestionResult.java`.

**Endpoints**

- `POST /api/recipients/{recipientId}/plans/suggestions` — `PlanRequest` → `PlanResponse`.
- `GET /api/recipients/{recipientId}/plans/active` → `PlanResponse`.
- `GET /api/recipients/{recipientId}/plans/history` → `List<PlanResponse>`.
- `POST /api/recipients/{recipientId}/plans/{planId}/accept` → `PlanResponse`.
- `POST /api/recipients/{recipientId}/plans/{planId}/edit-and-accept` — `EditPlanRequest` → `PlanResponse`.
- `POST /api/recipients/{recipientId}/plans/{planId}/dismiss` → `PlanResponse`.
- `POST /api/plans/generate` — `SummaryRequest` → `PlanGenerationController.Result(plan, aiUnavailable, safety)`.

**Pattern file:** `web/src/features/plans/PlansPage.tsx` for the current read/mutate UI workflow; backend pattern `backend/src/main/java/com/caregiver/plans/PlanController.java`.

## 11. Design system and theme

**Web styles actually loaded**

- `web/src/main.tsx` imports `web/src/styles/base.css`.
- `web/src/components/AppShell.tsx` imports `web/src/components/AppShell.css`.
- Feature styles are plain CSS files: `web/src/features/auth/auth.css`, `overview/overview.css`, `recipients/recipients.css`, `notes/notes.css`, `history/history.css`, `summary/summary.css`, and `plans/plans.css`.
- `web/src/styles/base.css` defines root styles and custom properties including `--color-ink`, `--color-muted`, `--color-green`, `--color-green-dark`, `--color-border`, `--color-surface`, `--color-soft`, and `--radius`.
- The application uses ordinary CSS files. No CSS Modules imports or Tailwind dependency/configuration were found in the inspected web tree/package manifest.
- `docs/design-system/tokens.css` exists and defines semantic CSS variables including `--color-primary`, `--color-background`, `--color-surface`, `--caregiver-primary`, `--caregiver-ink`, `--caregiver-muted`, `--space-xs`, `--space-sm`, `--space-md`, `--space-lg`, `--space-xl`, `--space-xxl`, `--radius-card`, `--radius-control`, and `--size-touch-target`. It defines dark/system selectors and palette selectors.
- `web/src/main.tsx` currently imports `./styles/base.css`; it does not import `docs/design-system/tokens.css`.
- `web/src/main.tsx` imports DM Sans Variable and Noto Sans Arabic Variable packages. `web/package.json` does not include an IBM Plex Sans Arabic font package.
- No web theme provider/context or web settings feature was found under `web/src/`; `docs/design-system/tokens.css` contains CSS selectors but no inspected web code applies a `data-theme` or `data-palette` preference.

**Design documentation**

- `docs/design-system/DESIGN-STYLE.md` — Android visual source guide; section 11 documents shallow web observations and notes current web fonts/styles.
- `docs/design-system/design-tokens.json` — existing token JSON file.
- `docs/design-system/tokens.css` — semantic CSS variable definitions.

**Token instructions for web design issues**

Use the existing semantic names from `docs/design-system/tokens.css`: `--color-background`, `--color-on-background`, `--color-surface`, `--color-on-surface`, `--color-primary`, `--color-on-primary`, `--caregiver-ink`, `--caregiver-muted`, `--caregiver-border`, spacing `--space-xs/sm/md/lg/xl/xxl`, radii `--radius-card` and `--radius-control`, and `--size-touch-target`. Read `docs/design-system/DESIGN-STYLE.md` before changing colors; that document records an Android/web discrepancy and warns against silently treating status colors as clinical severity. Do not add duplicate token values under new names.

**Pattern file:** `web/src/styles/base.css` for existing global styles; `docs/design-system/tokens.css` for the existing semantic token vocabulary.

## 12. Authentication

**Frontend**

- `web/src/features/auth/AuthProvider.tsx` — `AuthProvider`, `useAuth`, `useAuthStatus`; creates public and bearer-authenticated API clients and handles login/logout/expired sessions.
- `web/src/features/auth/LoginPage.tsx` — `LoginPage`.
- `web/src/features/auth/session.ts` — `parseSession`, `storeSession`, `restoreSession`, `clearStoredSession`; uses `sessionStorage` key `caregiver-auth-session-v1`, not localStorage.
- `web/src/routes/authRedirect.ts` — safe return-path helper.
- `web/src/features/auth/auth.css`.
- Tests: `web/src/features/auth/Auth.test.tsx`, `web/src/app/App.test.tsx`.

**Backend**

- `backend/src/main/java/com/caregiver/auth/AuthController.java`, `AuthService.java`, `JwtService.java`, `JwtAuthenticationFilter.java`, `AuthContext.java`, `CaregiverPrincipal.java`.
- DTOs: `RegisterRequest`, `LoginRequest`, `AuthResponse`, `ErrorResponse`.

**Endpoints**

- `POST /api/auth/register` — `RegisterRequest` → `AuthResponse` (201).
- `POST /api/auth/login` — `LoginRequest` → `AuthResponse` (200).

**Pattern file:** `web/src/features/auth/AuthProvider.tsx` for web session/API integration; backend pattern `backend/src/main/java/com/caregiver/auth/AuthController.java`.

## 13. Tests and exact commands

**Web**

- Unit/component/API tests use Vitest (`vitest run` in `web/package.json`), Testing Library (`@testing-library/react`, `user-event`, `jest-dom`) and jsdom.
- Representative unit files: `web/src/app/App.test.tsx`; `web/src/tests/Overview.test.tsx`, `Recipients.test.tsx`, `Notes.test.tsx`, `History.test.tsx`, `Summary.test.tsx`, `Plans.test.tsx`; API tests are named `*Api.test.ts`.
- Playwright is declared as `@playwright/test`. Existing browser test locations include `web/src/tests/recipients/`, `notes/`, `history/`, `summary/`, `plans/`, and `workflow-live/`. Config files exist in those folders.
- Unit command copied from `web/package.json`: `cd web && npm test` (script: `vitest run`).
- All Playwright tests command copied from `web/package.json`: `cd web && npm run test:e2e` (script builds then runs `playwright test`).
- Live workflow Playwright command copied from `web/package.json`: `cd web && npm run test:e2e:live` (script builds then uses `src/tests/workflow-live/playwright.config.ts`).
- TypeScript command copied from `web/package.json`: `cd web && npm run typecheck`.

**Backend**

- `backend/pom.xml` uses Spring Boot 3.3.4, Java 21, `spring-boot-starter-test`, AssertJ, H2 test scope, and Maven Surefire through the standard Maven test lifecycle. The tests in `backend/src/test/java/com/caregiver/` use JUnit Jupiter/Spring Boot test conventions.
- Representative test locations: `backend/src/test/java/com/caregiver/auth/`, `recipient/`, `notes/`, `plans/`, and `ai/`.
- Standard Maven test command for this `pom.xml`: `cd backend && mvn test`.
- **NOT FOUND:** no `mvnw` or `backend/mvnw` path exists in the inspected repository tree. `README.md` documents Maven but does not document a backend test command; it gives a backend start command instead. Therefore `cd backend && mvn test` is the standard Maven lifecycle invocation, not a README script.
- **NOT FOUND:** Testcontainers dependencies are not listed in `backend/pom.xml`, despite the README's testing-strategy text mentioning Testcontainers.

**Other**

- `git diff --check` is used in issue documentation verification; no additional repo-specific command alias was found.
- No test command was run while creating this map; commands above are read from the checked package/pom configuration or are the standard Maven test goal.

## 14. NOT FOUND index

- Standalone trend page/endpoint — searched `web/src/features/`, `web/src/api/`, `backend/src/main/java/com/caregiver/`; trend results currently travel inside summary responses.
- Caregiver BYOK screen, per-request BYOK endpoint/DTO, key management or key-removal API — searched `web/src/api/`, `web/src/features/`, `backend/src/main/java/com/caregiver/ai/`, and `backend/src/main/java/com/caregiver/config/`.
- Settings feature/theme provider — searched the complete `web/src/` path tree; no settings feature or theme context was present.
- Safety acknowledgement endpoint/entity/UI — searched backend controller/domain paths and `web/src/features/`.
- Standalone medication domain/API package — no `backend/src/main/java/com/caregiver/medication/` or `web/src/features/medication/` path was present. `MedicationEntry.java` exists only under `backend/src/main/java/com/caregiver/plans/` as plan content.
- IBM Plex Sans Arabic web dependency/font file — not found in `web/package.json` or inspected `web/src/main.tsx`; current web imports DM Sans Variable and Noto Sans Arabic Variable.
