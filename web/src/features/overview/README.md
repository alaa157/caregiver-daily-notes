# Workspace overview

`/app` is the authenticated caregiver workspace. It shows the selected calendar date and a factual note-availability row for every owned recipient. The selected date is the `date=YYYY-MM-DD` query parameter; changing it updates browser history, so reload, Back, and Forward restore the overview period.

## Data and semantics

The overview loads owned recipients from `GET /api/recipients`, then loads same-day notes through the existing caregiver-scoped history API:

```text
GET /api/notes?from=YYYY-MM-DD&to=YYYY-MM-DD&page=0&size=100
```

If the result spans more than one page, all pages are read before absence is classified. A found note links to its persisted note ID. An active recipient with no same-day note gets a create-note link with recipient and date prefilled; inactive recipients remain readable but have no ordinary create action.

“No note recorded” means only that the history query returned no note for that recipient and date. It is not a negative observation, missing observation, or risk classification. The overview performs no clinical calculations and does not infer follow-up urgency. History request failure leaves recipient rows visible with an unavailable status and retry action; recipient-list failure uses the normal retryable error state.

Date-only values are retained as `YYYY-MM-DD`, built from local calendar components for the default current day, and formatted using a UTC-midday date to avoid display shifts. No timestamp is substituted for the selected date.

## Related routes

- `/app/recipients`: search and distinct active/inactive groups.
- `/app/recipients/:id`: recipient detail, filtered history, and active-recipient note creation.
- `/app/notes/new?recipientId=<UUID>&date=YYYY-MM-DD`: workflow-prefilled note creation.
- `/app/notes/:id`: immutable original note, corrections, and recipient context.
- `/app/notes`: history and filters; `/app/notes/summary`: existing factual summary/trends.

The overview uses the existing React Router shell and API client; it adds no backend endpoint or clinical logic. English/Arabic strings use the shared i18n provider. Recipient names are isolated with `<bdi>`, controls remain labelled, and the list collapses to one column on narrow screens.

## Verification

Component tests cover date-scoped history calls, stable pagination before missing-note classification, persisted note links, inactive-recipient restrictions, partial failures/retry, and query-driven date changes. The full Web check commands are in [`web/README.md`](../../../README.md).
