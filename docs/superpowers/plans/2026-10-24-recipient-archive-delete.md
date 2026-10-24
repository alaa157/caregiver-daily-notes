# Recipient Archive & Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive and permanent delete for care recipients in the Android app, fully on-device, with a real Room schema migration and no web/server sync.

**Architecture:** Add a nullable `archivedAt` column to `recipients` with Room migration 6→7; existing rows migrate as unarchived. Archive and unarchive set/clear that timestamp. Permanent delete is allowed only for archived recipients and runs one Room transaction that removes the recipient plus all recipient-owned notes, addenda, plans, medications, medication intakes, and outbox rows. The feature is local-only: `LocalBackendApis` owns the writes, the Room database is the only source of truth, and no `RecipientApi`, Retrofit, or other web-server contract is added. Demo builds get in-memory parity; graphs without a local database expose no archive/delete actions. UI, ViewModels, backup/CSV handling, strings, fakes, and tests are updated to support both actions.

**Tech Stack:** Kotlin, Jetpack Compose, Room (schema v7), DataStore, Robolectric, Android instrumentation tests, Gradle.

**Spec:** Settled design from the recipient archive/delete grilling session (no separate spec file). Decisions and acceptance criteria are restated below.

---

# Two-Agent Execution Model

## Split summary

| Part | Owner | Scope | Tasks |
|---|---|---|---|
| Shared bootstrap | Agent A | Contract types, schema/migration, graph seam | Task 0 |
| Data & persistence | Agent A | Room deletion, local admin, backup/CSV, data tests | Tasks 1–4 |
| Presentation & UX | Agent B | ViewModels, screens, strings, picker guards, UI tests | Tasks 5–9 |
| Integration | Both | Merge, full verification, smoke test | Task 10 |

Only **Task 0** is serialized. Agent A lands the shared contract and migration on `feat/standalone-byok-ai`; after that, Agent A and Agent B work in separate branches and do not edit the same files.

## Copy-paste agent prompts

Give each agent the following prompt in its own session/worktree. Both agents read the same plan file; each follows only its part.

**Agent A prompt:**

```text
You are Agent A (data & persistence) for the recipient archive/delete feature.
Read docs/superpowers/plans/2026-10-24-recipient-archive-delete.md fully.
Work in /tmp/opencode/agent-a on branch agent/archive-delete-data.
First complete Task 0 (shared contract + Room migration) and push it to
feat/standalone-byok-ai; report the commit hash. Then implement Part A only
(Tasks 1-4). Do not edit presentation files, strings, Fakes.kt, or UI tests.
Everything must stay on-device: no RecipientApi endpoint, no Retrofit/HTTP,
no server sync. Follow the Frozen contract exactly. Run the data-layer tests
and report the migration test as unexecuted if no device is available.
```

**Agent B prompt:**

```text
You are Agent B (presentation & UX) for the recipient archive/delete feature.
Read docs/superpowers/plans/2026-10-24-recipient-archive-delete.md fully.
Wait for Agent A's Task 0 contract commit on feat/standalone-byok-ai, then work
in /tmp/opencode/agent-b on branch agent/archive-delete-ui.
Implement Part B only (Tasks 5-9). Code against the Frozen contract. Do not
edit Room entities/DAOs, LocalBackendApis, DemoBackend, AppGraph, backup, CSV
data logic, or build files. Use FakeRecipientAdmin and optional test injection
so your tests run without Agent A's branch. Keep archived recipients out of
every note/plan/medication/CSV selection flow and keep EN/AR strings in parity.
After Agent A's PR lands, rebase and run the end-to-end tests.
```

## Bootstrap handoff (do this first)

Agent A must complete **Task 0**, push it to `feat/standalone-byok-ai`, and post the commit hash. Agent B must not start production edits until that commit is available. While waiting, Agent B may draft strings and tests locally.

After Task 0, the shared contract is fixed. Any change to it requires both agents to agree and Agent A to push a follow-up contract commit.

## Branches and worktrees

From an up-to-date `feat/standalone-byok-ai` **after Task 0 is pushed**:

```bash
git fetch origin
git worktree add /tmp/opencode/agent-a -b agent/archive-delete-data origin/feat/standalone-byok-ai
git worktree add /tmp/opencode/agent-b -b agent/archive-delete-ui origin/feat/standalone-byok-ai
```

- Agent A works in `/tmp/opencode/agent-a` on `agent/archive-delete-data`.
- Agent B works in `/tmp/opencode/agent-b` on `agent/archive-delete-ui`.
- Both push their branches. Agent A opens the first PR/merge into `feat/standalone-byok-ai`; after it lands, Agent B rebases onto the updated feature branch, reruns tests, and opens the second PR/merge.
- Never merge Agent B before Agent A: Agent B's end-to-end UI tests need the real local admin.

## Frozen contract

These signatures and types are owned by Agent A after Task 0. Agent B codes against them and must not redefine them.

