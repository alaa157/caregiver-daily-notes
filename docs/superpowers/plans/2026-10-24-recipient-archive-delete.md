# Recipient Archive & Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive and permanent delete for care recipients in the Android app, fully on-device, with a real Room schema migration and no web/server sync.

**Architecture:** Add a nullable `archivedAt` column to `recipients` with Room migration 6→7; existing rows migrate as unarchived. Archive and unarchive set/clear that timestamp. Permanent delete is allowed only for archived recipients and runs one Room transaction that removes the recipient plus all recipient-owned notes, addenda, plans, medications, medication intakes, and outbox rows. The feature is local-only: `LocalBackendApis` owns the writes, the Room database is the only source of truth, and no `RecipientApi`, Retrofit, or other web-server contract is added. Demo builds get in-memory parity; graphs without a local database expose no archive/delete actions. UI, ViewModels, backup/CSV handling, strings, fakes, and tests are updated to support both actions.

**Tech Stack:** Kotlin, Jetpack Compose, Room (schema v7), DataStore, Robolectric, Android instrumentation tests, Gradle.

**Spec:** Settled design from the recipient archive/delete grilling session (no separate spec file). Decisions and acceptance criteria are restated below.

## Execution Contract

- Before editing, inspect the current implementation and tests for every listed surface. This plan is a checklist, not proof that a path, class, test, or API already exists; update obsolete paths and commands to match the repository.
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

## Global Constraints

- Branch: `feat/standalone-byok-ai`.
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

## Review Focus

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

## File Structure

Files modified:

- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`
- `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/PlanEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt` (DTO field only; no endpoint)
- `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`
- `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeViewModel.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Relevant plan-creation and medication-selection surfaces discovered during the initial audit
- `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupCodec.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-ar/strings.xml`
- `android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt`

Files created:

- `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientDeletionCounts.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientAdmin.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/LocalRecipientAdmin.kt` (or implement the interface directly in `LocalBackendApis`)
- `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientAdminTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/AddendumDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/PlanDaoTest.kt`
- `android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt`

Files generated and committed:

- `android/app/schemas/com.caregiver.mobile.data.local.AppDatabase/7.json`

Files modified for tests:

- `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/NoteDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/MedicationStoreTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/OutboxDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/backup/BackupCodecTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/csv/CsvImportPlannerTest.kt`
- `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

## Task 1: Room schema v7 migration

**Files:**
- Modify: `android/app/build.gradle.kts`
- Modify: `android/gradle/libs.versions.toml`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt`

**Interfaces:**
- Produces: `RecipientEntity.archivedAt: Long?`, `RecipientDto.archivedAt: Long?`, `AppDatabase.MIGRATION_6_7`
- Produces: exported schema `7.json`

- [ ] **Step 1: Add the Room testing dependency and write the failing migration test**

Add `room-testing` to the version catalog and `androidTestImplementation`:

```toml
room-testing = { group = "androidx.room", name = "room-testing", version.ref = "room" }
```

Create `RecipientMigrationTest.kt` using `MigrationTestHelper` and the exported `6.json` schema:

- Create a version 6 database.
- Insert at least two recipients, a note, an addendum, a plan, a medication, a medication intake, and an outbox row.
- Run `MIGRATION_6_7`.
- Assert every seeded row still exists unchanged and every recipient has `archivedAt = NULL`.
- Close and reopen the database through the migrated version; Room schema validation must pass.

- [ ] **Step 2: Run the test to verify it fails**

Run the migration test on an available device/emulator (Gradle filter may be set with `-Pandroid.testInstrumentationRunnerArguments.class=com.caregiver.mobile.data.local.RecipientMigrationTest`).

Expected: FAIL because `RecipientEntity.archivedAt`, `MIGRATION_6_7`, and schema `7.json` do not exist.

- [ ] **Step 3: Implement the migration and model field**

Add `archivedAt` as the last column on `RecipientEntity`:

```kotlin
val active: Boolean,
val archivedAt: Long? = null,
```

Add the same field with a default to `RecipientDto` so the existing in-memory mappings compile and any non-local JSON payload still decodes:

```kotlin
data class RecipientDto(
    val id: String,
    val name: String,
    val active: Boolean,
    val archivedAt: Long? = null,
)
```

