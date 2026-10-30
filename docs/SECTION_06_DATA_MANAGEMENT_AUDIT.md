# AI Response Processing — Code Audit Report

Correction: this is the requested **Section 06 — Data Management** audit. No project files were modified, no tests/lint/builds were run, and no commits were created.

## 1. Executive Summary

Overall assessment: **Needs improvement.**

The application has a clear local persistence structure: Room is used for cache/local-mode records and Preferences DataStore for settings. It has explicit migrations, avoids destructive migrations, uses transactions for several important operations, and provides a manual backup/restore workflow in standalone local mode.

However, one registered migration deterministically destroys historical medication-intake records during upgrade. Additional high-impact integrity weaknesses exist in restore conflict detection, referential integrity, and concurrent plan updates.

Findings: **12**

| Severity | Count |
|---|---:|
| CRITICAL | 1 |
| HIGH | 3 |
| MEDIUM | 6 |
| LOW | 1 |
| INFO | 1 |

Most concerning issues:

1. Database migration 5→6 collapses every historical intake for a medication into one row.
2. Restore protection compares entity counts, not content; edits made after preview can be silently overwritten.
3. Missing foreign keys plus permissive “writable” guards allow orphan notes, plans, medications, and intake records.
4. Plan updates are non-atomic read–modify–write operations, allowing lost versions.
5. Backup validation can accept structurally invalid plan histories that later crash plan deserialization.

## 2. Audit Scope and Methodology

Inspected persistence architecture and data flows across:

- Room database, entities, DAOs, migrations, and schema exports.
- Repository/cache/outbox paths.
- Standalone local backend and medication storage.
- Preferences DataStore and its registry.
- SharedPreferences usage for language and AI settings.
- CSV transfer and full backup/restore.
- Local deletion and archive lifecycle paths.
- Existing persistence, migration, backup, outbox, and settings tests.

Primary files inspected included:

- [AppDatabase.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt:25)
- [MedicationStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationStore.kt:14)
- [LocalBackendApis.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt:40)
- [NoteEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt:21)
- [MedicationEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt:21)
- [SettingsStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/SettingsStore.kt:20)
- [SettingsStoreRegistry.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/SettingsStoreRegistry.kt:15)
- [BackupRestoreService.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupRestoreService.kt:59)
- [BackupStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt:58)
- [BackupValidator.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupValidator.kt:40)
- [NoteRepository.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt:76)
- [CaregiverApp.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt:82)

Skills used: Android clean architecture and database-migration review guidance.

Tools used: read-only repository inspection with `rg`, `sed`, and `nl`.

Limitations:

- No application, migration, instrumented, or unit tests were executed, per instruction.
- No real device database or production backend was inspected.
- Remote API guarantees, especially idempotency, were assessed from the client contract and project documentation only.

## 3. Data Architecture Overview

```text
Compose UI
  ↓
ViewModels
  ↓
AppGraph
  ├─ LOCAL_MODE
  │    ├─ LocalBackendApis
  │    ├─ MedicationStore
  │    ├─ CSV Room store
  │    └─ Backup/restore store
  │             ↓
  │        Room: caregiver-db
  │
  ├─ Server mode
  │    ├─ Retrofit APIs
  │    ├─ RecipientRepository → Room cache
  │    ├─ NoteRepository → Room cache + outbox
  │    └─ WorkManager outbox drain
  │
  └─ Demo mode
       └─ In-memory demo API; no Room persistence

Settings/UI preferences
  ↓
SettingsStore
  ↓
Preferences DataStore: settings.preferences_pb

Language cold-start mirror
  ↓
LanguagePrefs
  ↓
SharedPreferences: language_prefs

AI configuration
  ↓
AiKeyStore
  ↓
SharedPreferences: ai_settings
```

Room database `caregiver-db`, currently schema version 7, contains:

- `recipients`
- `notes`
- `outbox`
- `plans`
- `addenda`
- `medications`
- `medication_intakes`

The production/server graph treats Room as a cache plus pending-note outbox. The standalone local graph treats it as authoritative local storage. Full backup/restore is intentionally available only in local mode.

## 4. Room Database Assessment

Strengths:

- Current version is 7 and schema export is enabled.
- All migrations 1→7 are explicitly registered.
- No `fallbackToDestructiveMigration()` configuration was found.
- The archived-recipient delete path is transactionally implemented.
- Scope refreshes in `NoteDao.replaceScope()` are transactional.
- Backup replacement is transactionally performed.
- Unique indexes exist for recipient and note server IDs.
- Medication records have an index on `recipientId`.

Material weaknesses:

- Migration 5→6 loses medication-intake history.
- Entities have no Room foreign keys, cascades, or database-level referential guarantees.
- Several frequently filtered columns are unindexed.
- The plan-version state is stored as opaque JSON, making constraints and atomic updates difficult.
- A second Room instance is created by the sample-data action rather than using the graph’s existing database instance.

## 5. DataStore Assessment

`SettingsStore` correctly centralizes settings keys and uses `DataStore.edit`, which provides atomic preference updates. `SettingsStoreRegistry` also protects against duplicate DataStore instances for the same registered name in-process.

Stored values include:

- Backend URL
- Authentication token
- Authentication email
- Language
- Theme mode
- Color palette
- Pure-black preference
- Onboarding completion

Concerns:

- DataStore reads have no I/O/corruption recovery flow.
- The auth token is persisted as a plain Preferences string; it is protected by Android app sandboxing but no at-rest encryption is present in this implementation.
- Language is duplicated across DataStore and SharedPreferences intentionally for cold-start locale setup. This is mitigated by the DataStore-as-source-of-truth design, but it remains duplicated state.

## 6. Migration Assessment

| From Version | To Version | Migration | Data Preservation | Test Coverage | Assessment |
|---|---:|---|---|---|---|
| 1 | 2 | Adds outbox | Preserves prior tables | No identified isolated migration test | Appears structurally correct |
| 2 | 3 | Adds plans | Preserves prior tables | No identified isolated migration test | Appears structurally correct |
| 3 | 4 | Adds addenda | Preserves prior tables | No identified isolated migration test | Appears structurally correct |
| 4 | 5 | Adds medication tables/index | Preserves prior tables | No identified isolated migration test | Appears structurally correct |
| 5 | 6 | Adds doses per day and rewrites intakes | **Does not preserve historical intake rows correctly** | No identified migration test | **Unsafe** |
| 6 | 7 | Adds archived timestamp | Preserves prior tables | `RecipientMigrationTest` covers this path | Appears structurally correct |

All migrations are registered at [AppDatabase.kt:228-238](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt:228). There is no destructive-migration fallback.

The project has an instrumented migration test for 6→7, but no identified migration test for 5→6 or a complete upgrade path from schema 1 through schema 7.

## 7. Persistence and Data Integrity Assessment

Normal local-mode persistence is generally direct and durable: local APIs write through Room and backup uses the same local database. Server mode uses Room as a cache and persists unsent note creations in the outbox.

Important integrity observations:

- Archived-recipient deletion is carefully transactional and deletes related rows manually.
- The database itself does not enforce those relationships.
- `MedicationStore` and `LocalBackendApis` can write child records after a parent no longer exists.
- Full restore is all-or-nothing, but its optimistic concurrency check only compares counts.
- Server-cache writes are intentionally best effort; failed cache writes are discarded rather than surfaced.
- Backup validates many referential and value constraints, but not the internal semantic contract of plan JSON.

## 8. Concurrency and Transaction Assessment

Correct transaction boundaries:

- `AppDatabase.deleteArchivedRecipient()`
- `NoteDao.replaceScope()`
- `RecipientDao.replaceAll()`
- `RoomBackupStore.snapshot()`
- `RoomBackupStore.replaceAll()`
- CSV Room imports

Unsafe or incomplete boundaries:

- Medication deletion is two independent statements.
- Local plan creation/version transitions use read–decode–modify–replace operations without a transaction or compare-and-set guard.
- The restore conflict guard detects only row-count differences.
- Child writes can race recipient deletion because parent existence is checked outside a write transaction and no foreign key prevents the later insert.

## 9. Error Handling and Recovery Assessment

Positive behavior:

- Backup prepare is read-only and semantic validation happens before mutation.
- Backup restore is transactionally all-or-nothing.
- Outbox retry status is retained across worker runs.
- Cache fallback rethrows transport errors where no cache exists.
- Cancellation exceptions are generally rethrown in backup/cache code.

Weaknesses:

