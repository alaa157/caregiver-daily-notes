# Recipient Archive & Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive and permanent delete for care recipients in the Android app, preserving offline-first behavior and the immutable-original-note rule.

**Architecture:** Reuse the existing `active` flag as the archived state. Archive updates `active=false` and remains reversible. Delete is available only for archived recipients and runs one Room transaction that removes the recipient plus all recipient-owned notes, addenda, plans, medications, medication intakes, and outbox rows. UI, ViewModels, backend implementations, demo data, backup/CSV handling, strings, fakes, and tests are updated to support both actions.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, Robolectric, Android instrumentation tests, Gradle.

**Spec:** Settled design from the recipient archive/delete grilling session (no separate spec file). Decisions and acceptance criteria are restated below.

## Execution Contract

- Before editing, inspect the current implementation and tests for every listed surface. This plan is a checklist, not proof that a path, class, test, or API already exists; update obsolete paths and commands to match the repository.
- Keep archive reversible and distinct from delete. `active=false` means archived; deleting is permanent for the current database only.
- Enforce archived-recipient restrictions in underlying selection/data flows, not only by hiding UI actions. Cover notes, plans, medications, and CSV import mappings.
- Delete only an archived recipient. Reject stale or repeated UI actions safely; do not report success if the backend or local cleanup fails.
- A Room transaction can make local row deletion atomic, but cannot make a remote HTTP request and local Room writes one atomic operation. Define and test remote-success/local-cleanup recovery explicitly.
- Use Room-supported transaction APIs (`RoomDatabase.withTransaction` or a DAO `@Transaction` method); do not assume `@Transaction` on an `AppDatabase` convenience method is supported.
- Add assertions for an unrelated recipient and every owned-data table. Guard empty `IN` lists and respect SQLite bind-parameter limits for large ID lists.
- Run commands that execute tests separately from commands that only compile/assemble them. If instrumentation cannot run without a device, report it as unexecuted rather than passed.
- Preserve unrelated worktree changes and do not change backend/server scope without an explicit API contract.

## Global Constraints

- Branch: `feat/standalone-byok-ai`.
- No backend sync in this version; app-side backend implementations only.
- No new Room migration; archive reuses `active`.
- Local deletion must be atomic across every recipient-owned table.
- The remote delete endpoint must exist and define archived-only behavior. If it does not, treat that as a blocking API dependency rather than simulating success.
- For the Retrofit-backed graph, deletion requires a successful server response before local cache cleanup. On HTTP/offline failure, leave local cached data unchanged and surface an error. If server deletion succeeds but local cleanup fails, surface failure and force/allow a full refresh so the server deletion is reflected; do not claim cross-network atomicity.
- The local backend must perform one Room transaction. Repeating cleanup for an already-absent recipient is a no-op; deleting an active recipient is rejected.
- Delete confirmation counts must reflect the data that will actually be removed. Use authoritative counts from the backend/local store; if exact counts are unavailable for the current backend, do not display misleading numbers and update the UI test/spec accordingly.
- Delete must cascade all recipient-owned local data.
- Archived recipients remain visible under an Archived filter.
- Archived recipients are read-only except Unarchive and Delete.
- English and Arabic strings must stay in parity.
- Existing tests must continue to pass.
- Backups are independent snapshots: an older backup may restore a deleted recipient and its data. Explain this in delete confirmation or existing backup/help documentation; deletion must not imply backup erasure.

## Review Focus

- A user archives the wrong recipient and expects Undo to restore it exactly, including returning it to the Active filter.
- A deleted recipient must not leave dangling notes, addenda, plans, medications, intakes, or outbox rows.
- An archived recipient must not be selectable for new notes, plans, or medications, or chosen as a CSV import target.
- Restore from an older backup can resurrect a deleted recipient; this must be documented and expected, not treated as a bug.
- CSV transfers notes, not recipient records: import must never reactivate an archived recipient or silently import a note onto one. Keep inactive recipients visible as unavailable if that is the existing UX.
- Backend failure must leave local rows intact; local transaction failure must not be rendered as a successful delete.
- The list and detail surfaces must prevent duplicate in-flight actions and reflect the final state after success or failure.

---

## File Structure

