# Recipient Archive & Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive and permanent delete for care recipients in the Android app, preserving offline-first behavior and the immutable-original-note rule.

**Architecture:** Reuse the existing `active` flag as the archived state. Archive updates `active=false` and remains reversible. Delete is available only for archived recipients and runs one Room transaction that removes the recipient plus all recipient-owned notes, addenda, plans, medications, medication intakes, and outbox rows. UI, ViewModels, backend implementations, demo data, backup/CSV handling, strings, fakes, and tests are updated to support both actions.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, Robolectric, Android instrumentation tests, Gradle.

**Spec:** Settled design from the recipient archive/delete grilling session (no separate spec file). Decisions are restated in section 2.

## Global Constraints

- Branch: `feat/standalone-byok-ai`.
- No backend sync in this version; app-side backend implementations only.
- No new Room migration; archive reuses `active`.
- Delete must be atomic.
- Delete must cascade all recipient-owned local data.
- Archived recipients remain visible under an Archived filter.
- Archived recipients are read-only except Unarchive and Delete.
- English and Arabic strings must stay in parity.
- Existing tests must continue to pass.

## Review Focus

- A user archives the wrong recipient and expects Undo to restore it exactly, including returning it to the Active filter.
- A deleted recipient must not leave dangling notes, addenda, plans, medications, intakes, or outbox rows.
- An archived recipient must not be selectable for new notes, plans, or medications.
- Restore from an older backup can resurrect a deleted recipient; this must be documented and expected, not treated as a bug.
- CSV import of an inactive/archived recipient must not accidentally mark it active.

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

Create `RecipientDeletionTest.kt` with an in-memory Room database. Insert a recipient, note, addendum, plan, medication, medication intake, and outbox row for recipient `r1`. Call `db.deleteRecipientAndOwnedData("r1")`. Assert all related rows are gone and an unrelated recipient's rows remain.

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

Implement `AppDatabase.deleteRecipientAndOwnedData(recipientId: String)` as a `@Transaction`. Delete addenda first using note IDs, then notes, plans, medication intakes, medications, outbox rows, and finally the recipient. Guard list-based deletes against empty lists.

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

- [ ] **Step 1: Write the failing repository test**

Add to `RecipientRepositoryTest.kt`:

- `deleteCallsBackendAndRemovesCachedRecipient`: backend delete is called with `r1`; Room no longer contains `r1`.
- `deleteFailureLeavesCachedRecipient`: backend throws; cached `r1` remains.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.RecipientRepositoryTest" -v`

Expected: FAIL because `delete` is not defined.

- [ ] **Step 3: Implement delete contract**

Add `@DELETE("api/recipients/{id}") suspend fun delete(@Path("id") id: String)` to `RecipientApi`.

Implement:

- `LocalBackendApis.delete(id)`: call `db.deleteRecipientAndOwnedData(id)`.
- `DemoBackend.delete(id)`: remove recipient from in-memory list.
- `RoomBackedBackendApis.delete(id)`: call `live.recipients().delete(id)`, then `recipients.delete(id)`.
- `RetrofitBackendApis.delete(id)`: call `api.delete(id)`.
- `RecipientRepository.delete(id)`: call `apis.recipients().delete(id)`, then `cacheWrite { dao?.deleteByServerId(id) }`.

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

`RecipientDetailViewModel`:

- Add `archive()`: update recipient to `active=false`.
- Add `unarchive()`: update recipient to `active=true`.
- Add `delete()`: delete recipient.
- Expose `busy`, `errorMessage`, and archived state.

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
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

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

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: FAIL or tests missing.

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

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: FAIL or tests missing.

- [ ] **Step 3: Implement detail screen**

- Determine archived state from `!recipient.active`.
- If archived, hide Add note, Add plan, and medication actions.
- Show history, summary, Unarchive, and Delete.
- If active, show existing actions plus Archive.
- Add delete confirmation dialog.
- Disable actions while busy.

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

