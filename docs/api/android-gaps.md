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

## Plan edits accept no reason

`POST /api/plans/{id}/versions` takes only `status` and `items`; the
version reason is server-generated (`"<status> via preview."`). The plan
edit screen therefore sends the edited items with the
edited-and-accepted status and omits the design's reason field. A
`reason` request field would close this gap.

## Plan version appends skip transition validation

`POST /api/plans/{id}/versions` (`append`) persists any status string
with no transition check — including onto `Archived` plans, which the
`accept`/`dismiss`/`edit-accept` transitions would reject with a 422.
The client saves edits through `append` (the dedicated `editAccept`
endpoint copies the latest items instead of accepting edited ones, so no
client calls it), meaning edited statuses can land where the transition
state machine would forbid them. Either route edits through the guarded
transition or validate `status` in `append`.

## No bounded home safety aggregate

`GET /api/recipients/{id}/signals` is per-recipient only, so the home
dashboard cannot fetch backend-computed flags (e.g.
`MEDICATION_UNCLEAR`) without per-recipient fan-out. Home derives its
fall/pain display from a single bounded notes-window response instead. A
batch safety endpoint would close this gap.