Files modified:

- `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/PlanEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/RecipientRepository.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/RoomBackedBackendApis.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/api/RetrofitBackendApis.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- `android/app/src/main/java/com/caregiver/mobile/presentation/notes/RecipientPickerSheet.kt`
- Relevant plan-creation and medication-selection surfaces discovered during the initial audit
- `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvImportPlanner.kt`
- `android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values-ar/strings.xml`
- `android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt`

Files created:

- `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/AddendumDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/PlanDaoTest.kt`

Files modified for tests:

- `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/NoteDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/MedicationStoreTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/local/OutboxDaoTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/RecipientRepositoryTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`
- `android/app/src/test/java/com/caregiver/mobile/data/demo/DemoBackendApisTest.kt`
- `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

## Task 1: Room recipient deletion transaction

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/RecipientEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/NoteEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AddendumDao.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/MedicationEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/PlanEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/OutboxEntity.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt`

**Interfaces:**
- Produces: `AppDatabase.deleteRecipientAndOwnedData(recipientId: String)`

- [ ] **Step 1: Write the failing test**

Create `RecipientDeletionTest.kt` with an in-memory Room database. Insert active and archived recipients plus a note, addendum, plan, medication, medication intake, and outbox row for each. Assert deleting an active recipient is rejected and leaves all rows unchanged. Delete archived `r1`; assert all its rows are gone and the unrelated recipient's rows remain. Assert deleting `r1` again is a no-op. Include the missing-recipient case.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v`

Expected: FAIL because `deleteRecipientAndOwnedData` does not exist.

- [ ] **Step 3: Implement DAO helpers and transaction**

Add these signatures:

- `RecipientDao.deleteByServerId(serverId: String)`
- `NoteDao.serverIdsByRecipient(recipientId: String): List<String>`
- `NoteDao.deleteByRecipient(recipientId: String)`
- `AddendumDao.deleteByNoteIds(noteIds: List<String>)`
- `PlanDao.deleteByRecipient(recipientId: String)`
- `MedicationDao.idsByRecipient(recipientId: String): List<String>`
- `MedicationDao.deleteByRecipient(recipientId: String)`
- `MedicationDao.clearIntakesForMedications(medicationIds: List<String>)`
- `OutboxDao.deleteByRecipient(recipientId: String)`

Implement `AppDatabase.deleteRecipientAndOwnedData(recipientId: String)` as one Room-supported transaction (prefer `withTransaction` on `AppDatabase`, or a transaction method on a DAO). Read/verify the recipient is archived inside that transaction; fail without mutation if active. Delete addenda first using note IDs, then notes, plans, medication intakes, medications, outbox rows, and finally the recipient. Guard list-based deletes against empty lists and chunk large ID lists to stay below SQLite bind limits. Repeated deletion of an absent recipient is a no-op.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.RecipientDeletionTest" -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/local/*.kt android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDeletionTest.kt
git commit -m "feat: add transactional recipient deletion"
```

## Task 2: Backend delete contract

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/RecipientRepository.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/RoomBackedBackendApis.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/RetrofitBackendApis.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/RecipientRepositoryTest.kt`

**Interfaces:**
- Consumes: `AppDatabase.deleteRecipientAndOwnedData(recipientId: String)` from Task 1
- Produces: `RecipientApi.delete(id: String)`, `BackendApis.recipients().delete(id: String)`, `RecipientRepository.delete(id: String)`

- [ ] **Step 1: Confirm the server contract and write failing repository tests**

Confirm the server supports `DELETE /api/recipients/{id}` and rejects deletion of active recipients. This feature has no server implementation in scope; stop and report a missing API contract rather than treating a client-only change as complete.

Add to `RecipientRepositoryTest.kt`:

- `deleteCallsBackendAndRemovesCachedRecipient`: backend delete is called with `r1`; Room no longer contains `r1`.
- `deleteFailureLeavesCachedRecipient`: backend throws; cached `r1` remains.
- `deleteActiveRecipientDoesNotCallBackend`: an active cached recipient is rejected before sending a delete request; the server must independently enforce archived-only deletion to cover stale clients.
- `deleteAlreadyAbsentRecipientIsSafe`: verify the documented idempotent no-op behavior.
- `localCleanupFailureAfterRemoteSuccessIsSurfaced`: verify the caller receives failure and can refresh; never return success-shaped fallback.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.RecipientRepositoryTest" -v`