- DataStore corruption/I/O errors can terminate settings flows.
- Cache-write failures are silently swallowed, including disk-full or SQLite write failures.
- A server may accept an outbox create but the client may fail before receiving the result; without server idempotency, retries can duplicate records.
- A malformed-but-accepted backup plan JSON can be committed and only fail later during plan reads.

## 10. Backup, Restore, and Privacy Assessment

Manual backup/restore exists only in standalone local mode. It snapshots recipients, notes, addenda, plans, medications, and medication-intake marks.

Intentionally excluded:

- Outbox rows
- DataStore settings
- Authentication token and email
- AI keys
- Room-local surrogate IDs
- Server/cache mode data

Android automatic backup is disabled through `android:allowBackup="false"` in [AndroidManifest.xml:18](/tmp/opencode/caregiver-daily-notes/android/app/src/main/AndroidManifest.xml:18).

The manual backup document contains sensitive caregiving data in JSON. Its SHA digest detects accidental corruption, but the implementation documents that it does not authenticate the file or prevent intentional alteration. This is an expected limitation for a user-selected local document, but users should understand that a backup is portable sensitive data and not encrypted by this mechanism.

## 11. Detailed Findings

**Finding ID:** DATA-001  
**Title:** Migration 5→6 collapses historical medication-intake records  
**Severity:** CRITICAL  
**Category:** Migration  
**Affected files:** [AppDatabase.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt:189), [MedicationEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt:42)  
**Location:** `MIGRATION_5_6`, lines 189-215  
**Description:** The migration creates a new intake identifier that omits the date. All existing intake rows for one medication receive the same primary key.  
**Evidence:** The migration selects `medicationId || ':0'` as every migrated row’s ID at lines 208-211. The current entity contract requires `<medicationId>:<date>:<doseIndex>`. Because `INSERT OR REPLACE` is used, later source rows replace earlier ones.  
**Reproduction scenario:** A version-5 user has intake rows for medication `med-1` on `2026-01-01` and `2026-01-02`. Upgrade to version 6. Both rows become `med-1:0`; one replaces the other.  
**Expected behavior:** Each old daily intake should become a distinct first-dose record, such as `med-1:2026-01-01:0` and `med-1:2026-01-02:0`.  
**Actual behavior:** At most one historical row per medication survives. Which row survives is not deterministic because the migration source `SELECT` has no ordering requirement.  
**Impact:** Permanent loss and corruption of medication adherence history during upgrade.  
**Suggested fix:** Construct the new key from medication ID, original date, and dose index; use an insert strategy that cannot silently replace distinct source rows; add migration tests containing multiple dates per medication.  
**Confidence:** High

---

**Finding ID:** DATA-002  
**Title:** Restore conflict detection ignores same-count data edits  
**Severity:** HIGH  
**Category:** Recovery  
**Affected files:** [BackupRestoreService.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupRestoreService.kt:24), [BackupStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt:73)  
**Location:** `RestorePreview.current`, `RoomBackupStore.replaceAll`, lines 73-101  
**Description:** A restore preview saves only counts of each entity type. The commit proceeds when counts match, even if the actual data changed after the preview.  
**Evidence:** `prepare()` captures `store.counts()` at lines 91-105. `replaceAll()` compares only `readCounts() != expected` at line 75 before deleting all restorable tables at lines 78-83.  
**Reproduction scenario:** Open a restore preview when there are 10 notes. Edit text or pain value in one existing note without adding/removing notes. Confirm the earlier restore preview.  
**Expected behavior:** Restore should detect that the current state differs from the preview state and ask the user to re-review.  
**Actual behavior:** Counts still equal 10, so all current rows are deleted and replaced by the older backup.  
**Impact:** User changes can be silently lost during restore.  
**Suggested fix:** Compare a stable snapshot revision or content digest per table, or serialize a transactionally captured state token rather than counts alone.  
**Confidence:** High

---