Map the field in `toDto()` / `toEntity()`.

Bump `@Database(version = 7)` and add:

```kotlin
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `recipients` ADD COLUMN `archivedAt` INTEGER")
    }
}
```

Register `MIGRATION_6_7` in `create(...)`, build the project so Room exports `7.json`, and commit that file. Do not add a fallback to destructive migration.

- [ ] **Step 4: Run the migration test and the existing DAO tests**

Run:

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.*" -v
```

and the migration test on a device/emulator.

Expected: PASS; if no device is available, record the migration test as unexecuted (not passed).

- [ ] **Step 5: Commit**

```bash
git add android/app/build.gradle.kts android/gradle/libs.versions.toml \
  android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt \
  android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt \
  android/app/schemas/com.caregiver.mobile.data.local.AppDatabase/7.json
git commit -m "feat: add recipient archivedAt Room migration"
```

## Task 2: Transactional Room deletion and counts

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/PlanEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientDeletionCounts.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`

**Interfaces:**
- Produces: `RecipientDeletionCounts`
- Produces: `AppDatabase.deleteArchivedRecipient(recipientId: String)`
- Produces: `AppDatabase.recipientDataCounts(recipientId: String): RecipientDeletionCounts`
- Produces DAO helpers listed below

- [ ] **Step 1: Write the failing deletion test**

Create `RecipientDeletionTest.kt` with an in-memory Room database. Insert:

- One archived recipient (`archivedAt != null`) and one unarchived recipient.
- For each: a note, addendum, plan, medication, medication intake, and outbox row.

Assert:

- Deleting an unarchived recipient throws and leaves every row unchanged.
- Deleting the archived recipient removes only its rows from `recipients`, `notes`, `addenda`, `plans`, `medications`, `medication_intakes`, and `outbox`.
- The unrelated recipient and all of its rows remain.
- Deleting the same recipient again is a no-op.
- Deleting a missing recipient is a no-op.
- `recipientDataCounts` matches the rows that the delete removes.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v
```

Expected: FAIL because the deletion API does not exist.

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

Define `RecipientDeletionCounts(notes, addenda, plans, medications, intakes, outbox)` in `RecipientDeletionCounts.kt` (owned by this task; Task 3's admin interface consumes it).

Implement `AppDatabase.deleteArchivedRecipient` with `withTransaction`:

- Read the recipient inside the transaction. Absent → return.
- If `archivedAt == null`, throw a typed `ActiveRecipientDeleteException` without mutating anything.
- Collect note ids; delete addenda in chunks, then notes.
- Delete plans by recipient.
- Collect medication ids; delete intakes in chunks, then medications.
- Delete outbox rows by recipient.
- Delete the recipient row last.
- Use a bind-safe chunk size (for example 500) for every `IN (...)` delete, and skip empty lists.

Implement `AppDatabase.recipientDataCounts` with the same scoping (counts include addenda and intakes owned through notes/medications).

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/local/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt
git commit -m "feat: add transactional recipient deletion"
```

## Task 3: Local-only recipient admin

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientAdmin.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientAdminTest.kt`

**Interfaces:**
- Produces: `RecipientAdmin.archive(id)`, `unarchive(id)`, `deleteArchived(id)`, `counts(id)`
- Produces: `AppGraph.recipientAdmin: RecipientAdmin?`
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
- `createPlanForArchivedRecipientIsRejected`

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientAdminTest" -v
```

Expected: FAIL because the admin API does not exist.

- [ ] **Step 3: Implement the admin seam**

Add `RecipientAdmin.kt`:

```kotlin
interface RecipientAdmin {
    suspend fun archive(id: String): RecipientDto
    suspend fun unarchive(id: String): RecipientDto
    suspend fun deleteArchived(id: String)
    suspend fun counts(id: String): RecipientDeletionCounts
}
```

Implement it in `LocalBackendApis`:

- `archive`: set `archivedAt = clock()` when currently null; leave an existing timestamp unchanged.
- `unarchive`: set `archivedAt = null`.
- `deleteArchived`: call `db.deleteArchivedRecipient(id)`.
- `counts`: call `db.recipientDataCounts(id)`.
- Return the updated `RecipientDto`, including `archivedAt`.
- Inject a `clock: () -> Long = System::currentTimeMillis` constructor default so tests can freeze time.