Expected: FAIL because `delete` is not defined.

- [ ] **Step 3: Implement delete contract**

Add `@DELETE("api/recipients/{id}") suspend fun delete(@Path("id") id: String)` to `RecipientApi`.

Implement with an explicit split between server and local responsibilities:

- `LocalBackendApis.delete(id)`: call `db.deleteRecipientAndOwnedData(id)`.
- `DemoBackend.delete(id)`: remove recipient from in-memory list.
- `RoomBackedBackendApis.delete(id)`: preserve the live API's archived-only check and delete semantics. Define exactly one owner for Room cache cleanup in this graph; do not duplicate writes or rely on two non-atomic operations to provide one transaction.
- `RetrofitBackendApis.delete(id)`: call `api.delete(id)`.
- `RecipientRepository.delete(id)`: reject a known active cached recipient, call the backend first, and only after success remove recipient-owned cache rows in one Room transaction when this repository owns that cache. A failed backend call leaves the cache unchanged. Surface local cleanup errors after remote success and make the UI refresh/reconcile; do not claim the remote operation was rolled back.
- Define behavior for HTTP not-found and repeated requests according to the confirmed server contract; preserve the same outcome in demo/local fakes.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.RecipientRepositoryTest" -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/api/RecipientApi.kt android/app/src/main/java/com/caregiver/mobile/data/RecipientRepository.kt android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt android/app/src/main/java/com/caregiver/mobile/data/RoomBackedBackendApis.kt android/app/src/main/java/com/caregiver/mobile/data/api/RetrofitBackendApis.kt android/app/src/test/java/com/caregiver/mobile/data/RecipientRepositoryTest.kt
git commit -m "feat: add recipient delete to backend implementations"
```

## Task 3: Recipient ViewModels

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt`

**Interfaces:**
- Consumes: `BackendApis.recipients().update(id, UpdateRecipientRequest(active))` and `BackendApis.recipients().delete(id)`
- Produces:
  - `RecipientsViewModel.archive(id: String)`
  - `RecipientsViewModel.unarchive(id: String)`
  - `RecipientsViewModel.delete(id: String)`
  - `RecipientsViewModel.consumeUndo()`
  - `RecipientDetailViewModel.archive()`
  - `RecipientDetailViewModel.unarchive()`
  - `RecipientDetailViewModel.delete()`

- [ ] **Step 1: Write failing ViewModel tests**

Add tests for:

- `archiveMovesRecipientToArchived`
- `undoArchiveRestoresActive`
- `unarchiveRestoresActive`
- `deleteRemovesArchivedRecipient`
- `deleteNotAvailableForActiveRecipient`
- `deleteFailureLeavesRecipientUnchanged`
- `detailArchiveKeepsDetailOpenAndReadOnly`
- `detailUnarchiveRestoresEditableState`
- `detailDeleteNavigatesBack`

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*ViewModelTest" -v`

Expected: FAIL because methods are missing.

- [ ] **Step 3: Implement ViewModels**

`RecipientsViewModel`:

- Add `archive(id)`: update recipient to `active=false`, refresh, set `undoArchiveId`.
- Add `unarchive(id)`: update recipient to `active=true`, refresh.
- Add `delete(id)`: delete recipient, refresh, set deleted message.
- Add `consumeUndo()`.
- Keep one pending undo target; clear it only after consume/expiry or a newer archive. Undo uses the authorized update path, restores the Active filter, and reports errors without claiming success.
- Add mutation busy/error state distinct from the add-person form state. Serialize or reject conflicting archive/unarchive/delete actions per recipient.

`RecipientDetailViewModel`:

- Add `archive()`: update recipient to `active=false`.
- Add `unarchive()`: update recipient to `active=true`.
- Add `delete()`: delete recipient.
- Expose `busy`, `errorMessage`, and archived state.
- Reject delete while the loaded recipient is active, and update state/navigation only after backend success. On failure retain the detail and expose a retryable error state.

