# Web Feature and Data Parity Implementation Roadmap

**Repository:** `alaa157/caregiver-daily-notes`  
**Target documentation path:** `docs/product-alignment/web-parity-roadmap/README.md`  
**Planning baseline:** Phase 1, Phase 2, and Phase 3A documentation retrieved from `main` on 2026-11-03  
**Document status:** Proposed implementation roadmap — product-owner approval required for unresolved decisions  
**Scope:** Web application parity and data/business-rule alignment only; no implementation authorization

---

## 1. Executive Summary

This roadmap defines a dependency-aware sequence for bringing the React/Spring Boot/PostgreSQL Web application closer to the caregiver capabilities already available in the Kotlin/Jetpack Compose Android application.

The objective is **capability and semantic parity**, not identical screens, databases, or infrastructure:

- Android remains a standalone, offline-first application using Room as its primary local store.  
- Android BYOK provider credentials, prompts, consent, and configuration remain on-device and are never sent to the Web backend.  
- Web remains an authenticated React/TypeScript application backed by Spring Boot and PostgreSQL, with backend-managed AI.  
- Both platforms should represent the same care concepts consistently: recipients, daily notes, observations, unknown and missing values, addenda, plans, medication events, dates, safety evidence, and history.  
- Existing working Web capabilities must be preserved and improved, not replaced merely to resemble Android.  
- Historical records, corrections, uncertainty, and provenance must remain traceable.

### Current documented parity position

Phase 1 records **33 feature-matrix rows**:

| Documented status | Rows |
| :---- | ----: |
| MATCH | 1 |
| PARTIAL | 17 |
| DIFFERENT IMPLEMENTATION | 4 |
| MISSING ON WEB | 10 |
| MISSING ON ANDROID | 1 |
| PLACEHOLDER | 0 |
| UNVERIFIED as primary matrix status | 0 |
| **Total** | **33** |

The 33-row count and classifications are reproduced in Section 4. They describe feature rows, not a percentage of completed work or a runtime test result.

The highest-risk gap is not a missing screen. It is the difference between the two platforms' data semantics and business rules: observation nullability, legacy defaults, recipient lifecycle, note/date uniqueness, addenda, deterministic safety rules, care-plan representation, and medication tracking. The Web already has substantial recipient, note, history, plan, summary, and authenticated-account functionality. Those areas should be aligned and strengthened rather than recreated.

### Recommended strategy

The recommended sequence is:

1. Resolve decisions that affect data meaning and define a Web parity contract.  
2. Align recipient, note, observation, addendum, date, and lifecycle semantics with safe database migrations.  
3. Complete the everyday caregiver workflow using the existing Web application.  
4. Align deterministic safety evaluation, summary evidence, and AI failure behavior while keeping provider architectures separate.  
5. Align care-plan structures and version history.  
6. Add medication management only after schedule and intake semantics are approved.  
7. Add Web CSV import/export as a separate portability capability.  
8. Design and implement independent Web backup/restore.  
9. Add useful Web reports, PDF output, printing, and sharing.  
10. Complete settings, localization, accessibility, and responsive UX.  
11. Run a cross-platform parity and release-readiness gate.

Some presentation and settings work can proceed in parallel with data-contract work, but no feature that depends on unresolved clinical or data semantics should be released ahead of those decisions.

**This roadmap does not implement or design the Android-to-Web transfer protocol.** It deliberately stops at Web parity and readiness. Transfer sessions, device linking, manifests, chunks, receipts, synchronization, and transfer-specific authentication remain out of scope.

---

## 2. Scope and Non-Goals

### 2.1 Included

- Web domain model and business-rule alignment with Android.  
- Improvements to existing Web workflows.  
- Missing caregiver capabilities that have a meaningful Web use case.  
- PostgreSQL schema evolution and safe Flyway migrations.  
- Backend API, validation, authorization, and service changes needed by Web parity.  
- Web UI, navigation, settings, localization, accessibility, responsive behavior, and user feedback states.  
- Web medication and care-plan capability, subject to required decisions.  
- CSV import/export, Web backup/restore, reports, PDF, printing, and sharing as separately scoped features.  
- Automated and manual tests that demonstrate semantic parity and preserve existing Web behavior.  
- Documentation of deferred or platform-specific behavior.

### 2.2 Explicit non-goals

This roadmap does **not** authorize or include:

- Android-to-Web transfer sessions or transfer APIs.  
- Device linking, device approval, or transfer-specific credentials.  
- Transfer manifests, immutable transfer previews, chunks, receipts, or retry reconciliation.  
- Automatic synchronization or bidirectional synchronization.  
- Android Room replacement or migration to a shared remote database.  
- Making Android dependent on a Web account or internet access.  
- Sending Android BYOK credentials, custom prompts, provider URLs, or AI preferences to the backend.  
- A shared runtime database or a shared implementation language across platforms.  
- Rewriting functioning Web features without a demonstrated parity or correctness reason.  
- Clinical policy changes without appropriate clinical review and product-owner approval.

CSV, a full Web backup, and Android-to-Web transfer are three distinct capabilities. One must not be used as a substitute for another.

### 2.3 Architecture constraints

1. Android Room remains the primary Android data store.  
2. Android remains usable offline for core local workflows.  
3. Android BYOK stays local and provider-specific.  
4. Web continues to use authenticated backend-managed AI.  
5. PostgreSQL remains the Web persistence layer.  
6. Platform-specific UI and storage are acceptable when data meaning and user-visible outcomes are compatible.  
7. Historical data and provenance are preserved.  
8. Unknown, missing, explicit negative, and ambiguous legacy values remain distinguishable.  
9. Migrations are additive where practical, reversible where feasible, and tested against existing data.  
10. No feature may silently overwrite or reinterpret records.  
11. No automatic cross-platform synchronization is introduced.  
12. Recommendations remain proposals until approved.

---

## 3. Source Documents and Planning Baseline

The following committed documents are the primary source of truth. This roadmap extends them and does not repeat their audits.

| Source | Purpose in this roadmap | Reference |
| :---- | :---- | :---- |
| Phase 1 — Comprehensive Product Discovery and Cross-Platform Alignment | Android product inventory, Web specification, data models, 33-row parity matrix, business logic/AI comparison, localization, import/export/backup readiness | `docs/product-alignment/phase-1/README.md` |
| Phase 2 — Canonical Data Model, Shared API Contracts & Cross-Platform Architecture Design | Proposed canonical semantics, field-level models, decision register PO-01–PO-23, architecture constraints, migration and security principles | `docs/product-alignment/phase-2/README.md` |
| Phase 3A — Cross-Platform Implementation Readiness Audit | Current readiness and gap inventory, Web readiness, data-model risks, implementation sequencing, testing requirements | `docs/product-alignment/phase-3a/README.md` |

### 3.1 Source snapshots recorded by the documents

| Document | Snapshot information recorded |
| :---- | :---- |
| Phase 1 | Android `feat/standalone-byok-ai` at `21876285ad808a6518593ea3974c51b6b1f6bb31`; Web/backend `main` at `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7` |
| Phase 2 | Android source at `21876285ad808a6518593ea3974c51b6b1f6bb31`; Web/backend application source at `14dfff2b7a7049d4ac6285fe340b29c59ba5b595`; Phase 2 documentation commit/current main before its revision recorded as `25c031b2bda50a5daa9cda9cb62f2f4cd306a579` |
| Phase 3A | Android branch HEAD `21876285ad808a6518593ea3974c51b6b1f6bb31`; `main` HEAD `bc9a673e48e0f4955b9435a411e85f84ac39c399` |

These are historical references recorded in the source documents, not a claim that the application code was re-audited at those commits for this roadmap. Phase 3A states that the two commits after the Phase 2 application-source snapshot changed only the Phase 2 documentation, so its Web/backend source assessment remained applicable to that comparison. Revalidate implementation status before each implementation phase if `main` has advanced materially.

### 3.2 Evidence and status vocabulary

This roadmap distinguishes:

- **Documented existing capability:** The source documents identify an implemented Web capability.  
- **Documented partial parity:** A broad capability exists on both platforms but behavior, fields, rules, or workflow differ.  
- **Documented Web gap:** Phase 1 identifies an Android capability without a Web counterpart.  
- **Proposed improvement:** A recommended change, not an approved requirement.  
- **Decision required:** A Phase 2/3A product-owner decision must be made before the affected behavior is frozen.  
- **External review required:** Clinical, security, privacy, or deployment review is needed.  
- **Runtime/operational unknown:** Static source inspection did not establish deployed or runtime behavior.

The three source documents report static inspections. Their existence does not establish that tests pass, a deployed database is durable, or every workflow has been verified on a live installation.

