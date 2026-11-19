# Test Strategy — Rafeek

Living document for the DEPI Software Testing track capstone. Last verified: **2026-11-19**, against `main` at commit `d70795fc`, executed on the project's Linux build VM (Debian 13, OpenJDK 21.0.12, Node 18+). Every count and result below was measured, not estimated; suites that were not executed in this pass are marked as inventory-only.

---

**Formal deliverable:** [rafeek-test-cases.pdf](rafeek-test-cases.pdf) — the IEEE 829-style Test Case Specification & Execution Report (61 pages) prepared for DEPI review: 27 formal key cases, full executed registers (700 backend, 1,086 web, 22 AI golden), specified E2E/Android inventories, defect summary, and sign-off block.

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
| Web unit | Vitest 2.1 + Testing Library (jsdom) | **49 files — 1,086 executed cases** | `cd web && npm test` |
| Web static | TypeScript 5.6 compiler; Vite build + metadata check | whole codebase | `npm run typecheck` / `npm run build` |
| Web browser | Playwright 1.64, axe-core; 4 viewport projects (desktop 1280×900, tablet 768×1024, mobile 390×844, compact 320×740) | **53 test definitions × 4 viewports = 212 runs** (11 e2e specs + browser contracts); EN/AR workflows covered inside specs | `npm run test:e2e` |
| Web live workflow | Playwright, separate config, real backend | end-to-end caregiver workflow | `npm run test:e2e:live` |
| Android JVM | JUnit on-device-free | **192 test classes — 1,654 `@Test` methods** | `cd android && ./gradlew :app:testDebugUnitTest` |
| Android instrumentation | Compose UI Test (`androidx.compose.ui.test`) | **35 test classes — 179 `@Test` methods** | `./gradlew :app:connectedDebugAndroidTest` (cloud device) |
| AI evaluation | Deterministic harness over golden JSON | **22 cases** (`docs/ai/golden/`), 4 metrics enforced at `1.0` | runs inside the backend suite (`GoldenDatasetTest`, `EvalMetricsTest`, `EvalReportTest`) |

Backend integration tests boot real Spring contexts against per-test in-memory H2 databases with Flyway migrations applied and `ddl-auto: validate` — no external database is required for `mvn test`.

## 4. Coverage (JaCoCo 0.8.12)

Measured 2026-11-19 with the suite fully green (700/700, run 7):

| Counter | Covered | Total | % |
|---|---|---|---|
| Lines | 3,423 | 3,670 | **93.3%** |
| Instructions | 17,806 | 19,228 | 92.6% |
| Branches | 1,560 | 2,034 | 76.7% |
| Methods | 830 | 885 | 93.8% |
| Classes | 212 | 213 | 99.5% |

An earlier measurement under the DEF-001 cascade read 50.3% lines — a floor, because the integration layer could not execute. With DEF-001 fixed the true reach is the table above. Web and Android coverage reporting are not yet configured (§9).

Reproduce (identical to the CI invocation):

```bash
mvn -f backend/pom.xml org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test \
    org.jacoco:jacoco-maven-plugin:0.8.12:report
# report: backend/target/site/jacoco/index.html
```

## 5. Execution status (local run, 2026-11-19)

| Suite | Result |
|---|---|
| Backend (`mvn test` + JaCoCo) | **700 run — 700 passed, 0 failures, 0 errors** (run 7, after DEF-001/003/004/005/006 fixes). |
| AI evaluation + deterministic safety | **Green.** `GoldenDatasetTest` (3), `EvalMetricsTest` (4), `EvalReportTest` (2), `SafetySignalEvaluatorTest` (15) all pass; the four metrics hold at `1.0`. |
| Web unit (`vitest run`) | **1,086 run — 1,086 passed, 0 failures (49 files)** after DEF-002 test-only alignments. |
| Web typecheck / build | Green (verified 2026-11-18 during the rebrand pass). |
| Playwright browser, live workflow | **Inventory-only this pass** — execution requires the built web app plus a running backend; counts verified statically. |
| Android JVM / instrumentation | **Inventory-only this pass** — last full JVM execution was during the rebrand build; instrumentation runs on Revyl cloud devices, not yet in CI. |

The earlier failure inventory (325 cascade errors + 24 assertion failures) is fully resolved; each root cause is documented in the [defect register](defects/README.md).

## 6. Defect register

Defects found by this testing effort are documented with evidence, repro, fix commits, and verification in [`docs/testing/defects/`](defects/README.md), each tracked as a GitHub issue:

| ID | Issue | Summary | Status |
|---|---|---|---|
| DEF-001 | [#1095](https://github.com/alaa157/caregiver-daily-notes/issues/1095) | V17 migration incompatible with H2 — 325 cascading errors | fixed |
| DEF-002 | [#1096](https://github.com/alaa157/caregiver-daily-notes/issues/1096) | 109 stale web unit tests after UI contract changes | fixed |
| DEF-003 | [#1097](https://github.com/alaa157/caregiver-daily-notes/issues/1097) | Backend suites/docs stale after the unified error contract | fixed |
| DEF-004 | [#1098](https://github.com/alaa157/caregiver-daily-notes/issues/1098) | Typed corrections 500 on never-recorded fields | fixed |
| DEF-005 | [#1099](https://github.com/alaa157/caregiver-daily-notes/issues/1099) | Nondeterministic LLM request bodies (Map.of) | fixed |
| DEF-006 | [#1100](https://github.com/alaa157/caregiver-daily-notes/issues/1100) | Plan prompt never stated the output schema | fixed (live-verified) |
| DEF-007 | [#1101](https://github.com/alaa157/caregiver-daily-notes/issues/1101) | Android key-error classification | fixed (device verification pending) |

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

1. **CI is currently disabled** (`ci.yml`, `disabled_manually`). The backend suite is now green locally, so re-enablement is the next milestone; wire the eval-report artifact upload that `docs/ai/eval-harness.md` describes.
2. Web and backend suites are green locally; the CI gate itself is the remaining step.
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

**Current state: met locally** — backend (700/700), web unit (1,086/1,086), eval, and typecheck/build are green; only the CI gate itself remains (workflow disabled manually). This document is updated as each milestone lands.

---

Related: [`docs/ai/eval-harness.md`](../ai/eval-harness.md) · [`docs/ai/golden/`](../ai/golden/) · [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml)
