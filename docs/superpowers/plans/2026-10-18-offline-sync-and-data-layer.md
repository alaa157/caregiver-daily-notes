# Offline Sync & Data Layer Fixes Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix offline sync bugs, note repository issues, outbox worker robustness, and data consistency problems.

**Architecture:** Add idempotency keys to note creation, fix the outbox drain loop, add proper conflict resolution, and ensure the Room database is the single source of truth for cached data.

**Tech Stack:** Kotlin, Room, WorkManager, Retrofit

## Global Constraints

- `minSdk = 24` (Android 7.0)
- Notes are append-only; corrections use addenda
- Outbox must drain oldest-first with exponential backoff
- All pending notes must survive app restart

---

### Task 1: Add Idempotency Keys to Note Creation

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/CreateNoteRequest.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/NoteRepositoryIdempotencyTest.kt`

**Interfaces:**
- Produces: `CreateNoteRequest` with `idempotencyKey: String?` field
- Produces: `NoteRepository.create()` that includes the key in API calls

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `create sends idempotency key`() = runTest {
    val request = CreateNoteRequest(
        // ... fields ...
        idempotencyKey = "test-key-123"
    )
    val result = repository.create(request)
    // Verify API was called with idempotency key header
}
```

- [ ] **Step 2: Add idempotencyKey to CreateNoteRequest**

```kotlin
data class CreateNoteRequest(
    // ... existing fields ...
    val idempotencyKey: String? = null
)
```

- [ ] **Step 3: Include key in API call**

In `NoteRepository.create()`, generate a UUID for the idempotency key if not provided and send it as a header.

- [ ] **Step 4: Run test**
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.NoteRepositoryIdempotencyTest"
```

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/
git commit -m "fix(sync): add idempotency keys to note creation"
```

---

### Task 2: Fix Outbox Drain Loop Termination

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/OutboxDrainTest.kt`

**Interfaces:**
- Consumes: Existing `drainOutbox(): OutboxDrain`

- [ ] **Step 1: Write failing test for infinite loop scenario**

```kotlin
@Test
fun `drain terminates when no new rows appear`() = runTest {
    // Insert a row that always fails (to test the attempted set)
    repository.insert(alwaysFailingRow)
    val result = repository.drainOutbox()
    assertEquals(OutboxDrain.RETRY, result)
    // Verify it doesn't loop forever
}
```

- [ ] **Step 2: Fix the drain loop**

In `NoteRepository.drainOutbox()`, ensure the `attempted` set prevents re-processing the same row. The current code has a subtle bug: if `outbox.updateStatus()` fails after a row is marked SYNCING, the row stays SYNCING forever and is never retried.

Fix:
```kotlin
suspend fun drainOutbox(): OutboxDrain {
    val outbox = outboxDao ?: return OutboxDrain.DONE
    val attempted = mutableSetOf<String>()
    var retry = false
    while (true) {
        val rows = outbox.query(null, null, null)
            .sortedBy { it.createdAt }
            .filter { it.localId !in attempted && it.status != OutboxStatus.SYNCING }
        if (rows.isEmpty()) return if (retry) OutboxDrain.RETRY else OutboxDrain.DONE
        for (row in rows) {
            attempted += row.localId
            try {
                outbox.updateStatus(row.localId, OutboxStatus.SYNCING)
                val created = apis.notes().create(row.toCreateRequest())
                cacheWrite { cacheNote(created.toEntity().copy(localId = row.localId)) }
                outbox.delete(row.localId)
            } catch (e: IOException) {
                outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                retry = true
            } catch (e: HttpException) {
                when {
                    e.code() == 401 || e.code() == 403 -> {
                        outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                        return OutboxDrain.STOPPED
                    }
                    e.code() >= 500 -> {
                        outbox.updateStatus(row.localId, OutboxStatus.PENDING)
                        retry = true
                    }
                    else -> {
                        outbox.updateStatus(row.localId, OutboxStatus.FAILED)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Run test**
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.OutboxDrainTest"
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt
git commit -m "fix(sync): prevent outbox drain infinite loop on persistent failures"
```

---

### Task 3: Handle Duplicate Note Conflict (422)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/NoteConflictTest.kt`

**Interfaces:**
- Produces: `NoteRepository.create()` that handles HTTP 422 Conflict by returning existing note

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `create returns existing note on duplicate day conflict`() = runTest {
    // Mock API to throw 422
    val result = repository.create(duplicateRequest)
    // Should return the existing note, not throw
    assertNotNull(result)
}
```

- [ ] **Step 2: Catch 422 in create**

In `NoteRepository.create()`, catch `HttpException` with code 422 and fetch the existing note for that recipient/date.

- [ ] **Step 3: Run test**
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.NoteConflictTest"
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt
git commit -m "fix(sync): handle duplicate note conflict gracefully"
```

---

### Task 4: Fix Bottom Navigation Bar Height

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/MainScaffoldTest.kt`

**Interfaces:**
- Consumes: `AppDimens.BottomNavH`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `bottom nav height includes system nav bar inset`() {
    val height = AppDimens.BottomNavH + navigationBarsInset
    // Verify total height doesn't exceed expected max
}
```

- [ ] **Step 2: Fix modifier order**

In `MainScaffold.kt`, change:
```kotlin
Modifier
    .fillMaxWidth()
    .windowInsetsPadding(WindowInsets.navigationBars)
    .height(AppDimens.BottomNavH)
```
to:
```kotlin
Modifier
    .fillMaxWidth()
    .height(AppDimens.BottomNavH)
    .windowInsetsPadding(WindowInsets.navigationBars)
```

This ensures the height is fixed and the padding is added below, not squeezed inside.

- [ ] **Step 3: Run test**
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.MainScaffoldTest"
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt
git commit -m "fix(ui): correct bottom nav bar height with system nav inset"
```

---

### Task 5: Add Pagination to History

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/NoteRepository.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/history/HistoryViewModel.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/NotePaginationTest.kt`

**Interfaces:**
- Produces: `NoteRepository.history()` returns `Flow<PagingData<NoteDto>>` instead of `List<NoteDto>`

- [ ] **Step 1: Add Paging dependency**

```kotlin
implementation("androidx.paging:paging-runtime-ktx:3.2.1")
implementation("androidx.paging:paging-compose:3.2.1")
```

- [ ] **Step 2: Write failing test**

```kotlin
@Test
fun `history paginates results`() = runTest {
    // Insert 50 notes, verify only first page is loaded
}
```

- [ ] **Step 3: Implement PagingSource**

Create a `NotePagingSource` that loads notes in pages of 20 from the API.

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/
git commit -m "feat(sync): add pagination to note history"
```

---

### Task 6: Fix WorkManager Initialization

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Test: `android/app/src/test/java/com/caregiver/mobile/WorkManagerInitTest.kt`

**Interfaces:**
- Produces: `OutboxSyncWorker.schedule(context: Context)` that uses WorkManager's `enqueueUniqueWork`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `schedule enqueues unique work`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    OutboxSyncWorker.schedule(context)
    val workManager = WorkManager.getInstance(context)
    val infos = workManager.getWorkInfosForUniqueWork("outbox-sync").get()
    assertTrue(infos.isNotEmpty())
}
```

- [ ] **Step 2: Implement schedule method**

```kotlin
object OutboxSyncWorker {
    fun schedule(context: Context) {
        val request = OneTimeWorkRequestBuilder<OutboxSyncWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "outbox-sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
```

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/OutboxSyncWorker.kt
git commit -m "fix(sync): use unique work policy for outbox drain"
```