**Finding ID:** DATA-003  
**Title:** Missing-parent writes create orphaned local records  
**Severity:** HIGH  
**Category:** Integrity  
**Affected files:** [LocalBackendApis.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt:51), [MedicationStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationStore.kt:17), [NoteEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt:21), [MedicationEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt:47)  
**Location:** `requireWritableRecipient`, `requireWritableMedication`, note/plan/medication entities  
**Description:** “Writable” guards reject archived parents but accept nonexistent parents. The schema has no foreign keys to reject child records that refer to missing recipients, notes, or medications.  
**Evidence:** `LocalBackendApis.requireWritableRecipient()` only throws if a found recipient is archived; a missing recipient returns successfully at lines 51-55. `MedicationStore.requireWritableRecipient()` has the same behavior at lines 17-20. `requireWritableMedication()` returns when the medication is absent at lines 23-25, after which `setTaken()` can insert an intake at lines 81-92. No entity declares `@ForeignKey`.  
**Reproduction scenario:** A stale screen begins saving a note while another operation deletes the archived recipient. The child insert can occur after deletion because there is no transaction spanning guard and insert, and no database constraint rejects it.  
**Expected behavior:** Writes for missing parents should fail, and database constraints should prevent orphans even if application checks race.  
**Actual behavior:** Orphan notes, plans, medications, or intake rows can be persisted.  
**Impact:** Inconsistent history, inaccessible records, incomplete deletion counts, and backups that can later fail semantic validation.  
**Suggested fix:** Make missing parent records an explicit failure; add foreign keys/cascade policies or transactional parent validation for all dependent writes.  
**Confidence:** High

---

**Finding ID:** DATA-004  
**Title:** Concurrent plan changes can overwrite version history  
**Severity:** HIGH  
**Category:** Concurrency  
**Affected files:** [LocalBackendApis.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt:238), [PlanDao.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/PlanDao.kt:8)  
**Location:** `suggest`, `appendVersion`, `transition`, `savePlan`  
**Description:** Plan changes read the JSON version chain, modify it in memory, and write it back with `REPLACE`. There is no transaction, version check, or optimistic locking.  
**Evidence:** `appendVersion()` reads at line 281, decodes and appends at lines 283-293, then `savePlan()` overwrites the entire entity at lines 317-325. `transition()` follows the same pattern at lines 302-315. `PlanDao.upsert()` uses `OnConflictStrategy.REPLACE` at lines 25-26.  
**Reproduction scenario:** Two coroutines load plan version 2. One accepts it and one dismisses it. Both create a new version from version 2; the final database write overwrites the other.  
**Expected behavior:** Conflicting transitions should serialize, reject stale updates, or preserve both valid version records.  
**Actual behavior:** Last writer wins, silently discarding the other transition/version.  
**Impact:** Loss of care-plan status changes and an invalid or incomplete plan audit trail.  
**Suggested fix:** Move plan mutations into a Room transaction and add an optimistic version/revision check; ideally normalize plan versions into a dedicated table.  
**Confidence:** High

---

**Finding ID:** DATA-005  
**Title:** Medication deletion is not atomic with intake cleanup  
**Severity:** MEDIUM  
**Category:** Integrity  
**Affected files:** [MedicationStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/MedicationStore.kt:65)  
**Location:** `MedicationStore.delete`, lines 65-70  
**Description:** Intake history is deleted and the medication is deleted through two separate suspend DAO calls without a transaction.  
**Evidence:** `clearIntakesFor(id)` runs at line 68, then `dao.delete(id)` at line 69. A cancellation or database failure between them leaves the medication without its intake history.  
**Reproduction scenario:** Delete a medication while the coroutine is cancelled after its intake-clear statement commits but before the medication delete executes.  
**Expected behavior:** Both operations should commit or roll back together.  
**Actual behavior:** Partial deletion is possible.  
**Impact:** Medication adherence history can disappear while the medication record remains.  
**Suggested fix:** Perform both statements inside `db.withTransaction`, or use a foreign key with an appropriate cascade policy.  
**Confidence:** High

---

**Finding ID:** DATA-006  
**Title:** Sample-data action opens a second unmanaged Room instance  
**Severity:** MEDIUM  
**Category:** Persistence  
**Affected files:** [SettingsScreens.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/presentation/settings/SettingsScreens.kt:247), [CaregiverApp.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt:114), [AppDatabase.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt:228)  
**Location:** Sample data click handler, lines 247-252  
**Description:** The settings screen creates a new `AppDatabase` for sample loading instead of using the `AppGraph` local database instance used by application services.  
**Evidence:** The click handler calls `AppDatabase.create(graph.context)` directly. `AppGraph` separately owns its lazy `localDatabase`. `AppDatabase.create()` creates a new Room builder each call and multi-instance invalidation is not enabled.  
**Reproduction scenario:** In local mode, open screens collecting Room flows from the graph database, then invoke “Load sample data.” The second database connection writes the same file.  
**Expected behavior:** All persistence operations should use the graph-owned singleton database connection.  
**Actual behavior:** A second open database instance writes to the same file and is not explicitly closed. Existing Room observers are not guaranteed to receive invalidation from that independently created instance.  
**Impact:** UI may show stale content until recreation/query refresh; repeated use creates avoidable database instances and complicates lifecycle/concurrency behavior.  
**Suggested fix:** Expose the graph’s local database or a graph-owned sample-data service rather than constructing Room from the composable.  
**Confidence:** Medium

