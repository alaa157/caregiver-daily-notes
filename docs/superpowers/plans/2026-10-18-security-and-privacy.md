# Security & Privacy Hardening Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix all security vulnerabilities in the BYOK Android app: plaintext token storage, missing certificate pinning, missing API key validation, and network security gaps.

**Architecture:** Move all secrets to encrypted storage using AndroidX Security library. Add OkHttp certificate pinners for all API domains. Add input validation for API keys before storage.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX Security Crypto, OkHttp, AndroidKeyStore

## Global Constraints

- `minSdk = 24` (Android 7.0)
- No new dependencies unless absolutely necessary; prefer existing AndroidX libraries
- All encrypted data must survive app restarts
- Keys must never be exported from the device

---

### Task 1: Encrypt Auth Token Storage

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/data/AuthRepository.kt` (modify)
- Create: `android/app/src/main/java/com/caregiver/mobile/data/TokenStore.kt` (new)
- Test: `android/app/src/test/java/com/caregiver/mobile/data/AuthRepositoryTest.kt` (modify)

**Interfaces:**
- Produces: `TokenStore` class with `getToken(): String?` and `setToken(token: String?)` methods
- Produces: `EncryptedSharedPreferences` wrapper for secure token storage

- [ ] **Step 1: Add AndroidX Security dependency**

In `android/app/build.gradle.kts`, add to dependencies:
```kotlin
implementation("androidx.security:security-crypto:1.1.0-alpha06")
```

- [ ] **Step 2: Create TokenStore with encrypted SharedPreferences**

Create `android/app/src/main/java/com/caregiver/mobile/data/TokenStore.kt`:
```kotlin
class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth_secure",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    fun getToken(): String? = prefs.getString("token", null)
    fun setToken(token: String?) {
        if (token == null) prefs.edit().remove("token").apply()
        else prefs.edit().putString("token", token).apply()
    }
    fun getEmail(): String? = prefs.getString("email", null)
    fun setEmail(email: String?) {
        if (email == null) prefs.edit().remove("email").apply()
        else prefs.edit().putString("email", email).apply()
    }
    fun clear() { prefs.edit().clear().apply() }
}
```

- [ ] **Step 3: Refactor AuthRepository to use TokenStore**

Modify `AuthRepository.kt` to delegate to `TokenStore` instead of raw `SharedPreferences`.

- [ ] **Step 4: Verify token persists across app restart**

```bash
cd android && ./gradlew :app:testDebugUnitTest --tests "com.caregiver.mobile.data.AuthRepositoryTest"
```

- [ ] **Step 5: Commit**
```bash
git add android/app/build.gradle.kts android/app/src/main/java/com/caregiver/mobile/data/
git commit -m "feat(security): encrypt auth token storage with EncryptedSharedPreferences"
```

---

### Task 2: Add Certificate Pinning for All API Domains

**Files:**
- Create: `android/app/src/main/java/com/caregiver/mobile/data/api/CertificatePinners.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/OpenAiCompatibleLlmClient.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/GeminiLlmClient.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/AnthropicLlmClient.kt`
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/api/ApiClient.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/api/CertificatePinningTest.kt`

**Interfaces:**
- Produces: `CertificatePinners.allPins: Map<String, CertificatePinner>` mapping domain → CertificatePinner

- [ ] **Step 1: Create CertificatePinners factory**

Create `android/app/src/main/java/com/caregiver/mobile/data/api/CertificatePinners.kt`:
```kotlin
object CertificatePinners {
    val allPins: Map<String, okhttp3.CertificatePinner> = mapOf(
        "api.anthropic.com" to CertificatePinner.Builder()
            .add("api.anthropic.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build(),
        "generativelanguage.googleapis.com" to CertificatePinner.Builder()
            .add("generativelanguage.googleapis.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")
            .build(),
        "openai.com" to CertificatePinner.Builder()
            .add("openai.com", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
            .build(),
        "x.ai" to CertificatePinner.Builder()
            .add("x.ai", "sha256/DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD=")
            .build(),
        "openrouter.ai" to CertificatePinner.Builder()
            .add("openrouter.ai", "sha256/EEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE=")
            .build()
    )
}
```
Note: Replace placeholder hashes with actual SPKI hashes for each service.

