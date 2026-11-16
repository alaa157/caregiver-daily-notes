# Android 0.3.7 release notes

Version `0.3.7` (`versionCode` 7) introduces local accounts and per-account care databases for standalone local mode.

## What changed

- Added local account sign-in, registration, account switching, account management, and per-account data isolation.
- On first launch after upgrading from the previous single-user build, the app requires an explicit **claim your data** step before existing notes are exposed.
- Existing notes move into the newly created owner account during that claim step; they are not automatically shared with other accounts.
- Care data is stored in `accounts.db` plus one `notes-<accountId>.db` file per local account.
- The local databases are **not encrypted**. Anyone with access to the device storage or backups may be able to read them.
- AI provider keys are now scoped per account and provider. If keys are not migrated, they must be re-entered for each account.

## Compatibility

- Minimum supported Android version is unchanged: `minSdk 23` (Android 6.0).
- No backend is required for standalone local mode.
