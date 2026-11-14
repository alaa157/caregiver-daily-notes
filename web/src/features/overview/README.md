# Workspace overview

`/app` is the authenticated caregiver workspace. It shows the selected calendar date and a factual note-availability row for every owned recipient. The selected date is the `date=YYYY-MM-DD` query parameter; changing it updates browser history, so reload, Back, and Forward restore the overview period.

## Data and semantics

The overview presents each recipient as a compact summary card in its accessible recipient list. Cards are presentational: they receive the hook's `OverviewRow` directly and do not fetch data or own date routing. The exported `SummaryRow` contract in `summaryContract.ts` is a separate, read-only projection for future consumers: recorded requires a matching recipient and selected-date note, missing means the successful history request confirmed absence, and error remains distinct from missing. It uses the authenticated overview/API results (the server scopes recipient and note reads to the caregiver); the mapper exposes no extra note fields. The current note API provides `createdAt` (entry time), not an observation timestamp, so `observedAt` remains `null` rather than mislabeling entry time. Loading, recipient-list errors, and explicit history `partial` states are preserved in `SummaryState`; partial state retains all known rows and the existing history error. Recipient-level error rows in otherwise ready input also promote the result to `partial`, without fabricating missing error details. The overview loads owned recipients from `GET /api/recipients`, then loads same-day notes through the existing caregiver-scoped history API:

```text
GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=100
```

If the result spans more than one page, all pages are read before absence is classified. A found note links to its persisted note ID. An active recipient with no same-day note gets a create-note link with recipient and date prefilled; inactive recipients remain readable but have no ordinary create action.

“No note recorded” means only that the history query returned no note for that recipient and date. It is not a negative observation, missing observation, or risk classification. The overview performs no clinical calculations and does not infer follow-up urgency. History request failure leaves recipient rows visible with an unavailable status and retry action; recipient-list failure uses the normal retryable error state.

The overview list, selected-date summary cards, and “Notes this month” metric have separate state. A recipient-list failure is an overview error. A history-page failure keeps verified notes and recipients visible, marks the overview partial, and leaves unverified row statuses unavailable; it never classifies them as missing. Its retry reloads the overview resource only. The KPI cards retain the known recipient total but make both note-status counts unavailable while the overview is partial.

The month-to-date window is the first day of the selected date's month through the selected date, inclusive (for example, `2026-11-11` queries `2026-11-01` through `2026-11-11`). The window is derived directly from the validated `YYYY-MM-DD` string, without timezone conversion. The total counts notes returned across every successful history page; failure on any page leaves the metric unavailable rather than displaying a partial count or zero. A true zero is shown only when all pages succeed and return no notes. The metric's retry reloads only its own resource; it does not reload the overview or KPI data. The existing caregiver-scoped history query filters by note date and caregiver, not recipient activity, so notes for archived or inactive recipients are included if the API returns them.

Date-only values are retained as `YYYY-MM-DD`, built from local calendar components for the default current day, and formatted using a UTC-midday date to avoid display shifts. No timestamp is substituted for the selected date.

## Selected-date summary cards

The summary cards use the same overview rows and count all recipients shown, including archived, inactive, and legacy-inactive recipients. “Notes recorded” counts rows classified as `recorded`; “Not yet recorded” counts rows classified as `missing`. If the recipient list fails, all three values are unavailable. If any note status is unverified, the recipient total remains available, but both note-status counts are unavailable rather than showing partial or zero counts.

## Recent activity feed

The feed is independent of the selected `?date=`; it shows the caregiver's most recent activity across all dates. Feed loading and refresh do not change that URL parameter or the selected-date overview rows. It uses the read-only `GET /api/activity?limit=10` endpoint through the existing authenticated API client. The backend derives caregiver scope from the authenticated request and reads a bounded maximum of 20 rows per source (notes, addenda, corrections), then merges and sorts by `createdAt` newest-first, with descending ID and kind as deterministic tie-breakers. The UI displays at most 10 items and resolves names only from the already-loaded recipient rows, using a neutral fallback while recipient data is loading, unavailable, or does not contain that recipient. Timestamps use the current EN/AR locale.

The presentation-only `DashboardActivityItem` projects the existing `ActivityItem`: API `kind` maps to presentation `type`, and `state` is `ready` for returned items. Its `summary` is only the localized kind label; recipient names remain a separate display field. It never summarizes clinical content or fetches enrichment data. Feed-level loading and errors remain in `ActivityFeedState`, with no placeholder item rows. Any request failure is a feed-level error (never an empty or partial feed); an empty message appears only after a successful empty response. Refresh aborts and supersedes the prior request.

## Related routes

- `/app/recipients`: search and distinct active/inactive groups.
- `/app/recipients/:id`: recipient detail, filtered history, and active-recipient note creation.
- `/app/notes/new?recipientId=<UUID>&date=YYYY-MM-DD`: workflow-prefilled note creation.
- `/app/notes/:id`: immutable original note, corrections, and recipient context.
- `/app/notes`: history and filters; `/app/notes/summary`: existing factual summary/trends.

The overview uses the existing React Router shell and API client; the activity endpoint returns metadata only and adds no clinical logic. English/Arabic strings use the shared i18n provider. Recipient names are isolated with `<bdi>`, controls remain labelled, and the list collapses to one column on narrow screens.

## Localization and direction

Every string on this screen comes from the shared i18n provider, and both locales define the same dashboard key set. Because some dashboard keys are shared with the recipient, notes, history, and summary features (for example `recipientsTitle`, `recipientCreate`, `recipientsEmpty`, `retry`, and the `historyTitle` routine link), they are translated once in their own translation modules rather than duplicated here. The routine tile for the notes workflow links to `notesCreate` ("Record a daily note"), which is the existing notes-create key, not a dashboard-specific one.

Counts are rendered through `Intl.NumberFormat` with the active locale, so Arabic uses Arabic-Indic digits, and they are wrapped in `<bdi>` so a number never reorders the surrounding Arabic sentence. Dates and timestamps use `Intl.DateTimeFormat` with the active locale while the `dateTime` attribute stays the machine-readable ISO value. Locale-formatted date output carries `dir="auto"` so the mixed date/time punctuation isolates correctly in RTL, and the native date input stays `dir="ltr"` because it accepts ISO digits.

Dashboard CSS uses logical properties only (`margin-block-*`, `padding-inline`, `margin-inline-start`, `border-block-*`), so the layout mirrors with the document direction instead of a parallel RTL stylesheet. The routine arrow uses `directional-icon`, which `[dir='rtl']` mirrors in `base.css`. The provider sets `lang` and `dir` on the document element when the locale changes.

## Verification

Component tests cover card rendering for recorded, missing, and unavailable statuses; date-scoped history calls; stable pagination before missing-note classification; selected-date MTD and complete pagination; unavailable states on partial failures; cancellation; persisted note links; inactive-recipient restrictions; retry; and query-driven date changes. The full Web check commands are in [`web/README.md`](../../../README.md).