---

## 4. Current Parity Baseline

### 4.1 Exact 33-row matrix

The following feature names and primary classifications preserve the Phase 1 matrix. They are not silently reclassified by this roadmap.

| # | Feature | Phase 1 status |
| ----: | :---- | :---- |
| 1 | Home / overview | PARTIAL |
| 2 | Main navigation | PARTIAL |
| 3 | Onboarding | MISSING ON WEB |
| 4 | Recipient creation | PARTIAL |
| 5 | Recipient edit | PARTIAL |
| 6 | Recipient lifecycle | DIFFERENT IMPLEMENTATION |
| 7 | Recipient relationship data | MISSING ON ANDROID |
| 8 | Daily note creation | PARTIAL |
| 9 | Note detail | PARTIAL |
| 10 | Addendum/correction | PARTIAL |
| 11 | History search/filter | PARTIAL |
| 12 | Care trends | PARTIAL |
| 13 | Summary window selection | PARTIAL |
| 14 | Summary result/evidence | PARTIAL |
| 15 | Deterministic safety logic | DIFFERENT IMPLEMENTATION |
| 16 | AI provider configuration | DIFFERENT IMPLEMENTATION |
| 17 | AI consent and prompt customization | MISSING ON WEB |
| 18 | Plan generation | PARTIAL |
| 19 | Plan lifecycle | PARTIAL |
| 20 | Plan edit and version history | PARTIAL |
| 21 | Medication tracker | MISSING ON WEB |
| 22 | Language choice | MATCH |
| 23 | RTL / mixed text direction | PARTIAL |
| 24 | Theme modes and palettes | MISSING ON WEB |
| 25 | Settings hub | PARTIAL |
| 26 | CSV import/export | MISSING ON WEB |
| 27 | Full backup/restore | MISSING ON WEB |
| 28 | PDF summary/share | MISSING ON WEB |
| 29 | About / app updater | MISSING ON WEB |
| 30 | Backend URL / connection selection | DIFFERENT IMPLEMENTATION |
| 31 | Authentication / access | PARTIAL |
| 32 | Sample/demo data | MISSING ON WEB |
| 33 | Optional offline outbox/transfer | MISSING ON WEB |

**Count check:** 1 MATCH + 17 PARTIAL + 4 DIFFERENT IMPLEMENTATION + 10 MISSING ON WEB + 1 MISSING ON ANDROID = 33. PLACEHOLDER = 0 and UNVERIFIED = 0 as primary matrix statuses.

Row 7 is intentionally retained as **MISSING ON ANDROID**, not a Web gap: the backend/Web model has a relationship field absent from the Android local recipient model. Row 33 is not a request to implement transfer in this roadmap; it is explicitly excluded.

### 4.2 Important documented Web foundations to preserve

The Web application already includes:

- Authenticated caregiver registration/login and account-scoped access.  
- Recipient create, view, update, and deactivate workflows.  
- Daily note creation, detail, and history retrieval with date and recipient filters.  
- Append-only structured note addenda/corrections.  
- Summary generation, evidence presentation, and deterministic server-side safety evaluation.  
- Care-plan generation and version-oriented backend structures.  
- Typed React API modules and a Spring Boot service/API architecture.  
- PostgreSQL persistence and Flyway-managed migrations.  
- Backend-managed AI provider configuration and execution.

These are not to be recreated. The implementation work should focus on missing workflow states, semantic alignment, completeness, and reliability.

### 4.3 Main documented semantic gaps

- Android local recipient lifecycle is archive/unarchive with guarded permanent deletion; Web/backend currently uses active/inactive/deactivate behavior.  
- Web has recipient relationship data that Android's local entity does not currently carry.  
- Android local notes and Web notes differ in nullability, date handling, medication fields, pain/fall fields, and verification semantics.  
- Backend note uniqueness is recipient/date based; Android's local schema does not establish the same recipient/date uniqueness invariant.  
- Android addenda are text events; backend addenda can represent structured corrections to selected observation fields.  
- Android and backend safety evaluators use different signals and rules. Neither is automatically a clinically approved standard.  
- Android care-plan items include free-text content; backend plans use structured action types and parameters.  
- Android has a medication catalogue and per-dose intake marks; Web/backend has no equivalent medication domain.  
- Android BYOK and Web backend-managed AI are intentionally different and must remain separate.  
- Web lacks Android's theme/palette, CSV, full backup/restore, PDF/share, and medication workflows.  
- Web localization has English/Arabic language choice, but RTL and mixed-direction behavior need improvement.  
- Deployment durability, operational backup, and restore guarantees are not established merely by PostgreSQL configuration.

