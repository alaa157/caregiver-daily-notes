# Test Strategy — Rafeek

Living document for the DEPI Software Testing track capstone. Last verified: **2026-11-19**, against `main` at commit `f44aaf78`, executed on the project's Linux build VM (Debian 13, OpenJDK 21.0.12, Node 18+). Every count and result below was measured, not estimated; suites that were not executed in this pass are marked as inventory-only.

---

## 1. Purpose and scope

Rafeek ships three testable deliverables from one domain model:

| Deliverable | What it is |
|---|---|
| `backend/` | Spring Boot REST API — the authority for business rules, ownership, safety signals, and audit |
| `web/` | React + TypeScript caregiver workspace (EN/AR, RTL) |
| `android/` | Native Kotlin/Compose app — local-first standalone release plus an offline demo |

Quality goal: a caregiver can trust that (a) the record is never silently altered, (b) safety flags are never suppressed by a model, (c) no other caregiver can see their data, and (d) every AI output is grounded, schema-valid, and medication-safe. All testing uses **synthetic data only**.

## 2. Risk focus

Testing effort is weighted toward the failure modes that would harm a care recipient or leak their data:

| Risk | Primary guards |
|---|---|
| A model suppresses or invents a safety flag | Deterministic backend red-flag tests; eval harness `redFlagRecall == 1.0` |
| Silent record alteration | Append-only immutability tests; addenda/correction replay; migration provenance tests |
| Cross-caregiver data access | Ownership tests (foreign resources return `404`, never `403`); JWT verification tests |
| Model-authored medication advice | Medication hard-lock tests; eval harness `medicationSafetyRate == 1.0` |
| Provider-key leakage | BYOK non-persistence tests; error-body scrubbing tests; Android Keystore instrumentation |
| Duplicate or conflicting writes | `Idempotency-Key` contract tests (replay vs `409 IDEMPOTENCY_CONFLICT`) |
| RTL/bidi corruption, inaccessible UI | Arabic string-parity tests; locale workflow specs; axe-core checks in every viewport project |
| Ungrounded or malformed AI output | Eval harness `groundednessRate == 1.0`, `schemaValidRate == 1.0` |

## 3. Test inventory (verified 2026-11-19)

| Layer | Framework | Scale | Command |
|---|---|---|---|
| Backend unit + integration | JUnit 5, Spring Boot Test, Flyway, in-memory H2 (`MODE=PostgreSQL`) | **93 test classes — 632 `@Test` methods + 12 parameterized** | `mvn -f backend/pom.xml test` |
| Web unit | Vitest 2.1 + Testing Library (jsdom) | **49 files — 1,080 executed cases** | `cd web && npm test` |
| Web static | TypeScript 5.6 compiler; Vite build + metadata check | whole codebase | `npm run typecheck` / `npm run build` |
| Web browser | Playwright 1.64, axe-core; 4 viewport projects (desktop 1280×900, tablet 768×1024, mobile 390×844, compact 320×740) | **81 test definitions × 4 viewports = 324 runs** (11 e2e specs + browser contracts); EN/AR workflows covered inside specs | `npm run test:e2e` |
| Web live workflow | Playwright, separate config, real backend | end-to-end caregiver workflow | `npm run test:e2e:live` |
| Android JVM | JUnit on-device-free | **194 test classes — 1,651 `@Test` methods** | `cd android && ./gradlew :app:testDebugUnitTest` |
| Android instrumentation | Compose UI Test (`androidx.compose.ui.test`) | **35 test classes — 179 `@Test` methods** | `./gradlew :app:connectedDebugAndroidTest` (cloud device) |
| AI evaluation | Deterministic harness over golden JSON | **22 cases** (`docs/ai/golden/`), 4 metrics enforced at `1.0` | runs inside the backend suite (`GoldenDatasetTest`, `EvalMetricsTest`, `EvalReportTest`) |

Backend integration tests boot real Spring contexts against per-test in-memory H2 databases with Flyway migrations applied and `ddl-auto: validate` — no external database is required for `mvn test`.

## 4. Coverage (JaCoCo 0.8.12)

Measured 2026-11-19 with the suite in its current state (§5):

| Counter | Covered | Total | % |
|---|---|---|---|
| Lines | 1,841 | 3,661 | **50.3%** |
| Instructions | 9,599 | 19,194 | 50.0% |
| Branches | 888 | 2,030 | 43.7% |
| Methods | 396 | 885 | 44.7% |
| Classes | 126 | 213 | 59.2% |

**Caveat:** 325 test errors (§6, D1) prevent the Spring integration layer from executing, so this figure is a floor, not the suite's true reach. Re-measure after D1 is fixed. Web and Android coverage reporting are not yet configured (§9).

Reproduce (identical to the CI invocation):

```bash
mvn -f backend/pom.xml org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test \
    org.jacoco:jacoco-maven-plugin:0.8.12:report
# report: backend/target/site/jacoco/index.html
```

## 5. Execution status (local run, 2026-11-19)

