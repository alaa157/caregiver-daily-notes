# Phase 3A — Cross-Platform Implementation Readiness Audit

**Repository:** `alaa157/caregiver-daily-notes`  
**Audit date:** 2026-11-02  
**Mode:** Read-only source inspection  
**Status:** Completed; implementation readiness not established

> This report distinguishes verified source-code facts from recommendations and unresolved product decisions. Phase 2 describes a proposed architecture; its proposals are not treated as approved product requirements.

## 1. Executive assessment

**Verdict: NOT READY to begin implementing the cross-platform transfer workflow.** The project has substantial reusable foundations on Android, web and backend, but the canonical cross-platform data contract, transfer authorization, transfer protocol, provenance model and imported-record behavior are not implemented.

This is principally an integration and data-contract problem, not a need to rebuild either application.

### Major blockers

| Blocker | Priority | Assessment |
|---|---|---|
| Canonical data contract and legacy mapping | Critical | Platform models differ in identity, nullability, lifecycle and semantics |
| Product-owner decisions | Critical | Multiple Phase 2 decisions remain unresolved |
| Source identity and provenance | Critical | No complete cross-platform source identity mapping |
| Device/account linking | Critical | No transfer-specific device authorization flow |
| Transfer backend | Critical | No sessions, manifests, preview, confirmation, chunks or receipts |
| Android transfer preparation | Critical | Existing outbox and backup are not the proposed transfer protocol |
| Deployment durability | Critical | Production persistence, backup and restore guarantees unverified |
| Web imported-record support | High | No provenance-aware imported-record model or workflow |
| Cross-platform semantic consistency | High | Observation, addendum, recipient and medication models differ |

### Existing foundations worth preserving

- Android has a Room-backed standalone architecture, database migrations, backup/restore, CSV functionality and WorkManager-based outbox infrastructure.
- Android BYOK credentials use provider-specific storage, Android Keystore encryption and separate consent handling.
- Backend has caregiver authentication, JWT validation, ownership checks, PostgreSQL/JPA persistence, Flyway migrations, note/addendum services, plans, audit events and AI summary services.
- Backend ordinary note and addendum creation already has database-backed idempotency mechanisms.
- Web has typed API modules, authentication handling, recipient/note workflows and server-generated AI summaries.
- Existing tests cover meaningful portions of all three platforms.

These capabilities are foundations, not proof that the Phase 2 transfer architecture exists.

## 2. Repository and branch verification

The branch references were retrieved from GitHub.

| Branch | HEAD | Commit message |
|---|---|---|
| `main` | `bc9a673e48e0f4955b9435a411e85f84ac39c399` | `docs: correct Phase 2 architecture and transfer contracts` |
| `feat/standalone-byok-ai` | `21876285ad808a6518593ea3974c51b6b1f6bb31` | `fix(cache): report cache-write failures through observable health state` |