---

## 5. Target Web Product Capabilities

### 5.1 Recipients and caregiver workspace

**Preserve:** Authenticated accounts, caregiver ownership checks, recipient creation, recipient editing, and current list/detail workflows.

**Improve:**

- Define recipient lifecycle semantics in coordination with Android.  
- Present active and archived/inactive recipients consistently once PO-02 is approved.  
- Make destructive actions explicit and distinguish archive/deactivate from permanent deletion.  
- Preserve recipient history and show the consequences/counts of destructive operations.  
- Align recipient field validation and relationship handling.

**Add if approved:** A relationship field on Android as well as Web, so the field has a shared meaning rather than remaining an accidental platform-only extension.

### 5.2 Daily notes, observations, addenda, and history

**Preserve:** Web note CRUD boundaries, date selection, note detail, filters, pagination, server-side ownership checks, and append-only correction history.

**Improve:**

- Define the observation state model for recorded, unknown, not recorded, not applicable, and legacy ambiguous values.  
- Keep verification separate from the observation value.  
- Align date and timezone meaning without shifting a calendar date through UTC conversion.  
- Decide and enforce the original-note cardinality rule only after duplicate analysis and approval.  
- Preserve original note content and append-only history.  
- Show correction events and their effects without hiding the original values.  
- Keep history filters, pagination, empty states, and URL state predictable.

### 5.3 Home, trends, and safety

**Preserve:** Web dashboard and summary entry points, server-derived signals, and current summary workflow.

**Improve:**

- Establish shared deterministic safety-rule definitions and fixtures.  
- Make signal evidence, date windows, rule version, and source records traceable.  
- Avoid silently changing thresholds or interpreting free text as a confirmed clinical event.  
- Provide useful home classifications and trend views only when their input semantics are agreed.  
- Separate a computed signal from caregiver acknowledgement/dismissal metadata if PO-21 is approved.

### 5.4 AI summaries and care-plan generation

**Preserve:** Web backend-managed AI provider chain, server-side secrets, summary endpoint, plan-generation endpoint, and Web-specific prompt ownership.

**Improve:**

- Align the meaning of summary fields, evidence references, uncertainty, and source transcript selection.  
- Make provider unavailable, timeout, malformed response, and validation failure explicit states.  
- Never label an unavailable/error message as an AI-generated summary.  
- Do not send a failed summary placeholder into plan generation as if it were evidence.  
- Preserve backend ownership of Web AI configuration.

**Remain platform-specific:** Android BYOK provider settings, keys, private provider URL, consent, and custom prompts remain on Android. Web does not need to reproduce Android's BYOK controls.

### 5.5 Care plans

**Preserve:** Web plan generation and version persistence.

**Improve/add:**

- Create/edit/review flows with clear active and historical version states.  
- Structured action catalogue and typed parameters.  
- Explicit handling for free-text legacy items that cannot be mapped safely.  
- Evidence references and source context where available.  
- Historical plan preservation and controlled activation.

No free-text care-plan item should be automatically converted into a clinical action unless the mapping is unambiguous and approved.

### 5.6 Medication management

**Add only after approval:** A Web medication domain covering medication identity, schedule representation, and per-dose intake events, with a clear history and audit trail.

The Web implementation must not infer ingestion from a reminder, schedule, or user action that only means “marked.” A schedule is not proof of intake. Medication name, dose, route, frequency, effective dates, and intake/verification meaning must be explicitly modelled and validated.

### 5.7 Portability, reports, and settings

**Proposed additions subject to decisions:**

- CSV export and import as user-controlled spreadsheet portability.  
- Full Web backup and restore as a separately versioned archive workflow.  
- PDF reports, print-friendly output, and browser-native share/download actions.  
- Theme mode and semantic palette preferences.  
- Complete Web settings hub, help/about information, supported links, and product version information.  
- Optional sample/demo data only if a safe, clearly isolated demo mode is approved.

Web does not need Android-only backend URL selection, native Android updater behavior, Room storage controls, or Android app-store update handling.

---

*(Continued in next update - full document being restored)*