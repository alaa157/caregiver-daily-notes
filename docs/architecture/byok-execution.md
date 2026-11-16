# Caregiver BYOK Execution Boundary

## 1. Purpose and scope

This document defines the architecture boundary for caregiver-supplied
provider credentials. AI summaries and care-plan suggestions are available
only through bring-your-own-key (BYOK): the backend holds no provider
credentials and no server-configured provider path exists.

## 2. Current implementation

On `main`:

- [`ProviderCredentials.java`](../../backend/src/main/java/com/caregiver/ai/ProviderCredentials.java)
  carries request-scoped `providerId`, `model` and `apiKey`; it is validated
  against an approved provider catalogue and HTTPS base URLs.
- [`ByokSummaryRequest.java`](../../backend/src/main/java/com/caregiver/ai/ByokSummaryRequest.java)
  and [`ByokPlanGenerationRequest.java`](../../backend/src/main/java/com/caregiver/ai/ByokPlanGenerationRequest.java)
  wrap the summary fields plus `credentials`.
- [`SummaryController.java`](../../backend/src/main/java/com/caregiver/ai/SummaryController.java)
  exposes `POST /api/summaries/byok`; [`PlanGenerationController.java`](../../backend/src/main/java/com/caregiver/ai/PlanGenerationController.java)
  exposes `POST /api/plans/generate/byok`; `AiConnectionTestController` exposes
  `POST /api/ai/byok/connection-test`.
- [`ChatCompletionsLlmClient.java`](../../backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java)
  uses the request-scoped credentials for endpoint, model and key. Calls
  without credentials fail closed; there is no configured fallback provider.
- [`LlmProperties.java`](../../backend/src/main/java/com/caregiver/config/LlmProperties.java)
  holds only shared client tunables (timeout, attempt count, backoff, token
  budget). No `GEMINI_API_KEY`, `OPENROUTER_API_KEY`, `LLM_API_KEY` or
  `LLM_BASE_URL` configuration exists.
- Web UI: caregivers configure provider/model/key in Settings → AI
  (`web/src/features/ai/`), gated by a connection test before activation.
  Android: Settings → AI stores keys in the Android Keystore.

## 3. Request lifecycle

1. The caregiver opens Settings → AI, selects a supported provider, enters
   their model and API key.
2. The UI holds the entered key in memory only; nothing is persisted by the
   backend.
3. The caregiver runs a connection test, which sends a fixed, harmless probe
   containing no caregiver notes, recipient information, or patient data.
4. When the caregiver requests a summary or plan, the app includes the BYOK
   credential in that individual authenticated request.
5. The backend authenticates the caregiver and verifies ownership of the
   requested recipient before any provider call.
6. The backend invokes the provider adapter with the supplied credential.
7. The credential is discarded when the request lifecycle ends.

## 4. Credential security and non-persistence rules

A caregiver-supplied API key must never be:

- Written to PostgreSQL.
- Written to `localStorage`, `sessionStorage`, IndexedDB or browser cookies.
- Included in CSV/PDF exports, audit metadata, application logs, exception
  messages, error responses, API responses, analytics or telemetry payloads.

Additionally, request headers and serialized bodies containing credentials
must never be logged, and error messages must not echo the supplied key.

## 5. Authentication and ownership boundary

BYOK does not replace normal application authentication. Every summary or
plan request still authenticates the caregiver and verifies that the
requested recipient belongs to them. Possession of a provider API key is not
proof of caregiver identity or recipient ownership; authentication and
ownership checks run before any provider call.

## 6. Provider reference catalogue

Backend-supported provider IDs (validated in `ProviderCredentials`):
`OPENAI`, `GEMINI`, `XAI`, `OPEN_ROUTER`, all over the OpenAI-compatible
chat-completions endpoint shape. The Android branch's catalogue also lists
`ANTHROPIC`, which the backend does not support; do not assume Android and
backend provider sets are identical.

Android reference values from `feat/standalone-byok-ai`:

| Provider ID | Display name | Default model | Default base URL |
|---|---|---|---|
| `OPENAI` | OpenAI | `gpt-6-luna` | `https://api.openai.com/v1` |
| `ANTHROPIC` | Anthropic | `claude-sonnet-5-5` | `https://api.anthropic.com` |
| `GEMINI` | Gemini | `gemini-3.8-flash` | `https://generativelanguage.googleapis.com` |
| `XAI` | xAI | `grok-4.7` | `https://api.x.ai/v1` |
| `OPEN_ROUTER` | OpenRouter | `openai/gpt-4o-mini` | `https://openrouter.ai/api/v1` |

These Android files are reference material only; no Android implementation
code is copied or ported by the backend.

## 7. Provider adapter boundary

The backend has one OpenAI-compatible
[`ChatCompletionsLlmClient`](../../backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java).
Provider-specific request formats and response parsing belong inside provider
adapters; a non-OpenAI-compatible provider would require its own adapter.

## 8. Android security reference versus browser security

Android's `AiKeyStore.kt` uses encrypted storage backed by Android Keystore.
That platform-specific implementation is reference material only and does not
authorize storing browser BYOK credentials in `localStorage`,
`sessionStorage`, IndexedDB, cookies, or any other browser persistence
mechanism. The web app keeps credentials in memory only for the request
lifetime.

## 9. Status

BYOK is implemented for summaries, plan suggestions and the connection test
on `main`, for both Web and Android. Live E2E coverage requires explicit
`CAREGIVER_E2E_BYOK_PROVIDER` / `_MODEL` / `_KEY` environment variables and
skips otherwise (see `web/HANDOFF.md`).

## 10. Non-goals

This boundary does not include:

- Server-side or platform-funded provider keys.
- Browser or backend persistence of caregiver keys.
- Anthropic support in the backend adapter set.
- Changes to Android implementation code.
