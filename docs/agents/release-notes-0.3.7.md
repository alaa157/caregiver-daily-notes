# Android 0.3.7 release notes

Version `0.3.7` (`versionCode` 7) introduces local accounts and per-account care databases for standalone local mode.

## What changed

- Added local account sign-in, registration, account switching, account management, and per-account data isolation.
- On first launch after upgrading from the previous single-user build, the app requires an explicit **claim your data** step before existing notes are exposed.
- Existing notes move into the newly created owner account during that claim step; they are not automatically shared with other accounts.
- Care data is stored in `accounts.db` plus one `notes-<accountId>.db` file per local account.
- The local databases are **not encrypted**. Anyone with access to the device storage or backups may be able to read them.
- AI provider keys are now scoped per account and provider. If keys are not migrated, they must be re-entered for each account.

## OpenRouter model catalogue (this release line)

- The model picker is now a searchable, full-height sheet. **Other model** is pinned above the
  list, so entering a custom id never means scrolling the whole catalogue. The privacy line
  states whether the list was fetched or bundled, and a failed first load can be retried in place.
- OpenRouter is the only provider with a live model list. Its `/models` response is de-duplicated
  and filtered to interactive text models: `:batch` entries and models with non-text output
  modalities are dropped, and the list is capped at 1,000. Models ending in `:free` or with both
  prompt and completion pricing of zero are tagged **Free**.
- OpenAI, Anthropic, Gemini and xAI stay on their bundled lists; their `/models` routes are not
  called. No model-list request ever sends note text.
- A cached list is discarded when the provider's base URL changes, and closing the picker
  cancels any in-flight model-list request.
- The OpenRouter setup guide now opens `https://openrouter.ai/keys` directly.

## Compatibility

- Minimum supported Android version is unchanged: `minSdk 23` (Android 6.0).
- No backend is required for standalone local mode.