- [ ] **Step 2: Apply pinning in all LLM clients**

In each LLM client constructor, add a `CertificatePinner` parameter and apply it to the OkHttpClient builder:
```kotlin
private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
    .apply { certificatePinner?.let { certificatePinner(it) } }
    .build()
```

- [ ] **Step 3: Apply pinning in ApiClient**

Modify `ApiClient.kt` to accept and apply a `CertificatePinner` for the backend API domain.

- [ ] **Step 4: Write pinning verification test**

```kotlin
@Test
fun `certificate pinner rejects untrusted certificate`() {
    val pinner = CertificatePinners.allPins["api.anthropic.com"]!!
    // Verify pinner rejects wrong certificate
}
```

- [ ] **Step 5: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/api/ android/app/src/main/java/com/caregiver/mobile/ai/
git commit -m "feat(security): add certificate pinning for all API domains"
```

---

### Task 3: Validate API Keys Before Storage

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/ai/AiKeyStore.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ai/AiKeyStoreValidationTest.kt`

**Interfaces:**
- Consumes: Existing `AiKeyStore.setApiKey(key: String)`
- Produces: `AiKeyStore.isValidKeyFormat(key: String): Boolean`

- [ ] **Step 1: Add key format validation**

In `AiKeyStore.kt`, add:
```kotlin
private fun isValidKeyFormat(key: String): Boolean {
    return when {
        key.startsWith("sk-") && key.length >= 32 -> true  // OpenAI format
        key.matches(Regex("^[A-Za-z0-9_-]{20,}$")) -> true    // Google/Anthropic
        else -> false
    }
}
```

- [ ] **Step 2: Reject invalid keys in setApiKey**

Modify `setApiKey` to throw `IllegalArgumentException` for invalid keys.

- [ ] **Step 3: Write validation test**

```kotlin
@Test
fun `rejects invalid API key format`() {
    val store = AiKeyStore(context)
    assertThrows<IllegalArgumentException> { store.setApiKey("bad-key") }
}
```

- [ ] **Step 4: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/ai/AiKeyStore.kt
git commit -m "feat(security): validate API key format before storage"
```

---

### Task 4: Encrypt SettingsStore (Base URL)

**Files:**
- Modify: `android/app/src/main/java/com/caregiver/mobile/data/SettingsStore.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/data/SettingsStoreTest.kt`

**Interfaces:**
- Produces: `SettingsStore` using `EncryptedSharedPreferences` for all stored values

- [ ] **Step 1: Replace SharedPreferences with EncryptedSharedPreferences**

Refactor `SettingsStore.kt` to use the same encrypted prefs pattern as `TokenStore`.

- [ ] **Step 2: Write encrypted storage test**

```kotlin
@Test
fun `base url persists in encrypted storage`() {
    val store = SettingsStore(context)
    store.baseUrl = "https://api.example.com"
    val reloaded = SettingsStore(context)
    assertEquals("https://api.example.com", reloaded.baseUrl)
}
```

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/java/com/caregiver/mobile/data/SettingsStore.kt
git commit -m "feat(security): encrypt settings storage"
```

---

### Task 5: Disable Backup and Add Screen Security

**Files:**
- Modify: `android/app/src/main/AndroidManifest.xml`
- Modify: `android/app/src/main/java/com/caregiver/mobile/MainActivity.kt`
- Test: `android/app/src/test/java/com/caregiver/mobile/ManifestTest.kt`

- [ ] **Step 1: Add FLAG_SECURE to prevent screenshots**

In `MainActivity.kt`, add:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    // ...
}
```

- [ ] **Step 2: Verify allowBackup is false and FLAG_SECURE is set**

- [ ] **Step 3: Commit**
```bash
git add android/app/src/main/AndroidManifest.xml android/app/src/main/java/com/caregiver/mobile/MainActivity.kt
git commit -m "feat(security): disable screenshots and confirm backup disabled"
```
