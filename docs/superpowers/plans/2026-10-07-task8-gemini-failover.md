# Gemini Backup Provider with Failover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task (standing owner order: inline execution only, no subagents). Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Gemini backup LLM provider behind the existing `LlmClient` interface with primary → backup → template-fallback failover, using only scripted fakes in tests.

**Architecture:** A new `ChatCompletionsLlmClient` speaks the OpenAI-compatible chat-completions shape both providers use (Gemini primary, OpenRouter LFM backup, each with its own props). A new `FailoverLlmClient` decorator tries primary, then backup once, then returns the standard `AI_UNAVAILABLE` fallback. Callers (`SummaryService`, `PlanSuggestionService`) take `LlmClient` by constructor already, so zero caller changes. `HfLlmClient` is left untouched.

**Tech Stack:** Java 21, Spring Boot 3.3.4, Maven, Jackson (already on classpath), JUnit 5 + AssertJ.

**Spec:** GitHub issue `alaa157/caregiver-daily-notes#29` ([MEMBER 1 — TASK 8]), plus the owner's locked live-probe decisions recorded in its comments: primary `gemini-3.5-flash-lite` (free tier, 1000 RPD), backup `liquid/lfm-2.5-2.6b:free` (mandatory reasoning), Gemini-first order, model ids in repo config (no model secrets), keys from env only (`GEMINI_API_KEY`, `OPENROUTER_API_KEY` — both already in GitHub Secrets).

## Global Constraints

- Java 21, Spring Boot 3.3.4, Maven only (no Gradle).
- Package namespace `com.caregiver`; provider code lives in `.../ai/`, config in `.../config/`.
- TDD: failing test first, minimal implementation, full `mvn -f backend/pom.xml test` green before finishing.
- No live network calls in any test — scripted `HttpExchange`/`LlmClient` fakes only.
- No secrets, keys, prompts, or note contents in code, logs, errors, or test resources.
- `LlmClient.complete` never throws (except on null request) — failures become `LlmStatus.FALLBACK` results with `AI_UNAVAILABLE`-prefixed errors and empty text.
- Do not modify `HfLlmClient.java` (working legacy path) or any caller.
- Backend-only paths (`backend/...`); verify with `git diff --cached --name-only` before every commit.

## File Structure

- Modify: `backend/src/main/java/com/caregiver/config/LlmProperties.java` — add backup endpoint fields, per-endpoint token budgets, refresh stale defaults.
- Modify: `backend/src/main/resources/application.yml` — add `llm.backup-*` keys bound to env (keys only), keep model ids as repo defaults.
- Create: `backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java` — OpenAI-compatible provider client (messages, max_tokens, optional reasoning cap, content extraction).
- Create: `backend/src/main/java/com/caregiver/ai/FailoverLlmClient.java` — primary → backup-once → fallback decorator.
- Modify: `backend/src/test/java/com/caregiver/config/LlmPropertiesTest.java` — bind assertions for the new keys.
- Create: `backend/src/test/java/com/caregiver/ai/ChatCompletionsLlmClientTest.java` — FakeExchange-idiom tests (copy the `FakeExchange` pattern from `HfLlmClientTest.java:26-54`, do not refactor that file).
- Create: `backend/src/test/java/com/caregiver/ai/FailoverLlmClientTest.java` — scripted-`FakeLlmClient` failover tests.

## Review Focus

- A `finish_reason: "length"` response must count as FAILURE (retry/never success), never as content — truncation reads as breakage.
- Backup key absent/blank must skip the backup silently (fail closed), preserving current single-provider behavior.
- Error strings and logs must never contain key material, prompt text, or note contents — even truncated.
- Every transport failure shape (non-2xx, 429, 503, timeout, malformed JSON) must funnel into failover; no exception may escape `complete`.
- Mood-language variance (Arabic `"جيد"` vs English `"good"`) is caller-prompt scope (prompts must enum-constrain mood), not provider scope — no task here normalizes model content beyond the existing `ModelSanitizer.strip`.

---

### Task 1: Provider properties (primary Gemini + backup OpenRouter)

**Files:**
- Modify: `backend/src/main/java/com/caregiver/config/LlmProperties.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/caregiver/config/LlmPropertiesTest.java`