- [main HEAD](https://github.com/alaa157/caregiver-daily-notes/commit/bc9a673e48e0f4955b9435a411e85f84ac39c399)
- [Android branch HEAD](https://github.com/alaa157/caregiver-daily-notes/commit/21876285ad808a6518593ea3974c51b6b1f6bb31)

The two commits between the previously inspected application-source snapshot `14dfff2b7a7049d4ac6285fe340b29c59ba5b595` and current `main` change only `docs/product-alignment/phase-2/README.md`. Thus, the inspected backend/web source snapshot remains applicable to current main for that comparison.

### Architecture documents

- [Phase 1 — Product baseline](../phase-1/README.md)
- [Phase 2 — Cross-platform architecture and contracts](../phase-2/README.md)

Both complete documents were retrieved and reviewed. Phase 2 was treated as a proposal/decision framework, not as evidence of implementation.

### Audit limitations

This was a static, read-only repository inspection. No builds, automated tests, APK installation, live deployment tests, database migrations, network-transfer experiments, backup restore drills or runtime airplane-mode tests were executed. Source presence can be verified; runtime and deployment behavior cannot be claimed as verified.

## 3. Implementation inventory

Status meanings:

- **IMPLEMENTED AND VERIFIED:** Source implementation exists and its behavior can be traced.
- **PARTIALLY IMPLEMENTED:** Some required behavior exists, but not the complete capability.
- **NOT IMPLEMENTED:** No corresponding implementation was identified.
- **IMPLEMENTED BUT INCONSISTENT:** Functionality exists but its model or behavior differs across platforms.
- **UNVERIFIED:** Available source does not establish runtime or operational behavior.

### 3.1 Platform infrastructure

| Capability | Status | Evidence / notes |
|---|---|---|
| Android Kotlin/Compose application | IMPLEMENTED AND VERIFIED | Android application and Compose modules |
| Android Room persistence | IMPLEMENTED AND VERIFIED | `AppDatabase.kt` and Room entities |
| Android Room migrations | IMPLEMENTED AND VERIFIED | Existing migration chain through database version 7 |
| Android standalone configuration | IMPLEMENTED AND VERIFIED in source | `build.gradle.kts`, `CaregiverApp.kt`; runtime offline behavior not tested |
| Android outbox | PARTIALLY IMPLEMENTED | `NoteRepository.kt`, `OutboxEntity.kt`, `OutboxSyncWorker.kt`; note-oriented, not transfer protocol |
| Android transfer snapshot | NOT IMPLEMENTED | No canonical snapshot/manifest implementation identified |
| Android installation identity for transfer | NOT IMPLEMENTED | No complete transfer installation namespace identified |
| Android transfer credential lifecycle | NOT IMPLEMENTED | No transfer-specific device grant |
| Android BYOK isolation | IMPLEMENTED AND VERIFIED in source | `AiKeyStore.kt`, `AndroidKeyStoreCipher.kt` |
| React/TypeScript application | IMPLEMENTED AND VERIFIED | `web/src` |
| Spring Boot backend | IMPLEMENTED AND VERIFIED | `backend/src/main/java` |
| PostgreSQL/JPA integration | IMPLEMENTED AND VERIFIED in source | Backend entities and datasource configuration |
| Flyway migration mechanism | IMPLEMENTED AND VERIFIED | Existing migrations through V6 |
| Production persistence/backup guarantees | UNVERIFIED | Repository does not prove deployment durability or successful restore |

### 3.2 Domain/data capabilities

| Capability | Status | Finding |
|---|---|---|
| Recipient management | IMPLEMENTED BUT INCONSISTENT | Android and backend differ in fields and lifecycle |
| Daily notes | IMPLEMENTED BUT INCONSISTENT | Different identity, nullability and observation semantics |
| Addenda | IMPLEMENTED BUT INCONSISTENT | Android text-only model; backend structured corrections |
| Care plans | IMPLEMENTED BUT INCONSISTENT | Android JSON version chain; backend version entities and structured content |
| Medication tracker | IMPLEMENTED BUT INCONSISTENT | Android normalized local tracker; no equivalent backend tracker domain |
| Medication verification | IMPLEMENTED BUT INCONSISTENT | Backend nullable Boolean does not fully express proposed verification states |
| Source provenance | NOT IMPLEMENTED | No complete source installation/entity/version mapping |
| Canonical entity identity | NOT IMPLEMENTED | No shared source-to-canonical identity contract |
| Duplicate source detection | NOT IMPLEMENTED | No permanent source identity uniqueness mapping |
| Recipient mapping for transfer | NOT IMPLEMENTED | No transfer mapping workflow |
| Imported-record representation | NOT IMPLEMENTED | Web model has no complete import provenance |
| Date/recipient conflict workflow | NOT IMPLEMENTED | Existing CRUD constraints are not a transfer conflict workflow |

### 3.3 Backend/API capabilities

| Capability | Status |
|---|---|
| Caregiver registration/login and JWT authentication | IMPLEMENTED AND VERIFIED in source |
| Caregiver ownership checks for existing resources | IMPLEMENTED AND VERIFIED in source |
| Ordinary note/addendum idempotency | IMPLEMENTED AND VERIFIED in source |
| Device/account linking | NOT IMPLEMENTED |
| Device approval and revocation | NOT IMPLEMENTED |
| Transfer-scoped grant/token lifecycle | NOT IMPLEMENTED |
| Transfer sessions | NOT IMPLEMENTED |
| Immutable manifests | NOT IMPLEMENTED |
| Preview and explicit confirmation | NOT IMPLEMENTED |
| Chunked transfer | NOT IMPLEMENTED |
| Transfer-level idempotency | NOT IMPLEMENTED |
| Atomic recipient dependency groups | NOT IMPLEMENTED |
| Retry/reconciliation | NOT IMPLEMENTED |
| Durable transfer receipts/history | NOT IMPLEMENTED |
| Transfer provenance persistence | NOT IMPLEMENTED |

### 3.4 Web capabilities

| Capability | Status |
|---|---|
| Authentication and session handling | IMPLEMENTED AND VERIFIED in source |
| Recipient and note workflows | IMPLEMENTED AND VERIFIED in source |
| Addenda and plan workflows | IMPLEMENTED AND VERIFIED in source |
| Server-generated AI summary | IMPLEMENTED AND VERIFIED in source |
| Imported-record provenance | NOT IMPLEMENTED |
| Recipient mapping UI | NOT IMPLEMENTED |
| Linked-device management | NOT IMPLEMENTED |
| Transfer history | NOT IMPLEMENTED |
| Import conflict resolution | NOT IMPLEMENTED |
| Explicit imported-record read-only policy | PARTIALLY IMPLEMENTED at most; existing original note fields are immutable, but addenda can be appended |

## 4. Architecture-to-code gap analysis

### GAP-01 — Canonical source identity and provenance

**Priority: Critical**

**Phase 2 requires:** A stable source identity including source platform, installation ID, entity type, source entity ID, source version and payload digest, mapped to a destination canonical ID and transfer reference.

**Current code:** Android Room entities have local string IDs and some server/local ID fields. Backend recipient and note IDs are server-generated UUIDs. No complete installation/entity/version provenance relationship is persisted.

**Missing:** A stable source identity namespace, permanent source-to-canonical mapping, version/digest tracking and owner-scoped uniqueness.

**Why it matters:** The server cannot reliably distinguish a previously imported record, a changed revision, a distinct record with the same date, or records from different installations.

**Action:** Implement Phase 2 source identity and provenance mapping. Do not overload Android `serverId` as canonical server identity.

Evidence:
- [Android NoteEntity](https://github.com/alaa157/caregiver-daily-notes/blob/21876285ad808a6518593ea3974c51b6b1f6bb31/android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt)
- [Android RecipientEntity](https://github.com/alaa157/caregiver-daily-notes/blob/21876285ad808a6518593ea3974c51b6b1f6bb31/android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt)
- [Backend DailyNote](https://github.com/alaa157/caregiver-daily-notes/blob/14dfff2b7a7049d4ac6285fe340b29c59ba5b595/backend/src/main/java/com/caregiver/notes/DailyNote.java)
- [Phase 2 identity section](../phase-2/README.md)

### GAP-02 — Observation semantics are incompatible

**Priority: Critical**

**Phase 2 requires:** Distinguishing recorded, unknown, not recorded, not applicable and legacy ambiguous states, with verification represented independently.

**Current code:** Android note fields include non-null strings, integers and Booleans such as mood, appetite, pain and fall. Backend fields include nullable values such as mood, medication status, pain score and fall report, plus nullable `medicationVerified`.

**Missing:** A shared observation state representation and independent verification enum.

**Why it matters:** A default `false`, zero or empty value must not silently become a confirmed clinical observation. A missing value is not equivalent to an explicit negative.

**Action:** Define a canonical observation wrapper and map legacy values through reviewed fixtures. Do not silently reinterpret old data.

Evidence:
- [Android NoteEntity](https://github.com/alaa157/caregiver-daily-notes/blob/21876285ad808a6518593ea3974c51b6b1f6bb31/android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt)
- [Backend DailyNote](https://github.com/alaa157/caregiver-daily-notes/blob/14dfff2b7a7049d4ac6285fe340b29c59ba5b595/backend/src/main/java/com/caregiver/notes/DailyNote.java)
- [Phase 2 observation section](../phase-2/README.md)

### GAP-03 — Recipient model and lifecycle differ

**Priority: High**

Android recipient data includes local/server IDs, name, active state and archive timestamp. Backend `CareRecipient` includes caregiver ownership, name, relationship, active state and timestamps. Android does not currently have an equivalent relationship field. Android archive/unarchive and permanent deletion behavior differ from backend soft deactivation and foreign-key restrictions.

**Why it matters:** A source archive must not automatically deactivate a web recipient. Name matching alone is not safe recipient identity.

**Action:** Resolve recipient relationship and lifecycle decisions before schema freeze; define explicit mapping and destination behavior.

Evidence: Android `RecipientEntity.kt`; backend `CareRecipient.java`, `RecipientService.java`.

### GAP-04 — Note uniqueness and date conflicts

**Priority: Critical**

Backend migration V5 establishes uniqueness for recipient/date and caregiver/idempotency key. Android note storage has a unique index on `serverId`, not an equivalent recipient/date constraint.

Two different source notes can target the same destination recipient and local calendar date. The current backend rejects a second note for that recipient/date, but there is no transfer conflict classification.

**Action:** Distinguish identical source identity/digest, changed source revision and distinct-source same-date conflict. Never silently overwrite. Preserve local calendar date and avoid accidental UTC date shifts.

Evidence: Android `NoteEntity.kt`; backend `DailyNote.java`; `V5__create_daily_notes_and_addenda.sql`.

### GAP-05 — Addenda represent different domain events

**Priority: High**

Android `AddendumEntity` is text plus ID, note ID and creation time. Backend `NoteAddendum` supports structured corrections to observation fields as well as free text, with chronological effective-value behavior.

**Why it matters:** Converting text into a structured correction invents information; flattening structured corrections into text loses semantics.

**Action:** Preserve text-only source events and structured correction events as distinct canonical event forms. Preserve historical events immutably.

Evidence: Android `AddendumEntity.kt`; backend `NoteAddendum.java`, `NoteSignalService.java`.

### GAP-06 — Medication tracker has no backend equivalent

**Priority: High**

Android has `MedicationEntity`, `MedicationIntakeEntity` and `MedicationStore`. The tracker includes medication name, optional dose and free-text schedule, active state, doses-per-day and intake marks. Backend medication-related note/plan fields do not constitute an equivalent tracker domain or API.

**Why it matters:** The current Android schedule cannot necessarily reconstruct exact administration times, and an intake mark must not be interpreted as verified ingestion.

**Action:** Defer medication tracker transfer unless its scope and semantics are explicitly approved.

Evidence: Android `MedicationEntity.kt` and related medication data module.

### GAP-07 — Care-plan representations differ

**Priority: High**

Android stores plan/version information in JSON (`PlanEntity.versionsJson`) with free-text items. Backend stores plan versions and structured content, source references and evidence note IDs, with a constrained action catalogue.

**Why it matters:** Free-text items cannot safely be converted into structured action IDs without an approved mapping.

**Action:** Preserve unmappable historical text or explicitly exclude it with preview disclosure, according to an approved policy. Do not invent structured mappings.

### GAP-08 — Transfer-specific backend architecture does not exist

**Priority: Critical**

The existing backend has auth, recipient, notes, plans, AI, audit and configuration domains. No transfer controller, service, entity, repository, device-link domain or transfer migration was identified.

Phase 2's device-link, session, preview, confirmation, chunk, retry, history and cancellation routes are proposed routes, not existing endpoints.

**Action:** Add a separately versioned transfer domain. Do not repurpose ordinary CRUD endpoints as a general synchronization protocol.

### GAP-09 — Existing Android outbox is not the transfer protocol

**Priority: Critical**

The Android outbox is note-oriented. It does not implement selected transfer scope, recipient mapping, multi-entity manifests, preview/confirmation, chunks, per-item receipts, provenance or conflict resolution.

There is also an idempotency contract mismatch to resolve: Android `CreateNoteRequest` carries an idempotency key in the request body, while backend `NoteController` accepts the key through the `Idempotency-Key` HTTP header; Android `NoteApi.create()` does not declare that header. Existing outbox behavior must not be assumed to satisfy transfer retry guarantees.

**Action:** Keep ordinary outbox and transfer protocol conceptually separate. A lost response after commit must be reconciled using stable chunk identity and durable server state.

Evidence: `OutboxEntity.kt`, `NoteRepository.kt`, Android `NoteApi.kt`, backend `NoteController.java`.

### GAP-10 — Web cannot distinguish imported records

**Priority: High**

The web note type contains note/recipient/date/observation and timestamps, but no complete source platform, installation, original source ID/version, transfer ID, import time or imported/native classification.

**Action:** Add provenance-aware imported records and visible origin metadata. Do not infer origin from labels or display conventions.

Evidence: [web/src/api/notes.ts](https://github.com/alaa157/caregiver-daily-notes/blob/14dfff2b7a7049d4ac6285fe340b29c59ba5b595/web/src/api/notes.ts).

### GAP-11 — Imported-record editability is unresolved

**Priority: High**

Backend original note fields are immutable, but existing addendum operations can append corrections to owned notes. This does not enforce a proposed read-only imported-copy policy.

**Action:** If read-only import is approved, enforce provenance-aware authorization and define whether web-authored correction events are permitted. Never overwrite the original imported source representation.

Evidence: backend `DailyNote.java`, `NoteController.java`. Requires PO-23 approval.

### GAP-12 — Deployment durability is unverified

**Priority: Critical**

PostgreSQL datasource configuration, Flyway and Hibernate validation exist. They do not prove persistent production storage, backup, successful restore, retention or deletion guarantees.

**Action:** Confirm deployment architecture, persistent volumes/database service, backup schedule, restore drill, RPO/RTO and retention/deletion policy before accepting real transferred data.

Evidence: backend `application.yml`. Requires PO-13 and deployment-owner confirmation.

## 5. Data model readiness

### 5.1 Canonical identities

Current models use different identity namespaces:

| Entity | Android | Backend |
|---|---|---|
| Caregiver | Local app context | UUID account |
| Recipient | Room string UUID/local mapping | Server UUID |
| Daily note | Room string UUID and row ID | Server UUID |
| Addendum | Local string UUID | Server entity |
| Care plan | Local string UUID and JSON version chain | Server version entities |
| Medication | Local string UUID | No equivalent tracker entity |

The proposed transfer identity dimensions should remain separate: source installation ID, source entity ID, source version, canonical entity ID, target recipient ID, transfer ID, transfer item ID and chunk ID.

### 5.2 Provenance

Android backup intentionally excludes synchronization mappings, outbox rows, AI keys, settings and tokens. That is reasonable for backup/restore, but the backup format is not the transfer protocol.

A separate canonical transfer envelope and permanent server provenance mapping are needed.

### 5.3 Recipient relationships and lifecycle

Android lacks the backend relationship field. Archive, deactivate and delete semantics differ. Resolve the relevant product decisions before schema freeze.

### 5.4 Observation and verification

The canonical model must distinguish explicit negative, not recorded, explicitly unknown, legacy ambiguous and not applicable. Medication verification must be separate from reported medication status.

### 5.5 Medication representation

Android schedule is currently free text plus doses-per-day and intake marks. This is not enough to infer a normalized schedule or verified administration. Defer transfer pending approval and model design.

### 5.6 Duplicate detection and conflicts

Existing backend uniqueness constraints protect ordinary CRUD. They do not replace permanent source identity uniqueness.

Classify at least:
- Same source identity and same digest: duplicate/replay.
- Same source identity with changed version/digest: source revision conflict.
- Different source identity but same destination recipient/date: date conflict.
- Recipient mapping ambiguity: mapping conflict.

Do not automatically merge or overwrite without an approved policy.

### 5.7 Migration implications

Android Room is at database version 7. Backend Flyway migrations currently extend through V6.

Future migrations should be additive and tested against representative existing data. Before adding uniqueness constraints, inspect actual existing records for duplicates. Preserve note IDs, addenda, medication intake records and historical uncertainty. Do not reinterpret legacy `false`, `0` or empty values without a reviewed mapping.

## 6. Backend and API readiness

### Existing backend foundation

The backend has caregiver authentication, BCrypt password hashing, HS256 JWT validation, stateless Spring Security, security-context actor identity and service-level ownership checks. Existing note/addendum services have database-backed idempotency and concurrency recovery.

Relevant modules include:
- `backend/src/main/java/com/caregiver/auth/`
- `backend/src/main/java/com/caregiver/config/SecurityConfig.java`
- `backend/src/main/java/com/caregiver/recipient/`
- `backend/src/main/java/com/caregiver/notes/`
- `backend/src/main/java/com/caregiver/audit/`

### Missing transfer capabilities

No implementation was identified for:
- Device authorization, approval and revocation.
- Transfer-scoped grant and refresh lifecycle.
- Transfer sessions and immutable manifests.
- Preview bound to exact selection/digest.
- Server-recorded explicit confirmation.
- Chunk commit and atomic recipient dependency groups.
- Transfer-level idempotency and durable receipts.
- Retry/reconciliation and transfer history.

Existing 24-hour stateless web JWT behavior is not a substitute for device-specific, revocable transfer authorization.

### Idempotency distinction

Existing note/addendum idempotency is valuable but does not provide session-, manifest-, item- and chunk-level identity, immutable payload binding, or receipt recovery. A timeout after a successful server commit must be treated as an unknown outcome and reconciled before retry.

### Atomic dependency groups

Recipient and dependent records need a defined atomic boundary. A failed recipient group must not leave dependent notes/addenda partially committed; independent groups may be handled separately if that is the approved policy.

## 7. Android readiness

### Strong foundations

- Room-backed local data graph.
- Standalone build configuration.
- Database migrations.
- Backup/restore and CSV support.
- WorkManager outbox and retry foundation.
- BYOK provider-specific encrypted credential storage and consent.

The build configuration sets local mode for debug, release and minified build types. The application graph selects local APIs when local mode is enabled. Runtime airplane-mode behavior was not tested. Some comments in `CaregiverApp.kt` appear inconsistent with the build configuration and should be reconciled.

### Backup is not transfer

The existing backup service has versioning, integrity checks, restore preview and atomic local restore protections. Its purpose and semantics differ from a selected, previewed, confirmed, provenance-aware transfer.

### BYOK isolation

The transfer serializer must explicitly exclude AI keys, provider settings, custom prompts, AI consent, app settings and authentication tokens. The existing backup format's exclusion of sensitive configuration is a useful precedent.

### Missing Android transfer work

- Installation identity.
- Transfer credential store and lifecycle.
- Canonical serializer and snapshot.
- Recipient mapping UI.
- Preview and confirmation UI.
- Chunk upload and reconciliation.
- Durable receipt persistence.

Do not build the transfer UI before the canonical contract and backend protocol are agreed.

## 8. Web readiness

The web application has typed API clients and recipient, note, addendum, plan and summary workflows. It is a suitable foundation for account-side device approval and imported-record presentation, but it has no linked-device or transfer-history workflow.

### Imported records

The web note model needs provenance fields and an imported/native distinction. Read-only behavior must be enforced server-side if approved, not just hidden in the UI.

### AI summary compatibility

The existing backend summary loader loads owned notes and addenda and builds a source transcript; summary results retain evidence references to note IDs. Imported notes could potentially participate in the existing summary pipeline if they are represented in the canonical note domain with stable provenance.

Required safeguards:
- Evidence references resolve to the correct imported source record.
- Observation uncertainty is preserved.
- Imported information is not represented as independently verified.
- Android BYOK credentials/configuration never flow to web.
- Web AI provider privacy/consent remains a separate policy.

Relevant files include web summary pages/results and backend `SummarySourceLoader.java` / `SummaryApplicationService.java`.

## 9. Security and privacy blockers

| Risk | Current situation | Required protection |
|---|---|---|
| Broad Android authentication | No transfer grant exists | Dedicated transfer-only authorization |
| Device revocation | No linked-device lifecycle | Server-enforced revocation |
| Token replay | No device refresh lifecycle | Rotation and replay detection where refresh credentials are used |
| Account switching | No transfer account binding | Account-bound grant and session |
| Cross-account access | Existing CRUD ownership checks | Transfer ownership and grant-scope checks |
| Consent | No transfer confirmation workflow | Exact preview and server-recorded confirmation |
| BYOK exposure | Android BYOK is isolated | Explicit serializer allowlist and exclusion tests |
| Prompt/settings exposure | Local preferences | Exclude from transfer envelope |
| Sensitive logging | Audit infrastructure exists | Safe-field allowlist and redaction |
| Data at rest | Room/PostgreSQL exist | Verify platform and deployment protections |
| Retention/deletion | Not established operationally | Approved policy and enforcement |
| TLS | Deployment not inspected | HTTPS enforcement and fail-closed behavior |

Consent to send data to an AI provider, permission to link an Android installation, and permission to transfer a particular selection of care records are separate permissions.

Transfer audit logs must not contain clinical note text, observations, medication names/doses, API keys, passwords, device tokens or refresh credentials. Existing caregiver ownership checks are a foundation, but every transfer object and target recipient must be checked against the authenticated caregiver and grant scope.

## 10. Product-owner decisions

The following decision IDs are carried forward from Phase 2. They require explicit approval; this audit does not decide them.

### Decisions affecting canonical schema and semantics

| ID | Decision |
|---|---|
| PO-01 | Should recipient relationship become a shared Android/web field? |
| PO-02 | What is the canonical recipient lifecycle: archive, deactivate and delete? |
| PO-03 | Should Android support historical note creation, remain today-only, or allow historical dates only during import? |
| PO-04 | Should there be exactly one original note per recipient per local calendar day? |
| PO-05 | How should missing, unknown, explicit negative and legacy ambiguous observations be represented? |
| PO-06 | Should the canonical addendum model support both text events and structured corrections? |
| PO-07 | Which shared safety rules should be adopted, subject to appropriate clinical review? |
| PO-08 | How should Android free-text care-plan items map to the backend action catalogue? |
| PO-20 | Should original source IDs be retained with installation namespaces and server-generated canonical IDs? |

### Decisions affecting transfer MVP scope

| ID | Decision |
|---|---|
| PO-09 | Should medication tracker records be transferred in v1? |
| PO-10 | Which record types belong in the first transfer? |
| PO-11 | Should one transfer support multiple recipients? |
| PO-12 | Which account-linking experience should Android use? |
| PO-13 | What persistence, backup and retention guarantees will the server provide? |

### Decisions affecting future parity and product behavior

| ID | Decision |
|---|---|
| PO-14 | Should web support CSV import/export? |
| PO-15 | Should web have a separate user-level backup/restore system? |
| PO-16 | Should web have PDF export and report sharing? |
| PO-17 | What theme and palette capabilities should web support? |
| PO-18 | Should medication tracking eventually have web parity? |
| PO-19 | Should platforms aim for full feature parity or capability parity? |
| PO-21 | Should safety indicators have caregiver acknowledgement/dismissal? |
| PO-22 | Should generated AI summaries ever be transferred as records? |
| PO-23 | Should imported Android records be read-only in v1? |

Recommended approval sequence:
1. Resolve PO-01 through PO-08 and PO-20 (plus historical-date implications of PO-03) before schema freeze.
2. Resolve PO-09 through PO-13 before transfer MVP scope and deployment acceptance.
3. Resolve PO-14 through PO-19 and PO-21 through PO-23 before the wider parity roadmap.

Phase 2 recommendations (for example, browser-mediated device authorization, one-way copy, and read-only imported copies) remain recommendations until approved.

## 11. Recommended implementation sequence

Each stage is a separately reviewable deliverable. The sequence is dependency-aware; deployment durability work can proceed in parallel with contract preparation.

### PH3-01 — Resolve architecture and product decisions

**Objective:** Produce an approved decision log.

**Affected areas:** Phase 1/Phase 2 product-alignment documentation and a new Phase 3 decision record.

**Dependencies:** None.

**Acceptance criteria:**
- All schema- and transfer-scope decisions are resolved or explicitly deferred with impact recorded.
- Decision owner and rationale are recorded.
- Clinical and deployment decisions have appropriate reviewers.
- Recommendations are not silently converted into approvals.

**Automated tests:** Decision-log completeness and documentation consistency checks.

**Manual verification:** Product-owner review and approval.

### PH3-02 — Freeze canonical schema v1

**Objective:** Create a language-neutral contract consumable by Kotlin, Java and TypeScript.

**Affected modules:** Android recipient/note/addendum/medication/plan data models; backend recipient/notes/plans; web API types; new shared schema and fixtures.

**Dependencies:** PH3-01.

**Acceptance criteria:**
- Every entity has defined identity and provenance.
- Nullability and enum semantics are explicit.
- Observation and verification are independent.
- Date/timezone behavior is defined.
- Lifecycle and compatibility/versioning rules are documented.
- Unsupported and ambiguous legacy values have explicit outcomes.

**Automated tests:** JSON Schema validation; enum compatibility; null/unknown/not-recorded fixtures; date/timezone boundaries; serialization/deserialization in all consuming languages.

**Manual verification:** Review contract against representative real legacy records.

### PH3-03 — Create cross-platform mapping fixtures

**Objective:** Establish golden fixtures for existing Android and backend records.

**Affected modules:** Android mapping layer, backend mapping layer, web contracts and shared test fixtures.

**Dependencies:** PH3-02.

**Acceptance criteria:** Every legacy field is classified as lossless, lossy-but-approved, legacy ambiguous or unsupported. No source data silently disappears.

**Automated tests:** Kotlin, Java and TypeScript mapping tests; cross-platform fixture comparison; Arabic/English preservation; duplicate/date conflict; addendum correction cases.

**Manual verification:** Review all lossy/ambiguous cases before approving mapping.

### PH3-04 — Establish deployment durability

**Objective:** Prove the backend can safely retain transferred records.

**Affected modules:** Backend deployment configuration, PostgreSQL infrastructure, backup/restore operations and operational documentation.

**Dependencies:** PO-13 approval; may proceed in parallel with PH3-02/03.

**Acceptance criteria:** Persistent storage survives replacement/redeployment; backup schedule and restore procedure are documented; RPO/RTO and retention/deletion responsibilities are defined; demo and durable environments are distinguished.

**Automated tests:** Deployment smoke tests, migration checks, backup-age monitoring and restore verification.

**Manual verification:** Restore a test database into an isolated environment and verify records.

### PH3-05 — Implement device authorization and credential lifecycle

**Objective:** Link Android to a caregiver web account without giving Android a general-purpose web session.

**Affected modules:** New backend device-link domain and security integration; Android transfer auth/Keystore storage; web device approval UI.

**Dependencies:** PH3-01, PH3-04 and an agreed authorization contract.

**Acceptance criteria:** Device approval is account-bound; grant scope is transfer-only; credentials expire; refresh rotation/replay protections are implemented if applicable; revocation is enforced server-side; BYOK remains separate.

**Automated tests:** Expiration, brute-force protection, polling, wrong-account approval, scope rejection, replay, revocation, rotation and account switching.

**Manual verification:** Link a physical test installation, revoke it from web and verify subsequent transfer requests fail.

### PH3-06 — Implement backend transfer persistence and protocol

**Objective:** Implement sessions, manifests, preview, confirmation, chunks, receipts and reconciliation.

**Affected modules:** New backend transfer package, JPA entities/repositories, Spring controllers/services, Flyway migrations, security and audit.

**Dependencies:** PH3-02, PH3-03, PH3-04 and PH3-05.

**Acceptance criteria:** Manifest membership immutable; preview does not upload care text; confirmation binds exact selection/digest; server records confirmation time; chunks have deterministic identities; dependency groups are atomic; same key/payload returns original outcome; changed payload conflicts; uncertain outcomes reconcile; receipts survive restart.

**Automated tests:** Concurrent duplicate transfer, changed payload under same key, timeout before/after commit, atomic rollback, independent-group partial success, expired session, cross-account access, invalid chunk membership, restart and receipt recovery.

**Manual verification:** Exercise the protocol using synthetic records and an HTTP client.

### PH3-07 — Implement Android snapshot and receipt persistence

**Objective:** Create a read-only snapshot of selected Room records.

**Affected modules:** Room database/entities/DAOs, new Android transfer package, credential storage and receipt persistence.

**Dependencies:** PH3-02, PH3-03, PH3-05 and PH3-06.

**Acceptance criteria:** Stable installation identity; source IDs unchanged; consistent snapshot; child records follow dependency rules; deterministic digests; no BYOK/settings/prompts/tokens serialized; no clinical Room mutation; receipts contain safe metadata only.

**Automated tests:** Snapshot consistency, concurrent local edits, stable IDs, digest reproducibility, credential exclusion, receipt persistence, Room migration and database unchanged after transfer outcomes.

**Manual verification:** Compare local data before/after successful, failed, cancelled and retried synthetic transfers.

### PH3-08 — Implement Android transfer workflow

**Objective:** Build Compose selection, preview, confirmation and result experience.

**Affected modules:** New Compose screens/ViewModels, transfer API client and navigation/settings integration.

**Dependencies:** PH3-06 and PH3-07.

**Acceptance criteria:** Caregiver can connect, select destination/recipients/types/dates, review mapping and conflicts, inspect exact preview, explicitly confirm and inspect receipt. No care content is sent before confirmation.

**Automated tests:** ViewModel transitions, UI selection, preview refresh, expired session, cancellation, network failures, stale preview and confirmation scope.

**Manual verification:** Complete workflow on physical device with synthetic data.

### PH3-09 — Implement web device management and imported-record provenance

**Objective:** Make web aware of linked devices and imported records.

**Affected modules:** Web auth feature, new device-management and transfer-history features, API clients, note presentation and backend provenance services.

**Dependencies:** PH3-05 and PH3-06.

**Acceptance criteria:** Approve/revoke devices; view transfer history; show imported provenance; preserve imported/native distinction; enforce approved read-only policy server-side; never expose device secrets.

**Automated tests:** Approval/revocation, account isolation, provenance rendering, read-only authorization, history pagination and empty/error states.

**Manual verification:** Import synthetic records and inspect them through the web account.

### PH3-10 — Conflict resolution and shared semantic verification

**Objective:** Verify meaning preservation and prevent silent overwrite.

**Affected modules:** Android mapping, backend import services, web note/recipient presentation, shared safety and summary fixtures.

**Dependencies:** PH3-03, PH3-06, PH3-07 and PH3-09.

**Acceptance criteria:** Identical records recognized; changed source versions classified; recipient/date conflicts visible; no unapproved automatic merge; addendum history preserved; summary evidence resolves correctly; safety outputs follow approved rules.

**Automated tests:** Duplicate/conflict matrix, addendum ordering, effective-value projection, summary evidence, safety fixtures and cross-platform consistency.

**Manual verification:** Exercise intentional duplicates, conflicting dates and corrected notes; verify source and destination remain intact.

### PH3-11 — Full staging acceptance and release gate

**Objective:** Prove end-to-end behavior under realistic failures.

**Dependencies:** All previous stages.

**Acceptance criteria:** No Android data loss, duplicate imports, unauthorized access or credential exposure; uncertain commits recover; partial success is accurately reported; revocation works; receipts survive restart; backup/restore is demonstrated.

**Automated tests:** All Android/backend/web suites plus contract, integration and security tests.

**Manual verification:** Airplane mode, process death, interrupted uploads, server restart, revocation, duplicate import, date conflict, database restoration and full Android-to-web workflow.

## 12. Testing readiness

Existing test suites cover Android Room/migrations, backup/restore, CSV, outbox/WorkManager, BYOK, medication and navigation; backend authentication/security, recipient ownership, note idempotency, addenda, plans and summaries; and web authentication, notes, recipients, history, plans, summaries and API behavior.

Examples identified include:
- Android: `NoteDaoTest.kt`, `OutboxDrainTest.kt`, `OutboxSyncWorkerTest.kt`, `BackupRestoreServiceTest.kt`, `MedicationStoreTest.kt`, `AiKeyStoreModelTest.kt`.
- Backend: `SecurityFilterTest.java`, `PasswordHashingTest.java`, `NoteIdempotencyTest.java`, `RecipientControllerTest.java`, `SummaryServiceTest.java`.

**Test execution was not performed.** Existence of a test is not evidence that it passes.

New test categories required include cross-platform mapping, provenance uniqueness, device authorization, manifest immutability, confirmation scope, atomic groups, chunk idempotency, reconciliation, receipt durability, cross-account isolation, credential exclusion, imported-record authorization and semantic conformance.

## 13. Final recommendation

### Can implementation begin?

- **Yes:** Phase 3 preparation, decision resolution, deployment investigation and contract design.
- **No:** The end-to-end transfer workflow or persistence changes based on an unapproved canonical schema.

### What must be resolved first?

Approve the recipient, observation, addendum, date/cardinality, safety, care-plan and source-identity decisions; settle transfer scope and account-linking behavior; and establish durable backend storage/backup guarantees.

### First technical deliverable

**PH3-02 — Freeze canonical schema v1 and compatibility policy**, after PH3-01 decisions are approved. PH3-04 deployment durability investigation can proceed in parallel.

The schema is the dependency for Android snapshot serialization, backend persistence, API contracts, web imported-record types, provenance, duplicate detection, conflict resolution and shared tests.

### Architecture to preserve

- Android remains independently functional: Kotlin, Compose, Room, offline-first and standalone BYOK.
- Transfer is an explicit, user-authorized one-way copy.
- Backend owns canonical imported records, provenance, ownership, idempotency, audit and durable receipts.
- Web presents imported records and transfer history and continues using its own server-managed AI behavior.
- No automatic synchronization, source deletion or silent source modification.

## 14. Audit integrity and scope

- No source files were modified during the audit.
- No branches, commits or pull requests were created during the audit.
- No GitHub changes were made as part of the audit itself.
- No product decisions were made on the owner's behalf.
- No tests/builds/deployments are claimed as passing.
- Findings are based on retrieved source and documentation; runtime and production deployment claims remain unverified.

---

**End of Phase 3A readiness audit.**
