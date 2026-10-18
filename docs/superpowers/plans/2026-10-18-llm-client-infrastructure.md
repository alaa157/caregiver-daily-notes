# LLM Client Infrastructure Hardening Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all LLM client bugs: compile errors, linear backoff, missing timeouts, per-call client creation, and missing fallback logic.

**Architecture:** Create a shared `LlmClientFactory` that produces properly configured OkHttpClient instances with timeouts and certificate pinning. Replace linear backoff with exponential backoff. Inject the OkHttpClient from AppGraph instead of creating per-call clients.

**Tech Stack:** Kotlin, OkHttp, kotlinx.coroutines

## Global Constraints

- `minSdk = 24` (Android 7.0)
- LLM API calls can take up to 60 seconds; timeout must be ≥ 60s
- Retry must use exponential backoff: base 500ms, multiplier 2, max 3 attempts
- All clients must share the same OkHttpClient configuration

---

### Task 1: Fix ApiClient Compile Error

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/ApiClient.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/api/ApiClientTest.kt`

**Interfaces:**
- Produces: Fixed `ApiClient.get<T>()` with correct type parameter

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `deserializes JSON response correctly`() {
    val mockClient = createMockClientWithResponse("""[{"id":"1"}]""")
    val api = ApiClient(mockClient)
    val result = api.get("/test", List::class.java)
    assertEquals(1, result.size)
}
```

- [ ] **Step 2: Fix Json.decodeFromString call**

In `ApiClient.kt`, change:
```kotlin
Json.decodeFromString(body)
```
to use proper type parameter via reified inline function or pass the TypeReference.

- [ ] **Step 3: Run test to verify**
```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.api.ApiClientTest"
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/api/ApiClient.kt
git commit -m "fix(api): correct JSON deserialization type parameter"
```

---

### Task 2: Create Shared LlmClientFactory

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/LlmClientFactory.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/AppGraph.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ai/LlmClientFactoryTest.kt`

**Interfaces:**
- Produces: `LlmClientFactory(okHttpClient: OkHttpClient)` with `createClient(baseUrl, model, apiKey): LlmClient` method

- [ ] **Step 1: Create factory**

```kotlin
class LlmClientFactory(private val okHttpClient: OkHttpClient) {
    fun createClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return OpenAiCompatibleLlmClient(baseUrl, model, apiKey, okHttpClient)
    }
    fun createGeminiClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return GeminiLlmClient(baseUrl, model, apiKey, okHttpClient)
    }
    fun createAnthropicClient(baseUrl: String, model: String, apiKey: String): LlmClient {
        return AnthropicLlmClient(baseUrl, model, apiKey, okHttpClient)
    }
}
```

- [ ] **Step 2: Wire into AppGraph**

Modify `AppGraph.kt` to inject `LlmClientFactory` and use it in AI-related screens.

- [ ] **Step 3: Write factory test**

```kotlin
@Test
fun `factory creates clients with shared OkHttpClient`() {
    val factory = LlmClientFactory(testClient)
    val client = factory.createClient("https://api.example.com", "test-model", "sk-test")
    assertNotNull(client)
}
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/ai/LlmClientFactory.kt
git commit -m "feat(ai): add shared LlmClientFactory with proper client injection"
```

---

### Task 3: Fix Backoff Strategy (Exponential, Not Linear)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/OpenAiCompatibleLlmClient.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/GeminiLlmClient.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/AnthropicLlmClient.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ai/BackoffStrategyTest.kt`

**Interfaces:**
- Produces: `calculateBackoff(attempt: Int, baseMs: Long = 500): Long` in each client

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `exponential backoff doubles wait time`() {
    val client = OpenAiCompatibleLlmClient("https://test.com", "model", "key")
    assertEquals(500L, client.calculateBackoff(0))
    assertEquals(1000L, client.calculateBackoff(1))
    assertEquals(2000L, client.calculateBackoff(2))
    assertEquals(4000L, client.calculateBackoff(3))
}
```

- [ ] **Step 2: Add backoff method to all three clients**

```kotlin
private fun calculateBackoff(attempt: Int): Long {
    return BACKOFF_BASE_MS * (1L shl attempt.coerceAtMost(4)) // cap at 16x
}

companion object {
    const val BACKOFF_BASE_MS = 500L
}
```

- [ ] **Step 3: Replace delay calls**

In each client, replace `delay(1000L * (attempt + 1))` with `delay(calculateBackoff(attempt))`.

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/ai/
git commit -m "fix(ai): use exponential backoff instead of linear"
```

---

### Task 4: Add Configurable Timeouts

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/core/network/OkHttpConfig.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/AppGraph.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/core/network/OkHttpConfigTest.kt`

**Interfaces:**
- Produces: `OkHttpConfig.defaultOkHttpClient(): OkHttpClient` with 60s timeouts and pinning

- [ ] **Step 1: Create OkHttpConfig**

```kotlin
object OkHttpConfig {
    fun defaultOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(TokenHolder))
            .build()
    }
}
```

- [ ] **Step 2: Use in AppGraph**

Modify `AppGraph.kt` to use `OkHttpConfig.defaultOkHttpClient()` when creating API clients.

- [ ] **Step 3: Write timeout test**

```kotlin
@Test
fun `client has correct timeouts`() {
    val client = OkHttpConfig.defaultOkHttpClient()
    assertEquals(30_000, client.connectTimeoutMillis)
    assertEquals(60_000, client.readTimeoutMillis)
}
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/core/network/OkHttpConfig.kt
git commit -m "feat(ai): add configurable 60s timeouts for LLM API calls"
```

---

### Task 5: Fix Per-Call OkHttpClient Creation

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteScreens.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ai/LlmClientReuseTest.kt`

**Interfaces:**
- Consumes: `LlmClientFactory` from AppGraph
- Produces: No longer creates `OkHttpClient()` per translation call

- [ ] **Step 1: Pass factory from AppGraph**

Modify `NoteDetailScreen.kt` to use `LlmClientFactory` from `AppGraph` instead of creating clients inline.

- [ ] **Step 2: Verify factory is used**

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteScreens.kt
git commit -m "fix(ai): inject LlmClientFactory instead of creating per-call clients"
```

---

### Task 6: Add Provider Fallback Logic

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/ai/FailoverAiService.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/presentation/notes/NoteScreens.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ai/FailoverAiServiceTest.kt`

**Interfaces:**
- Produces: `FailoverAiService(primary: LlmClient, secondary: LlmClient?)` with `translate(text, from, to): String`

- [ ] **Step 1: Write failing test**

```kotlin
@Test
fun `falls back to secondary when primary fails`() {
    val primary = FakeLlmClient(fail = true)
    val secondary = FakeLlmClient(response = "hello")
    val service = FailoverAiService(primary, secondary)
    assertEquals("hello", service.translate("مرحبا", "ar", "en"))
}
```

- [ ] **Step 2: Implement FailoverAiService**

```kotlin
class FailoverAiService(
    private val primary: LlmClient,
    private val secondary: LlmClient?,
    private val prompts: TranslationService
) {
    suspend fun translate(text: String, from: String, to: String): String {
        return try {
            primary.translate(text, from, to)
        } catch (e: Exception) {
            secondary?.translate(text, from, to) ?: throw e
        }
    }
}
```

- [ ] **Step 3: Wire into NoteDetailScreen**

Use `FailoverAiService` when translating notes.

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/ai/FailoverAiService.kt
git commit -m "feat(ai): add provider fallback for translation"
```