---

**Finding ID:** DATA-007  
**Title:** DataStore corruption and I/O failures are not recovered in settings flows  
**Severity:** MEDIUM  
**Category:** DataStore  
**Affected files:** [SettingsStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/SettingsStore.kt:20), [SettingsStoreRegistry.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/SettingsStoreRegistry.kt:19)  
**Location:** Settings `Flow` definitions, lines 22-29; DataStore creation, line 22  
**Description:** Settings flows directly map `dataStore.data` with no `catch` recovery for `IOException` or corruption. The factory has no configured corruption handler.  
**Evidence:** All settings flows are direct `dataStore.data.map { ... }`. The factory call is `PreferenceDataStoreFactory.create { produceFile() }` with no corruption handler. The only visible startup fallback is language-specific and occurs in `CaregiverApp.attachBaseContext()`.  
**Reproduction scenario:** The preferences file becomes unreadable or corrupted. A settings-dependent ViewModel starts collecting `baseUrl`, token, theme, or onboarding state.  
**Expected behavior:** The application should have defined behavior: recover to safe defaults, surface an explicit persistence error, or use a configured corruption handler.  
**Actual behavior:** The flow can fail/cancel; consumers outside the language cold-start fallback have no local recovery.  
**Impact:** Settings-dependent screens or API configuration may fail unexpectedly after a storage failure.  
**Suggested fix:** Add explicit `IOException` handling and define a corruption policy appropriate to the data classification; test corrupted/unreadable DataStore behavior.  
**Confidence:** High

---

**Finding ID:** DATA-008  
**Title:** Room cache write failures are silently reported as successful operations  
**Severity:** MEDIUM  
**Category:** Recovery  
**Affected files:** [CacheSupport.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/CacheSupport.kt:10), [NoteRepository.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt:98), [RecipientRepository.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/RecipientRepository.kt:40)  
**Location:** `cacheWrite`, lines 10-18  
**Description:** Any non-cancellation exception from a Room cache write is swallowed. Repository calls return fresh network data even if it was not persisted for offline access.  
**Evidence:** `cacheWrite` catches all `Exception` at line 15 and intentionally discards it. It wraps successful network refresh/create cache writes in notes and recipients.  
**Reproduction scenario:** Disk-full, SQLite I/O, or schema/storage failure occurs while a successful remote note response is written to Room. The user later goes offline.  
**Expected behavior:** The app should at least make cache persistence failure observable through telemetry, UI state, or a documented degraded mode.  
**Actual behavior:** The online operation appears successful; later offline screens can have stale or missing cache data.  
**Impact:** Reliability issue in server/cache mode. The remote record remains authoritative, so this is not confirmed remote data loss.  
**Suggested fix:** Preserve the non-fatal online result if desired, but emit a cache-health error/state and log structured diagnostics; distinguish storage failures from ordinary cache misses.  
**Confidence:** High

---

**Finding ID:** DATA-009  
**Title:** Outbox retries cannot safely deduplicate accepted-but-unacknowledged note uploads  
**Severity:** MEDIUM  
**Category:** Persistence  
**Affected files:** [NoteRepository.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt:71), [OutboxEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt:83)  
**Location:** `drainOutbox`, lines 207-245  
**Description:** The client retries a queued note after an `IOException`, but the documented server API does not guarantee idempotency. A server may have created the note before the client lost the response.  
**Evidence:** The repository explicitly documents that `POST /api/notes` has no usable server-side idempotency mechanism at lines 71-74 and 200-202. On I/O failure, the outbox record returns to `PENDING` at lines 224-227 and will be retried.  
**Reproduction scenario:** Server commits a note, then the connection drops before the response reaches the client. The worker retries the same queued record.  
**Expected behavior:** The server should return the original result for the same idempotency key, or the client should reconcile the ambiguous result reliably.  
**Actual behavior:** The upload may be duplicated. If the server rejects a duplicate-day submission, the outbox becomes `FAILED`, even if the original note exists remotely.  
**Impact:** Duplicate or apparently failed caregiver notes in server mode.  
**Suggested fix:** Add server-enforced idempotency keyed by a persistent client UUID, then reconcile uncertain requests by that key.  
**Confidence:** High