| Suite | Result |
|---|---|
| Backend (`mvn test`, failure-ignore) | **700 run — 371 passed, 4 assertion failures, 325 errors.** All 325 errors trace to one root cause (D1). |
| AI evaluation + deterministic safety | **Green.** `GoldenDatasetTest` (3), `EvalMetricsTest` (4), `EvalReportTest` (2), `SafetySignalEvaluatorTest` (15) all pass; the four metrics hold at `1.0`. |
| Web unit (`vitest run`) | **1,080 run — 971 passed, 109 failed across 19 files** (D2). Run under concurrent build load; a 32-failure subset in `App`/`Settings`/`LandingRoute` was previously confirmed in an isolated run. Authoritative re-run pending in CI. |
| Web typecheck / build | Green (verified 2026-11-18 during the rebrand pass). |
| Playwright browser, live workflow | **Inventory-only this pass** — execution requires the built web app plus a running backend; counts verified statically. |
| Android JVM / instrumentation | **Inventory-only this pass** — last full JVM execution was during the rebrand build; instrumentation runs on Revyl cloud devices, not yet in CI. |

Backend assertion failures (the 4, distinct from the D1 cascade):

1. `SummaryServiceTest.excludesNotesOutsideTheInclusiveSummaryWindow`
2. `SummaryServiceTest.periodBoundaries_useRequestedWindowOnly`
3. `TokenBucketLimiterTest.refillsOverTime` (timing-sensitive)
4. `ChatCompletionsLlmClientTest.nonOpenAiRequestBodies_remainByteForByteUnchanged`

## 6. Open defects

Full defect reports with evidence and repro scripts are the next deliverable (`docs/testing/defects/`); each will be filed as a GitHub issue. Summary of what is currently open:

| ID | Severity | Component | Summary |
|---|---|---|---|
| D1 | **Blocker (test layer)** | backend / migrations | `V17__add_medication_intake_event_void.sql` uses a multi-column `ALTER TABLE … ADD COLUMN voided_at TIMESTAMP WITH TIME ZONE …` that H2 2.2.224 rejects even in PostgreSQL mode. `flywayInitializer` fails, cascading into **325 errors** across every Spring-context test class. Production (PostgreSQL) is unaffected. Fix options: H2-compatible migration SQL, split ALTER statements, or move integration tests to real PostgreSQL (Testcontainers). |
| D2 | Major | web / unit | 109 failing unit cases across 19 files; dominant pattern is the BYOK connection-test credential flow and navigation/activity-feed drift (e.g. `App.test` 28, `Summary`/`Overview`/`Recipients` 17 each). Systematic workflow drift, not random flakiness. |
| D3 | Minor–Major | backend | The 4 assertion failures in §5: summary window boundaries (×2), rate-limiter refill timing, non-OpenAI request-body byte contract. |
| D4 | Major | android / AI | BYOK setup reports "Your AI key was rejected" with a verified-valid OpenRouter key. Typing fidelity proven (exact 73-char match); the app's exact request replayed from the VM returns HTTP 200; all bundled model IDs exist on OpenRouter. Suspected app-side request handling. Found on a Revyl cloud device. |
| D5 | Major | backend / AI | Plan generation fails `invalid_model_output` across three models: the prompt never states the output JSON schema, models return `actions`, and `StrictJsonParser` requires `changes`. Reproduced with `openai/gpt-4o-mini`, `openai/gpt-6.2-sol`, `anthropic/claude-sonnet-5`. Proposed fix: explicit schema instruction in the plan prompt. |

## 7. Defect management

- One defect or feature per GitHub issue; issues are independently actionable and self-contained. **542 issues closed** to date.
- Labels: priority, area, week, MoSCoW.
- Fix branches: `fix/<area>-<issue-number>-<slug>`; pull requests target `main`.
- Defect reports require: environment, repro steps, expected vs actual, evidence (logs/screenshots/repro script), root cause when known, and status.
- A separate problem found mid-fix is filed as a separate issue, never folded into the current change.

## 8. Environments and test data

| Environment | Use |
|---|---|
| In-memory H2 (PostgreSQL mode) | Backend unit/integration tests; per-test isolated databases |
| Local PostgreSQL / Supabase | Live backend runs, Playwright live-workflow suite, screenshot tours |
| Android offline demo | Canned in-memory data, no network, no login (long-press the logo) |
| Android standalone release | Local accounts, per-account Room databases, Keystore-held keys |
| Revyl cloud devices | Android instrumentation and manual exploratory runs |

All data is synthetic. No real patient or care-recipient data is used in any test, screenshot, or demo.

## 9. Known gaps and next milestones

1. **CI is currently disabled** (`ci.yml`, `disabled_manually`). Re-enable after D1–D3 are fixed; wire the eval-report artifact upload that `docs/ai/eval-harness.md` describes.
2. D1 blocks the backend integration layer — highest-leverage fix in the repo.
3. Android instrumentation is not yet part of CI (cloud-device execution is manual).
4. No coverage gates for web or Android; backend coverage is reported but not gated.
5. No load/performance, penetration, or mutation testing yet — out of scope for the capstone, recorded so the boundary is explicit.
6. Privacy policy and terms documents are absent; the factual data-handling audit is in [`docs/privacy/README.md`](../privacy/README.md).

## 10. Exit criteria (release readiness)

- Backend suite green (including the integration layer) and eval metrics at `1.0`.
- Web unit suite green; typecheck and production build green.
- Playwright green across all four viewport projects, EN and AR workflows, axe checks passing.
- Android JVM suite green; instrumentation smoke pass on a cloud device.
- Every open defect triaged with an issue link; no untracked blockers.

**Current state: not met** — see §5 and §6. This document is updated as each milestone lands.

---

Related: [`docs/ai/eval-harness.md`](../ai/eval-harness.md) · [`docs/ai/golden/`](../ai/golden/) · [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