Add defense-in-depth write guards in the local backend: `notes().create(...)` and `plans().suggest(...)` reject archived recipients with a typed `ArchivedRecipientException`. Medication creation (wherever the local store writes a medication for a recipient) must apply the same guard.

Implement `RecipientAdmin` in `DemoBackendApis` with in-memory behavior so debug/demo flows stay consistent: archive/unarchive update the in-memory `RecipientDto`; delete removes the recipient plus its in-memory notes, addenda, plans, and outbox-like pending data. Demo data never touches Room.

Expose it from `AppGraph`:

```kotlin
val recipientAdmin: RecipientAdmin? by lazy { live as? RecipientAdmin }
```

`LocalBackendApis` and `DemoBackendApis` implement the interface; `RetrofitBackendApis` and `RoomBackedBackendApis` do not, so those graphs return `null` and the UI hides the actions. Do not implement network fallbacks.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientAdminTest" -v
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/local/RecipientAdmin.kt \
  android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt \
  android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt \
  android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt \
  android/app/src/test/java/com/caregiver/mobile/data/local/RecipientAdminTest.kt
git commit -m "feat: add on-device recipient archive and delete"
```

## Task 4: Recipient ViewModels

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `RecipientAdmin` from Task 3
- Produces:
  - `RecipientsViewModel.archive(id)`, `unarchive(id)`, `delete(id)`, `consumeUndo()`
  - `RecipientsViewModel.setFilter(PeopleFilter)`
  - `RecipientDetailViewModel.archive()`, `unarchive()`, `delete()`, `consumeDeleted()`

- [ ] **Step 1: Write failing ViewModel tests**

Add tests for:

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

Expected: FAIL because methods and state are missing.

- [ ] **Step 3: Implement the ViewModels**

`RecipientsViewModel`:

- Add an optional `admin: RecipientAdmin?` constructor parameter (place it after the existing `scope` parameter or give it a default so current tests keep compiling) and pass `graph.recipientAdmin` from the screens.
- Add `PeopleFilter` (`Active`, `Archived`) and expose the selected filter.
- Add `archive(id)`, `unarchive(id)`, `delete(id)`, `consumeUndo()`, and `counts(id)`.
- Add mutation state with per-recipient busy ids, an error message, and one pending undo id. Clear the undo target on consume, expiry, or a newer archive.
- Archive uses `admin.archive`; undo/unarchive uses `admin.unarchive`; delete uses `admin.deleteArchived`.
- After a mutation, refresh the list and keep the UI truthful on failure (row unchanged, error shown, not reported as success).
- Serialize or reject conflicting actions per recipient.

`RecipientDetailViewModel`:

- Add the optional `admin` parameter and pass `graph.recipientAdmin` from the screen.
- Add `archive()`, `unarchive()`, `delete()`, `consumeDeleted()`.
- Add `archived: Boolean` based on `archivedAt != null` to `RecipientDetail`.
- Expose `busy`, `errorMessage`, and deletion state. Reject delete while unarchived. Update state/navigation only after the local operation succeeds.
- Disable actions while busy.

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*ViewModelTest" -v
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt \
  android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt \
  android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt
git commit -m "feat: add recipient archive and delete ViewModels"
```

## Task 5: People screen UI

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeViewModel.kt`
- Modify: recipient selection flows in notes, plan creation, and medication entry (including `RecipientPickerSheet.kt` where applicable)
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`
- Test: targeted tests for each recipient selector/creation flow

**Interfaces:**
- Consumes: `RecipientsViewModel.archive`, `unarchive`, `delete`, `consumeUndo`, `setFilter`
- Consumes: `AppGraph.recipientAdmin != null` to decide whether actions exist

- [ ] **Step 1: Write failing UI tests**

Add tests for:

- `archiveFromActiveListMovesRowToArchived`
- `undoArchiveRestoresRow`
- `archivedFilterShowsArchivedRecipient`
- `deleteFromArchivedListRemovesRow`
- `deleteConfirmationShowsCountsAndBackupCaveat`
- `archivedRowHasNoAddNoteAction`
- `archiveActionsHiddenWithoutLocalAdmin`

- [ ] **Step 2: Run tests to verify they fail**