**Interfaces:**
- Consumes: nothing new (extends the existing `llm.*` record).
- Produces: `LlmProperties` gains `backupBaseUrl()`, `backupModel()`, `backupApiKey()`, `maxTokens()`, `reasoningMaxTokens()` (nullable `Long`, null = omit), `backupMaxTokens()`, `backupReasoningMaxTokens()` — exact names later tasks construct against.

- [ ] **Step 1: Write the failing test.** In `LlmPropertiesTest.java` add `properties_bindsBackupEndpoint` asserting: `backupBaseUrl()` = `"https://openrouter.ai/api/v1"`, `backupModel()` = `"liquid/lfm-2.5-2.6b:free"`, `backupApiKey()` empty by default, `maxTokens()` = `10000`, `reasoningMaxTokens()` null, `backupMaxTokens()` = `10000`, `backupReasoningMaxTokens()` = `500L`. Also update the stale `@DefaultValue` expectation: refresh `LlmProperties.java` `@DefaultValue`s to `"https://generativelanguage.googleapis.com/v1beta/openai"` and `"gemini-3.5-flash-lite"` (the record defaults drifted from `application.yml`).
- [ ] **Step 2: Run it.** Run: `mvn -B -f backend/pom.xml test -Dtest=LlmPropertiesTest`. Expected: FAIL (unknown properties).
- [ ] **Step 3: Implement.** Add the seven components to the `LlmProperties` record with `@DefaultValue`s (`backupBaseUrl` → openrouter URL, `backupModel` → LFM id, `backupApiKey` → `""`, `maxTokens` → `"10000"`, `reasoningMaxTokens` → none/nullable `Long` default null, `backupMaxTokens` → `"10000"`, `backupReasoningMaxTokens` → `"500"`). In `application.yml` add `backup-base-url: ${BACKUP_BASE_URL:<openrouter-url>}`? No — owner rule: keys from env only, model ids/URLs as repo defaults. So: `backup-base-url` and `backup-model` as literal repo defaults (mirroring `base-url`/`model` style), `backup-api-key: ${OPENROUTER_API_KEY:}`, and change `api-key: ${HF_API_KEY:}` to `api-key: ${GEMINI_API_KEY:}`. Update the record javadoc (it still says HF).
- [ ] **Step 4: Run.** Run: `mvn -B -f backend/pom.xml test -Dtest=LlmPropertiesTest`. Expected: PASS (all tests in the class).
- [ ] **Step 5: Commit.** `git add backend/src/main/java/com/caregiver/config/LlmProperties.java backend/src/main/resources/application.yml backend/src/test/java/com/caregiver/config/LlmPropertiesTest.java`; `git commit -m "feat(ai): add backup provider properties for failover"`.

### Task 2: Chat-completions provider client

**Files:**
- Create: `backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java`
- Test: `backend/src/test/java/com/caregiver/ai/ChatCompletionsLlmClientTest.java`

**Interfaces:**
- Consumes: `LlmProperties` (Task 1), `HttpExchange.post(url, headers, jsonBody, timeout)`, `Sleeper` (same constructor shape as `HfLlmClient`: `(props, exchange, sleeper)` plus a `(props)` convenience), `ModelSanitizer.strip`, `StrictJsonParser.parseTree`.
- Produces: `ChatCompletionsLlmClient(LlmProperties props, HttpExchange exchange, Sleeper sleeper)` with `LlmResult complete(LlmRequest request)`; per-endpoint selection via a `boolean useBackup` constructor arg? No — simpler: the client takes explicit `(String baseUrl, String model, String apiKey, int maxTokens, Long reasoningMaxTokens, Duration timeout, int maxAttempts, long backoffBaseMs, HttpExchange exchange, Sleeper sleeper)`. Task 3 constructs two instances (primary from `props.*`, backup from `props.backup*`).