Disable actions while busy. On failure, keep recipient unchanged and set error.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.presentation.recipients.*ViewModelTest" -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModel.kt android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModel.kt android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientsViewModelTest.kt android/app/src/test/java/com/caregiver/mobile/presentation/recipients/RecipientDetailViewModelTest.kt
git commit -m "feat: add recipient archive and delete ViewModels"
```

## Task 4: People screen UI

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Modify: recipient selection flows in notes, plan creation, and medication entry (including `RecipientPickerSheet.kt` where applicable)
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`
- Test: targeted tests for each recipient selector/creation flow

**Interfaces:**
- Consumes: `RecipientsViewModel.archive`, `unarchive`, `delete`, `consumeUndo`

- [ ] **Step 1: Write failing UI tests**

Add tests for:

- `archiveFromActiveListRemovesRow`
- `undoArchiveRestoresRow`
- `archivedFilterShowsArchivedRecipient`
- `deleteFromArchivedListRemovesRow`
- `deleteConfirmationShowsCounts`

- [ ] **Step 2: Run tests to verify they fail**

Run the affected connected instrumentation tests on an available device/emulator. Assembly alone does not execute the tests; if no device is available, record that limitation and continue with compile validation.

Expected: the new tests fail for the missing behavior.

- [ ] **Step 3: Implement People screen**

Add:

- Active / Archived segmented filter.
- Active filter shows `recipients.filter { it.active }`.
- Archived filter shows `recipients.filter { !it.active }`.
- Overflow menu on `PersonRow`.
- Active row: Archive.
- Archived row: Unarchive, Delete.
- Delete confirmation dialog with counts.
- Undo snackbar for archive.
- Disable actions while busy.
- Apply the same active-only filter at each new-note, new-plan, and medication selection source. Do not rely on the People screen to enforce this invariant. If an open form's recipient becomes archived, block submission and show an actionable error.
- Add tests proving archived recipients cannot be selected or submitted through each creation flow.

- [ ] **Step 4: Run instrumentation compile**

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add recipient archive and delete UI"
```

## Task 5: Recipient detail UI

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt`
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

**Interfaces:**
- Consumes: `RecipientDetailViewModel.archive`, `unarchive`, `delete`

- [ ] **Step 1: Add failing detail UI tests**

Add tests for:

- `archivedDetailHidesAddNote`
- `archivedDetailHidesAddPlan`
- `archivedDetailHidesMedicationActions`
- `archivedDetailShowsUnarchiveAndDelete`
- `archivedDetailDeleteNavigatesBack`

- [ ] **Step 2: Run tests to verify they fail**

Run the affected connected instrumentation tests on an available device/emulator. Assembly alone does not execute the tests; if no device is available, record that limitation and continue with compile validation.

Expected: the new tests fail for the missing behavior.

- [ ] **Step 3: Implement detail screen**

- Determine archived state from `!recipient.active`.
- If archived, hide Add note, Add plan, and medication actions.
- Show history, summary, Unarchive, and Delete.
- If active, show existing actions plus Archive.
- Add delete confirmation dialog.
- Disable actions while busy.
- Show backup caveat in localized confirmation/help copy: older backups are not erased and can restore deleted records.
- Confirm the count source with the backend contract before presenting numeric counts; never substitute partial cached counts for a total without labeling them.

- [ ] **Step 4: Run instrumentation compile**

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/recipients/RecipientScreens.kt android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt
git commit -m "feat: add archived recipient detail actions"
```

## Task 6: Strings and localization

**Files:**
- Modify: `android/app/src/main/res/values/strings.xml`
- Modify: `android/app/src/main/res/values-ar/strings.xml`

**Interfaces:**
- Produces string keys used by Tasks 4 and 5.

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
- backup-restore caveat and localized busy/error/archived-selection messages added by Tasks 3-5

- [ ] **Step 2: Add Arabic strings**

Add matching Arabic translations for every key added in Step 1, including backup caveat, errors, busy state, and any archived-recipient validation introduced by Tasks 3-5.

- [ ] **Step 3: Run string parity tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.core.i18n.StringsParityTest" -v`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/res/values/strings.xml android/app/src/main/res/values-ar/strings.xml
git commit -m "feat: add recipient archive and delete strings"
```

## Task 7: Demo backend and fakes

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt`
- Modify: `android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/demo/DemoBackendApisTest.kt`

