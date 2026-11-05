# Caregiver BYOK Execution Boundary

## 1. Purpose and scope

This document defines the architecture boundary for caregiver-supplied
provider credentials in the Web application. It is an architecture contract for
future work, not evidence that caregiver BYOK is implemented.

The intended feature lets a caregiver supply their own provider API key for a
specific AI request. The key must not be stored in the application's backend
database or in browser persistence. This document distinguishes verified
behavior on `main` from the target request lifecycle and from Android-only
reference material.

## 2. Current implementation

On the current `main` branch:

- [`LlmRequest.java`](../../backend/src/main/java/com/caregiver/ai/LlmRequest.java)
  contains `systemPrompt` and `dataBlock`; it has no caregiver-supplied API
  key.
- [`ChatCompletionsLlmClient.java`](../../backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java)
  uses instance-configured provider settings, including base URL, model, and
  API key.
- [`LlmProperties.java`](../../backend/src/main/java/com/caregiver/config/LlmProperties.java)
  reads backend provider configuration, including the `GEMINI_API_KEY` and
  `OPENROUTER_API_KEY` environment variables.
- [`LlmClientConfig.java`](../../backend/src/main/java/com/caregiver/config/LlmClientConfig.java)
  wires configured primary and optional backup clients through
  `FailoverLlmClient`.
- The [code map](code-map.md) records that no caregiver BYOK settings page or
  per-request credential-management flow was found in the Web application.

**Caregiver BYOK is not implemented on the current `main` branch.** Environment
configured backend provider credentials are not caregiver BYOK and must not be
described as such.

## 3. Target request lifecycle

The following is target architecture, not current behavior:

1. The caregiver opens the future BYOK settings interface.
2. The caregiver selects a supported provider and enters their API key.
3. The Web UI holds the entered key in memory only.
4. The caregiver may perform a connection test.
5. A connection test sends a fixed, harmless probe containing no caregiver
   notes, recipient information, or patient data.
6. When the caregiver requests a summary or plan, the Web application includes
   the required BYOK credential in that individual authenticated request.
7. The backend authenticates the caregiver and verifies ownership of the
   requested recipient.
8. The backend selects the corresponding provider adapter and uses the
   supplied credential for that request.
9. The provider request completes.
10. The backend discards the request-scoped credential after the request
    lifecycle ends.
11. The UI clears its in-memory credential when the caregiver explicitly
    removes it or leaves the relevant settings context, according to the
    eventual UI lifecycle design.

This contract does not define endpoint names, request DTO names, or controller
names. Those details belong to the implementation issues.

## 4. Credential security and non-persistence rules

A caregiver-supplied API key must never be:

- Written to PostgreSQL.
- Written to `localStorage`.
- Written to `sessionStorage`.
- Written to IndexedDB.
- Written to browser cookies.
- Included in CSV exports.
- Included in PDF exports.
- Included in audit metadata.
- Included in application logs.
- Included in exception messages or error responses.
- Returned by any API response.
- Included in analytics or telemetry payloads.

Additionally:

- Never log request headers containing authorization credentials.
- Never log serialized request bodies if they contain a credential.
- Never include the key in debug output.
- Never expose the key through a backend DTO or response object.
- Error messages must not echo the supplied key.
- Key removal must clear the UI's in-memory copy.
- Backend request handling must not retain credentials beyond the lifetime
  required to complete the request.

The target request lifecycle requires the credential to accompany an individual
authenticated request, but this document does not prescribe its transport
representation. Any future request ingress handling must remain narrowly
request-scoped and must not expose, persist, log, or echo the key.

## 5. Authentication and ownership boundary

BYOK does not replace normal application authentication. Every future summary
or plan request must still:

- Authenticate the caregiver using the application's existing authentication
  mechanism.
- Verify that the requested recipient belongs to the authenticated caregiver.
- Prevent one caregiver from using another caregiver's recipient identifier
  to access data.
- Avoid treating possession of a provider API key as proof of caregiver
  identity or recipient ownership.

The provider credential authorizes provider access only. It does not authorize
application data access. These are target requirements, not claims that a BYOK
feature currently enforces them.

## 6. Provider reference catalogue

The following are **Android reference values** from the read-only
`feat/standalone-byok-ai` branch. They document that branch's catalogue only;
they do not imply that all five providers are supported by the current backend
or guarantee that the provider offerings remain current.

| Provider ID | Display name | Default model | Default base URL |
|---|---|---|---|
| `OPENAI` | OpenAI | `gpt-6-luna` | `https://api.openai.com/v1` |
| `ANTHROPIC` | Anthropic | `claude-sonnet-5-5` | `https://api.anthropic.com` |
| `GEMINI` | Gemini | `gemini-3.8-flash` | `https://generativelanguage.googleapis.com` |
| `XAI` | xAI | `grok-4.7` | `https://api.x.ai/v1` |
| `OPEN_ROUTER` | OpenRouter | `openai/gpt-4o-mini` | `https://openrouter.ai/api/v1` |

Provider IDs and base URL defaults are defined by Android's
[`AiProvider` in `LlmModels.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/feat/standalone-byok-ai/android/app/src/main/java/com/caregiver/mobile/ai/LlmModels.kt).
The Android model suggestions are in
[`AiModelCatalog.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/feat/standalone-byok-ai/android/app/src/main/java/com/caregiver/mobile/ai/AiModelCatalog.kt).
These files are reference material only; no Android implementation code is
copied or ported by this issue.

## 7. Provider adapter boundary

The current backend has an OpenAI-compatible
[`ChatCompletionsLlmClient`](../../backend/src/main/java/com/caregiver/ai/ChatCompletionsLlmClient.java).
Anthropic must use its own native adapter. Do not route Anthropic requests
through the existing OpenAI-compatible client and assume compatibility.

Provider-specific request formats, authentication headers, and response
parsing belong inside the corresponding provider adapter. This issue does not
implement adapters.

## 8. Android security reference versus browser security

The Android reference's
[`AiKeyStore.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/feat/standalone-byok-ai/android/app/src/main/java/com/caregiver/mobile/ai/AiKeyStore.kt)
uses encrypted key storage backed by Android Keystore. That platform-specific
implementation is reference material only.

It does not authorize storing browser BYOK credentials in `localStorage`,
`sessionStorage`, IndexedDB, cookies, or any other browser persistence
mechanism. Android Keystore encryption cannot be reused directly in a browser
and is not browser storage guidance.

## 9. Implementation dependencies and status

The related future work is tracked in [#103](https://github.com/alaa157/caregiver-daily-notes/issues/103),
[#104](https://github.com/alaa157/caregiver-daily-notes/issues/104),
[#122](https://github.com/alaa157/caregiver-daily-notes/issues/122),
[#123](https://github.com/alaa157/caregiver-daily-notes/issues/123),
[#125](https://github.com/alaa157/caregiver-daily-notes/issues/125),
[#126](https://github.com/alaa157/caregiver-daily-notes/issues/126), and
[#127](https://github.com/alaa157/caregiver-daily-notes/issues/127). Their
titles and current status are maintained by GitHub; this document does not mark
any of them complete.

Do not describe the target BYOK lifecycle as implemented until the relevant
implementation work has been completed and merged. Documentation alone does
not complete an implementation issue.

## 10. Non-goals

This issue does not:

- Implement a BYOK settings page.
- Implement a connection-test endpoint.
- Add credential fields to request DTOs.
- Add provider adapters.
- Add database tables.
- Add API endpoints.
- Change the current provider configuration.
- Change summary generation.
- Change plan generation.
- Modify Android.
- Add browser key persistence.
- Implement key encryption or key storage.
