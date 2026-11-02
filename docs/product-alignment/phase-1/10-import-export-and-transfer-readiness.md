# Import, export, backup and transfer readiness

**Reference:** Android `feat/standalone-byok-ai`, commit `21876285ad808a6518593ea3974c51b6b1f6bb31`. No equivalent web import/export feature was found in main.

## Existing Android flows

| Flow | Format/content | Validation, data preservation and limits | Network / caveats |
|---|---|---|---|
| CSV export | UTF-8 CSV with BOM; 11 columns: date, recipient ID/name, mood, appetite, sleep, mobility, medication taken, pain, fall, note text. Exports full note history. | Notes only: no addenda, plans, medication catalogue/intakes, recipient relationship, settings or AI credentials. IDs/date values included. | Local in release. Import/export service can use API-backed note store in a non-local graph. |
| CSV import | Same note schema; recipient ID preferred, name fallback. | Requires headers, parses booleans, validates dates/fields/recipient mapping, previews before commit. Existing `(recipientId,date)` rows are duplicates and are not overwritten. Local Room commit is transactional; API mode can partially succeed. Import file cap 10 MiB. | Release preserves dates. The API-backed planner limits imports to today because its create-request path does not carry the historic date. |
| Full backup | Versioned `.carebackup.json`, current schema v3, SHA-256 digest over canonical data. | Includes recipients, notes, addenda, plans, medication catalogue and intakes; retains IDs/relationships and archive state. Omits Room row IDs, sync `localId`, outbox, AI keys, preferences and tokens. Restore parses/validates, shows preview, checks a content token to reject concurrent local changes, then replaces restorable Room data transactionally. Import cap 10 MiB. Digest detects accidental corruption, not forgery. | Entirely local and disabled without authoritative local store. |
| Restore | Same versioned JSON; supports older v1/v2 and rejects unsupported future versions. | Full replacement after explicit confirmation; backup payload itself is held in memory during preview. Failure should leave local DB unchanged by transaction contract. | No automatic sync/replay; local only. |
| PDF summary | Locally generated bilingual PDF with recipient, period, generated date, summary text, flags, evidence quotes, recorded mood/pain/sleep observations and uncertainty/disclaimer. | Does not export a full note archive or all structured data. PDF is held in app cache and shared as a FileProvider URI. | User starts system share sheet; nothing is sent automatically by app code. |
| Sharing | Optional plain-text actual summary plus PDF via Android `ACTION_SEND`. | Empty summary text omitted; permission is granted to receiving app for URI. | Recipient app handles subsequent transmission/storage; outside this app's control. |
| Attachments | No note attachment model, upload endpoint, or attachment management screen found. | Not implemented in inspected source. | None. |

Sources: [`CareNoteCsv.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/csv/CareNoteCsv.kt#L35-L120), [`CsvTransferService.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt#L40-L144), [`BackupFormat.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt#L14-L145), [`BackupRestoreService.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupRestoreService.kt#L60-L134), [`CareSummaryPdfExporter.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/export/CareSummaryPdfExporter.kt#L20-L190), [`CareSummaryShare.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/presentation/summary/CareSummaryShare.kt#L22-L82).

## Transfer readiness assessment

No user-selected record/date-range transfer workflow or file-upload endpoint was found. Android's outbox is a retry mechanism for note creation in a server-backed graph, not the product's user-controlled export/transfer feature. It covers notes only, not recipients, addenda, plans, medication records or attachments. More importantly, current checked-in release never selects that graph, and its payload does not satisfy current main's required note date/field names. Current source therefore does **not** establish working Android-to-web transfer.

## Requirements for a future optional transfer phase (not implemented)

- Explicit user choice of entities, recipient(s), date range and included files.
- Preview exact record counts/contents, destination and ownership before send; show consent and privacy impact.
- Stable IDs and source/destination mapping; duplicate/idempotency semantics; no silent local delete, replacement or merge.
- Per-record upload state, retry/backoff, cancellation, partial-failure report, and recovery after app restart.
- Conflict policy for same recipient/date, edited server-side data, inactive recipients and repeated exports.
- Auth/transfer authorization; short-lived server availability; server persistence/retention, deletion, backup and export expectations.
- Versioned structured format/API contract for notes, addenda, plan versions, medication/intakes and attachments, with integrity and size limits.
- Explicitly distinguish server sync from file backup and from sharing a PDF.

Web currently has no local data-import path, so an imported mobile record has no web representation until a separate, verified backend transfer path exists.