**Interfaces:**
- Consumes: `RecipientApi.delete` from Task 2

- [ ] **Step 1: Write failing demo backend test**

Add test verifying `DemoBackend` deletes a recipient and that archive updates `active=false`.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.demo.DemoBackendApisTest" -v`

Expected: FAIL because delete is not implemented.

- [ ] **Step 3: Implement demo backend and fake**

- `DemoBackend.delete(id)`: remove recipient from in-memory list.
- `FakeRecipientApi`: add `deleteHandler` and default delete behavior.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.demo.DemoBackendApisTest" -v`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/data/demo/DemoBackend.kt android/app/src/sharedTest/java/com/caregiver/mobile/data/Fakes.kt android/app/src/test/java/com/caregiver/mobile/data/demo/DemoBackendApisTest.kt
git commit -m "feat: add recipient archive and delete demo support"
```

## Task 8: Backup and CSV behavior

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- Test: existing CSV planner/transfer tests discovered during the initial audit

**Interfaces:**
- Consumes: existing backup/CSV stores and recipient active-state contract

- [ ] **Step 1: Add backup test**

Add tests verifying an archived recipient with `active=false` is included in backup and restored with `active=false`. Document/test the expected behavior that restoring a pre-deletion backup may restore a previously deleted recipient and its data; deletion does not rewrite older backup files.

- [ ] **Step 2: Add CSV test**

CSV is a note-transfer format, not a recipient backup/export format. Do not assert that CSV exports or restores recipient records. Instead test that import does not change recipient active state and that planning/resolution cannot target an archived recipient or import a note for one. Preserve the existing unavailable-recipient UX where possible.

- [ ] **Step 3: Run both focused test groups**

Run the focused backup and CSV test classes with Gradle test filters. Running only the backup selector does not verify CSV behavior.

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt android/app/src/test/java/com/caregiver/mobile/data/csv/
git commit -m "test: verify archived recipients in backup and csv"
```

## Task 9: Data deletion tests

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/RecipientDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/NoteDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/AddendumDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/PlanDaoTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/MedicationStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/local/OutboxDaoTest.kt`

**Interfaces:**
- Consumes: DAO helpers from Task 1

- [ ] **Step 1: Add DAO deletion tests**

Add tests verifying:

- recipient deletion removes recipient row
- note deletion removes notes for recipient
- addendum deletion removes addenda for recipient's notes
- plan deletion removes plans for recipient
- medication deletion removes medications and intakes for recipient
- outbox deletion removes outbox rows for recipient

- [ ] **Step 2: Run tests**

Run all affected DAO/store tests and `RecipientDeletionTest` explicitly; the `*DaoTest` filter alone does not cover every deletion table or the cross-table transaction.

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/local/*DaoTest.kt
git commit -m "test: add recipient deletion cascade coverage"
```

## Task 10: Final verification

**Files:**
- No new files.

- [ ] **Step 1: Run unit tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run lint**

Run: `cd android && ./gradlew :app:lintDebug -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Compile instrumentation tests**

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Execute instrumentation tests when a device is available**

Run: `cd android && ./gradlew :app:connectedDebugAndroidTest -v`

Expected: instrumentation tests pass. If no device/emulator is available, record this as not run; assembly is compile-only and is not a test pass.

- [ ] **Step 5: Review diff**

Run: `git status --short && git diff --stat`

Expected: only intended files changed.

- [ ] **Step 6: Commit verification metadata if needed**

No commit required unless test files changed.

## Self-Review Notes

- Reconcile this plan against actual repository APIs before implementation; update obsolete paths, signatures, and test selectors instead of creating duplicate abstractions.
- Verify active-only selection in all create flows, archive undo/error behavior, archived-only deletion, remote/local failure semantics, transaction rollback, backup restore, and CSV import target eligibility.
- Do not mark a task complete from compilation alone when its acceptance criterion requires executed tests.

## Execution Handoff

Plan is ready for implementation after the server `DELETE` contract is confirmed. Implement sequentially where task interfaces depend on prior work; run each task's stated tests before marking its checkbox complete.