---

**Finding ID:** DATA-010  
**Title:** Several high-frequency Room query filters lack supporting indexes  
**Severity:** LOW  
**Category:** Performance  
**Affected files:** [NoteEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt:21), [OutboxEntity.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt:28), [AddendumDao.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt:10), [PlanDao.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/PlanDao.kt:16)  
**Location:** Note/outbox history queries, addendum lookup, plan recipient lookup  
**Description:** Several queries filter by fields without corresponding indexes.  
**Evidence:** Notes filter by `recipientId` and `date` but define only a unique `serverId` index. Outbox filters by `recipientId` and `date` with no secondary indexes. Addenda query by `noteId` without an index. Plans query by `recipientId` without an index.  
**Reproduction scenario:** A long-lived local database accumulates many notes, outbox records, plans, and addenda; history and scoped backup operations run repeatedly.  
**Expected behavior:** Common filtering paths should have indexes aligned with their query predicates and sorting needs.  
**Actual behavior:** SQLite may perform increasingly expensive table scans.  
**Impact:** Potential degraded history, summary, backup, and outbox performance as data grows.  
**Suggested fix:** Profile representative query plans and add targeted composite/single-column indexes through a tested migration.  
**Confidence:** High

---

**Finding ID:** DATA-011  
**Title:** Restore validation accepts semantically invalid plan version JSON  
**Severity:** MEDIUM  
**Category:** Integrity  
**Affected files:** [BackupValidator.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupValidator.kt:116), [BackupStore.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt:87), [LocalBackendApis.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt:228)  
**Location:** `BackupValidator.isJsonArray`, `RoomBackupStore.replaceAll`, `plans().list()`  
**Description:** Backup validation accepts any JSON array as `versionsJson`, but later plan reads deserialize it as `List<PlanVersionDto>` with required fields.  
**Evidence:** Plans are accepted if `versionsJson` parses as `JsonArray` at lines 126-128 and 170-176. Restore stores the raw string at line 87. `LocalBackendApis.plans().list()` then calls `json.decodeFromString(entity.versionsJson)` at lines 229-235 without recovery.  
**Reproduction scenario:** Restore a syntactically valid backup containing a plan `versionsJson` value of `[{}]`. The backup passes validation and restore reports success. Opening plans later fails deserialization because required version fields are absent.  
**Expected behavior:** Restore should reject invalid plan-version structures before deleting and replacing local data.  
**Actual behavior:** Corrupt plan data can be persisted and fail later on normal plan reads.  
**Impact:** A user-selected backup can leave the application with unusable plan data after a reported successful restore.  
**Suggested fix:** Decode and validate full `PlanVersionDto` objects during backup preparation, including status transitions and required values.  
**Confidence:** High

---

**Finding ID:** DATA-012  
**Title:** Backup intentionally excludes settings, authentication state, AI configuration, and outbox  
**Severity:** INFO  
**Category:** Privacy  
**Affected files:** [BackupFormat.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt:6), [BackupRestoreService.kt](/tmp/opencode/caregiver-daily-notes/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupRestoreService.kt:53)  
**Location:** Backup format documentation, lines 6-15  
**Description:** Manual backup is a data-record backup, not a full application-state backup.  
**Evidence:** The backup explicitly excludes `localId`, outbox rows, AI keys, settings, and tokens. The restore service also states that it never touches settings, tokens, keys, or outbox rows.  
**Reproduction scenario:** A user restores a backup on a new device and expects theme, language, backend address, login state, pending offline notes, or AI configuration to return.  
**Expected behavior:** Product behavior should clearly distinguish a clinical-record backup from a full app migration.  
**Actual behavior:** Core local records restore, but application configuration and pending sync state do not.  
**Impact:** This is a documented product limitation, not a confirmed defect. It may surprise users if not communicated clearly.  
**Suggested fix:** Clarify backup scope in user-facing copy and consider a separately secured settings-transfer option if product requirements call for full migration.  
**Confidence:** High

