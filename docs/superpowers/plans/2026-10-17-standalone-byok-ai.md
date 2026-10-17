# Standalone BYOK AI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android app installable from a GitHub release APK with no login, no backend, and AI features powered by the user's own API key, while keeping the existing backend/auth code behind a flag.

**Architecture:** Add a `LOCAL_MODE` build flag (default true). In local mode `CaregiverRoot` renders `MainScaffold` directly and Room becomes the source of truth through a new `LocalBackendApis` implementing the existing `BackendApis` seam, so ViewModels and screens stay unchanged. AI providers are hidden behind a Kotlin `LlmClient` interface with three OkHttp + kotlinx.serialization implementations; keys are stored in the Android Keystore. Summaries, plans, and translation route through that layer and degrade to a "set up AI in Settings" empty state when no key is configured. Distribution uses a tag-triggered GitHub Actions workflow.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.3, Compose BOM 2024.10.00, compile/target SDK 35, minSdk raised to 23, JDK 17, Room 2.x, OkHttp, kotlinx.serialization, JUnit 4, MockWebServer, Robolectric.

**Spec:** User-provided task in the 2026-10-17 conversation. No separate spec file; the plan argues from that task.

## Global Constraints

- Work on branch `feat/standalone-byok-ai`, based on `feat/premium-android-redesign` (no `feat/metrolist-style-ui` branch exists). Never commit to `main`. Small commits, format `type(scope): short description`.
- Touch only `android/` and docs/workflows needed for distribution. Do not touch `backend/` or `web/`. Ask before guessing on anything ambiguous.
- `LOCAL_MODE` defaults to `true` in every build type.
- In local mode there is no login, register, server-URL, sign-out, Retrofit, outbox, token handling, or WorkManager sync.
- Keep backend/auth code behind the flag; do not delete it.
- Keep demo data available as an optional "load sample data" action.
- Plans and summaries work with no backend; generate them through the AI layer and show a clear "set up AI in Settings" empty state when no key is configured. Everything else works with no key.
- AI providers: OpenAI, Anthropic, Gemini, xAI, OpenRouter.
- One `LlmClient` interface with three implementations: OpenAI-compatible (OpenAI/xAI/OpenRouter via base URL), Anthropic Messages API, Gemini generateContent. Use existing OkHttp + kotlinx.serialization. Verify each provider's current endpoint, headers, and request format against official docs before coding. No hardcoded model lists; the user types a model id with a sensible default per provider.
- Settings > AI: provider picker, masked API key field, model field, "Test connection" with clear errors (invalid key, no network, rate limit).
- Store the key encrypted with Android Keystore (AES-GCM; key never leaves the Keystore). Never log the key, never put it in Room, exports, or crash reports; exclude it from backups via data extraction rules.
- First AI use shows a consent dialog: care notes are sent to the chosen provider; the app is not a medical device and not HIPAA-compliant.
- Prompts live in one file. AI output is always labelled AI-generated and possibly wrong. Keep the safety banner.
- Definition of done per phase: `./gradlew :app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug` with no new findings; MockWebServer tests for each `LlmClient` (success, 401, 429, network failure) and key store tests; Arabic/English strings parity and RTL still pass.

## Review Focus

- Local-mode bypass must not leave auth screens reachable or sign-out visible, and must not crash on a null token; cover in Phase 1 tasks.
- Keystore-backed key storage must survive process death and never appear in Room/logs/exports/backups; cover in Phase 2 tasks.
- No-key and offline paths for summaries/plans/translation must show the empty state instead of crashing; cover in Phases 1 and 3.
- Adding AI strings must keep Arabic/English key parity and RTL layout; cover in every phase that adds strings.
- Provider HTTP handling must map 401/429/network failures to distinct, user-facing messages; cover in Phase 2 tests.

---

## Baseline and File Map

Current branch is `feat/premium-android-redesign` (the latest available base; no `feat/metrolist-style-ui` exists). The Android app lives under `android/` with a manual `AppGraph`, the `BackendApis` seam, Room cache (recipients/notes/outbox), ViewModels, and Compose destinations. Existing AI logic lives in the backend (`backend/src/main/java/com/caregiver/ai/`) and defines the output contracts (summary observations/evidence/uncertainties, plan suggestion JSON, safety signals); the Android side ports the needed parsing/safety logic and keeps the same shapes.

Primary files to change:

