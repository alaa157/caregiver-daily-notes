# Appearance, Settings UX, and AI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reimplement Metrolist-inspired UX without copying GPL code: dark/light/system appearance with color palettes, grouped settings with dialogs, editable system prompt, retry/backoff in all LLM clients, About section, and full AI feature wiring.

**Architecture:** Two independent agents work in parallel. Agent A owns the AI/services track; Agent B owns the UI/settings/theme track. They share only stable interfaces already present (`AiKeyStore`, `SettingsStore`, `LlmClient`, `Prompts`), so neither blocks the other. No GPL code is copied; all components are reimplemented.

**Tech Stack:** Kotlin 2.0.21, Compose Material3, DataStore, Keystore, OkHttp.

**Spec:** User-provided direction in the 2026-10-17 conversation. No separate spec file.

## Global Constraints

- No GPL-licensed code copied. Reimplement inspired patterns; keep our own package and headers.
- Do not touch `backend/` or `web/`.
- Keep `LOCAL_MODE` default true and all existing tests passing.
- Appearance changes must not break RTL or strings parity.

## Review Focus

- Dark mode must not reduce contrast or break existing screens.
- Retry/backoff must not hang the UI thread or leak keys.
- User-editable system prompt must fall back to defaults when blank.
- Grouped settings must remain navigable and testable.

---

## Agent A — AI & Services Track

Owns: `ai/*`, service wiring, ViewModels. Does not touch theme or settings layout.

### A1. Retry/backoff in Anthropic and Gemini clients
**Files:** `ai/AnthropicLlmClient.kt`, `ai/GeminiLlmClient.kt`
- [ ] Mirror the retry loop already in `OpenAiCompatibleLlmClient` (3 attempts, delay on 5xx/network/empty; immediate return on 4xx).
- [ ] Tests: extend MockWebServer tests for both clients.

### A2. User-editable system prompt
**Files:** `ai/AiKeyStore.kt` (already has `systemPrompt()`/`setSystemPrompt()`), `ai/SummaryAiService.kt`, `ai/TranslationService.kt`, `ai/PlanSuggestionService.kt`
- [ ] Each service reads `AiKeyStore.systemPrompt()`; if non-blank, prepend/replace the default prompt.
- [ ] Keep `Prompts.kt` as the default source.

### A3. Wire AI into ViewModels and screens
**Files:** `presentation/summary/SummaryViewModel.kt`, `presentation/notes/NoteDetailScreen.kt`, `presentation/plans/*`
- [ ] Summary: when a key is present, generate via `SummaryAiService` (fall back to API path otherwise) and label output “AI-generated”.
- [ ] Note detail: add Translate action using `TranslationService`.
- [ ] Plans: “Suggest” uses `PlanSuggestionService` then saves via `PlanApi`.
- [ ] No-key empty states already in place.

### A4. MockWebServer tests for Anthropic/Gemini
**Files:** `app/src/test/.../ai/AnthropicLlmClientTest.kt`, `GeminiLlmClientTest.kt`
- [ ] Success/401/429/network for both.

---

## Agent B — UI, Settings, Theme Track

Owns: theme, settings screens, components, About. Does not touch LLM clients or services.

### B1. Appearance settings (dark/light/system + palette)
**Files:** `core/theme/Theme.kt`, `core/theme/Color.kt`, `data/SettingsStore.kt`, `presentation/settings/SettingsScreens.kt`
- [ ] Add `AppDarkScheme` (done) and a palette selector (2–3 accent palettes) stored in `SettingsStore` (new `colorPalette` key).
- [ ] `CaregiverTheme(darkTheme, palette)` applies selected palette.
- [ ] Settings UI: segmented control for mode + palette chips.

### B2. Grouped settings screen (Metrolist-style, reimplemented)
**Files:** `presentation/settings/SettingsScreens.kt`, new `ui/component/SettingsGroup.kt`
- [ ] Reimplement a `SettingsGroup` composable (grouped cards, 24dp radius) without copying Metrolist.
- [ ] Reorganize into sections: Appearance, AI, Language, Data (load sample), About, and (non-local) Server/Sign-out.

### B3. Dialog-based AI settings
**Files:** `presentation/settings/AiSettingsScreen.kt`
- [ ] Rewrite to dialog-driven grouped settings: provider picker dialog, masked key dialog, model picker with suggestions + custom input, base URL (custom only), system prompt dialog with default/reset, help links.

### B4. About section
**Files:** `presentation/settings/AboutScreen.kt` (new), `SettingsScreens.kt`
- [ ] App version, description, credits, links. No music content.

### B5. Empty placeholder component
**Files:** `ui/component/EmptyPlaceholder.kt` (new)
- [ ] Reimplement a simple empty state composable used by summary/plan/no-data screens.

---

## Shared Interfaces (do not change without coordination)

- `AiKeyStore`: `provider()`, `setProvider()`, `apiKey()`, `setApiKey()`, `clearApiKey()`, `hasKey()`, `model()`, `setModel()`, `consentGiven()`, `setConsentGiven()`, `systemPrompt()`, `setSystemPrompt()`.
- `SettingsStore`: `themeMode`, `setThemeMode()`, `language`, `setLanguage()`. Add `colorPalette`/`setColorPalette()` (Agent B).
- `LlmClient`: `complete(LlmRequest): LlmResult` (Agent A adds retry internally; signature unchanged).
- `Prompts`: default constants (Agent A reads, Agent B does not modify).

## Merge Order

1. Both agents commit to the same branch in separate commits.
2. Agent A’s service wiring depends on `AiKeyStore.systemPrompt()` (already present) — no conflict.
3. Agent B’s settings grouping depends on `SettingsStore.colorPalette` (new) — Agent A must not reference it.
4. Final integration pass: run `assembleDebug`, `testDebugUnitTest`, `lintDebug`.

## Deferred (out of scope for this plan)

- Full theme rebrand to Metrolist colors (keep Calm Clinical Warmth palette).
- Instrumented (androidTest) AI settings flow tests.
- Backend/web changes.