- [ ] **Step 2: Add Arabic strings**

Add matching Arabic translations for every key added in Step 1.

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

## Task 8: Backup and CSV verification

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/csv/RoomCsvNoteStoreTest.kt` if present

**Interfaces:**
- Consumes: existing backup/CSV stores

- [ ] **Step 1: Add backup test**

Add test verifying an archived recipient with `active=false` is included in backup and restored with `active=false`.

- [ ] **Step 2: Add CSV test**

Add test verifying CSV export includes archived recipients and import preserves `active=false`.

- [ ] **Step 3: Run tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.backup.RoomBackupStoreTest" -v`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt
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

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.*DaoTest" -v`

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

- [ ] **Step 3: Build instrumentation tests**

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Review diff**

Run: `git status --short && git diff --stat`

Expected: only intended files changed.

- [ ] **Step 5: Commit verification metadata if needed**

No commit required unless test files changed.

## Self-Review Notes

- Spec coverage: every settled grilling decision maps to Tasks 1-10.
- No `TBD` steps remain.
- Signatures are consistent across tasks.
- Review Focus items are covered by deletion, archive undo, archived read-only, restore, and CSV tests.
- Proportion: code bodies are left to implementers; tests and signatures define behavior.

## Execution Handoff

Plan written for review. Implementation has not started.

Please review the plan. Does it capture what you want?

If approved, recommended execution approach: Subagent-driven, because the tasks have strict interface dependencies and each deletion/UI change benefits from an independent review before the next task starts.
```

Then create file via bash? We can use write tool. Need no implementation. Then git add/commit/push plan file. User wants plan in md file and push. We should do that. But in final answer maybe concise. We need execute commands now. We can write file with write tool. Then git commit/push. Need be careful branch. We'll do.<|end|><|start|><|start|>assistant to=write<|channel|>analysis <|constrain|>xml<|message|><rfl:parameter name="content">
# Recipient Archive & Delete Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add archive and permanent delete for care recipients in the Android app, preserving offline-first behavior and the immutable-original-note rule.

**Architecture:** Reuse the existing `active` flag as the archived state. Archive updates `active=false` and remains reversible. Delete is available only for archived recipients and runs one Room transaction that removes the recipient plus all recipient-owned notes, addenda, plans, medications, medication intakes, and outbox rows. UI, ViewModels, backend implementations, demo data, backup/CSV handling, strings, fakes, and tests are updated to support both actions.

**Tech Stack:** Kotlin, Jetpack Compose, Room, DataStore, Robolectric, Android instrumentation tests, Gradle.

**Spec:** Settled design from the recipient archive/delete grilling session (no separate spec file). Decisions are restated in section 2.

## Global Constraints

- Branch: `feat/standalone-byok-ai`.
- No backend sync in this version; app-side backend implementations only.
- No new Room migration; archive reuses `active`.
- Delete must be atomic.
- Delete must cascade all recipient-owned local data.
- Archived recipients remain visible under an Archived filter.
- Archived recipients are read-only except Unarchive and Delete.
- English and Arabic strings must stay in parity.
- Existing tests must continue to pass.

## Review Focus

- A user archives the wrong recipient and expects Undo to restore it exactly, including returning it to the Active filter.
- A deleted recipient must not leave dangling notes, addenda, plans, medications, intakes, or outbox rows.
- An archived recipient must not be selectable for new notes, plans, or medications.
- Restore from an older backup can resurrect a deleted recipient; this must be documented and expected, not treated as a bug.
- CSV import of an inactive/archived recipient must not accidentally mark it active.

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

Create `RecipientDeletionTest.kt` with an in-memory Room database. Insert a recipient, note, addendum, plan, medication, medication intake, and outbox row for recipient `r1`. Call `db.deleteRecipientAndOwnedData("r1")`. Assert all related rows are gone and an unrelated recipient's rows remain.

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

Implement `AppDatabase.deleteRecipientAndOwnedData(recipientId: String)` as a `@Transaction`. Delete addenda first using note IDs, then notes, plans, medication intakes, medications, outbox rows, and finally the recipient. Guard list-based deletes against empty lists.

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

- [ ] **Step 1: Write the failing repository test**

Add to `RecipientRepositoryTest.kt`:

- `deleteCallsBackendAndRemovesCachedRecipient`: backend delete is called with `r1`; Room no longer contains `r1`.
- `deleteFailureLeavesCachedRecipient`: backend throws; cached `r1` remains.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.RecipientRepositoryTest" -v`

