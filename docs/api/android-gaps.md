# Android API gaps (client-visible, backend-owned)

Recorded from the Task 5/6 client work. The app renders only what the API
returns; the items below need server-side support and must not be
derived or invented in the mobile client.

## History has no addendum counts

`GET /api/notes` returns `NoteDto` without any correction count, so the
history rows omit them. The client previously issued one `GET
/api/notes/{id}` per row to count addenda; that unbounded fan-out was
removed. If counts are wanted in list context, the history response (or a
dedicated aggregate) should carry them.

## History has no server-side bound

`GET /api/notes` with no filters returns the caregiver's full history.
The client narrows it with the supported `recipientId`/`from`/`to`
filters (the Notes tab always queries a single day); an unfiltered History
load is unbounded by server design. A `limit`/pagination parameter would
let the client bound it explicitly.

## Summaries have no structured trend sections

`POST /api/summaries` returns free `text` plus `redFlags`, `evidence`
quotes, and `uncertainties`. There are no dedicated trends, appetite,
sleep, or medication sections, so the result screen renders exactly those
fields with localized headings and no derived clinical conclusions.

## No bounded home safety aggregate

`GET /api/recipients/{id}/signals` is per-recipient only, so the home
dashboard cannot fetch backend-computed flags (e.g.
`MEDICATION_UNCLEAR`) without per-recipient fan-out. Home derives its
fall/pain display from a single bounded notes-window response instead. A
batch safety endpoint would close this gap.
