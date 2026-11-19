# DEF-009 — Bearer prefix fails the key-character check: every OpenAI/xAI/OpenRouter model-list fetch rejected

- **Severity:** Major (live model catalog never loads for the three Bearer-style providers; the picker silently falls back to the bundled list)
- **Component:** `android/.../ai/models/OpenAiCompatibleModelListSource.kt`, `ModelListHttp.kt`
- **Environment:** Android JVM suite (Robolectric + MockWebServer), all providers OpenAI / xAI / OpenRouter
- **Discovered:** 2026-11-19, first full JVM suite execution after the import (14 failures in `OpenAiCompatibleModelListSourceTest`, all `Failed(reasonCode=invalid_key)`)

## Root cause

`OpenAiCompatibleModelListSource.fetch` passed `"Authorization" to "Bearer $apiKey"` into `ModelListHttp.get`. The helper validates **every header value** with the key-character rules (no whitespace / ISO controls / non-printable-ASCII) — the space in `Bearer ` guarantees rejection with `invalid_key` **before any network call**. Gemini/Anthropic use space-free header values (`x-goog-api-key`, `x-api-key`) and were unaffected.

The helper contract is intentional (`ModelListHttpTest` pins `"Bearer key"` → `invalid_key`); the completion client (`OpenAiCompatibleLlmClient`) shows the intended pattern: validate the **raw** key, assemble the `Bearer ` prefix at request build, outside the validator.

## Fix

`ModelListHttp.get` gains an optional `bearerKey` parameter: the raw key is validated with the same character rules, and the `Authorization: Bearer <key>` header is assembled only at request build. `OpenAiCompatibleModelListSource` passes `bearerKey = apiKey` instead of a pre-assembled header. Header-value validation is unchanged; the pinned helper contract still holds.

## Verification

`OpenAiCompatibleModelListSourceTest` 14/14 green; full JVM suite green (see execution report).

**Status: fixed.**
