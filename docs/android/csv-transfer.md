# CSV import & export (Data & Backup)

Settings → **Data & Backup** offers two actions over the notes of the active
data source. Both run entirely on the device; the only file involved is the
one the user picks in the system file picker. No broad storage permission is
requested, and note text is never logged or sent anywhere.

## File shape

UTF-8 with a byte-order mark (so spreadsheet software renders Arabic
correctly; the importer strips it), CRLF records, RFC 4180 quoting.

Columns, in this exact export order:

```
date,recipient_id,recipient_name,mood,appetite,sleep,mobility,medication_taken,pain,fall,note_text
```

| Column | Meaning | Import rules |
| --- | --- | --- |
| `date` | ISO local date (`YYYY-MM-DD`) | required; must parse |
| `recipient_id` | stable recipient id | optional if `recipient_name` is present |
| `recipient_name` | display name | matched case-insensitively only when `recipient_id` is empty and exactly one recipient carries that name |
| `mood` | canonical editor value | required, trimmed, lowercased, max 32 chars |
| `appetite`,`sleep`,`mobility`,`medication_taken` | canonical editor values | optional, trimmed, lowercased, max 32 chars |
| `pain` | integer 0–10 | required |
| `fall` | boolean | required; accepts `true/false`, `yes/no`, `y/n`, `1/0`, case-insensitively |
| `note_text` | free text | required, non-blank; commas/quotes/newlines are quoted by the writer |

Export writes every note the active source can see — the whole history, not
the visible range — plus a real record count in the confirmation line. With
zero notes it still writes a header-only file and says so. A note whose
recipient cannot be found keeps its id and exports an empty name; a name is
never fabricated.

Unknown columns are reported and ignored. Duplicate headers, missing required
headers, malformed quoting, empty files, and header-only files are rejected
with an actionable, localized message.

## Import flow

1. The system document picker returns a content URI (both confirmed local and
   pending offline notes are exportable; a pending outbox note is dropped from
   the export only when an identical confirmed note already exists).
2. The file is read (10 MB cap) and validated into a **plan**; nothing is
   written yet.
3. A preview shows total / ready / duplicates / invalid counts, the first few
   invalid rows with a per-row reason, ignored columns, and one dropdown per
   recipient identifier that matched nothing. Each can be mapped to an
   existing recipient or left skipped — the app never invents a recipient and
   never attaches a note to a different person silently.
4. **Import** commits the plan and the summary reports imported / skipped /
   duplicates / rejected plus a readable error when something failed.

Rows that are blank, malformed, or unreadable never crash the import; they are
counted and explained.

## Duplicate policy

The domain treats one note per recipient per day as unique (the backend
rejects a second one with `422 VALIDATION_ERROR`). Imports follow the same
rule: a row whose (recipient, date) already exists — or was already claimed by
an earlier row in the same file — is **skipped**. Nothing is ever overwritten
or deleted, and imported rows get fresh local UUIDs, so a server or outbox id
from another device can never be reused.

## Data source behavior

| Active source | Store | Dates | Write path |
| --- | --- | --- | --- |
| Standalone (`LOCAL_MODE=true`, all shipping variants) | `RoomCsvNoteStore` | CSV date preserved verbatim | one Room transaction; aborts (and rolls back everything) on any failure |
| Server/demo | `ApiCsvNoteStore` | `POST /api/notes` assigns the date, so only rows dated today can import; others are rejected with a visible reason | per-row `apis.notes().create()`, i.e. the normal repository path that uses the offline outbox |
