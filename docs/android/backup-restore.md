# Backup & Restore (`.carebackup.json`)

Local, on-device backup and restore of the standalone (LOCAL_MODE) data
store. Users create a versioned JSON file through the system document picker
(SAF), preview a picked file, and explicitly confirm a **replace** restore.
Nothing is uploaded, nothing is logged, and no storage permission is needed.

Implementation: `data/backup/` (format, codec, validator, store, service,
SAF I/O) + `presentation/settings/BackupRestore*` (state, UI). Settings group:
**Data & Backup**. Related: [`csv-transfer.md`](csv-transfer.md).

## Data inventory and what is backed up

| Data | Where it lives | In backup | Notes |
| --- | --- | --- | --- |
| Recipients | `recipients` table (`RecipientEntity`) | yes | Authoritative in LOCAL_MODE; `serverId` is the stable id notes reference |
| Notes | `notes` table (`NoteEntity`) | yes | row ids are not serialized; list order is |
| Addenda | `addenda` table (`AddendumEntity`) | yes | `noteId` references a backed-up note |
| Care plans | `plans` table (`PlanEntity`) | yes | `versionsJson` kept verbatim (validated as a JSON array) |
| Outbox | `outbox` table (`OutboxEntity`) | **no** | Pending sync operations are never exported and never touched by a restore (see below) |
| AI keys | `AiKeyStore` (prefs + AndroidKeyStore) | **no** | Secret material; excluded by design |
| Settings | DataStore (language, server URL, theme, AI model) | **no** | Device preferences, not care data |
| Auth tokens | `TokenHolder` (memory only) | **no** | Never persisted |

Nothing else persists care data. The outbox is only ever written in
server-backed graphs (the standalone graph constructs `NoteRepository`
without an outbox DAO), and restore is only offered where a local store
exists, so an exported/imported file can never carry sync operations.

### Why restore is standalone-only

In server-backed graphs Room is a **cache** of server-owned data plus the
outbox. Restoring into a cache would fight the sync contract (stale rows could
be re-uploaded, refreshed away, or duplicated), so `BackupRestoreService` is
created without a store there (`available == false`) and the two Settings
actions are disabled with a localized explanation. Demo graphs have no
database at all and are likewise unavailable.

## File format (v1)

```json
{
  "format": "caregiver-daily-notes.backup",
  "formatVersion": 1,
  "appName": "caregiver-daily-notes",
  "appVersion": "1.0 (10)",
  "createdAt": "2026-10-19T09:00:00Z",
  "integrity": { "algorithm": "SHA-256", "digest": "<64 lowercase hex chars>" },
  "data": {
    "recipients": [
      { "serverId": "…", "name": "…", "active": true }
    ],
    "notes": [
      {
        "serverId": "…", "recipientId": "…", "date": "2026-10-19",
        "mood": "good", "appetite": "good", "sleep": "ok", "mobility": "walks",
        "medicationTaken": "taken", "pain": 2, "fall": false, "text": "…"
      }
    ],
    "addenda": [
      { "id": "…", "noteId": "…", "createdAt": "2026-10-19T18:20:00", "text": "…" }
    ],
    "plans": [
      { "id": "…", "recipientId": "…", "versionsJson": "[…]", "updatedAt": 1700000000000 }
    ]
  }
}
```

- **`integrity.digest`** is the SHA-256 of the *canonical compact JSON of
  `data`*, re-encoded by this app's serializer on both sides. It detects
  corruption and hand-edits, not forgery (the algorithm is public). Files this
  app writes always carry it; a missing block is rejected.
- The parser is **strict**: unknown keys, wrong types and malformed JSON are
  rejected (`UNREADABLE`) without ever trusting the file's extension.
- A record's local order is the contract for `rowId`: recipients keep their
  list order, notes are written in read order (`date DESC, rowId`), so list
  and same-day ordering survive a restore. Room implementation details are
  deliberately not serialized.
- `localId` (outbox sync mapping) is **not** part of the format.
- Text (including Arabic and emoji) is preserved verbatim as UTF-8; a BOM is
  tolerated on read.

### Versioning

`formatVersion` is `1`. The codec accepts exactly v1 and rejects newer files
with a clear "unsupported version" error; older/unknown versions are not
recognized as backups. To add a future version: bump `FORMAT_VERSION`, keep
`CareBackupCodec.decode` able to read v1 (migrating the decoded `BackupData`
to the new shape), and write only the newest version. Update this document and
the format tests.

## Restore pipeline

1. **Read** (SAF, off the main thread). Missing/empty/unreadable/oversized
   (> 10 MB) files fail with a localized message before any parse.
2. **Decode + integrity** (`CareBackupCodec`): format id, version gate,
   strict shape, SHA-256 comparison. The database is not touched.
3. **Validate** (`BackupValidator`): blank/duplicate ids per entity type,
   notes/plans referencing missing recipients, addenda referencing missing
   notes, ISO dates, pain `0..10`, non-empty mood, 32-char option groups, plan
   `versionsJson` is a JSON array. Any issue rejects the whole file.
4. **Preview** (dialog): creation time, app version, format version, record
   counts, the local counts that would be **replaced**, the replacement
   warning, and the exclusions (settings, AI keys, pending sync data are not
   part of backups). Note contents are never shown.
5. **Commit** (explicit confirmation only): one Room transaction in
   `RoomBackupStore.replaceAll` —
   - first re-reads the current counts and **aborts with `RESTORE_CONFLICT`
     when they changed** since the preview (a concurrent write is never
     silently discarded),
   - clears `addenda` → `plans` → `notes` → `recipients`,
   - re-inserts recipients → notes → addenda → plans in file order, with
     `ABORT` conflict strategies so any collision rolls the whole transaction
     back.
   A failure anywhere leaves the database exactly as it was. The outbox table
   is never read or written by this path.

### Restore policy

Explicit, previewed **replace of all four restorable tables**. There is no
merge mode: merging would need ambiguous conflict rules, and the product
guarantee is "what you previewed is exactly what you get". Users are warned
that existing local data is replaced and that this cannot be undone.

## Security

- The file contains personal care data (names, notes). It is **not
  encrypted**; store and share it like any other sensitive document (that is
  also what the settings copy says). Encryption was deliberately left out
  rather than inventing a custom scheme; adding it later would be a new format
  version with an established API (e.g. app-managed key + AES-GCM) documented
  here first.
- No secrets are ever exported: AI keys (AndroidKeyStore-backed), auth tokens
  and settings are excluded.
- Nothing is logged: failures carry reason codes; note text, names and file
  contents never reach log statements or the UI.
- Import trusts nothing: unknown formats, tampered payloads, duplicate ids and
  broken references are rejected before the database is touched, and a bad
  file can only fail — it cannot partially write.

## Testing map

- `data/backup/BackupCodecTest` — envelope round-trip, Arabic/Unicode,
  strict parsing, version gate, digest contract and tamper detection.
- `data/backup/BackupValidatorTest` — every validation rule.
- `data/backup/RoomBackupStoreTest` — snapshot consistency and ordering, atomic
  replace, conflict detection, rollback, outbox untouched.
- `data/backup/BackupRestoreServiceTest` — create/prepare/commit end-to-end,
  conflict after preview, rejection paths, outbox/secret exclusion,
  unavailable-without-store.
- `presentation/settings/BackupRestoreViewModelTest` — notices, preview gating,
  cancel-never-writes, re-entry blocking, unavailable path.
- `core/i18n/StringsParityTest` — every new string exists in `en` and `ar`.
