# Phase 1 findings and open questions

**Scope:** Android `feat/standalone-byok-ai` commit `21876285ad808a6518593ea3974c51b6b1f6bb31`; web/backend `main` commit `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7`. Static source and test inspection only; no branches changed, no app code/config edited, no tests run during this documentation pass.

## Prioritized findings

### CRITICAL

1. **Android and main backend wire contracts are incompatible.** Android creates notes without `date` and uses different fields; expects a `List` history response rather than Spring `Page`; calls an unimplemented signal route; sends a summary request without required end date and expects a flat DTO instead of the nested response; calls plan routes not exposed by main; and sends text-only addenda where main requires structured fields. This is source-level contract evidence, not a runtime test. Current server sync cannot be treated as working.
2. **No checked-in Android build selects the backend graph.** Debug/minified/release all use `LOCAL_MODE=true`; release is Room-backed and standalone. Login, Retrofit/cache and outbox do not describe the shipped release path. This preserves the offline goal but leaves Android/web shared-data integration unimplemented.
3. **Duplicated safety logic does not produce equivalent rules.** Android detects selected signals from free text and requires consecutive appetite days; backend uses structured inputs and counts any three distinct poor-appetite dates. Medication verification semantics also differ. This is a product consistency risk, not a statement of clinical accuracy.
4. **No user-selected Android-to-server transfer exists.** Local backup/CSV do not upload data; outbox is note-only, not selected transfer, and is not active in a build variant. The requirement for explicit data selection and no silent deletion is unmet by current implementation.

### HIGH

5. **Data/lifecycle models diverge.** Mobile local records are largely non-null string fields and local recipient deletion erases dependent data; backend notes are nullable structured data and recipient deactivation preserves history. Android local medication/intakes and plans have no corresponding backend medication model or same plan schema.
6. **Important mobile features are absent from web.** Medication tracking, theme palettes, full backup/restore, CSV and PDF/share have no matching web flows. Conversely, recipient editing exists on web but not Android.
7. **Current web lacks registration UI.** Backend has a register endpoint; web exposes login only. Android's current release bypasses authentication entirely.
8. **Test/CI evidence is fragmented.** Android API fixture tests describe legacy DTOs and do not execute against current main. Main CI runs backend tests; web unit/build and Android tests are not part of that workflow. Main Docker job is `continue-on-error`; runtime deployment and retention need explicit confirmation.

### MEDIUM

9. **Android care-trends composable is an empty, unregistered placeholder.** Web shows structured trends inside summary results, not as the same standalone mobile feature.
10. **Presentation/customization differs.** Both support English/Arabic, but field vocabulary, addendum behavior, summary output, themes and recipient lifecycle are not equivalent. A bilingual terminology review and accessible interaction review remain.
11. **Backend persistence policy is unspecified by source.** Current configuration requires PostgreSQL and Flyway, but no product retention, backup, deletion/export or temporary-server availability policy was established.
12. **README references `docs/api/android-gaps.md`, which was not present in the inspected Android tree.** It should not be used as evidence in place of current contracts.

## Open questions for manual/product confirmation

- Which Android runtime mode is intended for demonstration and release, and should any supported optional server-backed build be published?
- Does a note represent exactly one recipient/day? The server enforces uniqueness; Android Room does not declare the constraint.
- Are recipient relationship, medication verification, and structured addenda required shared data?
- Should local archive and backend deactivation be one lifecycle concept? Is permanent local deletion compatible with future transfer, retention and recovery?
- Should the shared summary window always end today, or may users select a historical end date?
- What deterministic rule set is authoritative, especially free-text detection, appetite streaks, unverified medication and severity labels? Clinical review is outside this code inspection.
- What are the account-registration/recovery requirements for web, and is mobile authentication intentionally out of scope?
- What server retention/backup/deletion guarantees apply when the backend is temporary or unavailable?
- Which records/files must be user-selectable for transfer, and what conflict/duplicate/partial-failure policy is acceptable?

## Summary

The Android branch is a substantial standalone mobile application with local persistence, BYOK, migration-tested Room data, local export/restore, medication tracking and AI safeguards. Main is a separate server-backed web application with owner-scoped API, JWT auth, structured notes, history filters, summaries, safety/trends and plan lifecycle. They share product concepts, not a verified data contract or shared business-logic implementation. Android offline-first and BYOK requirements can remain intact, but backend/mobile compatibility and parity must be treated as future work rather than assumed.

## Evidence index

- Android build and graph: [`build.gradle.kts`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/build.gradle.kts#L52-L87), [`CaregiverApp.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/CaregiverApp.kt#L84-L175).
- Android API DTOs: [`NoteApi.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/api/NoteApi.kt#L14-L113), [`SummaryApi.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/api/SummaryApi.kt#L10-L54), [`PlanApi.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/api/PlanApi.kt#L9-L55).
- Backend API: [`NoteController.java`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/notes/NoteController.java#L5-L15), [`SummaryRequest.java`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/ai/SummaryRequest.java#L1-L25), [`PlanController.java`](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/plans/PlanController.java#L12-L57).
- Safety divergence: [Android evaluator](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/ai/SafetySignalEvaluator.kt#L1-L108), [backend evaluator](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/ai/SafetySignalEvaluator.java#L1-L69).
- Transfer scope: [`BackupFormat.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/backup/BackupFormat.kt#L14-L145), [`CsvTransferService.kt`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/data/csv/CsvTransferService.kt#L40-L144).