Expected: FAIL because `delete` is not defined.

- [ ] **Step 3: Implement delete contract**

Add `@DELETE("api/recipients/{id}") suspend fun delete(@Path("id") id: String)` to `RecipientApi`.

Implement:

- `LocalBackendApis.delete(id)`: call `db.deleteRecipientAndOwnedData(id)`.
- `DemoBackend.delete(id)`: remove recipient from in-memory list.
- `RoomBackedBackendApis.delete(id)`: call `live.recipients().delete(id)`, then `recipients.delete(id)`.
- `RetrofitBackendApis.delete(id)`: call `api.delete(id)`.
- `RecipientRepository.delete(id)`: call `apis.recipients().delete(id)`, then `cacheWrite { dao?.deleteByServerId(id) }`.

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

`RecipientDetailViewModel`:

- Add `archive()`: update recipient to `active=false`.
- Add `unarchive()`: update recipient to `active=true`.
- Add `delete()`: delete recipient.
- Expose `busy`, `errorMessage`, and archived state.

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
- Test: `android/app/src/androidTest/java/com/caregiver/mobile/presentation/recipients/RecipientsFlowUiTest.kt`

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

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: FAIL or tests missing.

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

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: FAIL or tests missing.

- [ ] **Step 3: Implement detail screen**

- Determine archived state from `!recipient.active`.
- If archived, hide Add note, Add plan, and medication actions.
- Show history, summary, Unarchive, and Delete.
- If active, show existing actions plus Archive.
- Add delete confirmation dialog.
- Disable actions while busy.

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

- [ ] **Step 2: Add Arabic strings**

Add matching Arabic translations for every key added in Step 1.

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

## Task 8: Backup and CSV verification

**Files:**
- Test: `android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/csv/RoomCsvNoteStoreTest.kt` if present

**Interfaces:**
- Consumes: existing backup/CSV stores

- [ ] **Step 1: Add backup test**

Add test verifying an archived recipient with `active=false` is included in backup and restored with `active=false`.

- [ ] **Step 2: Add CSV test**

Add test verifying CSV export includes archived recipients and import preserves `active=false`.

- [ ] **Step 3: Run tests**

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.backup.RoomBackupStoreTest" -v`

Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/test/java/com/caregiver/mobile/data/backup/RoomBackupStoreTest.kt
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

Run: `cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.local.*DaoTest" -v`

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

- [ ] **Step 3: Build instrumentation tests**

Run: `cd android && ./gradlew :app:assembleDebugAndroidTest -v`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Review diff**

Run: `git status --short && git diff --stat`

Expected: only intended files changed.

- [ ] **Step 5: Commit verification metadata if needed**

No commit required unless test files changed.

## Self-Review Notes

- Spec coverage: every settled grilling decision maps to Tasks 1-10.
- No `TBD` steps remain.
- Signatures are consistent across tasks.
- Review Focus items are covered by deletion, archive undo, archived read-only, restore, and CSV tests.
- Proportion: code bodies are left to implementers; tests and signatures define behavior.

## Execution Handoff

Plan written for review. Implementation has not started.

Please review the plan. Does it capture what you want?

If approved, recommended execution approach: Subagent-driven, because the tasks have strict interface dependencies and each deletion/UI change benefits from an independent review before the next task starts.
```