## 12. Missing Test Coverage

High-priority missing tests:

- Migration 5→6 with multiple intake dates for one medication.
- Full migration chain from schema 1 through schema 7.
- Restore conflict when a record is edited but table counts remain unchanged.
- Restore rejection for `versionsJson` values such as `[{}]`, invalid status values, empty invalid chains, and malformed item payloads.
- Simultaneous plan transitions from the same source version.
- Child write concurrent with archived-recipient deletion.
- `MedicationStore.setTaken()` for a nonexistent medication ID.
- Cancellation/failure between medication intake deletion and medication deletion.
- DataStore corrupted-file and I/O exception behavior.
- Cache write failure behavior when SQLite/disk storage is unavailable.
- Outbox ambiguous-success reconciliation where the server commits but client receives no response.
- Performance/query-plan tests for large note/addendum/outbox datasets.
- UI/Room invalidation behavior after the sample-data path writes through a separately constructed Room database.

## 13. Prioritized Remediation Plan

### Priority 1: Critical data-loss and integrity issues

1. Fix and test migration 5→6 before any release that upgrades version-5 databases.
2. Strengthen restore conflict detection beyond entity counts.
3. Validate full plan-version structures before committing a restore.

### Priority 2: High-impact migration, persistence, and concurrency issues

1. Add enforceable parent-child integrity: foreign keys where practical, transactional guards elsewhere.
2. Make missing recipients/medications fail writes explicitly.
3. Serialize or optimistic-lock plan version updates.
4. Add server-backed idempotency/reconciliation for outbox uploads.

### Priority 3: Medium and low-severity improvements

1. Make medication deletion transactional.
2. Reuse the graph’s existing Room instance for sample data.
3. Define DataStore corruption and I/O recovery behavior.
4. Surface cache persistence failures as degraded offline capability.
5. Profile and add indexes for common filtered queries.

### Future improvements

- Normalize plan versions instead of storing the complete history as opaque JSON.
- Consider encrypted storage for credentials/settings if the threat model requires stronger at-rest protection.
- Offer a clearly separated, secured full-device migration capability if desired.

## 14. Final Verdict

1. **Is the Room database architecture reliable?**  
   Partially. It has clear structure and several good transaction boundaries, but the migration defect and absent referential constraints make it unsafe in important cases.

2. **Is DataStore implemented correctly?**  
   Basic singleton and atomic-update handling are sound. Corruption/I/O recovery is incomplete.

3. **Are all database migrations safe and complete?**  
   No. Migration 5→6 causes confirmed medication-intake data loss.

4. **Is user data preserved across application updates?**  
   Not reliably for users upgrading from schema version 5 with more than one intake record per medication.

5. **Are inserts, updates, and deletes reliable?**  
   Many are, but medication deletion and plan updates are not fully atomic; parent-child writes can create orphans.

6. **Are database relationships and constraints correct?**  
   No. Relationships are maintained mostly in application code, not by Room foreign keys or database constraints.

7. **Are transactions and concurrent operations handled safely?**  
   Some critical paths are transactional, but plan updates, restore conflict detection, and medication deletion have meaningful concurrency weaknesses.

8. **Does the application recover appropriately from persistence failures?**  
   Backup transaction behavior is strong, but DataStore, cache-write, and ambiguous outbox failure handling are incomplete.

9. **Is data deletion handled correctly?**  
   Archived-recipient deletion is well implemented transactionally. Medication deletion remains vulnerable to a partial operation.

10. **Is the architecture maintainable and scalable?**  
    Moderately maintainable. The split between local authoritative storage and server cache is documented, but opaque plan JSON, missing foreign keys, duplicated Room construction, and missing indexes will become harder to manage as usage grows.

11. **Five most important issues to fix first:**

    1. DATA-001 — migration 5→6 medication-intake data loss.
    2. DATA-002 — restore conflict check based only on counts.
    3. DATA-003 — missing-parent/orphan record creation.
    4. DATA-004 — plan update lost-write race.
    5. DATA-011 — invalid plan JSON accepted by backup restore.

**Final verdict: Needs improvement — significant data-integrity and migration-safety issues identified.**
