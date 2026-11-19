# DEF-008 — Setup check resolves to missing_key although a key is entered

- **Severity:** Major (blocks the AI setup happy path on cloud devices; needs investigation to separate app state bug from automation artifact)
- **Component:** `android/.../presentation/settings/AiSettingsScreen.kt` (key dialog → `startSetupCheck`)
- **Environment:** Rafeek standalone APK run 23, Revyl cloud Android device, EN locale, demo (offline) mode
- **Discovered:** 2026-11-19, during DEF-007 clean-key verification

## Repro (observed repeatedly, 4+ runs)

1. AI settings → provider OpenRouter → API key dialog.
2. Inject the 73-character OpenRouter key via the cloud IME (`--text-env`); the dialog field reports **73 masked characters** before closing.
3. Tap OK. The API key row shows the masked-key indicator (`••••••••`, rendered when `apiKey.isNotBlank()`).
4. Tap **Check setup** (hierarchy-derived coordinates; button visually enabled, no overlay).
5. Result panel: *"No AI key is saved. Add one or select another configured provider in Settings."* — the `missing_key` branch — with Summary format / Accuracy check **Skipped**.

The mask indicator is present immediately before **and after** the tap (evidence: `evidence/def-008-mask-present-after-tap.png`, `evidence/def-008-missing-key-with-entered-key.png`), yet `startSetupCheck`'s `apiKey.isBlank()` guard fired.

## Analysis

- The dialog TextField is live-bound to the screen state (`value = apiKey, onValueChange = { apiKey = it }`); OK only closes the dialog. The row mask and the guard read the **same** state variable, so the observed combination should be impossible in a single composition instance.
- Candidate explanations, unconfirmed: (a) the tap does not reach the button (hierarchy/touch coordinate divergence on the cloud device) and the visible panel is stale from an earlier empty-field check — note `keyError` is only cleared in `loadProvider`, so a stale red message can persist across checks; (b) two composition instances during a navigation/dialog transition (mask from one, click handled by another with blank state); (c) a genuine state-loss path not yet identified.
- The same cloud-IME path previously produced corrupted-key symptoms (the original DEF-007 report), so input-fidelity interactions on this harness remain suspect.

## What is NOT affected

- DEF-007's classification fix is verified: empty field → `missing_key` copy (this and prior runs), corrupted characters → `invalid_key_characters` (JVM regression tests), provider 401/403 → `invalid_key` (unchanged path).
- Backend/web BYOK flows are unaffected (web connection-test suite green; live OpenRouter plan generation verified from the VM).

## Next steps

1. Manual run on a physical device: enter the key by hand, Check setup — separates harness artifact from app bug.
2. If it reproduces manually: instrument `startSetupCheck` entry (key length only, never the key) and the dialog dismiss path; check for duplicate composition instances and `keyError` staleness.
3. Clear `keyError` when a new setup check starts (cosmetic fix regardless of root cause).

**Status: closed by owner decision (2026-11-19).** Remaining verification deferred; findings and evidence preserved above. The automated emulator regression (AiSettingsKeyCommitUiTest) remains in the suite.
