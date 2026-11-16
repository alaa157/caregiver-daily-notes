# ADR 001: Per-account databases

Status: accepted for LOCAL_MODE local accounts.

## Context

The standalone Android app is moving from a single anonymous/local user to
multiple local accounts on the same device. Care data is sensitive: recipients,
notes, care plans, saved summaries, exports, and AI-provider settings must not
leak between accounts. The app already has a legacy single-user Room database
(`caregiver-db`) that may contain the only copy of a user's notes.

The relevant product decisions are tracked in
[#586](https://github.com/alaa157/caregiver-daily-notes/issues/586),
[#597](https://github.com/alaa157/caregiver-daily-notes/issues/597),
[#615](https://github.com/alaa157/caregiver-daily-notes/issues/615),
[#617](https://github.com/alaa157/caregiver-daily-notes/issues/617), and
[#626](https://github.com/alaa157/caregiver-daily-notes/issues/626).

## Decision

Use one small Room database for the account registry and one Room database per
account for that account's care data:

- `accounts.db` stores account records and the active-account/session state.
- `notes-<accountId>.db` stores one account's recipients, notes, care plans,
  saved summaries, and related local data.
- The active account selects which `notes-<accountId>.db` is open. Switching or
  signing out must swap the per-account database provider rather than merely
  filtering rows.
- AI settings are account-scoped: keys are stored per account and are
  re-entered if they are not migrated.

The legacy `caregiver-db` is not opened under another account. A first-launch
upgrade with legacy data shows the blocking "claim your existing data" flow:
the user explicitly creates an owner account, and only after that account is
created does the migration handoff move the legacy data into that account's
`notes-<accountId>.db`.

## Alternatives considered

### Single database with an `account_id` column

Rejected. It makes isolation depend on every query, DAO, migration, backup,
and export path remembering to filter by `account_id`. One missed predicate can
leak or delete another account's data. Account removal would have to delete
rows across many tables instead of removing one account's data file, and a
single-file corruption or bad migration would affect every account.

### Single database with views per account

Rejected. Views do not isolate storage, deletion, backup, or migration. The
underlying tables still share one file and one schema, so the same cross-account
risk remains while adding view-maintenance complexity.

## Consequences

Positive:

- Strong storage isolation between accounts.
- Removing an account can delete that account's data file instead of trusting
  row-level deletes across the schema.
- Backup/rollback can treat each account's data file as a unit while keeping
  `accounts.db` as the registry that binds files to accounts.
- A compromised or corrupted per-account file is contained to that account.

Costs:

- More database files and open/close lifecycle management.
- Account switching must correctly swap providers and must not keep the old
  account's database open.
- Schema migrations may need to run per account file rather than once globally.
- The database files are **not encrypted**. This ADR does not claim otherwise;
  protection relies on Android device storage controls and the app's stated
  limitations.

## Migration strategy

Migration is explicit, not automatic:

1. `LegacyInstallDetector` performs a read-only check for legacy `caregiver-db`
   state and does not open the legacy database.
2. If `LegacyState.NeedsClaim`, the app blocks the workspace with the claim
   screen. The user must create an owner account; system Back cannot bypass the
   claim.
3. After account creation succeeds, `onClaimed` hands the new account to the
   integration layer, which performs the legacy file move/migration.
4. Only then is the legacy data associated with `notes-<newAccountId>.db`.

Claim-your-data was chosen over auto-provisioning because the legacy database
has no password and no proven owner. Auto-provisioning would either create an
account without the user's intent or silently attach personal notes to an
existing account. The explicit claim makes ownership clear, lets the user choose
the account credentials, and prevents another account from opening the legacy
data first.

## Related decisions

- [#586](https://github.com/alaa157/caregiver-daily-notes/issues/586): local
  account iteration and scoping decisions.
- [#597](https://github.com/alaa157/caregiver-daily-notes/issues/597): local
  session token is a device-local session marker for the active account, not a
  password or server credential.
- [#615](https://github.com/alaa157/caregiver-daily-notes/issues/615): AI keys
  are scoped per account and are not shared across accounts.
- [#617](https://github.com/alaa157/caregiver-daily-notes/issues/617):
  preference inventory decides which settings are global versus account-scoped.
- [#626](https://github.com/alaa157/caregiver-daily-notes/issues/626):
  backup/rollback treats per-account data files as the restore unit while
  preserving the account registry.
