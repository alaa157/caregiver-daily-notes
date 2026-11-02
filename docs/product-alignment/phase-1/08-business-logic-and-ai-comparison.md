# Business logic and AI comparison

**Reference revisions:** Android `21876285ad808a6518593ea3974c51b6b1f6bb31`; main `30b01f08bc51c8da2ed11bbc89dba1f3a73dfbe7`.

## AI/provider boundaries

- **Android:** caregiver selects provider/model/base URL and stores a separate API key per provider. Key bytes are encrypted using Android Keystore; consent is provider-specific. After consent, note data is sent directly from device to provider. Prompts can be customized. See [`AiKeyStore`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/ai/AiKeyStore.kt#L36-L130), [`AndroidKeyStoreCipher`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/ai/AndroidKeyStoreCipher.kt#L14-L99), [`AiConsentDialog`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/ai/AiConsentDialog.kt#L10-L24).
- **Web:** browser sends an authenticated summary/plan request to backend; backend owns provider keys/configuration and provider calls. Web parses model output and safety separately. Do not replace Android BYOK with server AI to achieve parity; preserve both configurations.

## Summary date range and data selection

Android supports 7/14/30 days ending today. It narrows notes and signals to the selected recipient and inclusive window before provider calls; `SummaryAiService` repeats recipient/date filtering. Web exposes an editable inclusive end date. Backend also requires an explicit end date and computes the inclusive window.

**Difference:** identical period lengths do not mean equivalent user-visible periods. An Android request has no `periodEndInclusive`, so it cannot satisfy current main's `SummaryRequest`. Android's API route for signals is `/api/recipients/{id}/signals`; main exposes `/api/notes/signals` with `recipientId/from/to`.

## Evidence, uncertainty and failure handling

Android JSON is parsed with an allowlist/size limit; observations must cite an existing in-window note, a nonempty quote found in normalized source text, and a lexically supported claim. It limits returned observations and may only add model urgency. The local no-key path returns an empty summary plus safety flags and `ai_not_configured`; it does not create generated prose.

Backend `SummaryService` constructs a source data block, parses a strict JSON shape, requires a valid note ID and quote present in its source, and returns deterministic safety/trends separately. Backend evidence validation checks quote provenance/presence; Android additionally applies `ClaimGrounding`. Web parses safety independently and retains a prior safety result when later model content is incomplete or invalid. These are related safeguards but not identical algorithms.

## Safety and trend divergence (high-priority)

| Rule | Android evaluator | Backend evaluator | Consequence |
|---|---|---|---|
| Fall | Structured checkbox or recognized English/Arabic free-text phrases. | Structured `fallReported` only. | A text-only fall can flag on Android and not on backend. |
| Pain | Score ≥7 or recognized chest-pain phrase. | Structured score ≥7. | Chest-pain text is an Android-only deterministic path. |
| Breathing / consciousness | Text rules add flags for breathing difficulty and loss of consciousness. | No corresponding deterministic rules found in backend evaluator. | Android can surface flags absent from web/backend. |
| Missed medication | Two or more distinct dates where status is missed. | Two or more distinct dates, but only where not marked unverified. | Unknown/verification semantics can change counts. |
| Poor appetite | Longest **consecutive** run of at least 3 days. | At least 3 distinct dates anywhere in the period. | Scattered days flag in backend; Android requires a streak. |
| Output | String flags; no common severity model. | Typed signal IDs/severity and `needsDoctorBanner`; trends count structured observations. | UI wording and aggregate results differ. |

The Android evaluator comments claim mirrored semantics, but the implementations differ. This is not a medical validation. It is a duplicated product rule requiring an explicit shared specification and test vectors. Evidence: [Android `SafetySignalEvaluator`](https://github.com/alaa157/caregiver-daily-notes/blob/2187628/android/app/src/main/java/com/caregiver/mobile/ai/SafetySignalEvaluator.kt#L1-L108), [backend evaluator](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/ai/SafetySignalEvaluator.java#L1-L69), [backend trends](https://github.com/alaa157/caregiver-daily-notes/blob/30b01f0/backend/src/main/java/com/caregiver/ai/TrendCalculator.java#L1-L50).

## Plan generation and lifecycle

Android plan suggestion receives the generated summary text, filters against a small allowed-action catalog, rejects nonempty model medication proposals, then stores a list of text items in a local version chain. Backend generation uses the owned source notes, authoritative summary/safety, and previous accepted plan; it stores a structured immutable plan version with provenance and protected edit behavior. Web exposes backend generation and lifecycle operations.

Both platforms limit medication changes, but their plan documents, action catalogs, persistence, version semantics, and HTTP contracts differ. Android's plan API routes `/api/plans/...`; main uses `/api/plans/generate` and `/api/recipients/{recipientId}/plans/...`. They cannot be treated as compatible implementations.

## Test evidence and unresolved behavior

Android has unit tests for `SafetySignalEvaluator`, grounding, provider clients, and plan services; backend has evaluator/summary/plan tests. These suites are on separate commits and do not establish parity. No cross-platform golden test or shared contract test was found. Do not infer the run outcome from test source presence.