```kotlin
// data/local/RecipientDeletionCounts.kt
data class RecipientDeletionCounts(
    val notes: Int,
    val addenda: Int,
    val plans: Int,
    val medications: Int,
    val intakes: Int,
    val outbox: Int,
)

// data/local/RecipientAdmin.kt
interface RecipientAdmin {
    suspend fun archive(id: String): RecipientDto
    suspend fun unarchive(id: String): RecipientDto
    suspend fun deleteArchived(id: String)
    suspend fun counts(id: String): RecipientDeletionCounts
}

class ActiveRecipientDeleteException(val recipientId: String) :
    IllegalStateException("Recipient $recipientId is not archived")

class ArchivedRecipientException(val recipientId: String) :
    IllegalStateException("Recipient $recipientId is archived")
```

```kotlin
// RecipientEntity: archive state is separate from the existing active flag.
val active: Boolean,
val archivedAt: Long? = null,

// RecipientDto: transport surface used by local and demo graphs.
data class RecipientDto(
    val id: String,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)

// CaregiverApp (AppGraph)
val recipientAdmin: RecipientAdmin? by lazy { live as? RecipientAdmin }

// CsvImportPlanner
data class RecipientRef(
    val id: String,
    val name: String,
    val active: Boolean,
    val archived: Boolean = false,
)
```

Room contract:

```kotlin
@Database(version = 7)
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipients` ADD COLUMN `archivedAt` INTEGER")
    }
}
```

## File ownership

**Agent A only** (do not edit from Agent B):

- `android/app/build.gradle.kts`, `android/gradle/libs.versions.toml`
- `android/app/schemas/com.caregiver.mobile.data.local.AppDatabase/7.json`
- `data/local/AppDatabase.kt`, `RecipientEntity.kt`, `NoteEntity.kt`
- `data/local/AddendumDao.kt`, `PlanDao.kt`, `MedicationEntity.kt`, `MedicationStore.kt`, `OutboxEntity.kt`
- `data/local/RecipientAdmin.kt`, `RecipientDeletionCounts.kt`, `LocalBackendApis.kt`
- `data/demo/DemoBackend.kt`
- `CaregiverApp.kt`
- `data/backup/*`
- `data/csv/*`
- `data/api/RecipientApi.kt` (DTO field only; never add an endpoint)
- `test/.../data/local/*`, `test/.../data/backup/*`, `test/.../data/csv/*`
- `androidTest/.../data/local/RecipientMigrationTest.kt`

**Agent B only** (do not edit from Agent A):

- `presentation/recipients/RecipientsViewModel.kt`
- `presentation/recipients/RecipientDetailViewModel.kt`
- `presentation/recipients/RecipientScreens.kt`
- `presentation/home/HomeViewModel.kt`, `presentation/home/MedicationViewModel.kt`
- `presentation/notes/RecipientPickerSheet.kt`, `presentation/notes/NoteEditorViewModel.kt`, `presentation/notes/NoteScreens.kt` where recipient selection/creation lives
- `presentation/plans/PlansViewModel.kt`, `presentation/plans/PlanEditViewModel.kt`, `presentation/plans/PlanScreens.kt` where recipient selection/creation lives
- `presentation/settings/CsvTransferUi.kt` (recipient choice list only)
- `MainScaffold.kt`
- `res/values/strings.xml`, `res/values-ar/strings.xml`
- `sharedTest/.../Fakes.kt`
- `test/.../presentation/recipients/*`, `test/.../presentation/home/*`, `test/.../presentation/notes/*`, `test/.../presentation/plans/*`
- `androidTest/.../presentation/recipients/*`

**No shared files after Task 0.** If a task seems to require a file owned by the other agent, stop and use the frozen contract instead of editing across the boundary.

## Agent A instructions

1. Work only in Part A (Tasks 0–4) and only in the Agent A ownership list.
2. Complete Task 0 first, push it to `feat/standalone-byok-ai`, and report the commit hash.
3. Create your worktree/branch from that commit and implement Tasks 1–4.
4. Keep the feature local: no `RecipientApi` endpoint, no Retrofit/HTTP call, no server contract, no `RoomBackedBackendApis` delete behavior.
5. Do not touch screens, ViewModels, strings, `Fakes.kt`, or UI tests. If UI needs a new behavior, expose it through the frozen contract.
6. Run the data-layer commands after every task; run the migration test where a device/emulator exists, otherwise record it as unexecuted.
7. Push `agent/archive-delete-data` and open the first PR.

**Agent A done criteria:**

- `7.json` committed; `MIGRATION_6_7` preserves every v6 row and sets `archivedAt = NULL`.
- Archive/unarchive/delete work only against Room; delete is one transaction and cascades every owned table.
- `RecipientAdmin` is implemented by `LocalBackendApis` and `DemoBackendApis`; `AppGraph.recipientAdmin` resolves correctly.
- Notes/plans/medications for archived recipients are rejected in the data layer.
- Backup v2 preserves `archivedAt`; v1 still restores; CSV never targets archived recipients.
- Data tests pass; no network/API changes.

## Agent B instructions

1. Wait for Agent A's Task 0 commit. Then create your worktree/branch from `feat/standalone-byok-ai`.
2. Work only in Part B (Tasks 5–9) and only in the Agent B ownership list.
3. Code against the frozen contract. Do not edit Room entities, DAOs, `LocalBackendApis`, `DemoBackend`, `AppGraph`, backup, CSV data logic, or build files.
4. Use a `FakeRecipientAdmin` in `Fakes.kt` for ViewModel tests and screen-level tests. Inject it into ViewModels and, where needed, add an optional `admin: RecipientAdmin? = graph.recipientAdmin` parameter to screens so UI tests do not depend on Agent A's implementation.
5. Implement archived-recipient filtering/guards in every presentation flow; catch `ArchivedRecipientException` from the data layer and show the localized error.
6. Run presentation tests and compile instrumented tests without waiting for the data branch. End-to-end UI tests that require the real local store are marked for Task 10.
7. After Agent A's PR lands, rebase onto `feat/standalone-byok-ai`, run the full presentation suite plus the end-to-end tests, and open the second PR.

**Agent B done criteria:**

- Active/Archived filter, archive/unarchive/delete actions, undo, confirmation counts, and archived read-only states work.
- Archived recipients are excluded from every note/plan/medication picker and CSV recipient choice.
- English/Arabic strings are in parity.
- ViewModel and Robolectric UI tests pass with fakes; instrumented tests compile.
- No data-layer files changed.

## Integration protocol (Task 10)

1. Agent A merges first into `feat/standalone-byok-ai`.
2. Agent B rebases `agent/archive-delete-ui` onto the updated feature branch. Conflicts should be limited to import ordering; ownership rules decide who fixes what.
3. Agent B runs the full end-to-end UI suite against the real `LocalBackendApis` and local Room database.
4. Agent A runs the complete unit test suite, lint, and the migration/dao suites again.
5. Both review `git diff` together: no server/API calls, no duplicate admin implementations, no strings missing, no archived recipient pickers left open.
6. Final sign-off requires all tests green or a recorded device-dependent test that could not run.

---

# Execution Contract

- Before editing, inspect the current implementation and tests for every listed surface. This plan is a checklist, not proof that a path, class, test, or API already exists; update obsolete paths and commands to match the repository.
- The shared contract in **Frozen contract** is authoritative. Do not rename methods, change nullability, or add parallel abstractions in the other agent's layer.
- Keep archive reversible and distinct from delete. `archivedAt != null` means archived; `active` keeps its existing meaning and is **not** reused as the archive flag. Deleting is permanent for the current database only.
- The schema change is mandatory: bump Room to version 7 and add `MIGRATION_6_7`. Existing installs must upgrade in place with all recipients and owned data preserved.
- Everything stays on the device. Do not add `delete`/archive endpoints to `RecipientApi`, do not add HTTP annotations, and do not implement server or Retrofit archive/delete behavior. Do not make `RecipientRepository` or `RoomBackedBackendApis` perform network deletes.
- The local store is authoritative: `LocalBackendApis` performs archive/unarchive/delete directly against Room, and delete uses one Room-supported transaction.
- Enforce archived-recipient restrictions in underlying selection/data flows, not only by hiding UI actions. Cover notes, plans, medications, and CSV import mappings.
- Delete only an archived recipient. Reject stale or repeated UI actions safely; never report success when the transaction fails.
- Use Room-supported transaction APIs (`RoomDatabase.withTransaction` or a DAO `@Transaction` method); do not assume `@Transaction` on an `AppDatabase` convenience method is supported.
- Add assertions for an unrelated recipient and every owned-data table. Guard empty `IN` lists and respect SQLite bind-parameter limits for large ID lists.
- Run commands that execute tests separately from commands that only compile/assemble them. If a device-dependent migration/instrumentation test cannot run, report it as unexecuted rather than passed.
- Preserve unrelated worktree changes and keep the feature scoped to the standalone app.

# Global Constraints

- Branch: `feat/standalone-byok-ai`; agent branches as defined above.
- No web backend sync in this version: no server calls, no API contract, no outbox involvement for archive/delete. Archive/delete must work with the device offline.
- Room migration 6→7 is required. `archivedAt` is nullable with no backfill: every pre-migration recipient becomes unarchived, and no existing column is rewritten.
- Archive is reversible and local. Unarchive preserves the recipient's existing `active` value.
- Permanent delete is allowed only when `archivedAt != null`.
- Local deletion must be atomic across every recipient-owned table and must remove pending outbox rows for that recipient.
- Delete confirmation counts must reflect the local rows that will actually be removed.
- Archived recipients remain visible under an Archived filter.
- Archived recipients are read-only except Unarchive and Delete.
- Archived recipients must not be selectable for new notes, plans, medications, or CSV import targets.
- English and Arabic strings must stay in parity.
- Existing tests must continue to pass.
- Backups remain local files. A backup created before a deletion can restore the deleted recipient and its data; deletion does not rewrite old backups. The confirmation/help copy must explain this.
- Backup files must preserve archive state. Bump the backup format so restored archived recipients do not silently become active.
- CSV is a note-transfer format, not a recipient export; it must never reactivate or write notes onto an archived recipient.

# Review Focus

- Upgrade path: a v6 install with recipients, notes, addenda, plans, and medications opens on v7 with every row intact and `archivedAt = NULL`.
- A user archives the wrong recipient and expects Unarchive (or the Undo snackbar) to restore it exactly, including returning it to the non-archived list.
- A deleted recipient must not leave dangling notes, addenda, plans, medications, intakes, or outbox rows.
- An archived recipient must not be selectable for new notes, plans, or medications, or chosen as a CSV import target — even if a form was opened before the archive.
- Restore from an older backup can resurrect a deleted recipient; this must be documented and expected, not treated as a bug.
- CSV transfers notes, not recipient records: import must never reactivate an archived recipient or silently import a note onto one.
- Archive/delete must fail atomically and surface the error; a failed delete must leave every row untouched.
- The list and detail surfaces must prevent duplicate in-flight actions and reflect the final state after success or failure.
- Archive/delete must be absent (not fake-success) in graphs that do not own a local database.

---

# Part A — Data & Persistence (Agent A)

## Task 0: Shared contract and schema bootstrap (Agent A)

**Owner:** Agent A. **Serialized:** complete this before Agent B starts production edits.

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientDeletionCounts.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientAdmin.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt`

**Interfaces:**
- Produces the entire **Frozen contract**.
- Produces exported schema `7.json`.

- [ ] **Step 1: Add the schema field and migration**

Add `archivedAt: Long? = null` as the last column on `RecipientEntity`, map it in `toDto()` / `toEntity()`, and add the same defaulted field to `RecipientDto`.

Bump `@Database(version = 7)` and add `MIGRATION_6_7` exactly as shown in the frozen contract. Register it in `create(...)`.

- [ ] **Step 2: Add the contract types**

Create `RecipientDeletionCounts.kt` and `RecipientAdmin.kt` with the exact declarations from the frozen contract. Include the two exception classes.

- [ ] **Step 3: Add the graph seam and CSV ref field**

Add `AppGraph.recipientAdmin` exactly as shown. Add `archived: Boolean = false` to `RecipientRef`; do not use it in the planner yet (Task 3 does that).

- [ ] **Step 4: Add and run the migration test**

Add `room-testing` to the version catalog and `androidTestImplementation`, then create `RecipientMigrationTest`:

- Open a version 6 database using the exported `6.json`.
- Insert two recipients, a note, an addendum, a plan, a medication, an intake, and an outbox row.
- Migrate to version 7.
- Assert every row still exists and every recipient has `archivedAt = NULL`.
- Reopen through Room and confirm schema validation passes.

Build to generate and commit `7.json`. Do not use destructive migration.

- [ ] **Step 5: Verify the shared base**

```bash
cd android && ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest -v
```

Run the migration test on a device/emulator if available. If not, record it as unexecuted.

- [ ] **Step 6: Commit and hand off**

```bash
git add android/app/build.gradle.kts android/gradle/libs.versions.toml \
  android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/RecipientAdmin.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/RecipientDeletionCounts.kt \
  android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt \
  android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt \
  android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt \
  android/app/schemas/com.caregiver.mobile.data.local.AppDatabase/7.json
git commit -m "feat: add recipient archive contract and Room migration"
git push origin feat/standalone-byok-ai
```

Post the commit hash for Agent B.

## Task 1: Transactional Room deletion and counts (Agent A)

**Owner:** Agent A.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/PlanDao.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`

**Interfaces:**
- Produces: `AppDatabase.deleteArchivedRecipient(recipientId: String)`
- Produces: `AppDatabase.recipientDataCounts(recipientId: String): RecipientDeletionCounts`
- Produces DAO helpers listed below

- [ ] **Step 1: Write the failing deletion test**

Create `RecipientDeletionTest.kt` with an in-memory Room database. Insert an archived recipient and an unarchived recipient, each with a note, addendum, plan, medication, intake, and outbox row.

Assert:

- Deleting an unarchived recipient throws and leaves every row unchanged.
- Deleting the archived recipient removes only its rows from all seven tables.
- The unrelated recipient and its rows remain.
- Deleting the same recipient again is a no-op.
- Deleting a missing recipient is a no-op.
- Counts match the rows removed.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v
```

- [ ] **Step 3: Add DAO helpers and the transaction**

Add:

- `RecipientDao.setArchivedAt(serverId: String, archivedAt: Long?): Int`
- `RecipientDao.deleteByServerId(serverId: String): Int`
- `NoteDao.serverIdsByRecipient(recipientId: String): List<String>`
- `NoteDao.countByRecipient(recipientId: String): Int`
- `NoteDao.deleteByRecipient(recipientId: String)`
- `AddendumDao.countForRecipient(recipientId: String): Int`
- `AddendumDao.deleteByNoteIds(noteIds: List<String>)`
- `PlanDao.countByRecipient(recipientId: String): Int`
- `PlanDao.deleteByRecipient(recipientId: String)`
- `MedicationDao.idsByRecipient(recipientId: String): List<String>`
- `MedicationDao.countByRecipient(recipientId: String): Int`
- `MedicationDao.countIntakesForRecipient(recipientId: String): Int`
- `MedicationDao.clearIntakesForMedications(medicationIds: List<String>)`
- `MedicationDao.deleteByRecipient(recipientId: String)`
- `OutboxDao.countByRecipient(recipientId: String): Int`
- `OutboxDao.deleteByRecipient(recipientId: String)`

Implement `AppDatabase.deleteArchivedRecipient` with `withTransaction`:

- Read the recipient inside the transaction. Absent → return.
- `archivedAt == null` → throw `ActiveRecipientDeleteException` without mutating anything.
- Delete addenda by note ids, then notes.
- Delete plans by recipient.
- Delete intakes by medication ids, then medications.
- Delete outbox rows by recipient.
- Delete the recipient row last.
- Chunk every `IN (...)` delete (for example 500 ids) and skip empty lists.

Implement `AppDatabase.recipientDataCounts` with the same scoping.

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/local/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt
git commit -m "feat: add transactional recipient deletion"
```

## Task 2: Local-only recipient admin (Agent A)

**Owner:** Agent A.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationStore.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientAdminTest.kt`

**Interfaces:**
- Consumes: the frozen `RecipientAdmin` contract from Task 0
- Produces: `LocalBackendApis : RecipientAdmin`, `DemoBackendApis : RecipientAdmin`
- Explicitly does **not** change any `RecipientApi` endpoint, `RetrofitBackendApis`, `RecipientRepository`, or `RoomBackedBackendApis`

- [ ] **Step 1: Write failing local admin tests**

Create `RecipientAdminTest.kt` covering:

- `archiveSetsArchivedAtAndReturnsArchivedRecipient`
- `archiveIsIdempotent`
- `unarchiveClearsArchivedAt`
- `unarchivePreservesActiveFlag`
- `archiveMissingRecipientThrows`
- `deleteArchivedRemovesRecipientAndOwnedRows`
- `deleteUnarchivedRecipientIsRejected`
- `countsMatchStoredRows`
- `createNoteForArchivedRecipientIsRejected`
- `appendNoteForArchivedRecipientIsRejected`
- `createPlanForArchivedRecipientIsRejected`
- `planTransitionForArchivedRecipientIsRejected`
- `addMedicationForArchivedRecipientIsRejected`
- `medicationUpdateOrIntakeForArchivedRecipientIsRejected`

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientAdminTest" -v
```

- [ ] **Step 3: Implement the local admin**

Make `LocalBackendApis` implement `RecipientAdmin`:

- `archive`: set `archivedAt = clock()` when currently null; leave an existing timestamp unchanged.
- `unarchive`: set `archivedAt = null`.
- `deleteArchived`: call `db.deleteArchivedRecipient(id)`.
- `counts`: call `db.recipientDataCounts(id)`.
- Return the updated `RecipientDto`, including `archivedAt`.
- Inject `clock: () -> Long = System::currentTimeMillis` as a constructor default for deterministic tests.

Add defense-in-depth guards so every recipient-owned write rejects an archived recipient with `ArchivedRecipientException`:

- Notes: `create(...)` and `append(...)`; appending resolves the note's recipient first.
- Plans: `suggest(...)`, `appendVersion(...)`, and every transition (`accept`, `editAccept`, `dismiss`, `archive`); resolve the plan's recipient first.
- Medications: `MedicationStore.add(...)`, `update(...)`, and intake writes; resolve the medication/recipient first.

This is required even though Agent B hides the UI actions: a stale open form or a race must not write to an archived recipient.

Make `DemoBackendApis` implement `RecipientAdmin` in memory: archive/unarchive update the in-memory DTOs; delete removes the recipient plus its in-memory notes, addenda, and plans. Apply the same archived-recipient write guards. Demo data never touches Room. Do not add network fallbacks.

`AppGraph.recipientAdmin` was added in Task 0; it now resolves to these implementations automatically. Do not edit the graph unless the cast needs an import.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientAdminTest" -v
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/MedicationStore.kt \
  android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt \
  android/app/src/test/java/com/caregiver/mobile/data/local/RecipientAdminTest.kt
git commit -m "feat: add on-device recipient archive and delete"
```

## Task 3: Backup and CSV behavior (Agent A)

**Owner:** Agent A.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupCodec.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/BackupCodecTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/csv/CsvImportPlannerTest.kt`

- [ ] **Step 1: Add failing backup tests**

Verify:

- Snapshots include `archivedAt`.
- Restores recreate archived recipients with their timestamp and unarchived recipients with `null`.
- A v1 backup without `archivedAt` still restores; those recipients become unarchived.
- A v2 document round-trips.
- Restoring a pre-deletion backup can recreate a deleted recipient; assert the documented behavior.

- [ ] **Step 2: Add failing CSV tests**

Do not treat CSV as a recipient backup. Verify:

- Import planning cannot target an archived recipient.
- An archived recipient stays archived after import.
- A note is never imported onto an archived recipient via id or name matching.
- Archived recipients are excluded from resolution targets.

- [ ] **Step 3: Implement backup v2 with v1 compatibility**

- Add `archivedAt: Long? = null` to `BackupRecipient`.
- Map it in `RecipientEntity.toBackup()` / `BackupRecipient.toEntity()`.
- Bump `CareBackupCodec.FORMAT_VERSION` to `2`.
- Decode accepts 1 and 2; v1 leaves `archivedAt = null`; encode always writes v2.
- Keep the strict parser and add a deliberate v1 decode path.

- [ ] **Step 4: Implement CSV archived-recipient rules**

- Populate `RecipientRef.archived` from `archivedAt != null` in `CsvTransferService`.
- In `CsvImportPlanner`, archived recipients never satisfy a match or resolution and never receive planned notes.
- Do not change recipient `active` or `archivedAt` during import.
- Do not add a new `ImportIssue`; reuse the existing unmatched/unavailable behavior so Agent B does not need a new data enum.

- [ ] **Step 5: Run focused tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.backup.*" -v
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.csv.*" -v
```

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/backup/*.kt \
  android/app/src/main/java/com/caregiver/mobile/data/csv/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/backup/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/csv/*.kt
git commit -m "feat: preserve archive state in backup and block archived csv targets"
```

## Task 4: Data-layer verification (Agent A)

**Owner:** Agent A.

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/NoteDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/AddendumDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/PlanDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/MedicationStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/OutboxDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt`

- [ ] **Step 1: Add DAO deletion tests**

Cover recipient, note, addendum, plan, medication, intake, and outbox deletion; unrelated rows remain; counts match; empty lists and repeated deletes are safe.

- [ ] **Step 2: Run the data suites**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.*" -v
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.backup.*" -v
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.csv.*" -v
```

Run `RecipientMigrationTest` on a device/emulator if available; otherwise record it as unexecuted.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/local/*Test.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt
git commit -m "test: cover recipient deletion cascade and migration"
```

---

# Part B — Presentation & UX (Agent B)

## Task 5: Recipient ViewModels (Agent B)

**Owner:** Agent B. Start after Agent A's Task 0 is pushed.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt`
- Modify: `android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`

**Interfaces:**
- Consumes: frozen `RecipientAdmin`, `RecipientDeletionCounts`, `ArchivedRecipientException`
- Produces:
  - `RecipientsViewModel.archive(id)`, `unarchive(id)`, `delete(id)`, `consumeUndo()`, `setFilter(PeopleFilter)`
  - `RecipientDetailViewModel.archive()`, `unarchive()`, `delete()`, `consumeDeleted()`
  - `FakeRecipientAdmin`

- [ ] **Step 1: Write failing ViewModel tests with a fake admin**

Add a configurable `FakeRecipientAdmin` to `Fakes.kt`. Add tests for:

- `archiveMovesRecipientToArchived`
- `undoArchiveRestoresRecipientAndActiveFilter`
- `unarchiveRestoresRecipientToList`
- `deleteRemovesArchivedRecipient`
- `deleteNotAvailableForUnarchivedRecipient`
- `deleteFailureLeavesRecipientUnchanged`
- `archiveFailureShowsErrorAndKeepsRow`
- `countsAreLoadedForConfirmation`
- `detailArchiveKeepsDetailOpenAndReadOnly`
- `detailUnarchiveRestoresEditableState`
- `detailDeleteNavigatesBack`
- `actionsHiddenWhenAdminMissing`

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*ViewModelTest" -v
```

- [ ] **Step 3: Implement the ViewModels**

`RecipientsViewModel`:

- Add an optional `admin: RecipientAdmin?` constructor parameter (default so existing tests compile) and pass `graph.recipientAdmin` from the screens. Also allow screens/tests to inject a fake.
- Add `PeopleFilter` (`Active`, `Archived`) and expose the selected filter.
- Add `archive`, `unarchive`, `delete`, `consumeUndo`, and `counts`.
- Add per-recipient busy ids, an error message, and one pending undo id. Clear undo on consume, expiry, or a newer archive.
- Archive uses `admin.archive`; undo/unarchive uses `admin.unarchive`; delete uses `admin.deleteArchived`.
- Refresh after mutation; on failure keep the row unchanged, expose an error, and never report success.
- Serialize or reject conflicting actions per recipient.

`RecipientDetailViewModel`:

- Add the optional `admin` parameter.
- Add `archived: Boolean` based on `archivedAt != null` to `RecipientDetail`.
- Add `archive`, `unarchive`, `delete`, `consumeDeleted`, plus `busy` and `errorMessage`.
- Reject delete while unarchived; update state/navigation only after success.
- Disable actions while busy.

Do not edit Room, DAOs, `LocalBackendApis`, `DemoBackend`, `AppGraph`, backup, or CSV data logic.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*ViewModelTest" -v
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt \
  android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt \
  android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt \
  android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt
git commit -m "feat: add recipient archive and delete ViewModels"
```

## Task 6: People screen and picker guards (Agent B)

**Owner:** Agent B.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/home/MedicationViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/RecipientPickerSheet.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteEditorViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlansViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlanScreens.kt` where recipient selection/creation lives
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/CsvTransferUi.kt` (recipient choice list only)
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`
- Test: targeted tests for each recipient selector/creation flow

**Interfaces:**
- Consumes: `RecipientsViewModel.archive`, `unarchive`, `delete`, `consumeUndo`, `setFilter`
- Consumes: `RecipientDto.archivedAt`, `RecipientRef.archived`, `ArchivedRecipientException`
- Consumes: `AppGraph.recipientAdmin != null` to decide whether actions exist

- [ ] **Step 1: Write failing UI tests**

Use a fake admin injected into the screen/ViewModel; do not depend on Agent A's data branch. If the screen needs a fake list source, add optional test-only injection (for example `apis`/`admin` parameters with production defaults) or extract stateless content composables. Do not edit data-layer files. Add tests for:

- `archiveFromActiveListMovesRowToArchived`
- `undoArchiveRestoresRow`
- `archivedFilterShowsArchivedRecipient`
- `deleteFromArchivedListRemovesRow`
- `deleteConfirmationShowsCountsAndBackupCaveat`
- `archivedRowHasNoAddNoteAction`
- `archiveActionsHiddenWithoutLocalAdmin`
- `archivedRecipientIsNotSelectableForNewNote`
- `archivedRecipientIsNotSelectableForNewPlan`
- `archivedRecipientIsNotSelectableForMedication`
- `archivedRecipientIsNotSelectableForCsvImport`

- [ ] **Step 2: Run tests to verify they fail**

Run the affected Robolectric tests, or connected instrumentation when necessary. Assembly alone is not a test run.

- [ ] **Step 3: Implement the People screen**

Add:

- Active / Archived segmented filter driven by `RecipientsViewModel.setFilter`.
- Active filter: `archivedAt == null`; Archived filter: `archivedAt != null`.
- Overflow menu per row.
- Unarchived row: Archive. Archived row: Unarchive, Delete.
- Delete confirmation dialog with authoritative counts and the backup caveat.
- Undo snackbar for archive.
- Disable row actions while a mutation is in flight.
- Render archive/delete actions only when an admin is available.

Apply the archived guard at every new-note, new-plan, medication, and CSV selection source:

- Keep the existing `active` filter and add `archivedAt == null`.
- Do not rely on the People screen to enforce this invariant.
- If an open form's recipient becomes archived, block submission and show a localized error.
- Catch `ArchivedRecipientException` in the note/plan/medication create paths and surface the error.
- Update `MainScaffold` and `HomeViewModel` picker filters to exclude archived recipients.
- Update `CsvTransferUi` recipient choices to exclude `RecipientRef.archived`.

Add an optional `admin: RecipientAdmin? = graph.recipientAdmin` parameter to the recipient screens if needed for test injection; production call sites keep the default.

- [ ] **Step 4: Run tests and compile instrumentation**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*" -v
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt \
  android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/home/MedicationViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/notes/RecipientPickerSheet.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteEditorViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlansViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlanScreens.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/settings/CsvTransferUi.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add recipient archive and delete UI"
```

## Task 7: Recipient detail UI (Agent B)

**Owner:** Agent B.

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

**Interfaces:**
- Consumes: `RecipientDetailViewModel.archive`, `unarchive`, `delete`

- [ ] **Step 1: Add failing detail UI tests**

Add tests for:

- `archivedDetailHidesAddNote`
- `archivedDetailHidesPlanAction`
- `archivedDetailShowsUnarchiveAndDelete`
- `archivedDetailDeleteConfirmationShowsCaveat`
- `archivedDetailDeleteNavigatesBack`
- `unarchivedDetailShowsArchive`

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement the detail screen**

- Determine archived state from `recipient.archivedAt != null`.
- If archived: hide Add note and the plan action; keep History/Summary; show Unarchive and Delete.
- If unarchived: show existing actions plus Archive.
- Add an archived status pill.
- Add a delete confirmation dialog with authoritative local counts and the backup caveat.
- Disable actions while busy.
- On success, refresh or navigate back after delete; on failure keep the detail and show a retryable error.
- Hide archive/delete actions when no admin is available.

- [ ] **Step 4: Run instrumentation compile**

```bash
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add archived recipient detail actions"
```

## Task 8: Strings and localization (Agent B)

**Owner:** Agent B.

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ar/strings.xml`

- [ ] **Step 1: Add English strings**

Add keys for:

- `people_filter_active`
- `people_filter_archived`
- `people_archive`
- `people_unarchive`
- `people_delete`
- `people_delete_title`
- `people_delete_body` with count placeholders
- `people_delete_success`
- `people_archive_undo`
- `people_archived_readonly`
- `people_archive_failed`
- `people_delete_failed`
- `people_delete_backup_caveat`
- `people_archived_recipient_unavailable`
- busy/error/archived-selection messages used by Tasks 5–7

- [ ] **Step 2: Add Arabic strings**

Add matching Arabic translations for every key, including the backup caveat, errors, busy state, and archived-recipient validation.

- [ ] **Step 3: Run string parity**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.core.i18n.StringsParityTest" -v
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-ar/strings.xml
git commit -m "feat: add recipient archive and delete strings"
```

## Task 9: Presentation verification (Agent B)

**Owner:** Agent B.

**Files:**
- Test: recipient ViewModel tests
- Test: recipient UI tests
- Test: picker/creation flow tests
- Test: `StringsParityTest` (existing)

- [ ] **Step 1: Run the presentation suite**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*" -v
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.core.i18n.StringsParityTest" -v
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

- [ ] **Step 2: Confirm no data-layer files changed**

```bash
git diff --name-only origin/feat/standalone-byok-ai...HEAD | grep -E "data/(local|api|backup|csv)|AppDatabase|RecipientEntity|LocalBackendApis|DemoBackend|build.gradle|libs.versions" && echo "OWNERSHIP VIOLATION" || true
```

Expected: no data-layer paths. If any appear, revert them and use the frozen contract.

- [ ] **Step 3: Commit test-only changes**

```bash
git add android/app/src/test/java/com/caregiver/mobile/presentation \
  android/app/src/androidTest/java/com/caregiver/mobile/presentation
git commit -m "test: cover recipient archive and delete presentation flows"
```

---

# Part C — Integration

## Task 10: Merge and final verification (both agents)

**Owner:** Agent A leads the data checks; Agent B leads the UI checks.

- [ ] **Step 1: Merge Agent A first**

Agent A's PR lands on `feat/standalone-byok-ai`. Confirm `LocalBackendApis` and `DemoBackendApis` implement `RecipientAdmin`, and `AppGraph.recipientAdmin` no longer returns null in local mode.

- [ ] **Step 2: Rebase Agent B**

```bash
git checkout agent/archive-delete-ui
git fetch origin
git rebase origin/feat/standalone-byok-ai
```

Resolve only import-order conflicts. Do not let Agent B edit data files or Agent A edit UI files.

- [ ] **Step 3: Run end-to-end UI tests**

Run the recipient UI flows against the real `LocalBackendApis` and Room database, including archive → undo, archive → delete, detail read-only state, picker exclusion, and restart persistence.

- [ ] **Step 4: Run the full verification**

```bash
cd android && ./gradlew :app:testDebugUnitTest -v
cd android && ./gradlew :app:lintDebug -v
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

Run `connectedDebugAndroidTest` (including `RecipientMigrationTest`) if a device/emulator is available; otherwise record it as not run.

- [ ] **Step 5: Final diff review**

```bash
git status --short && git diff --stat
```

Confirm:

- `7.json` is committed and `6.json` is unchanged.
- No server/API/Retrofit archive/delete code exists.
- No duplicate `RecipientAdmin` implementations or parallel archive fields exist.
- English and Arabic keys match.
- No archived recipient can be selected in any creation flow.

- [ ] **Step 6: Merge Agent B and sign off**

Merge Agent B's PR. Both agents confirm the acceptance criteria; any device-dependent test not run is listed explicitly.

---

# Self-Review Notes

- Reconcile this plan against actual repository APIs before implementation; update obsolete paths and test selectors instead of creating duplicate abstractions.
- Verify no archive/delete network call was introduced anywhere; the only writer is `LocalBackendApis` over Room.
- Verify the v6→v7 migration on exported schema data, not just a fresh in-memory database.
- Verify `active` remains untouched by archive/unarchive and that filters use `archivedAt`.
- Verify archived-only deletion, undo, transaction rollback, backup restore, and CSV import target eligibility.
- Do not mark a task complete from compilation alone when its acceptance criterion requires executed tests.

# Execution Handoff

1. Agent A executes Task 0 and pushes to `feat/standalone-byok-ai`.
2. Agent A starts Part A; Agent B starts Part B from the pushed contract commit.
3. Agent A merges first; Agent B rebases and runs end-to-end tests.
4. Both complete Task 10. There is no server/API dependency.