- [ ] **Step 1: Write the failing tests.** `ChatCompletionsLlmClientTest.java` (FakeExchange idiom copied from `HfLlmClientTest`): (a) `validChatCompletion_yieldsOk` — body `{"choices":[{"message":{"content":"{\"mood\":\"good\"}"},"finish_reason":"stop"}]}` → `OK` with stripped text; assert request URL, `Authorization: Bearer` header, body contains `"model"`, `"messages"`, `"max_tokens":10000`; (b) `lengthFinishReason_countsAsFailure` — `finish_reason: "length"` with valid JSON content → NOT `OK` (falls into retry/fallback path; with all attempts scripted length → `FALLBACK`); (c) `reasoningCapIncludedWhenSet` — body contains `"reasoning":{"max_tokens":500}` when cap non-null, absent when null; (d) `http500EveryAttempt_yieldsFallbackNeverThrows` — mirrors `HfLlmClientTest.allAttempts500` (3 calls, delays 200/400, `AI_UNAVAILABLE` prefix, empty text; script a timeout first, then 500s; assert the fallback error contains neither `"Bearer"` nor the request body); (e) `blankKey_sendsNoAuthHeader` — blank-key props → captured headers contain no `Authorization` entry.
- [ ] **Step 2: Run.** Run: `mvn -B -f backend/pom.xml test -Dtest=ChatCompletionsLlmClientTest`. Expected: FAIL (class does not exist).
- [ ] **Step 3: Implement.** Minimal port of the `HfLlmClient` retry/backoff/repair loop (same `MAX_ATTEMPTS`/`REPAIR_ATTEMPTS`/`FALLBACK_MARKER` semantics, same `DATA_BEGIN`/`DATA_END` note delimiters and `escapeDataMarkers`), with: URL = `baseUrl + "/chat/completions"` (trailing-slash trimmed); body `{"model":..., "messages":[{"role":"system","content":systemPrompt},{"role":"user","content":delimitedData}], "max_tokens":..., "temperature":0.2}` plus `"reasoning":{"max_tokens":cap}` only when cap non-null; `extractText` reads `choices[0].message.content` and returns null unless `choices[0].finish_reason` is `"stop"` (any other reason, including `"length"`, counts as failure); `Authorization` header only when key non-blank. `complete` never throws (null request → `IllegalArgumentException`, matching `HfLlmClient`).
- [ ] **Step 4: Run.** Run: `mvn -B -f backend/pom.xml test -Dtest='ChatCompletionsLlmClientTest,HfLlmClientTest'`. Expected: PASS (new + legacy untouched).
- [ ] **Step 5: Commit.** `git add` the two files; `git commit -m "feat(ai): add OpenAI-compatible chat completions client"`.

### Task 3: Failover decorator

**Files:**
- Create: `backend/src/main/java/com/caregiver/ai/FailoverLlmClient.java`
- Test: `backend/src/test/java/com/caregiver/ai/FailoverLlmClientTest.java`

**Interfaces:**
- Consumes: `LlmClient` (any two), `LlmRequest`, `LlmResult`, `LlmStatus`.
- Produces: `FailoverLlmClient(LlmClient primary, LlmClient backup)` with `LlmResult complete(LlmRequest request)`; null request → `IllegalArgumentException`; null primary → `NullPointerException` via `Objects.requireNonNull`; null backup = backup skipped (fail closed). Provider attribution: `FailoverLlmClient(LlmClient primary, LlmClient backup, java.util.function.Consumer<String> events)` overload emits exactly one token per call — `"primary-ok"` | `"backup-ok"` | `"fallback"` (content-free); the 2-arg constructor delegates with a no-op consumer. Wiring (two `ChatCompletionsLlmClient` instances from Task 1 props) happens wherever composition roots live once the entrypoint exists — NOT in this task (no DI bootstrap in repo yet).

