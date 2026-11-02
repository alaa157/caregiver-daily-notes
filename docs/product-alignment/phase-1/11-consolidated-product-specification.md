# Consolidated product specification (Android-based reference)

This document states the intended shared product capabilities from the Android reference, while preserving mobile platform-specific offline behavior. It is not a design for replacing Room or for implementing the missing transfer path.

## Product principles

1. Android remains useful without a running backend; Room remains its primary store. Creating, reading, editing/managing local data, local backup and restore do not depend on server availability.
2. Android BYOK stays direct-to-provider and user-controlled. Provider-specific consent is required before notes are sent. Web AI remains backend-managed; provider infrastructure may differ by platform.
3. A server is an optional bridge/processing layer for web and explicit transfer, not a prerequisite for Android.
4. Data sent to a backend must be user-selected, previewed, and traceable. Transfer must never silently delete or replace local records.
5. Care observations and AI output are not medical diagnosis/certification. Deterministic flags and model statements remain distinguishable.

## Shared capabilities and expected behavior

| Capability | User goal and expected behavior | Data/rules and offline expectation |
|---|---|---|
| Recipients | Create, list, inspect, edit user-maintained recipient details; archive/deactivate without losing history. Permanent deletion, where offered locally, requires explicit confirmation and impact preview. | Stable IDs; recipient owns notes/plans/medication data. Android local actions remain available offline. Product must decide whether archive/deactivate are one concept and whether web ever supports permanent delete. |
| Daily note | Record one day of observations for a recipient; display immutable history and allow later corrections as append-only addenda. | Shared canonical fields must cover mood, appetite, sleep, mobility, medication status/verification, pain 0–10, fall and free text; data may be unrecorded. Business date is a local calendar date. Android reference assigns today; web lets user choose a date. Decide if one note per recipient/day is mandatory (backend enforces it; Room schema does not declare it). |
| Addendum | Add late information/correction without rewriting the original. Show original and addenda with dates and preserve provenance. | Android reference's addendum is text-only; web/backend use structured partial observation fields. Product should select a canonical capability without erasing the Android history semantics. |
| History | Find and open recipient notes by date/recipient; present pending/local versus transferred state honestly if transfer is later added. | Web currently supports filters/pagination; mobile UI is simpler. Offline mobile history remains available from Room. |
| Summary | Generate a selected-period summary with source-linked evidence, uncertainty, and deterministic safety result. Supported periods are 7/14/30 inclusive days. | Android reference ends window today; web allows an explicit end date. Android BYOK vs backend-managed web AI remains platform-specific. No-key/offline Android still shows deterministic safety, not a fabricated AI narrative. |
| Safety/trends | Compute deterministic signals from structured observations; keep model content from suppressing them. Clearly show incomplete/uncertain data and source. | Android currently includes text detection and consecutive-appetite semantics; backend differs. The exact rule set and severity mapping need a reviewed, shared specification and tests before calling outputs consistent. No medical validity is implied. |
| Care plan | Offer constrained, reviewable follow-up suggestions; user accepts, dismisses or edits permitted parts; preserve versions and source references. | Medication changes are prohibited by the Android lock. Web/backend uses structured plan documents and protected edits; Android stores text items. Align state vocabulary/version provenance while preserving data and the Android medication lock. |
| Medication tracker | Track medication details and per-dose intake history for each recipient. | Android-only today; local functionality must not require backend. Decide if/when user-selected transfer to a compatible web view is supported. |
| Language/accessibility | Offer English/Arabic and correct RTL/bidi handling; preserve platform-native layouts and navigation. | Align meaning and terminology across translations; mobile may keep native bottom tabs/themes while web uses responsive navigation. |
| Portability | Back up/restore local records; export/import notes; optionally share a human-readable summary. | Distinguish backup, CSV, PDF share and server transfer. Restore is explicit and atomic; user data and IDs remain intact. |
| Settings | Let user control language/theme and Android provider settings, including consent and key management. | Android BYOK settings remain device-local; web does not expose provider secrets. |

## Error and edge expectations

- Network absence must not block local Android reads/writes or local backup/restore.
- Failed/ambiguous writes must not be presented as confirmed success. Use idempotency, state refresh and clear retry/review behavior.
- Invalid/malformed AI output cannot erase deterministic safety; unsupported claims are rejected or marked uncertain.
- Duplicate dates, recipient deletion, restore conflicts, invalid imports, unsupported backup versions, invalidated keys and unavailable providers should be explicit and non-destructive.
- Transferred data should keep source IDs, ownership/provenance and append-only correction history.

## Unresolved product decisions

1. Canonical note/addendum field schema, requiredness, nullability and enum terms.
2. Recipient relationship as shared field and common archive/deletion behavior.
3. Whether daily note uniqueness by recipient/date is a product rule.
4. Summary end-date semantics and shared safety thresholds/severity.
5. Plan action catalog, wire format, version semantics and evidence relationship.
6. Account expectations for Android versus local-only standalone use.
7. Server data retention, backup, export and deletion policy, especially for temporary demos.
8. What user-directed transfer includes and how conflicts are resolved.

Until these are resolved, the specification must not imply that Android and web have equivalent behavior or that current mobile sync works.