Run the affected tests. Prefer the Robolectric unit tests when the screen is covered there; otherwise run connected instrumentation on an available device/emulator. Assembly alone does not execute the tests; if no device is available, record that limitation and continue with compile validation.

Expected: the new tests fail for the missing behavior.

- [ ] **Step 3: Implement the People screen**

Add:

- An Active / Archived segmented filter driven by `RecipientsViewModel.setFilter`.
- Active filter: `archivedAt == null`.
- Archived filter: `archivedAt != null`.
- Overflow menu on each row.
- Unarchived row: Archive.
- Archived row: Unarchive, Delete.
- Delete confirmation dialog showing authoritative local counts (notes, addenda, plans, medications, intakes, outbox) and the backup caveat.
- Undo snackbar for archive.
- Disable row actions while a mutation is in flight.
- Render archive/delete actions only when `graph.recipientAdmin != null`.

Apply the same archived guard at every new-note, new-plan, and medication selection source:

- Keep the existing `active` filter and add `archivedAt == null`.
- Do not rely on the People screen to enforce this invariant.
- If an open form's recipient becomes archived, block submission and show an actionable error.
- Update `MainScaffold` and `HomeViewModel` picker filters to exclude archived recipients.

- [ ] **Step 4: Run tests and compile instrumentation**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*" -v
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

Expected: unit tests PASS; instrumentation BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt \
  android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt \
  android/app/src/main/java/com/caregiver/mobile/presentation/home/HomeViewModel.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add recipient archive and delete UI"
```

## Task 6: Recipient detail UI

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

Run the affected tests (Robolectric or connected instrumentation); if no device is available, record the limitation and continue with compile validation.

Expected: the new tests fail for the missing behavior.

- [ ] **Step 3: Implement the detail screen**

- Determine archived state from `recipient.archivedAt != null`.
- If archived: hide Add note and the plan action; keep History/Summary available; show Unarchive and Delete.
- If unarchived: show existing actions plus Archive.
- Add an archived status pill.
- Add a delete confirmation dialog with authoritative local counts and the backup caveat.
- Disable actions while busy.
- On success: refresh the detail or navigate back after delete; on failure keep the detail and show a retryable error.
- Hide archive/delete actions when `graph.recipientAdmin == null`.

- [ ] **Step 4: Run instrumentation compile**

```bash
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add archived recipient detail actions"
```

## Task 7: Strings and localization

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ar/strings.xml`

**Interfaces:**
- Produces string keys used by Tasks 5 and 6.

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
- localized busy/error/archived-selection messages introduced by Tasks 3–6

- [ ] **Step 2: Add Arabic strings**

Add matching Arabic translations for every key added in Step 1, including the backup caveat, errors, busy state, and archived-recipient validation.

- [ ] **Step 3: Run string parity tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.core.i18n.StringsParityTest" -v
```

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-ar/strings.xml
git commit -m "feat: add recipient archive and delete strings"
```

## Task 8: Backup and CSV behavior

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupStore.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/backup/BackupCodec.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/BackupCodecTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/csv/CsvImportPlannerTest.kt`

**Interfaces:**
- Consumes: `RecipientEntity.archivedAt` from Task 1
- Produces: backup schema v2 with `archivedAt`

- [ ] **Step 1: Add failing backup tests**

Add tests verifying:

- A backup snapshot includes `archivedAt` for archived recipients.
- A restore recreates archived recipients with their timestamp and unarchived recipients with `null`.
- A v1 backup (no `archivedAt`) still restores; its recipients become unarchived.
- A v2 document round-trips through the codec.
- Restoring a pre-deletion backup can recreate a deleted recipient; assert this documented behavior rather than treating it as a bug.

- [ ] **Step 2: Add failing CSV tests**

CSV is a note-transfer format, not a recipient backup/export format. Do not assert that CSV exports or restores recipient records. Instead test:

- Import planning cannot target an archived recipient.
- An archived recipient stays archived after an import.
- A note is never imported onto an archived recipient through name or id matching.
- The existing unavailable-recipient UX is preserved.

- [ ] **Step 3: Implement backup v2 with backward compatibility**

- Add `archivedAt: Long? = null` to `BackupRecipient`.
- Map it in `RecipientEntity.toBackup()` / `BackupRecipient.toEntity()`.
- Bump `CareBackupCodec.FORMAT_VERSION` to `2`.
- Decode accepts version 1 and version 2: v1 payloads leave `archivedAt` at its default `null`; v2 preserves it. Encode always writes v2.
- Keep the strict unknown-key parser; add a deliberate v1 decode path instead of loosening validation.
- Update the backup documentation/tests where the format is described.

- [ ] **Step 4: Implement CSV archived-recipient rules**

- Add `archived: Boolean` to `RecipientRef` (default `false` so existing tests compile).
- Populate it from `archivedAt != null` in `CsvTransferService`.
- In `CsvImportPlanner`, treat archived recipients as unavailable targets: they never satisfy a recipient match or resolution, and a note is never planned for them.
- Do not change recipient `active` or `archivedAt` during import.

- [ ] **Step 5: Run the focused tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.backup.*" -v
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.csv.*" -v
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/backup/*.kt \
  android/app/src/main/java/com/caregiver/mobile/data/csv/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/backup/*.kt \
  android/app/src/test/java/com/caregiver/mobile/data/csv/*.kt
git commit -m "feat: preserve archive state in backup and block archived csv targets"
```