- [ ] **Step 1: Write the failing tests.** `FailoverLlmClientTest.java` with scripted `FakeLlmClient`s (existing test util, do not modify it) plus an `ArrayList<String> events` passed to the 3-arg constructor: (a) `primaryOk_backupNeverCalled` — primary scripted `new LlmResult(LlmStatus.OK, "{\"a\":1}", null)`, assert result equals that value, `backup.calls() == 0`, events `["primary-ok"]`; (b) `primaryFallbackThenBackupOk_returnsBackupOk` — primary scripted `new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down")`, backup scripted `new LlmResult(LlmStatus.OK, "{\"a\":1}", null)`, assert the `OK` value returned, both `calls() == 1`, events `["backup-ok"]`; (c) `bothFallback_yieldsFallbackEmptyText` — assert `FALLBACK`, empty text, error starts with `AI_UNAVAILABLE`, events `["fallback"]`; (d) `backupInvalidStillFallsBack` — primary `FALLBACK`, backup `REPAIRED`? No — decorator passes backup results through untouched (validation is the caller's job, same as today): assert backup's exact status/text returned verbatim, including `REPAIRED`; (e) `nullBackup_skipsBackup` — primary `FALLBACK` → result `FALLBACK`, no throw; (f) `nullRequest_throwsIllegalArgument`.
- [ ] **Step 2: Run.** Run: `mvn -B -f backend/pom.xml test -Dtest=FailoverLlmClientTest`. Expected: FAIL (class does not exist).
- [ ] **Step 3: Implement.** `complete`: null request → throw; `LlmResult first = primary.complete(request)`; emit `"primary-ok"` and return it unless `first.status() == FALLBACK` and backup non-null, else return `backup.complete(request)` verbatim emitting `"backup-ok"` on non-fallback / `"fallback"` otherwise; backup throwing (contract violation) → catch `RuntimeException`, emit `"fallback"`, return `FALLBACK` empty-text `AI_UNAVAILABLE: backup failed` — the decorator boundary must hold the never-throws contract even against a misbehaving delegate. The events consumer receives tokens only, never request/response content (no other logging in this class).
- [ ] **Step 4: Run.** Run: `mvn -B -f backend/pom.xml test -Dtest=FailoverLlmClientTest`. Expected: PASS.
- [ ] **Step 5: Commit.** `git add` the two files; `git commit -m "feat(ai): add primary-backup failover LLM client"`.

### Task 4: Full-suite green + ship

**Files:** none (verification + push only).

- [ ] **Step 1: Run the full suite.** Run: `mvn -B -f backend/pom.xml test`. Expected: `Tests run: 131+ (new tests included), Failures: 0, Errors: 0` and `BUILD SUCCESS`.
- [ ] **Step 2: Verify the tree.** Run: `git status --short` (only the six intended files across Tasks 1–3) and `git diff --cached --name-only` after staging; grep the diff for key material (`Bearer`, `sk-or`, `AIza`, `sbp_`, `gQAA`) — must be empty.
- [ ] **Step 3: Push.** Push `main` (fast-forward only, never force). Expected: CI workflow green on the push.
- [ ] **Step 4: Evidence comment on #29.** Comment per-task verification (tests added, suite count, no live calls, no secrets) without pasting any values.

### Task 5: Composition-root wiring + close (gate: entrypoint exists)

**Files:**
- Create/modify: composition-root bean wiring `ChatCompletionsLlmClient` (primary, Task 1 props) → `ChatCompletionsLlmClient` (backup, Task 1 backup props) → `FailoverLlmClient` (Task 3) wherever the DI bootstrap lives (`CaregiverApplication` composition root, landed via #12).
- Test: extend or add a wiring test proving the injected `LlmClient` is a `FailoverLlmClient` over two chat-completions delegates (no live calls).

**Interfaces:**
- Consumes: `LlmProperties` (Task 1), `ChatCompletionsLlmClient` (Task 2), `FailoverLlmClient` (Task 3).
- Produces: a single injectable `LlmClient` bean with the Gemini → OpenRouter → template chain; downstream AI services inject this bean instead of constructing clients.

- [ ] **Step 1: Write the failing test.** Wiring test boots the application context (test-scoped `LlmProperties` values, no network) and asserts the `LlmClient` bean is a `FailoverLlmClient`.
- [ ] **Step 2: Run.** `mvn -B -f backend/pom.xml test -Dtest=<WiringTest>`. Expected: FAIL (no bean defined).
- [ ] **Step 3: Implement.** Define the `@Bean` chain in the composition root; keys stay env-only (`GEMINI_API_KEY`, `OPENROUTER_API_KEY`); no model ids in secrets.
- [ ] **Step 4: Run.** Full suite: `mvn -B -f backend/pom.xml test`. Expected: green, `BUILD SUCCESS`.
- [ ] **Step 5: Close #29.** Evidence comment (wiring test, suite count, no live calls, no secrets), tick issue checkboxes, close.
