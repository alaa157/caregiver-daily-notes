# DEF-007 — Android: corrupted or missing keys reported as "provider rejected the key"

- **Severity:** Major (misleading recovery guidance; caregiver blames a valid key)
- **Component:** `android/.../ai/OpenAiCompatibleLlmClient.kt` (+ Gemini/Anthropic clients), `AiRecovery`, AI settings
- **Environment:** Rafeek standalone/demo APK (run 22), Revyl cloud Android device, OpenRouter provider
- **Discovered:** 2026-11-18, AI settings "Check setup" reported `Your AI key was rejected` with a verified-valid OpenRouter key.

## Investigation

1. **Typing fidelity:** the 73-character key was re-entered into a visible field and compared character-for-character — exact match. Input method ruled out as a systematic cause.
2. **Provider side:** the app's exact request shape (URL normalization, `Authorization: Bearer`, JSON-mode probe body) replayed from the build VM against OpenRouter → **HTTP 200** `{"ok":true}`. All bundled default model ids confirmed present in OpenRouter's live catalog.
3. **App side:** the clients run a strict preflight — blank key, or any whitespace/control/non-ASCII character, returns `invalid_key` **before any HTTP call**; HTTP 401/403 also maps to `invalid_key`. The settings flow already guards a blank field with `missing_key`, so the observed `invalid_key` implies either a character-level violation (e.g. a corrupted paste) or a genuine provider rejection — and the recovery copy could not distinguish them, telling the caregiver the key was "rejected" in every case.

## Fix

Commit `d08720b7`:

- Blank key → `missing_key` (existing recovery copy: enter a key).
- Whitespace/control/non-ASCII characters → new `invalid_key_characters` code with dedicated EN/AR recovery copy ("contains spaces or unsupported characters — enter it again"), leading to Settings.
- Genuine 401/403 keeps `invalid_key`.
- Regression tests: client preflight split (2 cases) and recovery-copy mapping.

## Verification

- JVM regression tests: preflight split (blank → `missing_key`, corrupted → `invalid_key_characters`) and recovery-copy mapping — green.
- Device (Revyl cloud, APK run 23): the **`missing_key` branch verified live** — with an empty key field, "Check setup" now shows *"No AI key is saved. Add one or select another configured provider in Settings."* and marks Summary format / Accuracy check as Skipped (evidence: `evidence/def-007-missing-key-message.png`, `evidence/def-007-stages-skipped.png`). The old misleading "key was rejected" copy no longer appears for this case.
- The clean-key end-to-end pass could not be completed under cloud-device automation: text injected into the Compose password field via the cloud IME does not reliably commit to the field state before the dialog closes (the same harness quirk explains the original report — partially committed/corrupted text hit the strict character preflight and surfaced as "rejected"). The `invalid_key_characters` branch is covered by the JVM regression tests; a manual clean-key pass on a physical device is the remaining check.

**Status: fixed; one manual verification step remaining (tracked in #1101).**