## Task 9: Cascade and migration coverage

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/NoteDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/AddendumDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/PlanDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/MedicationStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/OutboxDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt`

**Interfaces:**
- Consumes: DAO helpers from Task 1 and Task 2

- [ ] **Step 1: Add DAO deletion tests**

Add tests verifying:

- Recipient deletion removes the recipient row.
- Note deletion removes notes for the recipient.
- Addendum deletion removes addenda for the recipient's notes.
- Plan deletion removes plans for the recipient.
- Medication deletion removes medications and intakes for the recipient.
- Outbox deletion removes pending rows for the recipient.
- Counts match the removed rows.
- Empty ID lists and repeated deletes are safe.

- [ ] **Step 2: Re-run the migration test**

Run `RecipientMigrationTest` on a device/emulator and confirm v6 data survives.

- [ ] **Step 3: Run all affected tests**

Run all affected DAO/store tests and `RecipientDeletionTest` explicitly; the `*DaoTest` filter alone does not cover every deletion table or the cross-table transaction.

Expected: PASS. Record the migration test as unexecuted if no device is available.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/local/*Test.kt \
  android/app/src/androidTest/java/com/caregiver/mobile/data/local/RecipientMigrationTest.kt
git commit -m "test: cover recipient archive, deletion cascade, and migration"
```

## Task 10: Final verification

**Files:**
- No new files.

- [ ] **Step 1: Run unit tests**

```bash
cd android && ./gradlew :app:testDebugUnitTest -v
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run lint**

```bash
cd android && ./gradlew :app:lintDebug -v
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Compile instrumentation tests**

```bash
cd android && ./gradlew :app:assembleDebugAndroidTest -v
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Execute instrumentation tests when a device is available**

```bash
cd android && ./gradlew :app:connectedDebugAndroidTest -v
```

Expected: instrumentation tests pass, including `RecipientMigrationTest`. If no device/emulator is available, record this as not run; assembly is compile-only and is not a test pass.

- [ ] **Step 5: Review the migration artifacts and diff**

```bash
git status --short && git diff --stat
```

Confirm `7.json` is committed, `6.json` is unchanged, and only intended files changed.

- [ ] **Step 6: Commit verification metadata if needed**

No commit required unless test files changed.

## Self-Review Notes

- Reconcile this plan against actual repository APIs before implementation; update obsolete paths, signatures, and test selectors instead of creating duplicate abstractions.
- Verify that no archive/delete network call was introduced anywhere; the only writer is `LocalBackendApis` over Room.
- Verify the v6→v7 migration on real exported schema data, not just a fresh in-memory database.
- Verify `active` remains untouched by archive/unarchive and that filters use `archivedAt`.
- Verify active-only and non-archived selection in all create flows, archive undo/error behavior, archived-only deletion, transaction rollback, backup restore, and CSV import target eligibility.
- Do not mark a task complete from compilation alone when its acceptance criterion requires executed tests.

## Execution Handoff

Plan is ready for implementation. There is no server/API dependency: archive/delete is implemented entirely on-device over Room. Implement sequentially where task interfaces depend on prior work; run each task's stated tests before marking its checkbox complete.