- `android/app/build.gradle.kts` — `LOCAL_MODE` flag, minSdk 23.
- `android/app/src/main/java/com/caregiver/mobile/CaregiverRoot.kt` — local-mode bypass.
- `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt` — AppGraph wiring, no WorkManager in local mode.
- `android/app/src/main/java/com/caregiver/mobile/data/api/BackendApis.kt` — unchanged interface; new `LocalBackendApis` implements it.
- `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt` — plan storage + migration.
- `android/app/src/main/AndroidManifest.xml` — backup exclusion rules.
- `android/app/src/main/java/com/caregiver/mobile/presentation/settings/SettingsScreens.kt` — hide auth/server-URL in local mode, add AI settings entry and sample-data action.
- `android/app/src/main/java/com/caregiver/mobile/presentation/common/AppDrawer.kt` — hide auth entry in local mode.
- New: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt` and local API implementations.
- New: `android/app/src/main/java/com/caregiver/mobile/ai/*` (provider layer, key store, prompts, services).
- New: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/AiSettingsScreen.kt`.
- New: `.github/workflows/android-release.yml`.
- `android/README.md` and `README.md` — install and API-key instructions.

---

## Phase 0 — Branch and Baseline

### Task 0: Create branch and verify baseline

**Files:** none (git/build verification).

- [ ] **Step 1: Confirm branch**

Run: `git branch --show-current`
Expected: `feat/standalone-byok-ai`

- [ ] **Step 2: Baseline build**

Run: `cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug`
Expected: all pass on the base branch before changes.

- [ ] **Step 3: Commit**

No commit (verification only).

---

## Phase 1 — Remove Login, Go Local-First

### Task 1: Add `LOCAL_MODE` build flag (default true)

**Files:**
- Modify: `android/app/build.gradle.kts`

**Interfaces:** produces `BuildConfig.LOCAL_MODE: Boolean`.

- [ ] **Step 1: Edit build.gradle.kts**

Add to `defaultConfig`: `buildConfigField("boolean", "LOCAL_MODE", "true")`. Keep `DEMO_BACKEND` logic intact.

- [ ] **Step 2: Verify build**

Run: `./gradlew :app:assembleDebug`
Expected: PASS and `BuildConfig.LOCAL_MODE` resolves.

- [ ] **Step 3: Commit**

```bash
git add android/app/build.gradle.kts
git commit -m "feat(android): add LOCAL_MODE build flag (default true)"
```

### Task 2: `CaregiverRoot` local-mode bypass

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverRoot.kt`

**Interfaces:** consumes `AppGraph`; produces unchanged `CaregiverRoot(graph)`.

- [ ] **Step 1: Write failing test**

Create `android/app/src/test/java/com/caregiver/mobile/CaregiverRootLocalModeTest.kt` asserting that when `BuildConfig.LOCAL_MODE` is true, `CaregiverRoot` composes `MainScaffold` and does not reference auth destinations. (Use a Robolectric/compose test or a simple conditional unit test on a helper `localModeStartDestination()`.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.CaregiverRootLocalModeTest"`
Expected: FAIL.

- [ ] **Step 3: Implement**

In `CaregiverRoot`, at top: `if (BuildConfig.LOCAL_MODE) { MainScaffold(graph); return }`. Keep the auth `NavHost` in the `else` branch unchanged.

- [ ] **Step 4: Run test**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/caregiver/mobile/CaregiverRoot.kt android/app/src/test/java/com/caregiver/mobile/CaregiverRootLocalModeTest.kt
git commit -m "feat(android): bypass login in LOCAL_MODE"
```

### Task 3: Hide auth/server-URL/sign-out UI in local mode

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/SettingsScreens.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/common/AppDrawer.kt`

- [ ] **Step 1: SettingsScreen** — wrap the server-URL button and sign-out button in `if (!BuildConfig.LOCAL_MODE)`. Keep language chips always.

- [ ] **Step 2: AppDrawer** — keep People/Plans/Settings entries; no sign-out entry exists, so only ensure no auth-only navigation is added. (No change needed beyond guard if any.)

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug :app:lintDebug`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git commit -am "feat(android): hide auth and server-url UI in LOCAL_MODE"
```

### Task 4: Plan storage in Room (local source of truth)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/local/AppDatabase.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/PlanEntity.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/PlanDao.kt`

**Interfaces:** `PlanDao` with `observeAll()`, `byId(id)`, `upsert(plan)`, `delete(id)`.

- [ ] **Step 1: Write failing test**

Create `PlanDaoTest.kt` (Robolectric in-memory Room) verifying insert + read round-trip and migration 2->3. Expected to fail (no entity).

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement `PlanEntity`**

Columns: `id TEXT PK`, `recipientId TEXT`, `versionsJson TEXT` (serialized `List<PlanVersionDto>`), `updatedAt INTEGER`. Use kotlinx.serialization for JSON (de)serialization in DAO default or repository.

- [ ] **Step 4: Add `PlanDao`** and register in `AppDatabase` (`abstract fun planDao()`), bump version to 3, add `MIGRATION_2_3` creating `plans` table.

- [ ] **Step 5: Run tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(android): add Room plan storage for local mode"
```

### Task 5: `LocalBackendApis` (Room source of truth)

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalRecipientApi.kt` (or nested in `LocalBackendApis.kt`)
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalNoteApi.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalSummaryApi.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalPlanApi.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/local/LocalBackendApis.kt`

**Interfaces:** implements `BackendApis`; returns `AuthApi` (no-op local), `RecipientApi`, `NoteApi`, `SummaryApi`, `PlanApi` backed by Room DAOs. IDs generated with `UUID.randomUUID()`.

- [ ] **Step 1: Write failing tests** for each local API (create/get/list/update recipients; create/detail/history notes; plan list/suggest/versions/transitions). Use in-memory Room.

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement**

  - `LocalRecipientApi`: CRUD over `RecipientDao`, returning `RecipientDto`; `create` inserts and returns with new UUID; `update` toggles active.
  - `LocalNoteApi`: CRUD over `NoteDao`; `create` inserts with client UUID + today's date and returns `NoteDto` (pending=false); `detail` returns `NoteDetailDto` with addenda from a local addendum store (Room or in-memory; use a `note_addenda` table or store addenda JSON on `NoteEntity` — prefer adding `addendaJson` column via migration 3 or 4). `history` filters by recipientId/from/to.
  - `LocalSummaryApi`: `signals` derives `SignalDto` from notes in Room; `summarize` returns a placeholder `SummaryDto` with empty text/evidence/uncertainties (real generation in Phase 3). For Phase 1, return `SummaryDto(id, recipientId, periodDays, text="", redFlags=emptyList(), evidence=emptyList(), uncertainties=emptyList())`? But UI empty state handles no key. Keep non-null.
  - `LocalPlanApi`: list/suggest/versions/appendVersion/accept/editAccept/dismiss/archive over `PlanDao`, mirroring `DemoBackendApis` plan state machine.
  - `LocalBackendApis.auth()`: return a no-op `AuthApi` returning `AuthResponse("local-token")`.

- [ ] **Step 4: Run tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): add LocalBackendApis on Room"
```

Note: if addenda storage needs a migration, include it in Task 4 or here; do not leave half-wired.

### Task 6: AppGraph wiring for local mode

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt`

**Interfaces:** adds `val isLocalMode: Boolean` to `AppGraph`; `apis`/`noteRepository` behavior.

- [ ] **Step 1: Edit AppGraph**

  - `val localMode: Boolean get() = !forceProduction && BuildConfig.LOCAL_MODE` (or `BuildConfig.LOCAL_MODE && !forceProduction`).
  - In local mode: `db = AppDatabase.create(context)`; `live = LocalBackendApis(context, db)`; `apis = live` (no `RoomBackedBackendApis` wrapper); `noteRepository = NoteRepository(apis = live, dao = null, outboxDao = null)` (passthrough, empty outbox); `auth = AuthRepository(live, settings, tokens)`.
  - In non-local mode: keep existing Retrofit/Room/outbox path unchanged.
  - `isDemo` unchanged (demo backend flag).
  - Add `isLocalMode` property.

- [ ] **Step 2: CaregiverApp.onCreate** — schedule `OutboxSyncWorker` only when `!graph.isDemo && !graph.isLocalMode`.

- [ ] **Step 3: Verify**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(android): wire AppGraph for LOCAL_MODE"
```

### Task 7: Optional "load sample data" action

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/SettingsScreens.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/data/SampleDataLoader.kt`

- [ ] **Step 1: Implement `SampleDataLoader.load(context, db)`** inserting demo recipients/notes (reuse `DemoBackendApis` fixture values or a small seed list) into Room. Idempotent: skip if recipients already exist.

- [ ] **Step 2: Settings UI** — in local mode show a "Load sample data" button (with confirmation dialog) calling `SampleDataLoader`.

- [ ] **Step 3: Strings** — add `settings_load_sample_data`, `settings_load_sample_data_done`, `settings_load_sample_data_confirm` in both `values/strings.xml` and `values-ar/strings.xml`.

- [ ] **Step 4: Test** — `SampleDataLoaderTest` verifying idempotency and counts.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): add optional load-sample-data action"
```

### Task 8: No-key empty states (UI scaffolding)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/summary/SummaryScreen.kt` (or `SummaryViewModel.kt`)
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlanScreens.kt`

- [ ] **Step 1: Add empty-state composables** shown when no AI key is configured: "Set up AI in Settings" with a button navigating to Settings. Gate on `AiKeyStore` (introduced in Phase 2); until then gate on a temporary `SettingsStore` boolean? Instead, implement `AiKeyStore` stub in Phase 2 first. To keep ordering, move Task 8 after Phase 2 Task 6. (See Phase 3.)

- [ ] **Step 2: Commit** with Phase 3.

---

## Phase 2 — AI Provider Layer

### Task 9: Raise minSdk to 23

**Files:**
- Modify: `android/app/build.gradle.kts`

- [ ] **Step 1: Set `minSdk = 23`** in `defaultConfig`.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(android): raise minSdk to 23 for Keystore AES-GCM"
```

### Task 10: `LlmClient` interface and provider enum

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/LlmClient.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/LlmModels.kt`

**Interfaces:**
- `enum class AiProvider { OPENAI, ANTHROPIC, GEMINI, XAI, OPEN_ROUTER }` with `defaultBaseUrl: String`, `defaultModel: String`, `displayName: String`.
- `data class LlmRequest(val systemPrompt: String, val dataBlock: String)`
- `enum class LlmStatus { OK, REPAIRED, FALLBACK }`
- `data class LlmResult(val status: LlmStatus, val text: String = "", val error: String? = null, val httpCode: Int? = null)`
- `interface LlmClient { suspend fun complete(request: LlmRequest): LlmResult }`

- [ ] **Step 1: Write unit test** for data class invariants (non-blank systemPrompt).

- [ ] **Step 2: Implement.**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(android): add LlmClient interface and provider enum"
```

### Task 11: OpenAI-compatible client (OpenAI, xAI, OpenRouter)

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/OpenAiCompatibleLlmClient.kt`
- Create: `android/app/src/test/java/com/caregiver/mobile/ai/OpenAiCompatibleLlmClientTest.kt`

**Interfaces:** `OpenAiCompatibleLlmClient(baseUrl, model, apiKey, okHttpClient, json)`.

- [ ] **Step 1: Write failing MockWebServer tests** — success (200 with choices), 401, 429, network failure (server shutdown). Assert `LlmResult.status` and `httpCode`/error mapping.

- [ ] **Step 2: Run tests to verify they fail**

- [ ] **Step 3: Implement**

  - POST `{baseUrl}/chat/completions`.
  - Headers: `Authorization: Bearer $apiKey`, `Content-Type: application/json`.
  - Body: `{"model":..,"messages":[{"role":"system","content":..},{"role":"user","content":..}],"max_tokens":..,"temperature":0.2}`.
  - Parse `choices[0].message.content`.
  - Map HTTP 401 -> error "invalid_key", 429 -> "rate_limit", IOException -> "network", other -> "http_$code". Return `FALLBACK`.

- [ ] **Step 4: Run tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): add OpenAI-compatible LlmClient"
```

### Task 12: Anthropic Messages client

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/AnthropicLlmClient.kt`
- Create: `android/app/src/test/java/com/caregiver/mobile/ai/AnthropicLlmClientTest.kt`

- [ ] **Step 1: Failing MockWebServer tests** (success, 401, 429, network).

- [ ] **Step 2: Implement**

  - POST `{baseUrl}/v1/messages`.
  - Headers: `x-api-key: $apiKey`, `anthropic-version: 2023-06-01`, `content-type: application/json`.
  - Body: `{"model":..,"max_tokens":..,"system":..,"messages":[{"role":"user","content":..}]}`.
  - Parse `content[0].text`.

- [ ] **Step 3: Tests pass; commit**

```bash
git commit -m "feat(android): add Anthropic Messages LlmClient"
```

### Task 13: Gemini generateContent client

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/GeminiLlmClient.kt`
- Create: `android/app/src/test/java/com/caregiver/mobile/ai/GeminiLlmClientTest.kt`

- [ ] **Step 1: Failing MockWebServer tests** (success, 401, 429, network).

- [ ] **Step 2: Implement**

  - POST `{baseUrl}/v1beta/models/{model}:generateContent` with query `?key=$apiKey` (or header `x-goog-api-key`).
  - Body: `{"contents":[{"parts":[{"text":..}]}],"systemInstruction":{"parts":[{"text":..}]},"generationConfig":{"maxOutputTokens":..,"temperature":0.2}}`.
  - Parse `candidates[0].content.parts[0].text`.

- [ ] **Step 3: Tests pass; commit**

```bash
git commit -m "feat(android): add Gemini generateContent LlmClient"
```

### Task 14: Encrypted AI key store (Keystore AES-GCM)

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/AiKeyStore.kt`
- Create: `android/app/src/test/java/com/caregiver/mobile/ai/AiKeyStoreTest.kt`
- Modify: `android/app/src/main/AndroidManifest.xml`
- Create: `android/app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:**
- `class AiKeyStore(context)` with:
  - `fun provider(): AiProvider?` / `fun setProvider(p: AiProvider)`
  - `fun apiKey(): String?` / `fun setApiKey(key: String)` / `fun clearApiKey()`
  - `fun model(): String?` / `fun setModel(m: String)`
  - `fun hasKey(): Boolean`
  - `fun consentGiven(): Boolean` / `fun setConsentGiven()`

Storage: AES-GCM key in Android Keystore (alias `caregiver_ai_key`), ciphertext in SharedPreferences file `ai_settings`. Never log. Exclude that prefs file from backups via data extraction rules (and set `android:allowBackup="false"` or rules).

- [ ] **Step 1: Write failing test** for round-trip set/get/clear (instrumented or Robolectric with a fake Keystore if needed). If Keystore unavailable in JVM tests, test against an in-memory fake implementing the same interface and add an instrumented test for the real implementation.

- [ ] **Step 2: Implement.**

- [ ] **Step 3: Manifest + extraction rules** excluding `ai_settings` shared prefs from backup.

- [ ] **Step 4: Run tests; commit**

```bash
git commit -m "feat(android): store AI key in Keystore (AES-GCM)"
```

### Task 15: Settings > AI screen

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/presentation/settings/AiSettingsScreen.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/core/navigation/Routes.kt` (or `AppDestinations`) to add `AiSettings` destination.
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainScaffold.kt` (NavHost entry) and `SettingsScreens.kt` (entry button).
- Modify: strings (both locales).

UI: provider picker (dropdown), masked API key field (`PasswordVisualTransformation`), model field with default placeholder per provider, "Test connection" button. Test connection instantiates the right client with current inputs (without persisting) and shows success or mapped error (invalid key / no network / rate limit). Save persists to `AiKeyStore`.

- [ ] **Step 1: Add destination + nav entry.**

- [ ] **Step 2: Implement screen.**

- [ ] **Step 3: Strings in both locales.**

- [ ] **Step 4: Instrumented UI test** `AiSettingsFlowUiTest` covering picker, masked field, test connection error display.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): add Settings > AI screen"
```

### Task 16: Consent dialog

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/ConsentDialog.kt` (composable) or inline in AI features.
- Strings (both locales).

Show before first AI call: care notes are sent to the chosen provider; app is not a medical device and not HIPAA-compliant. Persist consent in `AiKeyStore`.

- [ ] **Step 1: Implement composable.**

- [ ] **Step 2: Strings.**

- [ ] **Step 3: Commit**

```bash
git commit -m "feat(android): add AI consent dialog"
```

---

## Phase 3 — AI Features

### Task 17: Prompts file (single source)

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/Prompts.kt`

Contains all system prompts: translation (EN<->AR), summary (JSON observations/uncertainties), plan suggestion. Port wording from backend `SummaryPrompts`/`PlanSuggestionService` but keep data in delimited blocks.

- [ ] **Step 1: Implement.**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(android): add single Prompts file for AI"
```

### Task 18: Note translation (EN <-> AR)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteDetailScreen.kt` (or NoteDetailViewModel)
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/TranslationService.kt`
- Strings.

`TranslationService.translate(text, from, to, client): String?` calls LlmClient with prompt; returns translated text or null on fallback. UI shows loading, result labelled "AI-generated, may be inaccurate", retry on failure.

- [ ] **Step 1: Failing test** `TranslationServiceTest` with MockWebServer (success + fallback).

- [ ] **Step 2: Implement.**

- [ ] **Step 3: UI + strings.**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(android): add note translation via AI"
```

### Task 19: Summaries + doctor report via AI

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/summary/SummaryViewModel.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/SummaryAiService.kt`
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/SafetySignalEvaluator.kt` (port thresholds)
- Strings.

Flow: if no key -> empty state. Else gather notes via `graph.apis.notes().history(...)`, build data block (SummaryDataBuilder port), compute safety signals deterministically, call LlmClient, parse JSON into `SummaryDto` (text/redFlags/evidence/uncertainties), validate evidence quotes against note texts (port EvidenceValidator), merge safety (never downgrade). On fallback -> `SummaryDto` with empty observations + uncertainty "AI unavailable". Doctor report = summary text + red flags + evidence + uncertainties (existing structure).

- [ ] **Step 1: Failing tests** `SummaryAiServiceTest` (success parse, fallback, bad JSON -> fallback/empty) with MockWebServer.

- [ ] **Step 2: Implement service + safety evaluator.**

- [ ] **Step 3: Wire ViewModel + empty state.**

- [ ] **Step 4: Strings + AI-generated label.**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(android): generate summaries via AI layer"
```

### Task 20: Plan suggestions via AI (keep accept/edit/dismiss/archive)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/plans/PlanDetailViewModel.kt` (or a new viewmodel)
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/PlanSuggestionService.kt`
- Strings.

Flow: if no key -> empty state. Else gather summary + safety + notes, call LlmClient, parse suggestion JSON, validate evidence note ids, save via `graph.apis.plans().suggest(...)` (local in local mode). Existing accept/edit/dismiss/archive transitions unchanged.

- [ ] **Step 1: Failing tests** `PlanSuggestionServiceTest`.

- [ ] **Step 2: Implement.**

- [ ] **Step 3: Wire UI + empty state + AI-generated label.**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(android): generate plan suggestions via AI"
```

### Task 21: No-key empty states (final wiring)

**Files:**
- Modify: summary + plans screens to show "Set up AI in Settings" when `!AiKeyStore.hasKey()`.

- [ ] **Step 1: Implement.**

- [ ] **Step 2: Commit**

```bash
git commit -m "feat(android): add set-up-AI empty states"
```

---

## Phase 4 — Distribution

### Task 22: GitHub Actions release workflow

**Files:**
- Create: `.github/workflows/android-release.yml`

Trigger on tags `v*`. Steps: checkout, set up JDK 17, decode keystore from secrets (`KEYSTORE_BASE64`) to a temp file, build `:app:assembleRelease` with signing env vars, create GitHub Release, attach APK. Do not commit keystore or secrets; use repository secrets.

- [ ] **Step 1: Write workflow.**

- [ ] **Step 2: Verify YAML parses** (e.g., `python -c` or actionlint if available).

- [ ] **Step 3: Commit**

```bash
git commit -m "ci(android): add signed release APK workflow on tags"
```

### Task 23: README install + API key instructions

**Files:**
- Modify: `android/README.md` (and root `README.md` if present).

Include: download APK from Releases, install (allow unknown sources), how to get an API key from each provider and enter it in Settings > AI, consent note, no-backend/no-login note.

- [ ] **Step 1: Write docs.**

- [ ] **Step 2: Commit**

```bash
git commit -m "docs(android): add install and API key instructions"
```

---

## Final Verification

### Task 24: Full definition of done

- [ ] **Step 1: Build**

Run: `cd android && ./gradlew :app:assembleDebug`
Expected: PASS.

- [ ] **Step 2: Tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (including MockWebServer LlmClient tests, key store tests, strings parity).

- [ ] **Step 3: Lint**

Run: `./gradlew :app:lintDebug`
Expected: no new findings.

- [ ] **Step 4: Manual checks**

  - Install debug APK; app opens straight to main scaffold (no login).
  - RTL + Arabic parity test passes; switch language.
  - Summary/plan screens show "set up AI" empty state with no key.
  - With a test key, translation/summary/plan work and are labelled AI-generated.

- [ ] **Step 5: Commit any final fixes; summarize changes per phase and untested areas.**

---

## Open Questions

- Exact provider endpoints/models: verify against official docs before coding (Phase 2 tasks). Defaults chosen at implementation time.
- Addenda storage in Room: decide between a separate table or JSON column during Task 5; document the choice in the commit.
- Whether `LOCAL_MODE` should be overridable per build via a Gradle property: default true is required; keep simple unless needed.
